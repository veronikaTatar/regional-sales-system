ALTER TABLE regional_plans
    ADD COLUMN assigned_manager_id BIGINT NULL,
    ADD COLUMN sent_to_manager_at DATETIME(6) NULL,
    ADD COLUMN planning_comment TEXT NULL,
    ADD COLUMN max_discount_percent DECIMAL(5,2) NULL,
    ADD COLUMN updated_at DATETIME(6) NULL,
    ADD CONSTRAINT fk_plan_manager FOREIGN KEY (assigned_manager_id) REFERENCES app_users(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_plan_dispatch CHECK (sent_to_manager_at IS NULL OR assigned_manager_id IS NOT NULL),
    ADD CONSTRAINT ck_plan_discount CHECK (max_discount_percent IS NULL OR max_discount_percent BETWEEN 0 AND 100),
    ADD INDEX ix_plan_manager_status (assigned_manager_id, status);
