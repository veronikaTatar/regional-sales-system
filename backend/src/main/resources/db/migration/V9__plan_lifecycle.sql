ALTER TABLE regional_plans
    DROP CHECK ck_plan_status,
    ADD CONSTRAINT ck_plan_status CHECK (status IN (
        'DRAFT','AWAITING_ANALYSIS','IN_PROGRESS','NEEDS_CORRECTION','IN_REVIEW','REVISION','APPROVED','CANCELLED')),
    ADD COLUMN validation_note MEDIUMTEXT NULL,
    ADD COLUMN active_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'CANCELLED' THEN NULL ELSE 1 END) STORED,
    DROP INDEX uk_plan_region_period,
    ADD CONSTRAINT uk_active_plan_region_period UNIQUE (region_id, period_start, period_end, active_slot);

UPDATE regional_plans SET status = 'IN_PROGRESS'
WHERE status = 'DRAFT' AND sent_to_manager_at IS NOT NULL;
