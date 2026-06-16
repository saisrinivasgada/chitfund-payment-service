ALTER TABLE chit_month_cycles
    ADD COLUMN closed_at  DATETIME(6) NULL AFTER opened_by,
    ADD COLUMN closed_by  VARCHAR(36) NULL AFTER closed_at;
