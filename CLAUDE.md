# OpenMRS Core 2.5.15 — Java 25 + Spring Boot 3.4.5 Migration

## Environment

| Tool | Location |
|------|----------|
| JDK 25 | `D:\Java\jdk-25.0.3` |
| Maven 3.9.11 | `C:\apache-maven\apache-maven-3.9.11` |
| Project | `E:\JavaUpgradeDemo\openmrs-core-2.5.15` |
| Git remote | `https://github.com/dbrijesh/openmrs` branch `test` |
| H2 database | `E:\JavaUpgradeDemo\openmrs-h2\openmrs.mv.db` |
| App logs | `E:\JavaUpgradeDemo\openmrs-logs\openmrs.log` |

## Build & Run

```powershell
# Build (all modules, skip tests)
$env:JAVA_HOME = "D:\Java\jdk-25.0.3"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd E:\JavaUpgradeDemo\openmrs-core-2.5.15
& "C:\apache-maven\apache-maven-3.9.11\bin\mvn.cmd" package -DskipTests -P skip-all-checks

# Run (from project dir so openmrs-runtime.properties is found)
D:\Java\jdk-25.0.3\bin\java.exe -jar webapp\target\openmrs.war
```

Build succeeds in ~1:08 min. App starts in ~40 sec.

## Current State (2026-06-03)

### What's done ✓
- **Java 25** — `java.version=25`, all modules compile
- **Spring Boot 3.4.5** as root parent POM (replaces manual Spring 5.2.x management)
- **Spring 6.2.6** (Jakarta EE, `jakarta.*` namespace throughout)
- **Hibernate ORM 6.6.4.Final** (`org.hibernate.orm` groupId)
- **Hibernate Search 7.2.2.Final** (`org.hibernate.search` groupId)
- **All 27+ DAO files** migrated from Hibernate Criteria → HQL
- **javax.* → jakarta.*** across all source files
- **EHCache 3.x** with JCache/JCacheCacheManager
- **Groovy 4.x** (`org.apache.groovy`)
- **H2 2.3.232**, Mockito 5.x (PowerMock removed)
- **`OpenmrsApplication.java`** — `@SpringBootApplication` entry point
- **`WebConfig.java`** — Java config replacing `web.xml` (all filters + servlets)
- **`application.properties`** — server.port=8080, context=/openmrs
- **`openmrs-runtime.properties`** — H2 file-based DB config

### Spring Boot integration fixes applied
| Fix | File(s) |
|-----|---------|
| `${springVersion}` undefined → removed redundant spring-framework-bom import | `pom.xml` |
| `CommonsMultipartResolver` removed → `StandardServletMultipartResolver` | `openmrs-servlet.xml` |
| `allow-bean-definition-overriding=true` for XML bean overrides | `application.properties` |
| `allow-circular-references=true` for openmrsEventListeners cycle | `application.properties` |
| `BeanFactoryPostProcessor` loads `openmrs-runtime.properties` BEFORE `HibernateSessionFactoryBean` | `WebConfig.java` |
| `NativeIfNotAssignedIdentityGenerator` — implements both `BeforeExecutionGenerator` + `OnExecutionGenerator` with per-entity `generatedOnExecution(Object,Session)` | `NativeIfNotAssignedIdentityGenerator.java` |
| Removed `multipartFilter` DelegatingFilterProxy (not a Filter in Spring Boot) | `WebConfig.java` |
| `Listener.contextInitialized()` — null-guard `getRealPath()` in `clearDWRFile`, `copyCustomizationIntoWebapp`, `loadBundledModules` | `Listener.java` |
| `Listener.contextInitialized()` — skip `createWebApplicationContext` when Spring Boot root context already exists | `Listener.java` |
| `Listener.contextInitialized()` — skip `WebDaemon.startOpenmrs()` in Spring Boot path; deferred to `ApplicationReadyEvent` | `Listener.java` |
| `WebConfig.openmrsStartupListener()` — `ApplicationReadyEvent` calls `Listener.startOpenmrs()` after all beans are ready | `WebConfig.java` |
| `log4j2.xml` — removed `${openmrs:}` custom lookups (fail before OpenMRS context), use fixed log path, `<Null>` placeholder for MEMORY_APPENDER | `log4j2.xml` |
| `DatabaseDetective.isDatabaseEmpty()` — restrict `getTables()` to user schema (H2 2.x exposes `INFORMATION_SCHEMA.CONSTANTS` as `BASE TABLE` via null-schema query) | `DatabaseDetective.java` |
| `StartupFilter.doFilter()` — serve `/images` and `/initfilter/scripts` via `getResourceAsStream()` when `getRealPath()` is null (embedded Tomcat) | `StartupFilter.java` |
| `OpenmrsLoggingUtil.getMemoryAppender()` — `instanceof` guard before casting (prevents `ClassCastException` when MEMORY_APPENDER is `NullAppender` placeholder) | `OpenmrsLoggingUtil.java` |

## App Access

- **URL**: `http://localhost:8080/openmrs/`
- **Actuator health**: `http://localhost:8081/actuator/health`
- **Login**: `admin` / `Admin123` (after initial setup)

## Database Setup Status

The app currently uses **H2 file-based DB** (`E:/JavaUpgradeDemo/openmrs-h2/openmrs`).

**First run** — must complete the Installation Wizard:
1. Open `http://localhost:8080/openmrs/`
2. Language → English → forward
3. Simple installation → follow wizard
4. Click "Finish Installation" — Liquibase creates ~100+ tables (~15-30 min on H2)
5. Login with `admin` / `Admin123`

**Runtime properties** — `E:\JavaUpgradeDemo\openmrs-core-2.5.15\openmrs-runtime.properties`:
- `has_current_openmrs_database=false` + `auto_update_database=false` = fresh install via wizard
- `has_current_openmrs_database=true` + `auto_update_database=false` = skip wizard (DB already set up)
- Delete `E:\JavaUpgradeDemo\openmrs-h2\openmrs.mv.db` to reset to fresh state

**Switch to MySQL** (recommended for production, ~3 min setup):
```properties
connection.url=jdbc:mysql://localhost:3306/openmrs?autoReconnect=true&useSSL=false
connection.driver_class=com.mysql.cj.jdbc.Driver
connection.username=openmrs
connection.password=openmrs
hibernate.dialect=org.hibernate.dialect.MySQLDialect
has_current_openmrs_database=false
auto_update_database=true
```

## Key Architecture Decisions

- WAR packaging (not JAR) — OpenMRS module classloader system requires it
- XML configs loaded via `@ImportResource` — too many beans to migrate to Java config
- `DataSourceAutoConfiguration` excluded — OpenMRS owns DataSource via C3P0/Hibernate
- `HibernateJpaAutoConfiguration` excluded — OpenMRS uses SessionFactory, not EntityManager
- `Listener.startOpenmrs()` deferred to `ApplicationReadyEvent` — fixes ordering: `Listener.contextInitialized()` fires during `Tomcat.start()` inside `context.onRefresh()`, BEFORE `finishBeanFactoryInitialization()`. The `context` bean (which wires `contextDAO`) is not ready yet at that point.

## Pending / Next Steps

- [ ] MySQL install: `winget install Oracle.MySQL` (run as Admin — needs UAC)
- [ ] After MySQL: run full Liquibase migration (~3 min), login, verify all features
- [ ] Run unit tests: `mvn test` (many tests likely need fixes for Spring 6 / Hibernate 6 APIs)
- [ ] Fix "OpenMRS Core **null** Installation Wizard" title — `BUILD_TIMESTAMP` init param not set in Spring Boot
- [ ] Test module loading (`.omod` files) via the Module Admin page
- [ ] Consider switching `OpenSessionInViewFilter` from `hibernate5` to `hibernate6` package

## Important Files

```
webapp/src/main/java/org/openmrs/web/
  OpenmrsApplication.java        Spring Boot main class
  WebConfig.java                 Filter/Servlet registration + BeanFactoryPostProcessor

webapp/src/main/resources/
  application.properties         Spring Boot config
  log4j2.xml                     Logging config (no openmrs: lookups)

web/src/main/resources/
  openmrs-servlet.xml            Web MVC config (has StandardServletMultipartResolver)

web/src/main/java/org/openmrs/web/
  Listener.java                  getRealPath() null-guards, deferred startOpenmrs()
  filter/StartupFilter.java      getResourceAsStream() fallback for static files
  filter/initialization/
    DatabaseDetective.java       isDatabaseEmpty() uses connection schema, not null
    InitializationFilter.java    Installation wizard

api/src/main/java/org/openmrs/
  api/db/hibernate/NativeIfNotAssignedIdentityGenerator.java  Hibernate 6 dual-interface
  logging/OpenmrsLoggingUtil.java  instanceof guard for MemoryAppender

openmrs-runtime.properties       DB connection + install settings (in project root)
STATE.md                         Detailed migration step log
```
