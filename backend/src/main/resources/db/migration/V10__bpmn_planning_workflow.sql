ALTER TABLE regional_plans
    DROP CHECK ck_plan_status,
    DROP CHECK ck_plan_pending_analysis,
    ADD CONSTRAINT ck_plan_status CHECK (status IN (
        'DRAFT','AWAITING_SALES_DATA','SALES_REVISION','AWAITING_ANALYSIS','IN_PROGRESS',
        'NEEDS_CORRECTION','IN_REVIEW','REVISION','APPROVED','REJECTED','CANCELLED')),
    ADD COLUMN sales_submitted_at DATETIME(6) NULL,
    ADD COLUMN sales_shipment_count BIGINT NULL,
    ADD COLUMN sales_revenue DECIMAL(38,2) NULL,
    ADD COLUMN planning_check TEXT NULL;

-- Pending plans enter the required sales-data step. Draft positions and prior analysis stay linked.
UPDATE regional_plans
SET status = CASE WHEN assigned_manager_id IS NULL THEN 'DRAFT' ELSE 'AWAITING_SALES_DATA' END,
    sent_to_manager_at = CASE WHEN assigned_manager_id IS NULL THEN NULL ELSE COALESCE(sent_to_manager_at, CURRENT_TIMESTAMP(6)) END,
    validation_note = NULL,
    version = version + 1
WHERE status IN ('AWAITING_ANALYSIS','IN_PROGRESS','NEEDS_CORRECTION','IN_REVIEW','REVISION')
   OR (status = 'DRAFT' AND sent_to_manager_at IS NOT NULL);

ALTER TABLE regional_plans
    ADD CONSTRAINT ck_plan_sales_snapshot CHECK (
        (sales_submitted_at IS NULL AND sales_shipment_count IS NULL AND sales_revenue IS NULL)
        OR (sales_submitted_at IS NOT NULL AND sales_shipment_count IS NOT NULL AND sales_shipment_count > 0
            AND sales_revenue IS NOT NULL AND sales_revenue >= 0)),
    ADD CONSTRAINT ck_plan_analysis_input CHECK (status <> 'AWAITING_ANALYSIS' OR sales_submitted_at IS NOT NULL);
