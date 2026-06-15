-- WHY a payment_batches table separate from payment_records?
-- A member can pay a lump sum covering multiple months (e.g. ₹15,000 for 3 missed months).
-- The batch captures that single payment event — who paid, how much, via what mode.
-- Allocations (V4) then show how that ₹15,000 was distributed across the 3 records (FIFO).
--
-- CASH two-step flow:
--   Worker collects from member → status = AWAITING_REMITTANCE
--   Admin confirms receipt from worker → status = COMPLETED → allocations applied
-- Non-cash (UPI / BANK_TRANSFER / CHEQUE):
--   Admin records directly → status = COMPLETED → allocations applied immediately

CREATE TABLE payment_batches (
    id              VARCHAR(36)    NOT NULL,
    chit_id         VARCHAR(36)    NOT NULL,
    member_id       VARCHAR(36)    NOT NULL,
    total_amount    DECIMAL(15,2)  NOT NULL,
    payment_mode    VARCHAR(20)    NOT NULL,
    status          VARCHAR(25)    NOT NULL,
    collected_by    VARCHAR(36),
    collected_at    DATETIME(6),
    remitted_at     DATETIME(6),
    remitted_by     VARCHAR(36),
    notes           TEXT,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_batches_member_chit (member_id, chit_id),
    INDEX idx_batches_status (status),
    INDEX idx_batches_collected_by (collected_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
