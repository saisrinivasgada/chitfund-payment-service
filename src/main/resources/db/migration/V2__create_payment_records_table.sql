-- One row per (member, chit, month). Created when admin opens a cycle.
-- OUTSTANDING → PARTIALLY_PAID → SETTLED via FIFO payment application.
-- WAIVED when the month is skipped — amount_due is stored for reporting
-- (shows what would have been owed, even though it was waived).
-- due_date denormalized here so balance queries need no join to cycles table.

CREATE TABLE payment_records (
    id              VARCHAR(36)    NOT NULL,
    chit_id         VARCHAR(36)    NOT NULL,
    member_id       VARCHAR(36)    NOT NULL,
    month_number    INT            NOT NULL,
    due_date        DATE           NOT NULL,
    amount_due      DECIMAL(15,2)  NOT NULL,
    amount_paid     DECIMAL(15,2)  NOT NULL DEFAULT 0.00,
    status          VARCHAR(20)    NOT NULL DEFAULT 'OUTSTANDING',
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_record (chit_id, member_id, month_number),
    INDEX idx_records_member_chit (member_id, chit_id),
    INDEX idx_records_member_status (member_id, status),
    INDEX idx_records_chit_month (chit_id, month_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
