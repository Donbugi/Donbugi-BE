package hongik.finEdu.trading.stream;

import hongik.finEdu.trading.engine.TradeExecutedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 체결 알림 실시간 푸시 (SSE).
 *
 * 체결 커밋 → Redis Pub/Sub 발행 → 모든 인스턴스가 수신 → 해당 사용자의 로컬 SSE 연결로 전송.
 * 체결 엔진이 도는 인스턴스와 사용자가 연결된 인스턴스가 달라도 전달된다.
 */
@Slf4j
@Component
public class TradingEventStream implements MessageListener {

    public static final String CHANNEL = "finEdu:trading:events";
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public TradingEventStream(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        Runnable remove = () -> {
            List<SseEmitter> list = emitters.get(userId);
            if (list != null) {
                list.remove(emitter);
            }
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
            remove.run();
        }
        return emitter;
    }

    /** 체결 트랜잭션 커밋 후에만 발행 (롤백된 체결이 알림으로 나가지 않도록) */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradeExecuted(TradeExecutedEvent event) {
        try {
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("[SSE] 체결 이벤트 발행 실패: {}", e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            TradeExecutedEvent event = objectMapper.readValue(json, TradeExecutedEvent.class);
            List<SseEmitter> list = emitters.get(event.userId());
            if (list == null || list.isEmpty()) {
                return;
            }
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().name("trade").data(json));
                } catch (Exception e) {
                    list.remove(emitter);
                }
            }
        } catch (Exception e) {
            log.warn("[SSE] 메시지 처리 실패: {}", e.getMessage());
        }
    }
}
