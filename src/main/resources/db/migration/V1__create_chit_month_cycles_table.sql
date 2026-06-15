-- WHY a separate chit_month_cycles table?
-- Admin explicitly opens or skips each month rather than auto-generating records.
-- This gives admin control: open when ready, skip with a reason (e.g. COVID, festival).
-- Skipped months are still recorded for audit/reporting — they never disappear.
-- status is just OPEN or SKIPPED — CLOSED is tracked by payment_records (all SETTLED).

CREATE TABLE chit_month_cycles (
    id                  VARCHAR(36)    NOT NULL,
    chit_id             VARCHAR(36)    NOT NULL,
    month_number        INT            NOT NULL,
    due_date            DATE           NOT NULL,
    installment_amount  DECIMAL(15,2)  NOT NULL,
    total_members       INT            NOT NULL,
    status              VARCHAR(20)    NOT NULL,
    skip_reason         TEXT,
    opened_at           DATETIME(6),
    opened_by           VARCHAR(36),
    skipped_at          DATETIME(6),
    skipped_by          VARCHAR(36),
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_chit_month_cycle (chit_id, month_number),
    INDEX idx_cycles_chit (chit_id),
    INDEX idx_cycles_status (status),
    INDEX idx_cycles_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
