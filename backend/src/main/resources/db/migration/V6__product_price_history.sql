CREATE TABLE product_price_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    unit_price DECIMAL(14,2) NOT NULL,
    vat_rate DECIMAL(5,2),
    price_with_vat DECIMAL(14,2),
    retail_price DECIMAL(14,2),
    price_list_code VARCHAR(50),
    CONSTRAINT uk_price_history_product_start UNIQUE (product_id, valid_from),
    CONSTRAINT fk_price_history_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    CONSTRAINT ck_price_history_period CHECK (valid_to >= valid_from),
    CONSTRAINT ck_price_history_amounts CHECK (unit_price >= 0
        AND (vat_rate IS NULL OR vat_rate BETWEEN 0 AND 100)
        AND (price_with_vat IS NULL OR price_with_vat >= 0)
        AND (retail_price IS NULL OR retail_price >= 0)),
    INDEX ix_price_history_from (valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
