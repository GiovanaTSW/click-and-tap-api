package mx.edu.clickandtap.api;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Checks the schema itself: that Flyway ran, that Hibernate's ddl-auto=validate accepted it
 * (the context would not start otherwise) and that the constraints really reject bad data.
 */
class DatabaseIntegrationTest extends IntegrationTestBase {

    @Test
    void flywayAppliedEveryMigrationSuccessfully() {
        List<String> versions = jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank", String.class);

        assertThat(versions).contains("1", "2", "3");
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where not success", Integer.class))
                .isZero();
    }

    @Test
    void createdAtColumnsStoreTimeZoneAwareTimestamps() {
        List<String> types = jdbc.queryForList("""
                select data_type from information_schema.columns
                where table_schema = 'public' and column_name = 'created_at'
                  and table_name in ('users', 'orders', 'digital_experiences')""", String.class);

        assertThat(types).hasSize(3).containsOnly("timestamp with time zone");
    }

    @Test
    void negativeStockIsRejectedByTheDatabaseEvenIfTheApplicationHasABug() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into products (name, type, price, stock) values ('chk-stock', 'PLATE', 1.00, -1)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_products_stock");
    }

    @Test
    void negativePriceIsRejected() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into products (name, type, price, stock) values ('chk-price', 'PLATE', -0.01, 1)"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_products_price");
    }

    @Test
    void unknownEnumValuesAreRejected() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into products (name, type, price, stock) values ('chk-type', 'SPACESHIP', 1.00, 1)"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (name, email, password_hash, role) values ('x', 'role-check@example.com', 'h', 'HACKER')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_users_role");
    }

    @Test
    void emailsMustBeStoredInLowerCase() {
        assertThatThrownBy(() -> jdbc.update(
                "insert into users (name, email, password_hash) values ('x', 'UPPER@EXAMPLE.COM', 'h')"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_users_email_lower");
    }
}
