ALTER TABLE purchasing_power_calculations
    ADD COLUMN input_snapshot TEXT NULL;

ALTER TABLE regional_plans
    DROP CHECK ck_plan_status,
    ADD CONSTRAINT ck_plan_status CHECK (status IN ('DRAFT','AWAITING_ANALYSIS','IN_REVIEW','REVISION','APPROVED')),
    ADD COLUMN analytical_recommendation_id BIGINT NULL,
    ADD CONSTRAINT uk_plan_recommendation UNIQUE (analytical_recommendation_id),
    ADD CONSTRAINT fk_plan_recommendation FOREIGN KEY (analytical_recommendation_id)
        REFERENCES analytical_recommendations(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_plan_pending_analysis CHECK (status <> 'AWAITING_ANALYSIS' OR
        (assigned_manager_id IS NOT NULL AND sent_to_manager_at IS NULL AND analytical_recommendation_id IS NULL));
