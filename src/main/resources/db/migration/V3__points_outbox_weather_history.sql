-- 포인트 교환 멱등키 + 메일 outbox, 경제 날씨 일별 이력

ALTER TABLE IF EXISTS point_redemption ADD COLUMN IF NOT EXISTS idempotency_key varchar(64);
DO $$
BEGIN
    IF to_regclass('point_redemption') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_redemption_idempotency') THEN
        ALTER TABLE point_redemption
            ADD CONSTRAINT uk_redemption_idempotency UNIQUE (user_id, idempotency_key);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS point_mail_outbox (
    id              bigserial PRIMARY KEY,
    redemption_ref  varchar(36)  NOT NULL,
    user_id         varchar(64)  NOT NULL,
    email           varchar(320) NOT NULL,
    benefit_code    varchar(40)  NOT NULL,
    points_spent    integer      NOT NULL,
    balance_after   integer      NOT NULL,
    status          varchar(10)  NOT NULL,
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamp(6) NOT NULL,
    last_error      varchar(500),
    created_at      timestamp(6) NOT NULL,
    sent_at         timestamp(6),
    CONSTRAINT uk_mail_outbox_ref UNIQUE (redemption_ref)
);
CREATE INDEX IF NOT EXISTS idx_mail_outbox_status_next ON point_mail_outbox (status, next_attempt_at);

CREATE TABLE IF NOT EXISTS economic_weather_history (
    id                     bigserial PRIMARY KEY,
    weather_date           date         NOT NULL,
    weather_code           varchar(20)  NOT NULL,
    score                  integer      NOT NULL,
    kospi_change_rate      numeric(9, 4),
    sp500_change_rate      numeric(9, 4),
    fx_ten_day_change_rate numeric(9, 4),
    updated_at             timestamp(6) NOT NULL,
    CONSTRAINT uk_weather_history_date UNIQUE (weather_date)
);
