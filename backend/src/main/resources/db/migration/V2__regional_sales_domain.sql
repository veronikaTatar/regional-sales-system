CREATE TABLE regions (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_region_code UNIQUE (code),
    CONSTRAINT uk_region_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE products (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    category VARCHAR(100),
    unit VARCHAR(20) NOT NULL DEFAULT 'шт.',
    current_price DECIMAL(14,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_product_sku UNIQUE (sku),
    CONSTRAINT ck_product_price CHECK (current_price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE region_assignments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    region_id BIGINT NOT NULL,
    manager_id BIGINT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    CONSTRAINT uk_assignment_start UNIQUE (region_id, manager_id, start_date),
    CONSTRAINT ck_assignment_period CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT fk_assignment_region FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_assignment_manager FOREIGN KEY (manager_id) REFERENCES app_users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX ix_assignment_manager_period (manager_id, start_date, end_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE shipments (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    region_id BIGINT NOT NULL,
    recorded_by_user_id BIGINT NOT NULL,
    shipment_date DATE NOT NULL,
    quantity DECIMAL(14,3) NOT NULL,
    unit_price DECIMAL(14,2) NOT NULL,
    total_amount DECIMAL(18,2) GENERATED ALWAYS AS (ROUND(quantity * unit_price, 2)) STORED,
    reference_number VARCHAR(80),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_shipment_reference UNIQUE (reference_number),
    CONSTRAINT ck_shipment_quantity CHECK (quantity > 0),
    CONSTRAINT ck_shipment_price CHECK (unit_price >= 0),
    CONSTRAINT fk_shipment_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_shipment_region FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_shipment_user FOREIGN KEY (recorded_by_user_id) REFERENCES app_users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX ix_shipment_region_date (region_id, shipment_date),
    INDEX ix_shipment_product_date (product_id, shipment_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE statistical_indicators (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    unit VARCHAR(40) NOT NULL,
    periodicity VARCHAR(10) NOT NULL,
    CONSTRAINT uk_indicator_code UNIQUE (code),
    CONSTRAINT ck_indicator_periodicity CHECK (periodicity IN ('MONTH','QUARTER','YEAR'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE statistics_imports (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    source_name VARCHAR(150) NOT NULL,
    imported_by_user_id BIGINT NOT NULL,
    imported_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    status VARCHAR(15) NOT NULL DEFAULT 'PROCESSING',
    rows_total INT NOT NULL DEFAULT 0,
    rows_accepted INT NOT NULL DEFAULT 0,
    rows_rejected INT NOT NULL DEFAULT 0,
    rows_skipped INT NOT NULL DEFAULT 0,
    CONSTRAINT ck_import_status CHECK (status IN ('PROCESSING','COMPLETED','PARTIAL','FAILED')),
    CONSTRAINT ck_import_rows CHECK (rows_total >= 0 AND rows_accepted >= 0 AND rows_rejected >= 0 AND rows_skipped >= 0 AND rows_accepted + rows_rejected + rows_skipped <= rows_total),
    CONSTRAINT fk_import_user FOREIGN KEY (imported_by_user_id) REFERENCES app_users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX ix_import_date (imported_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE regional_statistics (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    region_id BIGINT NOT NULL,
    statistical_indicator_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    indicator_value DECIMAL(20,4) NOT NULL,
    statistics_import_id BIGINT,
    source_url TEXT,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_statistic_region_indicator_period UNIQUE (region_id, statistical_indicator_id, period_start, period_end),
    CONSTRAINT ck_statistic_period CHECK (period_end >= period_start),
    CONSTRAINT ck_statistic_value CHECK (indicator_value >= 0),
    CONSTRAINT fk_statistic_region FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_statistic_indicator FOREIGN KEY (statistical_indicator_id) REFERENCES statistical_indicators(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_statistic_import FOREIGN KEY (statistics_import_id) REFERENCES statistics_imports(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX ix_statistic_period (period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE purchasing_power_calculations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    region_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    method_version VARCHAR(40) NOT NULL,
    index_value DECIMAL(18,6) NOT NULL,
    calculated_by_user_id BIGINT NOT NULL,
    calculated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    notes TEXT,
    CONSTRAINT ck_calculation_period CHECK (period_end >= period_start),
    CONSTRAINT ck_calculation_index CHECK (index_value >= 0),
    CONSTRAINT fk_calculation_region FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_calculation_user FOREIGN KEY (calculated_by_user_id) REFERENCES app_users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX ix_calculation_region_period (region_id, period_start, period_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE regional_plans (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    region_id BIGINT NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    budget_limit DECIMAL(18,2),
    target_revenue DECIMAL(18,2),
    purchasing_power_calculation_id BIGINT,
    created_by_user_id BIGINT NOT NULL,
    approved_by_user_id BIGINT,
    approved_at DATETIME(6),
    review_comment TEXT,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_plan_region_period UNIQUE (region_id, period_start, period_end),
    CONSTRAINT ck_plan_period CHECK (period_end >= period_start),
    CONSTRAINT ck_plan_status CHECK (status IN ('DRAFT','IN_REVIEW','REVISION','APPROVED')),
    CONSTRAINT ck_plan_budget CHECK (budget_limit IS NULL OR budget_limit >= 0),
    CONSTRAINT ck_plan_revenue CHECK (target_revenue IS NULL OR target_revenue >= 0),
    CONSTRAINT ck_plan_approval CHECK ((status = 'APPROVED' AND approved_by_user_id IS NOT NULL AND approved_at IS NOT NULL) OR (status <> 'APPROVED' AND approved_by_user_id IS NULL AND approved_at IS NULL)),
    CONSTRAINT fk_plan_region FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_plan_calculation FOREIGN KEY (purchasing_power_calculation_id) REFERENCES purchasing_power_calculations(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_plan_creator FOREIGN KEY (created_by_user_id) REFERENCES app_users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_plan_approver FOREIGN KEY (approved_by_user_id) REFERENCES app_users(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    INDEX ix_plan_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE plan_items (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    regional_plan_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    planned_quantity DECIMAL(14,3) NOT NULL,
    planned_unit_price DECIMAL(14,2) NOT NULL,
    planned_amount DECIMAL(18,2) GENERATED ALWAYS AS (ROUND(planned_quantity * planned_unit_price, 2)) STORED,
    CONSTRAINT uk_plan_product UNIQUE (regional_plan_id, product_id),
    CONSTRAINT ck_plan_item_quantity CHECK (planned_quantity > 0),
    CONSTRAINT ck_plan_item_price CHECK (planned_unit_price >= 0),
    CONSTRAINT fk_plan_item_plan FOREIGN KEY (regional_plan_id) REFERENCES regional_plans(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_plan_item_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE analytical_recommendations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    region_id BIGINT NOT NULL,
    purchasing_power_calculation_id BIGINT,
    author_user_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    recommendation_text TEXT NOT NULL,
    CONSTRAINT fk_recommendation_region FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_recommendation_calculation FOREIGN KEY (purchasing_power_calculation_id) REFERENCES purchasing_power_calculations(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_recommendation_author FOREIGN KEY (author_user_id) REFERENCES app_users(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    INDEX ix_recommendation_region_date (region_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
