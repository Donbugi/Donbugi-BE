package hongik.finEdu.points.service;

import hongik.finEdu.points.dto.PointActivityItemDto;
import hongik.finEdu.points.entity.PointHistoryEntry;
import hongik.finEdu.points.repository.PointHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;

/**
 * 사용자별 최근 적립·사용 내역 3건을 Redis에 JSON으로 보관한다.
 * DB {@link PointHistoryEntry}가 소스 오브 트루스이며, 적립/교환 후 {@link #refreshFromDb(String)}로 갱신한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointActivityCacheService {

    private static final int RECENT_LIMIT = 3;

    private final StringRedisTemplate stringRedisTemplate;
    private final PointHistoryRepository pointHistoryRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.points.activity-redis-key-prefix:finEdu:points:activity:}")
    private String redisKeyPrefix;

    @Value("${app.points.activity-cache-ttl-hours:168}")
    private long activityCacheTtlHours;

    public void refreshFromDb(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }
        String uid = userId.trim();
        List<PointHistoryEntry> rows = pointHistoryRepository.findByUserIdOrderByOccurredAtDesc(
                uid, PageRequest.of(0, RECENT_LIMIT));
        List<PointActivityItemDto> dtos = rows.stream()
                .map(PointActivityCacheService::toDto)
                .toList();
        try {
            String json = objectMapper.writeValueAsString(dtos);
            stringRedisTemplate.opsForValue().set(redisKey(uid), json,
                    Duration.ofHours(activityCacheTtlHours));
        } catch (Exception e) {
            log.error("[포인트] Redis 최근 내역 저장 실패 userId={}", uid, e);
        }
    }

    /**
     * Redis → 없거나 깨지면 DB에서 {@link #refreshFromDb} 후 재조회.
     */
    public List<PointActivityItemDto> getRecent(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        String uid = userId.trim();
        String key = redisKey(uid);
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            refreshFromDb(uid);
            json = stringRedisTemplate.opsForValue().get(key);
        }
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<PointActivityItemDto>>() {});
        } catch (Exception e) {
            log.warn("[포인트] Redis 역직렬화 실패, DB 기준으로 재구성 userId={}", uid, e);
            refreshFromDb(uid);
            String again = stringRedisTemplate.opsForValue().get(key);
            if (again == null || again.isBlank()) {
                return List.of();
            }
            try {
                return objectMapper.readValue(again, new TypeReference<List<PointActivityItemDto>>() {});
            } catch (Exception e2) {
                log.error("[포인트] 최근 내역 조회 실패 userId={}", uid, e2);
                return List.of();
            }
        }
    }

    private String redisKey(String userId) {
        return redisKeyPrefix + userId;
    }

    private static PointActivityItemDto toDto(PointHistoryEntry e) {
        return new PointActivityItemDto(
                e.getDelta() >= 0 ? "EARN" : "SPEND",
                e.getDelta(),
                e.getTitle(),
                e.getDetail(),
                e.getRelatedRef(),
                e.getOccurredAt()
        );
    }
}
