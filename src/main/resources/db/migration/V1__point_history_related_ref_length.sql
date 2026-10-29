-- 기존 PointHistorySchemaMigration(ApplicationRunner) 를 Flyway 로 이관.
-- 포인트 claimKey(예: dailyquiz:{userId}:{date}:slot:1) 를 related_ref 에 저장하므로 220자로 확장.
-- 모든 마이그레이션은 빈 DB(테스트/신규 환경)에서도 안전하도록 IF EXISTS 로 작성한다.
-- (빈 DB 에서는 Hibernate ddl-auto 가 엔티티 기준으로 테이블을 생성)
ALTER TABLE IF EXISTS point_history ALTER COLUMN related_ref TYPE varchar(220);
