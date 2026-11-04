# Local Performance Checks

This opt-in check measures authenticated HTTP requests through embedded Tomcat,
Spring Security, services, JPA, and a fresh PostgreSQL Testcontainer. It uses the
existing Java test dependencies. The client and API share one JVM; PostgreSQL runs
in Docker. No running API, Compose database, or production URL is used.

## Run

Use JDK 21 and a working Docker daemon. From the repository root:

```bash
./mvnw --batch-mode --no-transfer-progress \
  -Dtest=BackendPerformanceCheck \
  -Dperf.revision="$(git rev-parse HEAD)" \
  -DargLine='-Xms512m -Xmx1g' test > /tmp/delivery-perf.log 2>&1
```

Run that command three times, sequentially, with other heavy workloads stopped.
Each invocation creates a new JVM, application context, and database. The command
prints the path to a unique JSON report under `target/performance/` after all
checks pass. Inspect `/tmp/delivery-perf.log` and the Maven exit status; a failure
does not produce a valid baseline report. Earlier reports remain in the directory
until `./mvnw clean`. Tokens and response bodies are not written to the reports.

Record `git status --short` alongside the revision when comparing modified code.
The `perf.revision` value is a supplied label, not a verification of a clean tree.
Also record CPU model, memory, Docker version, storage, background load, and any
JVM/container overrides. Reports capture Java/OS versions, visible CPU/memory,
maximum heap, PostgreSQL version/image ID, dataset sizes, and measurements.

The class name `BackendPerformanceCheck` deliberately falls outside
[Surefire's default test naming patterns](https://maven.apache.org/surefire/maven-surefire-plugin/examples/inclusion-exclusion.html).
Normal `./mvnw test` / `verify` still run all correctness tests, including the
metric calculation tests, but do not run this workload. Docker failures are errors
when the performance check is selected. There are no machine-dependent latency
thresholds in CI.

## Dataset And Workloads

The [fixture](../src/test/resources/performance/fixture.sql) contains 20 customers,
one dispatcher, 500 available couriers, and 10,000 orders (500 per customer).
Half the orders are pending; half are delivered and reference couriers. The
initial 25,000 timeline events match those states. Fixed IDs, timestamps, and
coordinates make page contents and the nearest-courier sequence predictable.
Flyway creates the schema, Hibernate validates it, and PostgreSQL statistics are
analyzed after loading the fixture. The runner explicitly enables and verifies
`fsync`, `synchronous_commit`, and `full_page_writes`; Testcontainers otherwise
disables `fsync` for faster tests. Fixture creation and real HTTP logins are
outside the measurement windows.

Workloads run in this order:

1. Customer pages: `GET /api/v1/orders?page=...&size=20`, 200 warm-up requests,
   then 2,000 measured requests using four concurrent clients. Requests cycle
   through all 20 customer tokens and pages 0-24 (offsets 0-480).
2. Operational pages: `GET /api/v1/dispatch/orders?page=...&size=20`, 200 warm-up
   requests, then 2,000 measured requests using four concurrent clients and a
   dispatcher token. Requests cycle through pages 0-499 (offsets 0-9,980), with no
   status filter, loading both customer and courier details.
3. Automatic dispatch: `POST /api/v1/dispatch/orders/{id}/auto-assign`, 100 warm-up
   assignments, then 200 measured assignments from one client. Warm-up assignments
   and their events are reset outside timing. Each measured request assigns a
   different pending order; eligible couriers decline from 500 before the first
   request to 300 after the last. No courier is reused during that batch.

Every request must return 200 and a request ID. Read checks verify page size,
totals, exact order IDs/statuses, ownership, and operational customer/courier data.
Dispatch checks verify the selected nearest courier and assigned state. Independent
JDBC checks confirm committed order/user versions, courier availability, unique
assignments, and exactly one assignment event per affected order. Any failed
response, assertion, or timeout fails the run; there are no retries or discarded
error samples. HTTP request timeouts are 10 seconds.

## Measurement

The [runner](../src/test/java/com/yusufnazim/deliverydispatch/performance/BackendPerformanceCheck.java)
uses a shared Java HTTP/1.1 client with connection reuse and a fixed pool of
workers. Each worker sends its next request after validating the previous
response: this is a closed-loop workload with no think time or fixed arrival rate.
Warm-up uses the same concurrency and validation as measurement.

Latency uses `System.nanoTime()` immediately around `HttpClient.send`, ending
after the full response body arrives. It includes HTTP, authentication, database
work, and serialization; it excludes request construction and client-side JSON
validation. Throughput is completed requests divided by the wall time of the
whole measured batch, including client validation and scheduling. It is not the
inverse of average latency. Percentiles use the nearest-rank method; reports
include mean, p50, p95, p99, maximum, elapsed time, and requests/second.

The [performance profile](../src/test/resources/application-performance.yml) uses
real repositories, OSIV disabled, a 10-connection Hikari pool, and INFO request
logging enabled. Demo seeding is disabled and the server binds to loopback on a
random port. Database cleanup is owned by Testcontainers; no existing volumes are
removed. Measurements use the test classpath and test JWT configuration.

These short runs are a local comparison baseline, not a capacity, endurance, or
production SLO test. Client and server compete for the same CPU/heap; Docker,
console log output, JIT/GC, page cache, workload order, and host activity affect
results. Warm-up does not visit every measured page or prove steady state. The
closed-loop load reduces its request rate when responses slow down and does not
model a fixed-arrival queue or production tail latency. Sequential dispatch
measures successful assignments, not contention or conflict rates. Separate
concurrency tests cover assignment correctness. Filtering, manual assignment,
delivery completion, TLS, and remote-network costs are not measured here.

## Recorded Baseline

Measured on 2026-11-04 against application revision `a27b226`, with the performance
runner introduced in this commit. No application code or Maven dependencies were
changed. The command above used that revision label and redirected INFO console
logs to a local file.

Environment:

- Intel Core i5-1135G7, four physical cores/eight logical CPUs, 14.9 GiB visible RAM.
- Linux `7.0.0-42-generic`, x86-64; Ubuntu OpenJDK `21.0.12.1+1-1-26.04.4-Ubuntu`.
- JVM `-Xms512m -Xmx1g`; client and API share that heap. No CPU affinity or fixed
  CPU frequency. Desktop applications remained open; no other builds or test
  suites were launched during measurement. This was not a dedicated benchmark host.
- Docker `29.8.2`, `overlay2`, local SK hynix BC901 NVMe storage with ext4.
  PostgreSQL used an anonymous Docker volume, without explicit CPU/memory limits.
- PostgreSQL `16.13` (`postgres:16-alpine`), shared buffers `128MB`, maximum
  connections `100`, and all three durability settings above enabled. The exact
  image ID is preserved in the reports; the image tag itself can change over time.

All three independent runs passed with zero failed HTTP or persistence checks:
4,200 measured requests per run, plus 500 warm-up requests and 21 logins. The
[raw JSON reports](performance-baseline.json) retain each run's timing and metadata.
Observed ranges across the three runs (p95 ranges are per-run percentiles, not a
pooled percentile):

- Customer pages: 133.8-193.6 requests/second; p95 38.7-57.5 ms.
- Operational pages: 53.7-63.7 requests/second; p95 90.7-111.2 ms.
- Automatic dispatch: 49.5-56.3 requests/second; p95 23.2-25.7 ms.

The variation is substantial, especially for customer reads. Compare repeated
runs on the same controlled environment before attributing a difference to code.
These values establish an initial observation; they do not demonstrate an
optimization, maximum throughput, or production capacity.
