package com.yusufnazim.deliverydispatch.performance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yusufnazim.deliverydispatch.order.DeliveryOrderRepository;
import com.yusufnazim.deliverydispatch.timeline.DeliveryEventRepository;
import com.yusufnazim.deliverydispatch.user.UserRepository;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("performance")
@Testcontainers
@DirtiesContext
class BackendPerformanceCheck {

    private static final int CUSTOMERS = 20;
    private static final int ORDERS_PER_CUSTOMER = 500;
    private static final int ORDERS = CUSTOMERS * ORDERS_PER_CUSTOMER;
    private static final int COURIERS = 500;
    private static final int PAGE_SIZE = 20;
    private static final int READ_REQUESTS = 2000;
    private static final int READ_WARMUP = 200;
    private static final int DISPATCH_REQUESTS = 200;
    private static final int DISPATCH_WARMUP = 100;
    private static final String PASSWORD = "performance-fixture-only";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            // Testcontainers normally disables fsync; measured commits must include disk synchronization.
            .withCommand("postgres", "-c", "fsync=on", "-c", "synchronous_commit=on", "-c", "full_page_writes=on");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PasswordEncoder encoder;
    private final JpaProperties jpa;
    private final List<Object> repositories;
    private final int port;

    @Autowired
    BackendPerformanceCheck(JdbcTemplate jdbc, ObjectMapper json, PasswordEncoder encoder, JpaProperties jpa,
                            UserRepository users, DeliveryOrderRepository orders, DeliveryEventRepository events,
                            @LocalServerPort int port) {
        this.jdbc = jdbc;
        this.json = json;
        this.encoder = encoder;
        this.jpa = jpa;
        this.repositories = List.of(users, orders, events);
        this.port = port;
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.MINUTES)
    void measureValidatedHttpReadsAndUncontendedDispatch() throws Exception {
        seedIsolatedDatabase();
        List<PerformanceResult> results = new ArrayList<>();
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(5)).build()) {
            List<String> customerTokens = new ArrayList<>();
            for (int customer = 1; customer <= CUSTOMERS; customer++) {
                customerTokens.add(login(client, "customer-" + customer + "@perf.example"));
            }
            String dispatcherToken = login(client, "dispatcher@perf.example");
            Operation customerRead = index -> readPage(client, customerTokens.get(index % CUSTOMERS),
                    index % CUSTOMERS + 1, (index / CUSTOMERS) % (ORDERS_PER_CUSTOMER / PAGE_SIZE));
            Operation operationalRead = index -> readPage(client, dispatcherToken, 0, index % (ORDERS / PAGE_SIZE));
            measure("customer-read-warmup", READ_WARMUP, 4, customerRead);
            results.add(measure("customer-orders", READ_REQUESTS, 4, customerRead));
            measure("operational-read-warmup", READ_WARMUP, 4, operationalRead);
            results.add(measure("operational-orders", READ_REQUESTS, 4, operationalRead));
            Operation dispatch = index -> assign(client, dispatcherToken, index);
            measure("dispatch-warmup", DISPATCH_WARMUP, 1, dispatch);
            assertAssignments(DISPATCH_WARMUP);
            resetWarmupAssignments();
            results.add(measure("auto-dispatch", DISPATCH_REQUESTS, 1, dispatch));
            assertAssignments(DISPATCH_REQUESTS);
        }
        writeReport(results);
    }

    private void seedIsolatedDatabase() throws Exception {
        assertThat(jpa.getOpenInView()).isFalse();
        repositories.forEach(repository -> assertThat(AopProxyUtils.ultimateTargetClass(repository))
                .isEqualTo(SimpleJpaRepository.class));
        assertThat(jdbc.queryForObject("show fsync", String.class)).isEqualTo("on");
        assertThat(jdbc.queryForObject("show synchronous_commit", String.class)).isEqualTo("on");
        assertThat(jdbc.queryForObject("show full_page_writes", String.class)).isEqualTo("on");
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertThat(connection.getMetaData().getURL()).isEqualTo(POSTGRES.getJdbcUrl());
            assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("performance/fixture.sql"));
        }
        jdbc.update("update users set password_hash = ?", encoder.encode(PASSWORD));
        assertThat(jdbc.queryForObject("select count(*) from delivery_orders", Integer.class)).isEqualTo(ORDERS);
        assertThat(jdbc.queryForObject("select count(*) from delivery_events", Integer.class)).isEqualTo(25000);
        assertAssignments(0);
    }

    private String login(HttpClient client, String email) throws Exception {
        HttpRequest request = request("/api/v1/auth/login").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(
                        Map.of("email", email, "password", PASSWORD)))).build();
        JsonNode body = checkedBody(client.send(request, HttpResponse.BodyHandlers.ofByteArray()));
        assertThat(body.path("token").asText()).isNotBlank();
        return body.path("token").asText();
    }

    private long readPage(HttpClient client, String token, int customerId, int page) throws Exception {
        boolean customer = customerId != 0;
        HttpRequest request = request((customer ? "/api/v1/orders" : "/api/v1/dispatch/orders")
                + "?page=" + page + "&size=" + PAGE_SIZE).header("Authorization", "Bearer " + token).GET().build();
        long start = System.nanoTime();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        long elapsed = System.nanoTime() - start;
        JsonNode body = checkedBody(response);
        assertThat(body.path("totalElements").asInt()).isEqualTo(customer ? ORDERS_PER_CUSTOMER : ORDERS);
        assertThat(body.path("page").asInt()).isEqualTo(page);
        assertThat(body.path("size").asInt()).isEqualTo(PAGE_SIZE);
        assertThat(body.path("items").size()).isEqualTo(PAGE_SIZE);
        int firstId = (customer ? customerId * ORDERS_PER_CUSTOMER : ORDERS) - page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int id = firstId - i;
            JsonNode item = body.path("items").get(i);
            assertThat(item.path("id").asInt()).isEqualTo(id);
            assertThat(item.path("status").asText()).isEqualTo(id % 2 == 0 ? "DELIVERED" : "PENDING");
            if (!customer) {
                int owner = (id - 1) / ORDERS_PER_CUSTOMER + 1;
                assertThat(item.path("customerId").asInt()).isEqualTo(owner);
                assertThat(item.path("customerEmail").asText()).isEqualTo("customer-" + owner + "@perf.example");
                if (id % 2 == 0) {
                    int courier = (id / 2 - 1) % COURIERS + 1;
                    assertThat(item.path("courierId").asInt()).isEqualTo(1000 + courier);
                    assertThat(item.path("courierEmail").asText()).isEqualTo("courier-" + courier + "@perf.example");
                } else {
                    assertThat(item.path("courierId").isNull()).isTrue();
                }
            }
        }
        return elapsed;
    }

    private long assign(HttpClient client, String token, int index) throws Exception {
        int orderId = index * 2 + 1;
        HttpRequest request = request("/api/v1/dispatch/orders/" + orderId + "/auto-assign")
                .header("Authorization", "Bearer " + token).POST(HttpRequest.BodyPublishers.noBody()).build();
        long start = System.nanoTime();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        long elapsed = System.nanoTime() - start;
        JsonNode body = checkedBody(response);
        assertThat(body.path("orderId").asInt()).isEqualTo(orderId);
        assertThat(body.path("courierId").asInt()).isEqualTo(1001 + index);
        assertThat(body.path("status").asText()).isEqualTo("ASSIGNED");
        return elapsed;
    }

    private JsonNode checkedBody(HttpResponse<byte[]> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Request-ID")).isPresent();
        return json.readTree(response.body());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(10));
    }

    private PerformanceResult measure(String name, int requests, int concurrency, Operation operation) throws Exception {
        long[] samples = new long[requests];
        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(concurrency)) {
            try {
                List<Future<?>> futures = new ArrayList<>();
                for (int worker = 0; worker < concurrency; worker++) {
                    int offset = worker;
                    futures.add(workers.submit(() -> {
                        ready.countDown();
                        assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                        for (int index = offset; index < requests; index += concurrency) {
                            samples[index] = operation.execute(index);
                        }
                        return null;
                    }));
                }
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                long began = System.nanoTime();
                start.countDown();
                for (Future<?> future : futures) {
                    future.get(2, TimeUnit.MINUTES);
                }
                return PerformanceResult.from(name, concurrency, samples, System.nanoTime() - began);
            } finally {
                workers.shutdownNow();
            }
        }
    }

    private void assertAssignments(int count) {
        assertThat(jdbc.queryForObject("select count(*) from delivery_orders where status = 'ASSIGNED'", Integer.class))
                .isEqualTo(count);
        assertThat(jdbc.queryForObject("""
                select count(*) from delivery_orders o join users u on u.id = o.courier_id
                where o.status = 'ASSIGNED' and o.version = 1 and u.version = 1
                  and u.courier_availability_status = 'ON_DELIVERY'
                  and o.id = 2 * (u.id - 1001) + 1
                  and (select count(*) from delivery_events e
                       where e.delivery_order_id = o.id and e.event_type = 'COURIER_ASSIGNED') = 1
                """, Integer.class)).isEqualTo(count);
        assertThat(jdbc.queryForObject("select count(distinct courier_id) from delivery_orders where status = 'ASSIGNED'",
                Integer.class)).isEqualTo(count);
        assertThat(jdbc.queryForObject("select count(*) from users where courier_availability_status = 'AVAILABLE'",
                Integer.class)).isEqualTo(COURIERS - count);
        assertThat(jdbc.queryForObject("select count(*) from delivery_events", Integer.class)).isEqualTo(25000 + count);
    }

    private void resetWarmupAssignments() {
        // These writes only reset the disposable fixture, outside every measurement window.
        jdbc.update("""
                delete from delivery_events where event_type = 'COURIER_ASSIGNED'
                and delivery_order_id in (select id from delivery_orders where status = 'ASSIGNED')
                """);
        jdbc.update("""
                update delivery_orders set status = 'PENDING', courier_id = null, version = 0,
                    updated_at = created_at + interval '3 seconds' where status = 'ASSIGNED'
                """);
        jdbc.update("""
                update users set courier_availability_status = 'AVAILABLE', version = 0, updated_at = created_at
                where role = 'COURIER'
                """);
        assertAssignments(0);
    }

    private void writeReport(List<PerformanceResult> results) throws Exception {
        var os = ManagementFactory.getOperatingSystemMXBean();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("completedAt", Instant.now().toString());
        report.put("sourceRevision", System.getProperty("perf.revision", "unrecorded"));
        report.put("java", System.getProperty("java.runtime.version"));
        report.put("javaVendor", System.getProperty("java.vendor"));
        report.put("os", os.getName() + " " + os.getVersion() + " " + os.getArch());
        report.put("availableProcessors", os.getAvailableProcessors());
        report.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        if (os instanceof com.sun.management.OperatingSystemMXBean host) {
            report.put("visibleMemoryBytes", host.getTotalMemorySize());
        }
        report.put("postgresVersion", jdbc.queryForObject("show server_version", String.class));
        report.put("postgresImage", POSTGRES.getDockerImageName());
        report.put("postgresImageId", POSTGRES.getContainerInfo().getImageId());
        report.put("postgresSettings", Map.of(
                "fsync", jdbc.queryForObject("show fsync", String.class),
                "synchronousCommit", jdbc.queryForObject("show synchronous_commit", String.class),
                "fullPageWrites", jdbc.queryForObject("show full_page_writes", String.class),
                "sharedBuffers", jdbc.queryForObject("show shared_buffers", String.class),
                "maxConnections", jdbc.queryForObject("show max_connections", String.class)));
        report.put("dataset", Map.of("customers", CUSTOMERS, "couriers", COURIERS, "orders", ORDERS,
                "ordersPerCustomer", ORDERS_PER_CUSTOMER, "initialEvents", 25000, "pageSize", PAGE_SIZE));
        report.put("warmupRequests", Map.of("eachReadWorkload", READ_WARMUP, "autoDispatch", DISPATCH_WARMUP));
        report.put("results", results);
        Path directory = Path.of("target", "performance");
        Files.createDirectories(directory);
        Path output = Files.createTempFile(directory, "baseline-", ".json");
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("Validated performance report: " + output.toAbsolutePath());
    }

    @FunctionalInterface
    private interface Operation {
        long execute(int index) throws Exception;
    }
}
