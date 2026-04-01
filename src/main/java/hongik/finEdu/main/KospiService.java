package hongik.finEdu.main;

import hongik.finEdu.common.exception.BusinessException;
import hongik.finEdu.common.exception.ErrorCode;
import hongik.finEdu.main.dto.KospiResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class KospiService {

    private static final String NOW_VALUE_SELECTOR = "em#now_value";

    private final StringRedisTemplate redisTemplate;

    @Value("${kospi.naver-url:https://finance.naver.com/sise/sise_index.naver?code=KOSPI}")
    private String naverUrl;

    @Value("${kospi.redis-key:market:kospi:now}")
    private String redisKey;

    @Value("${kospi.cache-ttl-seconds:60}")
    private int cacheTtlSeconds;

    @Value("${kospi.connect-timeout-ms:10000}")
    private int connectTimeoutMs;

    /**
     * Redis에 값이 있으면 그대로 반환, 없으면 네이버에서 수집 후 Redis에 TTL 저장.
     */
    public KospiResponseDto getKospi() {
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null && !cached.isBlank()) {
            BigDecimal value = parseDecimal(cached);
            return KospiResponseDto.builder()
                    .value(value)
                    .formatted(formatKospi(value))
                    .cached(true)
                    .at(Instant.now())
                    .build();
        }

        BigDecimal value = fetchFromNaver();
        redisTemplate.opsForValue().set(redisKey, value.toPlainString(), Duration.ofSeconds(cacheTtlSeconds));
        log.info("[KOSPI] 네이버 수집 후 Redis 저장, value={}, ttl={}s", value, cacheTtlSeconds);

        return KospiResponseDto.builder()
                .value(value)
                .formatted(formatKospi(value))
                .cached(false)
                .at(Instant.now())
                .build();
    }

    private BigDecimal fetchFromNaver() {
        try {
            Document doc = Jsoup.connect(naverUrl)
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(connectTimeoutMs)
                    .get();

            Element now = doc.selectFirst(NOW_VALUE_SELECTOR);
            if (now == null) {
                throw new BusinessException(ErrorCode.KOSPI_FETCH_FAILED, "#now_value 없음");
            }
            String raw = now.text().trim().replace(",", "");
            if (raw.isEmpty()) {
                throw new BusinessException(ErrorCode.KOSPI_FETCH_FAILED, "지수 텍스트 비어있음");
            }
            return new BigDecimal(raw);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[KOSPI] 네이버 수집 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.KOSPI_FETCH_FAILED, e.getMessage(), e);
        }
    }

    private static BigDecimal parseDecimal(String plain) {
        try {
            return new BigDecimal(plain.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.KOSPI_FETCH_FAILED, "캐시 값 파싱 실패: " + plain);
        }
    }

    private static String formatKospi(BigDecimal value) {
        DecimalFormat fmt = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));
        return fmt.format(value);
    }
}
