# jee6-ejb-examples

A multi-module Gradle project for Java EE 6 with EJB 2.x examples, packaged as an
EAR and used from servlets and standalone remote clients:

* **Stateful session bean**: `ShoppingCartEJB`, a shopping cart that keeps per-client state
* **Stateless session bean**: `CurrencyConverterEJB`, a pooled currency converter configured by an `env-entry`
* **CMP 2.x entity bean**: `ProductEJB`, a product catalog with EJB-QL finders, an `ejbSelect` and a home business method
* **Message-driven bean**: `PriceChangeMDB`, which consumes JMS price change messages and applies them to `ProductEJB`

## Build

```bash
./gradlew buildEar      # ear/build/libs/jee6-ejb-examples.ear   <- deploy this
./gradlew buildWar      # web/build/libs/jee6-ejb-examples-web.war (web module only)
./gradlew clientJar     # client/build/libs/jee6-ejb-examples-client.jar
./gradlew test          # unit tests for the beans, servlets and remote clients (see "Unit tests")
./gradlew coverage      # all unit tests + combined coverage report (see "Code coverage")
```

## Modules

```
jee6-ejb-examples/
├── ejb/      all beans + META-INF/ejb-jar.xml, glassfish-ejb-jar.xml  -> jee6-ejb-examples-ejb.jar
├── web/      servlets + WEB-INF/web.xml                               -> jee6-ejb-examples-web.war
├── client/   standalone remote clients for the session beans          -> jee6-ejb-examples-client.jar
└── ear/      bundles the EJB jar and the WAR, generates application.xml -> jee6-ejb-examples.ear
```

**Why an EAR?** The EJB 3.1 spec doesn't allow entity beans (CMP or BMP) in a
WAR, so the beans live in a separate EJB module. The WAR uses the EJB module's
interfaces (`providedCompile project(':ejb')`) without bundling them. Its manifest
`Class-Path` points at the EJB jar in the EAR. The WAR on its own is not
deployable; deploy the EAR.

## Stateful example

| File | Role |
|------|------|
| `ShoppingCartHome` / `ShoppingCart` | EJB 2.x **remote** home + component interfaces (`EJBHome` / `EJBObject`) |
| `ShoppingCartLocalHome` / `ShoppingCartLocal` | EJB 2.x **local** home + component interfaces (`EJBLocalHome` / `EJBLocalObject`) |
| `ShoppingCartBean` | Bean class implementing `javax.ejb.SessionBean` (`ejbCreate`, `ejbRemove`, `ejbActivate`, `ejbPassivate`) |
| `ejb-jar.xml` | Declares the bean as `Stateful`, with container-managed transactions (no annotations) |
| `web.xml` | Servlet mapping and the `ejb-local-ref` (`java:comp/env/ejb/ShoppingCart`) |
| `ShoppingCartServlet` | Looks up the local home through JNDI, calls `create()`, keeps the cart in the `HttpSession`, and calls `remove()` at checkout |

## Stateless example

| File | Role |
|------|------|
| `CurrencyConverterHome` / `CurrencyConverter` | Remote home + component interfaces |
| `CurrencyConverterLocalHome` / `CurrencyConverterLocal` | Local home + component interfaces |
| `CurrencyConverterBean` | `SessionBean` with a no-arg `ejbCreate()`. It reads `java:comp/env/commissionPercent` and keeps no client state |
| `ejb-jar.xml` | `Stateless` session, `env-entry` `commissionPercent` = 1.5, transaction attribute `Supports` |
| `CurrencyConverterServlet` | Caches the local home, calls `create()` per request, stores nothing in the `HttpSession` |

Stateful vs. stateless in EJB 2.x:

| | Stateful cart | Stateless converter |
|---|---|---|
| Home `create` | Any arguments (`create(String)`), one bean instance per client | Exactly one, no arguments. Returns a reference; instances come from a pool |
| `ejbCreate` called | On every client `create()` | When the container adds an instance to the pool |
| `remove()` | Ends the conversation, `ejbRemove()` runs | Only discards the reference |
| `ejbActivate`/`ejbPassivate` | Called around passivation | Never called |
| `isIdentical` of two `create()` results | `false` | `true` |

## CMP entity example

| File | Role |
|------|------|
| `ProductLocalHome` | `create(sku, name, price)`, `findByPrimaryKey`, `findAll`, `findByMaxPrice`, and the home business method `getAveragePrice()` |
| `ProductLocal` | Getters/setters plus the business method `applyDiscount(percent)`. The primary key `sku` has no setter |
| `ProductBean` | **Abstract** class implementing `EntityBean`. cmp-fields are abstract get/set pairs that the container implements. `ejbCreate` returns `null`; `ejbHomeGetAveragePrice()` uses the abstract `ejbSelectAllPrices()` |
| `ejb-jar.xml` | `<entity>` with `persistence-type` `Container`, `cmp-version` `2.x`, abstract schema `Product`, cmp-fields, `primkey-field`, and the EJB-QL for `findAll`, `findByMaxPrice` and `ejbSelectAllPrices`. Transaction attribute `Required` |
| `glassfish-ejb-jar.xml` | GlassFish-only CMP setup: uses `jdbc/__default` (built-in Derby) and creates/drops the table at deploy/undeploy |
| `ProductServlet` | Wraps each request in a `UserTransaction`. Lists products, filters by max price, creates, discounts (update on commit) and deletes (`remove()`); also loads sample data |

The entity has a **local view only**, as is usual for CMP 2.x entities: remote
clients should go through a session bean ("session facade"), not call entity
beans directly.

CMP table mapping is vendor-specific. The included `glassfish-ejb-jar.xml` covers
GlassFish 3.x. Other servers need their own descriptor; for example JBoss EAP 6
needs `jbosscmp-jdbc.xml` and its CMP subsystem enabled, and WildFly and later
servers have no CMP support at all.

## Message-driven bean example

| File | Role |
|------|------|
| `PriceChangeBean` | Implements `javax.ejb.MessageDrivenBean` + `javax.jms.MessageListener`. `ejbCreate()` looks up the Product home; `onMessage()` reads a `MapMessage` (`sku`, `percent`) and calls `applyDiscount()` on the entity |
| `ejb-jar.xml` | `<message-driven>`: `messaging-type` `javax.jms.MessageListener`, `destinationType` activation property `javax.jms.Queue`, `mapped-name` `jms/PriceChangeQueue`, an `ejb-local-ref` to `ProductEJB`, and `onMessage` with trans-attribute `Required` |
| `PriceChangeServlet` | Sends the messages (JMS 1.1) through a `resource-ref` (`jms/ConnectionFactory`) and a `message-destination-ref` (`jms/PriceChangeQueue`) declared in `web.xml` |

Things to notice:
* **No home or component interface.** Clients can't call an MDB; they can only
  send a message to its queue. The servlet returns immediately and the MDB does
  the work a moment later. Open `/products` to see the new price.
* **One transaction per message.** With `Required`, the JMS receive and the CMP
  update commit together. If `onMessage` calls `setRollbackOnly()` (on a
  `FinderException` or `JMSException`), the message goes back on the queue and
  is redelivered.
* **Poison messages.** Messages that can never succeed (unknown SKU, invalid
  percent, wrong message type) are logged and committed, not rolled back.
  Otherwise they would be redelivered over and over.

## Unit tests

`./gradlew test` runs the unit tests in `ejb/src/test`, `web/src/test` and
`client/src/test` (JUnit 5 + Mockito). They test the bean, servlet and client
classes directly as plain objects, with the test acting as the container. No
server or database is needed.

### Bean tests (`ejb/src/test`)

| Test | Covers |
|------|--------|
| `ShoppingCartBeanTest` | `ejbCreate` validation; state building up across calls; `ItemNotFoundException` vs. `EJBException`; read-only `getItems()`; state surviving passivation (serialize/deserialize between `ejbPassivate` and `ejbActivate`); `ejbRemove` |
| `CurrencyConverterBeanTest` | Reading the `commissionPercent` env-entry from JNDI, and failing when it's missing; conversion math and rounding; case-insensitive codes; `UnsupportedCurrencyException`; pooled instances being interchangeable |
| `ProductBeanTest` | Uses `InMemoryProductBean`, a subclass that does what the container's generated class would: it implements the abstract cmp-fields and returns a canned `ejbSelectAllPrices()`. Covers `ejbCreate` normalization, validation and the `null` key; `applyDiscount` rounding and bounds; the `ejbHomeGetAveragePrice` home method; `ejbRemove` using the mocked `EntityContext` |
| `PriceChangeBeanTest` | `onMessage` with a mocked `ProductLocalHome`/`MapMessage`. Valid messages update the entity; an unknown SKU, an invalid percent or a wrong message type are discarded without rollback; a `FinderException` or `JMSException` calls `setRollbackOnly()` so the message is redelivered; `ejbCreate` failing without the home |

### Servlet tests (`web/src/test`)

The servlets are called directly (`doGet`/`doPost`). `HttpFixture` supplies a
mocked request and response, with parameters and session attributes backed by
maps, and captures the response HTML. Bean homes, the `UserTransaction` and the
JMS objects are mocks bound in `InMemoryJndi` under the servlets' `java:comp/...` names.

| Test | Covers |
|------|--------|
| `ShoppingCartServletTest` | `create()` once per login, with the bean kept in the session and reused by later requests; the old bean is removed on a new login; `remove()` and session cleanup at checkout; an expired bean (`NoSuchObjectLocalException`); application exception messages; HTML escaping |
| `CurrencyConverterServletTest` | `init()` failing without the home; `create()` per request and no session use; the conversion result; missing or invalid amounts rejected before calling the bean; `UnsupportedCurrencyException`; HTML escaping |
| `ProductServletTest` | Each request inside one `UserTransaction` (`begin`, then finder and getters, then `commit`, in that order); `findAll`/`findByMaxPrice`; create/duplicate/validation; discount range checked before the entity is touched; delete; sample data creating only missing products; rollback and **no page sent** on a failure or a failed commit; rollback-only status; HTML escaping |
| `PriceChangeServletTest` | The `MapMessage` sent (trimmed, upper-case SKU and integer percent) through a non-transacted session; nothing sent for invalid input; the connection closed even when `send` fails; HTML escaping |

### Remote client tests (`client/src/test`)

Each test runs the client's `main()` with the remote home and component
interfaces mocked (standing in for RMI-IIOP stubs) and bound in `InMemoryJndi`
under the client's lookup name. `ConsoleCapture` records what the client prints.

| Test | Covers |
|------|--------|
| `ShoppingCartRemoteClientTest` | The exact console output of the whole stateful conversation; all calls going to the same bean, in order; the customer-name argument; overriding `-Dcart.jndi` (JBoss name); the home and the `Handle`'s `EJBObject` both going through `PortableRemoteObject.narrow()`, with the wrong type rejected; lookup and `CreateException` failures; a wrapped `RemoteException` after `remove()` |
| `CurrencyConverterRemoteClientTest` | The exact output of the sample conversions; a single conversion from three arguments (other argument counts use the samples); an invalid amount; two `create()` references with only the first removed; overriding `-Dconverter.jndi`; narrow, wrong type and lookup failures |

`javax.rmi.PortableRemoteObject` was removed from the JDK after Java 8. The client
tests get the API from `org.glassfish.corba:glassfish-corba-omgapi` (test-only)
and plug in `testsupport/LocalPortableRemoteObject`, an in-JVM delegate. Its
`narrow()` is a checked cast that also records each call, so the tests can check
that the clients narrow instead of casting.

### How it works

* **API jars:** `javax:javaee-api:6.0` has no method bodies and can't be loaded
  at runtime, so the test classpath uses the JBoss spec jars for EJB 3.1, JMS 1.1
  and JTA 1.1 (plus Servlet 3.0 for `web`) instead. They are test-only and never
packaged. The shared setup lives in `gradle/unit-tests.gradle`, which both modules
apply. It also keeps `javaee-api` off the test classpath, since the war plugin
would otherwise add it through `providedCompile`.
* **JNDI:** the beans use `new InitialContext().lookup("java:comp/env/...")`.
  `InMemoryJndi` (in `ejb/src/testFixtures`, shared with `web` through Gradle's
  `java-test-fixtures` plugin) is a small `InitialContextFactory` that tests
  install, so env-entries and mocked homes can be bound under those names.
* **Java level:** test code is compiled for Java 11, because Mockito 5 needs it
  and tests only run on the build JDK. The beans themselves stay at Java 8.
* **Not covered:** the EJB-QL queries, the CMP table mapping, transactions, JMS
  delivery and real RMI-IIOP calls are the container's and ORB's job. They can only be tested by deploying
  the EAR to a real server (an in-container test tool such as Arquillian could
  automate that).

## Code coverage

Coverage is measured with JaCoCo while the unit tests run. It covers production
code only, not the tests or test helpers.

```bash
./gradlew coverage   # combined report: build/reports/jacoco/testCodeCoverageReport/html/index.html
./gradlew test       # per-module reports: <module>/build/reports/jacoco/test/html/index.html
./gradlew check      # also fails if any module's line coverage is below the minimum
```

* **Reports:** HTML for reading, plus XML (`*.xml` next to each `html/`
  folder) for CI tools and IDEs. The combined report uses Gradle's
  `jacoco-report-aggregation` plugin in the root `build.gradle`; the per-module
  setup is in `gradle/unit-tests.gradle`.
* **Minimum:** `check` (and so `build`) runs `jacocoTestCoverageVerification` in
  each module and fails below 85% line coverage. Change the default with
  `defaultMinLineCoverage` in the root `build.gradle`, or for one run with
  `-PminLineCoverage=0.9`.

## Deploy

Deploy `ear/build/libs/jee6-ejb-examples.ear` to a **Java EE 6 Full Profile**
server, then open `http://localhost:8080/jee6-ejb-examples/` (cart at `/cart`,
converter at `/converter`, products at `/products`, MDB price changes at `/pricing`).

GlassFish 3.x example:

```bash
asadmin start-domain
asadmin start-database          # Derby, for the CMP entity's jdbc/__default

# JMS resources for the MDB and PriceChangeServlet (create once, before deploying)
asadmin create-jms-resource --restype javax.jms.ConnectionFactory jms/ConnectionFactory
asadmin create-jms-resource --restype javax.jms.Queue \
        --property Name=PriceChangeQueue jms/PriceChangeQueue

asadmin deploy ear/build/libs/jee6-ejb-examples.ear
```

Notes:
* The deployment fails if the JMS resources `jms/ConnectionFactory` and
  `jms/PriceChangeQueue` don't exist on the server.
* On JBoss EAP 6, `mapped-name` doesn't bind the MDB to the queue. Add a
  `destination` activation-config property with the queue's JNDI name instead,
  and map the servlet's JMS references in a `jboss-web.xml`.
* EJB 2.x home/component views, entity beans and message-driven beans are not part of EJB 3.1 *Lite*,
  so a Web Profile server won't run this. Use a Full Profile server.
* The classes are compiled with `--release 8` (the oldest target current JDKs
  support), so the server must run on Java 8 or later. Examples: GlassFish 3.1.2.2,
  JBoss EAP 6.x, WebLogic 12c.
* `javax:javaee-api:6.0` is compile-only, so it is not packaged anywhere.

## Running on JBoss EAP 6.4 in Docker

`docker/` builds a JBoss EAP 6.4 image (Java 8, `standalone-full` profile)
configured for this application. Gradle tasks drive it:

```bash
./gradlew eapUp        # build the EAR and the image, start EAP, deploy, wait for the result
./gradlew eapDeploy    # rebuild and redeploy the EAR into the running server
./gradlew eapClients   # run both remote EJB clients (in a container; no local JDK 8 needed)
./gradlew eapLogs      # follow the server log
./gradlew eapDown      # stop and remove the container
```

**Before the first `eapUp`:** EAP can't be redistributed, so download
*Red Hat JBoss Enterprise Application Platform 6.4.0* (`jboss-eap-6.4.0.zip`) from
[developers.redhat.com](https://developers.redhat.com) (free account) and put it
in `docker/`. For any other EAP 6.4.x zip, pass its name with
`-PeapZip=<file name>`. `docker/*.zip` is gitignored.

Then open http://localhost:8080/jee6-ejb-examples/. The management console is at
http://localhost:9990 (`admin` / `Admin#2026`).

**Why EAP 6.4:** it's the last JBoss EAP release that supports CMP entity beans.
EAP 7 removed entity beans entirely and rejects the EAR. EAP 6 is long past
mainstream support, so use this for development and learning only.

| File | Role |
|------|------|
| `docker/Dockerfile` | `eclipse-temurin:8-jdk` + the EAP zip. Creates a management user and an application user (for remote EJB), then runs `configure-eap.cli` against a briefly started server. The healthcheck passes once the app answers |
| `docker/configure-eap.cli` | Adds the HornetQ queue `java:/jms/queue/PriceChangeQueue`. Everything else the app needs (the `cmp` subsystem, `ExampleDS` H2 datasource, `java:/JmsXA`, the remote EJB connector on 4447) is already in `standalone-full.xml` |
| `docker/compose.yaml` | The `eap` service (ports 8080, 9990, 4447) and an on-demand `client` service that runs the remote clients with the image's Java 8 and `jboss-client.jar` |
| `docker/eap.sh` | Called by the Gradle tasks. `deploy` streams the EAR into the deployment scanner's folder and waits for EAP's `.deployed` or `.failed` marker |
| `docker/client/jboss-ejb-client.properties` | Remote EJB connection settings for the clients (host `eap`, port 4447, the application user) |

JBoss-specific descriptors in the app (other servers ignore them):

| File | Purpose |
|------|---------|
| `ejb/.../META-INF/jbosscmp-jdbc.xml` | Maps `ProductEJB` to a `PRODUCT` table in `java:jboss/datasources/ExampleDS` (in-memory H2), created at deploy time. Data is lost when the server restarts |
| `ejb/.../META-INF/jboss-ejb3.xml` | Sets the MDB's `destination` activation property; JBoss ignores `mapped-name` for MDBs |
| `web/.../WEB-INF/jboss-web.xml` | Binds the servlet's `jms/ConnectionFactory` to `java:/JmsXA` and `jms/PriceChangeQueue` to the HornetQ queue |

The passwords (`Admin#2026`, `EjbUser#2026`) are development defaults. Change
them with `--build-arg` (`ADMIN_PASSWORD`, `EJB_PASSWORD`) and in
`docker/client/jboss-ejb-client.properties`.

## Remote clients

The `client` module contains standalone Java SE programs that use the session
beans' **remote** EJB 2.x views.

`ShoppingCartRemoteClient` (stateful):

1. JNDI lookup of `ShoppingCartHome`, then `PortableRemoteObject.narrow()`
2. `home.create(customer)` creates a new stateful instance
3. Several business calls against the same instance, so cart state accumulates
4. An application exception (`ItemNotFoundException`) reaching the client
5. `getHandle()` / `handle.getEJBObject()`: a serializable reference to the same conversation
6. `remove()`, then a call on the removed bean, which fails with `NoSuchObjectException`

`CurrencyConverterRemoteClient` (stateless) does the same for the converter: a
no-arg `create()`, several conversions, an `UnsupportedCurrencyException`,
`isIdentical()` returning `true` for two references, and `remove()`, which leaves
the pool untouched.

```bash
# Run against a server where the EAR is deployed (GlassFish 3 example):
./gradlew runClient \
    -PserverClientJar=/opt/glassfish3/glassfish/lib/gf-client.jar \
    -PclientJavaHome=/usr/lib/jvm/java-8-openjdk-amd64

# Stateless converter client, same settings:
./gradlew runConverterClient -PserverClientJar=... -PclientJavaHome=... -Pconvert="250 GBP JPY"
```

| Property | Purpose |
|----------|---------|
| `serverClientJar` | **Required.** The server's client library (`gf-client.jar`, `jboss-client.jar`, `wlthint3client.jar`, ...). Separate several paths with `:` |
| `clientJavaHome` | A JDK 8 to run the client with. RMI-IIOP was removed from the JDK after Java 8, and Java EE 6 era client libraries need it |
| `cartJndi` | `runClient`: home JNDI name. Defaults to `java:global/jee6-ejb-examples/jee6-ejb-examples-ejb/ShoppingCartEJB!com.example.ejb.cart.ShoppingCartHome` |
| `customerName` | `runClient`: customer name passed to `create()` (default `Alice`) |
| `converterJndi` | `runConverterClient`: home JNDI name. Defaults to `java:global/jee6-ejb-examples/jee6-ejb-examples-ejb/CurrencyConverterEJB!com.example.ejb.converter.CurrencyConverterHome` |
| `convert` | `runConverterClient`: one conversion, e.g. `-Pconvert="250 GBP JPY"` (default: three sample conversions) |

The portable names follow `java:global/<application-name>/<module-name>/<ejb-name>!<interface>`.
The application name is set in the EAR's `application.xml` and the module name
in `ejb-jar.xml`.

JNDI connection settings live in `client/src/main/resources/jndi.properties`,
which has commented blocks for GlassFish and JBoss AS 7 / EAP 6. On JBoss use:

```
-PcartJndi='ejb:jee6-ejb-examples/jee6-ejb-examples-ejb//ShoppingCartEJB!com.example.ejb.cart.ShoppingCartHome?stateful'
-PconverterJndi='ejb:jee6-ejb-examples/jee6-ejb-examples-ejb//CurrencyConverterEJB!com.example.ejb.converter.CurrencyConverterHome'
```
