package com.yusufnazim.deliverydispatch.dispatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ActiveCourierMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private String schema;
    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbc;
    private Long customerId;
    private Long courierId;

    @BeforeEach
    void createVersionSevenDatabase() {
        schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setSchema(schema);
        flyway("7").migrate();
        jdbc = new JdbcTemplate(dataSource);
        customerId = insertUser("migration-customer@example.com", "CUSTOMER", null);
        courierId = insertUser("migration-courier@example.com", "COURIER", "ON_DELIVERY");
    }

    @Test
    void upgradePreservesExistingAssignmentAndInitializesUserVersions() {
        Long orderId = insertActiveOrder();

        flyway("8").migrate();

        assertThat(jdbc.queryForList("select version from users order by id", Long.class))
                .containsExactly(0L, 0L);
        assertThat(jdbc.queryForObject("select courier_id from delivery_orders where id = ?", Long.class, orderId))
                .isEqualTo(courierId);
        assertThat(jdbc.queryForObject("select status from delivery_orders where id = ?", String.class, orderId))
                .isEqualTo("ASSIGNED");
        assertThatThrownBy(this::insertActiveOrder)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_delivery_orders_active_courier");
        assertThat(jdbc.queryForObject("select count(*) from delivery_orders", Long.class)).isEqualTo(1L);
    }

    @Test
    void upgradeRejectsDuplicateActiveAssignmentsWithoutRewritingData() {
        Long firstOrderId = insertActiveOrder();
        Long secondOrderId = insertActiveOrder();

        assertThatThrownBy(() -> flyway("8").migrate())
                .isInstanceOf(FlywayException.class)
                .hasStackTraceContaining("uk_delivery_orders_active_courier");

        assertThat(jdbc.queryForList("select id from delivery_orders order by id", Long.class))
                .containsExactly(firstOrderId, secondOrderId);
        assertThat(jdbc.queryForList("select courier_id from delivery_orders order by id", Long.class))
                .containsExactly(courierId, courierId);
        assertThat(jdbc.queryForList("select status from delivery_orders order by id", String.class))
                .containsExactly("ASSIGNED", "ASSIGNED");
        assertThat(jdbc.queryForObject("select courier_availability_status from users where id = ?",
                String.class, courierId)).isEqualTo("ON_DELIVERY");
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = ? and table_name = 'users' and column_name = 'version'
                """, Long.class, schema)).isZero();
    }

    private Flyway flyway(String target) {
        return Flyway.configure().dataSource(dataSource).schemas(schema).target(target).load();
    }

    private Long insertUser(String email, String role, String availability) {
        return jdbc.queryForObject("""
                insert into users (email, password_hash, role, courier_availability_status, created_at, updated_at)
                values (?, 'hash', ?, ?, now(), now()) returning id
                """, Long.class, email, role, availability);
    }

    private Long insertActiveOrder() {
        return jdbc.queryForObject("""
                insert into delivery_orders (customer_id, courier_id, status,
                    pickup_address, pickup_latitude, pickup_longitude,
                    dropoff_address, dropoff_latitude, dropoff_longitude, created_at, updated_at)
                values (?, ?, 'ASSIGNED', 'Pickup', 41.0369, 28.985,
                    'Dropoff', 40.97, 29.057, now(), now()) returning id
                """, Long.class, customerId, courierId);
    }
}
