package hongik.finEdu.support;

import hongik.finEdu.trading.client.NaverStockClient;
import hongik.finEdu.trading.client.StockQuote;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 통합 테스트 공통 설정.
 * <ul>
 *   <li>PostgreSQL 16 + Redis 7 컨테이너 (Docker 없으면 테스트 자체를 skip)</li>
 *   <li>시계는 2026-10-29(목) 10:00 서울 — 접속매매 시간으로 고정</li>
 *   <li>네이버 시세는 mock (삼성전자 70,000원)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(IntegrationTestSupport.FixedClockConfig.class)
public abstract class IntegrationTestSupport {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 10, 0);

    @MockitoBean
    protected NaverStockClient naverStockClient;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedTestClock() {
            return Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        }
    }

    @BeforeEach
    void stubQuotes() {
        when(naverStockClient.fetchQuote(anyString())).thenAnswer(inv -> quote(inv.getArgument(0)));
        when(naverStockClient.fetchQuoteDetail(anyString())).thenAnswer(inv -> quote(inv.getArgument(0)));
    }

    protected static StockQuote quote(String code) {
        return new StockQuote(code, code, new BigDecimal("70000"), new BigDecimal("0.50"), new BigDecimal("350"),
                "RISING", new BigDecimal("69800"), new BigDecimal("70500"), new BigDecimal("69500"), 1_000_000L, "OPEN");
    }
}
