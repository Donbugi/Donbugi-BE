-- ===================================================================
-- 모의투자 엔진 재설계 (lock 모델, 주문 테이블, 원장, 스냅샷)
-- 빈 DB(신규/테스트)에서도 안전하도록 IF [NOT] EXISTS 사용.
-- ===================================================================

-- 1) 계좌: 미체결 매수에 묶인 금액
ALTER TABLE IF EXISTS trading_account ADD COLUMN IF NOT EXISTS locked_cash numeric(15, 2) NOT NULL DEFAULT 0;
DO $$
BEGIN
    IF to_regclass('trading_account') IS NOT NULL THEN
        UPDATE trading_account SET version = 0 WHERE version IS NULL;
    END IF;
END $$;

-- 2) 보유종목: 미체결 매도에 묶인 수량 + 낙관적 락 버전
ALTER TABLE IF EXISTS trading_holding ADD COLUMN IF NOT EXISTS locked_quantity integer NOT NULL DEFAULT 0;
ALTER TABLE IF EXISTS trading_holding ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

-- 3) 체결내역: 주문 연결 + 실현손익
ALTER TABLE IF EXISTS trade_history ADD COLUMN IF NOT EXISTS order_id bigint;
ALTER TABLE IF EXISTS trade_history ADD COLUMN IF NOT EXISTS realized_pnl numeric(15, 2);

-- 4) 주문 (pending_order 대체)
CREATE TABLE IF NOT EXISTS trading_order (
    id                bigserial PRIMARY KEY,
    user_id           varchar(64)    NOT NULL,
    stock_code        varchar(10)    NOT NULL,
    order_type        varchar(4)     NOT NULL,
    price_type        varchar(6)     NOT NULL,
    limit_price       numeric(12, 2),
    quantity          integer        NOT NULL,
    filled_quantity   integer        NOT NULL DEFAULT 0,
    filled_amount     numeric(15, 2) NOT NULL DEFAULT 0,
    locked_amount     numeric(15, 2) NOT NULL DEFAULT 0,
    status            varchar(20)    NOT NULL,
    order_session     varchar(20)    NOT NULL,
    client_order_id   varchar(64),
    reject_reason     varchar(200),
    original_order_id bigint,
    created_at        timestamp(6)   NOT NULL,
    updated_at        timestamp(6)   NOT NULL,
    version           bigint,
    CONSTRAINT uk_trading_order_client_id UNIQUE (user_id, client_order_id)
);
CREATE INDEX IF NOT EXISTS idx_trading_order_user_created ON trading_order (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_trading_order_status_stock ON trading_order (status, stock_code);

-- 5) 예수금 원장
CREATE TABLE IF NOT EXISTS trading_cash_ledger (
    id            bigserial PRIMARY KEY,
    user_id       varchar(64)    NOT NULL,
    kind          varchar(10)    NOT NULL,
    delta         numeric(15, 2) NOT NULL,
    balance_after numeric(15, 2) NOT NULL,
    trade_id      bigint,
    created_at    timestamp(6)   NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_cash_ledger_user_created ON trading_cash_ledger (user_id, created_at);

-- 6) 일별 자산 스냅샷
CREATE TABLE IF NOT EXISTS trading_daily_snapshot (
    id               bigserial PRIMARY KEY,
    user_id          varchar(64)    NOT NULL,
    snapshot_date    date           NOT NULL,
    cash             numeric(15, 2) NOT NULL,
    stock_evaluation numeric(15, 2) NOT NULL,
    total_asset      numeric(15, 2) NOT NULL,
    realized_pnl     numeric(15, 2) NOT NULL,
    profit_rate      numeric(9, 4)  NOT NULL,
    created_at       timestamp(6)   NOT NULL,
    CONSTRAINT uk_daily_snapshot_user_date UNIQUE (user_id, snapshot_date)
);

-- 7) 구 모델(pending_order)의 미체결 주문 정리
--    구 모델은 지정가 매수 시 현금을 즉시 차감하고, 매도 시 보유수량을 즉시 차감했다.
--    새 모델(lock)로 옮기기 전에 원상복구 후 취소 처리한다. (당일 주문은 어차피 장 마감 시 취소되는 day order)
DO $$
BEGIN
    IF to_regclass('pending_order') IS NOT NULL AND to_regclass('trading_account') IS NOT NULL THEN
        UPDATE trading_account a
        SET cash = a.cash + s.amt
        FROM (SELECT user_id, SUM(reserved_amount) AS amt
              FROM pending_order
              WHERE status = 'PENDING' AND order_type = 'BUY' AND reserved_amount IS NOT NULL
              GROUP BY user_id) s
        WHERE a.user_id = s.user_id;

        INSERT INTO trading_holding (user_id, stock_code, quantity, locked_quantity, avg_price, created_at, updated_at, version)
        SELECT user_id, stock_code, SUM(quantity), 0,
               COALESCE(MAX(sell_avg_price), MAX(target_price)), now(), now(), 0
        FROM pending_order
        WHERE status = 'PENDING' AND order_type = 'SELL'
        GROUP BY user_id, stock_code
        ON CONFLICT (user_id, stock_code)
            DO UPDATE SET quantity = trading_holding.quantity + EXCLUDED.quantity, updated_at = now();

        UPDATE pending_order SET status = 'CANCELLED', updated_at = now() WHERE status = 'PENDING';
    END IF;
END $$;

-- 8) 기존 계좌의 원장 기초잔액(OPENING) — 이후 cash == Σ ledger 로 검증
DO $$
BEGIN
    IF to_regclass('trading_account') IS NOT NULL THEN
        INSERT INTO trading_cash_ledger (user_id, kind, delta, balance_after, trade_id, created_at)
        SELECT a.user_id, 'OPENING', a.cash, a.cash, NULL, now()
        FROM trading_account a
        WHERE NOT EXISTS (SELECT 1 FROM trading_cash_ledger l WHERE l.user_id = a.user_id);
    END IF;
END $$;
