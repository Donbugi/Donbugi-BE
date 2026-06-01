package hongik.finEdu.points.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 포인트 적립 시 claimKey(예: newsdetail-quiz:{userId}:{articleId})를 related_ref에 저장하는데,
 * 초기 스키마가 varchar(36)이면 500 오류가 난다. 기동 시 컬럼 길이를 220으로 맞춘다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointHistorySchemaMigration implements ApplicationRunner {

    private static final int TARGET_LENGTH = 220;

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer currentMax = jdbcTemplate.queryForObject("""
                    SELECT character_maximum_length
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'point_history'
                      AND column_name = 'related_ref'
                    """, Integer.class);
            if (currentMax != null && currentMax >= TARGET_LENGTH) {
                return;
            }
            jdbcTemplate.execute(
                    "ALTER TABLE point_history ALTER COLUMN related_ref TYPE varchar(" + TARGET_LENGTH + ")");
            log.info("[schema] point_history.related_ref → varchar({})", TARGET_LENGTH);
        } catch (Exception e) {
            log.warn("[schema] point_history.related_ref migration skipped: {}", e.getMessage());
        }
    }
}
