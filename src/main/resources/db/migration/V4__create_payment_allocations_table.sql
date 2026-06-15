-- WHY payment_allocations?
-- Records exactly how each batch's amount was distributed across payment_records (FIFO order).
-- Example: Member pays ₹6,000. Month 2 had ₹1,000 remaining, Month 3 was ₹5,000.
--   Allocation 1: batch → month_2 record, ₹1,000 (month 2 now SETTLED)
--   Allocation 2: batch → month_3 record, ₹5,000 (month 3 now SETTLED)
-- This gives us full audit trail: "which payment covered which month, and how much?"

CREATE TABLE payment_allocations (
    id                  VARCHAR(36)    NOT NULL,
    batch_id            VARCHAR(36)    NOT NULL,
    payment_record_id   VARCHAR(36)    NOT NULL,
    chit_id             VARCHAR(36)    NOT NULL,
    member_id           VARCHAR(36)    NOT NULL,
    month_number        INT            NOT NULL,
    allocated_amount    DECIMAL(15,2)  NOT NULL,
    created_at          DATETIME(6)    NOT NULL,

    PRIMARY KEY (id),
    CONSTRAINT fk_alloc_batch  FOREIGN KEY (batch_id)          REFERENCES payment_batches(id),
    CONSTRAINT fk_alloc_record FOREIGN KEY (payment_record_id) REFERENCES payment_records(id),
    INDEX idx_allocations_batch (batch_id),
    INDEX idx_allocations_record (payment_record_id),
    INDEX idx_allocations_member_chit (member_id, chit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
