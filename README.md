# jee6-ejb-examples

A multi-module Gradle project that started as Java EE 6 / EJB 2.x examples and
has been migrated to **plain Java objects (POJOs)** with **Redis** (through the
[Redisson](https://redisson.org) client) in place of the EJB container and JNDI.
It runs as a single WAR on Tomcat 9, with standalone clients that call the
services remotely through Redis.

| Example | Was (EJB 2.x) | Now |
|---|---|---|
| Shopping cart | Stateful session bean `ShoppingCartEJB` | `ShoppingCartService`: carts are Redis keys with a 30-minute idle timeout |
| Currency converter | Stateless session bean `CurrencyConverterEJB` | `CurrencyConverterService`: one shared, thread-safe instance |
| Product catalog | CMP 2.x entity bean `ProductEJB` | `Product` value object + `ProductCatalog` stored in a Redis hash |
| Price changes | Message-driven bean `PriceChangeMDB` on a JMS queue | `PriceChangeWorker` thread consuming a reliable Redis list queue |

## Build

```bash
./gradlew buildWar      # web/build/libs/jee6-ejb-examples.war   <- deploy this
./gradlew test          # unit tests (the Redis ones need Docker; see "Tests")
./gradlew coverage      # all tests + combined coverage report
./gradlew appUp         # Redis + Tomcat in Docker, with the WAR deployed (see "Run")
```

Needs JDK 17 or later. The code is compiled with `--release 17`.

## Modules

```
jee6-ejb-examples/
├── core/     services (POJOs), their Redis implementations, AppServices -> jee6-ejb-examples-core.jar
├── web/      servlets + AppContextListener + WEB-INF/web.xml             -> jee6-ejb-examples.war
└── client/   standalone clients using Redisson RemoteService             -> ./gradlew :client:installDist
```

The `ejb` module became `core` (packages `com.example.core.*`). The `ear` module
is gone: without entity beans there is no need for an EAR, and the core jar,
Redisson and its dependencies are packaged in the WAR's `WEB-INF/lib`.

## What replaced what

| EJB / Java EE | Replacement |
|---|---|
| Home and component interfaces (`EJBHome`, `EJBLocalHome`, `EJBObject`, `EJBLocalObject`) | One plain business interface per service: `ShoppingCartService`, `CurrencyConverter`, `ProductCatalog`, `PriceChangeQueue` |
| Bean classes and lifecycle callbacks (`ejbCreate`, `ejbRemove`, `ejbActivate`, `ejbPassivate`, ...) | Ordinary classes with constructors. Nothing to passivate: state is in Redis |
| `ejb-jar.xml`, `glassfish-ejb-jar.xml`, `jboss-ejb3.xml`, `jbosscmp-jdbc.xml` | None. `AppServices` builds and wires everything in Java |
| `ejb-local-ref` in web.xml + `new InitialContext().lookup("java:comp/env/ejb/...")` | `AppContextListener` stores each service in the `ServletContext` under its interface name. Servlets fetch it in `init()` |
| `env-entry` `commissionPercent` (JNDI) | Field `commissionPercent` in the Redis hash `jee6-ejb-examples:config` (`AppConfig`), read on every call, so it can be changed while running |
| CMP fields, EJB-QL finders, `ejbSelect`, home business method | `RedisProductCatalog`: Java queries over a Redis hash |
| `UserTransaction` around each product request | None. Each catalog call is atomic on its own (`HSETNX` for create, compare-and-set for discounts) |
| JMS queue + `MapMessage` + MDB `onMessage` | `RedisPriceChangeQueue` (JSON messages on Redis lists) + `PriceChangeWorker` + `PriceChangeHandler` |
| MDB rollback/redelivery (`setRollbackOnly`) | The message goes back on the queue with `attempt + 1`. After 5 attempts it goes to a dead-letter list |
| Remote views over RMI-IIOP (`PortableRemoteObject.narrow`, `Handle`, vendor client jar, Java 8) | Redisson RemoteService: `RRemoteService.get(ShoppingCartService.class)` gives a proxy. The cart id plays the part of the `Handle` |
| `NoSuchObjectLocalException` (removed or timed-out stateful bean) | `CartNotFoundException` |
| `CreateException`, `EJBException`, `DuplicateKeyException`, `ObjectNotFoundException` | `IllegalArgumentException`, `DuplicateProductException`, `ProductNotFoundException` |
| JBoss EAP 6.4 Docker image (`docker/Dockerfile`, `configure-eap.cli`, `eap.sh`) | `docker/compose.yaml` (Redis 7.4 + Tomcat 9) and `docker/app.sh` |

### Wiring: `AppServices` and `AppContextListener`

`core/.../AppServices` is the composition root, the one place where objects are
created with `new` and handed their dependencies through constructors. Starting
it:

1. writes default settings to the config hash, without overwriting existing ones
2. moves any unacknowledged price changes back onto the queue, then starts the worker thread
3. publishes the cart and converter through Redisson RemoteService

`close()` does the reverse. In the web application, `AppContextListener`
(registered in web.xml) starts it when the application starts and closes it when
it stops.

The Redis address comes from the `redis.address` system property, then the
`REDIS_ADDRESS` environment variable, and defaults to `redis://localhost:6379`.

### Redis data layout

All keys start with `jee6-ejb-examples:`.

| Key | Type | Contents |
|---|---|---|
| `config` | hash | Settings as strings, e.g. `commissionPercent` → `1.5` |
| `products` | hash | Field: the SKU as JSON (`"MG-002"`). Value: the product as JSON, e.g. `{"name":"Coffee mug","price":9.50,"sku":"MG-002"}` |
| `cart:{<id>}:customer` | string | The customer name. The cart exists while this key does. Expires after 30 minutes idle |
| `cart:{<id>}:items` | list | Item names in the order added. Same expiry |
| `pricing:queue` | list | Waiting price change messages (JSON) |
| `pricing:processing` | list | Messages taken but not yet acknowledged |
| `pricing:dead` | list | Messages that failed 5 times |
| `remote:...` | various | Redisson RemoteService requests and responses |

For example, with the Docker setup running:

```bash
docker compose -f docker/compose.yaml exec redis redis-cli HGETALL jee6-ejb-examples:products
docker compose -f docker/compose.yaml exec redis redis-cli HSET jee6-ejb-examples:config commissionPercent 2.0
```

The second command changes the converter's commission at once, without a redeploy.

## The examples

### Shopping cart (was the stateful session bean)

| File | Role |
|---|---|
| `ShoppingCartService` | `createCart(customer)` returns a cart id. Every other method takes that id |
| `RedisShoppingCartService` | Keeps each cart in two keys (see above). Every call pushes the expiry back |
| `ShoppingCartServlet` | Keeps only the cart id in the `HttpSession`. Checkout calls `removeCart`. An expired cart is dropped from the session with a message |

Stateful vs. stateless, before and after:

| | Before (stateful bean) | Now |
|---|---|---|
| Where the state lives | In the bean instance, on one server | In Redis, visible to every server and to remote clients |
| Identity | The `EJBObject` / `Handle` | The cart id string |
| Timeout | Container's stateful timeout, then `NoSuchObjectLocalException` | Redis key expiry (30 min idle), then `CartNotFoundException` |
| Passivation | Serialized to disk by the container | Not needed |
| Server restart | Carts lost | Carts kept (the Docker Redis uses an append-only file) |

### Currency converter (was the stateless session bean)

`CurrencyConverterService` has no per-client state and no mutable fields, so one
instance is shared by every thread and no pool is needed. The commission comes
from a `Supplier<BigDecimal>`. In the application that is
`AppConfig::getCommissionPercent`, which reads Redis on every call.

### Product catalog (was the CMP entity bean)

| File | Role |
|---|---|
| `Product` | Immutable value: `Product.of(sku, name, price)` validates and normalizes (SKU upper case, price to 2 decimals). `withDiscount(percent)` returns a new product |
| `ProductCatalog` | `create`, `find`, `findAll` (by SKU), `findByMaxPrice` (cheapest first), `applyDiscount`, `delete`, `getAveragePrice` |
| `RedisProductCatalog` | One Redis hash. `create` uses `HSETNX`, so concurrent duplicates fail cleanly. `applyDiscount` is an optimistic compare-and-set loop, so concurrent discounts all apply |
| `ProductServlet` | The same page as before, without the `UserTransaction`. It still builds the page in memory, so a failure gives an error rather than half a page |

The queries read the whole hash. That suits a demo catalog. A large one would
need an index (for example a sorted set of prices) or a search module.

### Asynchronous price changes (was the message-driven bean)

| File | Role |
|---|---|
| `PriceChangeServlet` | Sends `(sku, percent)` with `PriceChangeQueue.send` and returns at once with the message id |
| `PriceChange` | The message, as JSON: `id`, `sku`, `percent`, `attempt` |
| `RedisPriceChangeQueue` | The "reliable queue" pattern on Redis lists: `BRPOPLPUSH` moves a message from `queue` to `processing` in one atomic step, and it stays there until acknowledged |
| `PriceChangeWorker` | A daemon thread: receive, handle, then acknowledge, retry or dead-letter. At startup it puts back whatever a previous run left in `processing` |
| `PriceChangeHandler` | The old `onMessage` rules: apply the discount; **discard** messages that can never succeed (unknown SKU, percent outside 1–99, unreadable JSON); **retry** when the catalog fails (e.g. Redis unavailable) |

Delivery is **at least once**. A crash after applying a discount but before
acknowledging it applies the discount again on restart. The JMS version avoided
that by committing the receive and the update in one transaction; Redis has no
such transaction across the queue and the update. The design also assumes **one
consumer** per queue (one application instance). For several, give each
consumer its own processing list, or switch to Redis Streams with consumer groups.

## Remote clients

The `client` module contains Java SE programs that call the web application's
services through **Redisson RemoteService**. A call on the proxy becomes a
request in Redis; one of the web application's RemoteService workers runs it
and sends back the result or the exception. Checked exceptions such as
`ItemNotFoundException` reach the client as themselves, as they did with EJB
remote views. The clients need only the Redis address: no JNDI settings, vendor
client jar or Java 8.

`ShoppingCartRemoteClient` creates a cart, adds and removes items, gets an
`ItemNotFoundException`, shows the cart id (the old `Handle`), removes the cart,
and gets a `CartNotFoundException` afterwards. `CurrencyConverterRemoteClient`
lists the currencies and commission, runs sample conversions, and gets an
`UnsupportedCurrencyException`.

```bash
./gradlew runClient                                  # cart client, customer "Alice"
./gradlew runClient -PcustomerName=Bob
./gradlew runConverterClient -Pconvert="250 GBP JPY"
./gradlew runClient -PredisAddress=redis://otherhost:6379
```

A client fails with a `RemoteServiceAckTimeoutException` after 2 seconds if no
web application is serving that Redis.

## Run (Docker)

```bash
./gradlew appUp        # build the WAR, start Redis + Tomcat, deploy, wait for it
./gradlew appDeploy    # rebuild and redeploy the WAR into the running Tomcat
./gradlew appClients   # run both remote clients (on the host, against localhost:6379)
./gradlew appLogs      # follow the Tomcat log (the services log there)
./gradlew appDown      # stop and remove the containers and the Redis data
```

Then open http://localhost:8080/jee6-ejb-examples/ (cart at `/cart`, converter at
`/converter`, products at `/products`, price changes at `/pricing`).

| File | Role |
|---|---|
| `docker/compose.yaml` | `redis` (7.4, append-only file in a volume, port 6379 on 127.0.0.1 only) and `tomcat` (9.0, JDK 21, port 8080, `REDIS_ADDRESS=redis://redis:6379`) |
| `docker/app.sh` | Called by the Gradle tasks. `deploy` streams the WAR into `webapps/` with a final rename, and waits for Tomcat's "deployment ... has finished" log line and an HTTP 200 |

To deploy elsewhere, put the WAR on any Servlet 4.0 container (Tomcat 9, Jetty
9.4/10) and set `REDIS_ADDRESS` or `-Dredis.address`. Tomcat 10 and later use
`jakarta.servlet` and would need the servlets' imports changed.

Notes:
* Redisson's own logging goes through SLF4J (`slf4j-simple`, configured in
  `web/src/main/resources/simplelogger.properties`). The ERROR entries that
  RedissonRemoteService writes for every exception a service throws are turned
  off there, because the caller receives and handles those exceptions.
* On redeploy Tomcat may report that the application "created a ThreadLocal
  ... InternalThreadLocalMap" (Netty, used by Redisson). Tomcat renews its
  threads to clear it. To avoid the message, install Redisson in Tomcat's
  `lib/` instead of the WAR.

## Tests

`./gradlew test` runs JUnit 5 + Mockito tests in all three modules.

* **Plain unit tests** (no Redis): `Product`, `CurrencyConverterService`,
  `PriceChange` JSON, `PriceChangeHandler` outcomes, `PriceChangeWorker`
  (acknowledge / retry / dead-letter, start and stop) with a mocked queue, every
  servlet with mocked services, `AppContextListener`, and the clients' exact
  console output with mocked services.
* **Redis tests**: each Redis class against a real Redis 7.4 that
  Testcontainers starts in Docker (`core/src/testFixtures/.../RedisTestServer`,
  one container per test JVM). They cover cart expiry, concurrent discounts, the
  JSON stored, queue order, recovery and dead letters, the worker applying a
  queued discount end to end (`AppServicesTest`, `WebIntegrationTest`), and
  remote calls with exceptions over RemoteService (`RemoteClientsIntegrationTest`).
  **Without Docker these tests are skipped, not failed.** Coverage then drops,
  so run `check` with `-PminLineCoverage=0`.

## Code coverage

```bash
./gradlew coverage   # combined report: build/reports/jacoco/testCodeCoverageReport/html/index.html
./gradlew test       # per-module reports: <module>/build/reports/jacoco/test/html/index.html
./gradlew check      # also fails if any module's line coverage is below the minimum
```

`check` (and so `build`) fails below 85% line coverage in any module. Change the
default with `defaultMinLineCoverage` in the root `build.gradle`, or for one run
with `-PminLineCoverage=0.9`.
