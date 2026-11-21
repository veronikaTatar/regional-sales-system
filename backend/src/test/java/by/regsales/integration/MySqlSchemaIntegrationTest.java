package by.regsales.integration;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import java.sql.SQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

/** Opt in against a disposable MySQL 8.4 database using the normal DB_* variables. */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "MYSQL_INTEGRATION_TESTS", matches = "true")
class MySqlSchemaIntegrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test void migrationsAndReferenceDataArePresent() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM roles", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM regions", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_type='BASE TABLE'", Integer.class)).isEqualTo(16);
    }

    private long product() {
        jdbc.update("INSERT INTO products(sku,name,unit,current_price) VALUES(?, 'Проверка', 'шт.', 12.35)", "test-"+UUID.randomUUID());
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
    private long user() {
        String login = "test-"+UUID.randomUUID();
        jdbc.update("INSERT INTO app_users(role_id,login,email,full_name) VALUES(3,?,?, 'Проверка')", login,login+"@example.com");
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @Test void shipmentAmountIsGeneratedAndHistoryCannotBeDeleted() {
        long product = product(), user = user();
        assertThat(jdbc.queryForObject("SELECT session_version FROM app_users WHERE id=?", Long.class, user)).isZero();
        long region = jdbc.queryForObject("SELECT MIN(id) FROM regions", Long.class);
        jdbc.update("INSERT INTO shipments(product_id,region_id,recorded_by_user_id,shipment_date,quantity,unit_price) VALUES(?,?,?,CURRENT_DATE,2.500,12.35)", product, region, user);
        long shipment = jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        assertThat(jdbc.queryForObject("SELECT total_amount FROM shipments WHERE id=?", BigDecimal.class,shipment)).isEqualByComparingTo("30.88");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id=?",product)).isInstanceOf(DataIntegrityViolationException.class);
        assertCheck(() -> jdbc.update("INSERT INTO shipments(product_id,region_id,recorded_by_user_id,shipment_date,quantity,unit_price) VALUES(?,?,?,CURRENT_DATE,-1,12.35)",product,region,user), "ck_shipment_quantity");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO shipments(product_id,region_id,recorded_by_user_id,shipment_date,quantity,unit_price) VALUES(-1,?,?,CURRENT_DATE,1,1)",region,user)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void priceHistoryProtectsProductAndPeriod() {
        long product = product();
        String insert = "INSERT INTO product_price_history(product_id,valid_from,valid_to,unit_price) VALUES(?,?,?,?)";
        jdbc.update(insert, product, "2024-01-01", "2024-03-31", new BigDecimal("4.21"));
        assertThatThrownBy(() -> jdbc.update(insert, product, "2024-01-01", "2024-03-31", new BigDecimal("4.21")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertCheck(() -> jdbc.update(insert, product, "2024-04-01", "2024-03-31", new BigDecimal("4.21")),
                "ck_price_history_period");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM products WHERE id=?", product))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void statisticsRejectDuplicatePeriodsAndNegativeValues() {
        long region = jdbc.queryForObject("SELECT MIN(id) FROM regions",Long.class);
        long indicator = jdbc.queryForObject("SELECT MIN(id) FROM statistical_indicators",Long.class);
        String sql="INSERT INTO regional_statistics(region_id,statistical_indicator_id,period_start,period_end,indicator_value) VALUES(?,?, '2090-01-01','2090-01-31',?)";
        jdbc.update(sql,region,indicator,new BigDecimal("100.0000"));
        assertThatThrownBy(() -> jdbc.update(sql,region,indicator,new BigDecimal("200"))).isInstanceOf(DataIntegrityViolationException.class);
        assertCheck(() -> jdbc.update("INSERT INTO regional_statistics(region_id,statistical_indicator_id,period_start,period_end,indicator_value) VALUES(?,?, '2090-02-01','2090-02-28',-1)",region,indicator), "ck_statistic_value");
    }

    @Test void planItemsGenerateAmountAndPlansRequireApprovalMetadata() {
        long region = jdbc.queryForObject("SELECT MIN(id) FROM regions",Long.class);
        long user = user(), product=product();
        jdbc.update("INSERT INTO regional_plans(region_id,period_start,period_end,created_by_user_id) VALUES(?,'2091-01-01','2091-01-31',?)",region,user);
        long plan=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
        jdbc.update("INSERT INTO plan_items(regional_plan_id,product_id,planned_quantity,planned_unit_price) VALUES(?,?,3,10.50)",plan,product);
        assertThat(jdbc.queryForObject("SELECT planned_amount FROM plan_items WHERE regional_plan_id=?",BigDecimal.class,plan)).isEqualByComparingTo("31.50");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO plan_items(regional_plan_id,product_id,planned_quantity,planned_unit_price) VALUES(?,?,1,1)",plan,product)).isInstanceOf(DataIntegrityViolationException.class);
        assertCheck(() -> jdbc.update("UPDATE regional_plans SET status='APPROVED' WHERE id=?",plan), "ck_plan_approval");
    }
    private void assertCheck(Runnable operation, String constraint) {
        assertThatThrownBy(operation::run).isInstanceOf(DataAccessException.class).satisfies(error -> {
            var sql = (SQLException) ((DataAccessException) error).getMostSpecificCause();
            assertThat(sql.getErrorCode()).isEqualTo(3819);
            assertThat(sql.getMessage()).contains(constraint);
        });
    }
}
