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
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
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
 *   <li>컨테이너는 JVM 당 1번만 띄우는 싱글톤. @Container 로 두면 테스트 클래스마다 컨테이너가 내려가는데
 *       Spring 은 캐시된 컨텍스트(이전 컨테이너 포트)를 재사용해서 두 번째 클래스부터 연결 거부로 실패한다.</li>
 *   <li>시계는 2026-10-29(목) 10:00 서울 — 접속매매 시간으로 고정</li>
 *   <li>네이버 시세는 mock (삼성전자 70,000원)</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Import(IntegrationTestSupport.FixedClockConfig.class)
public abstract class IntegrationTestSupport {

    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        // Docker 가 없으면 띄우지 않는다 (@Testcontainers(disabledWithoutDocker) 가 테스트를 skip)
        if (DockerClientFactory.instance().isDockerAvailable()) {
            postgres.start();
            redis.start();
        }
    }

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
