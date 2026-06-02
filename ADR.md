# Architecture Decision Records - Java 25 Migration

## ADR-001: Keep Spring 5.x Instead of Migrating to Spring 6.x
**Date**: 2026-06-01  
**Status**: Accepted

### Context
OpenMRS Core 2.5.15 uses Spring 5.2.14.RELEASE. Java 25 support requires either:
- Option A: Spring 5.3.x (latest 5.x) - no javax→jakarta namespace change needed
- Option B: Spring 6.x - requires javax→jakarta in all source files (~300+ files)

### Decision
Upgrade to Spring 5.3.39 (latest 5.x), keeping javax namespace.

### Rationale
- Spring 5.3.x works with Java 25 for compilation when using `source/target` mode (not `--release`)
- Migrating to Spring 6.x would require changing ALL servlet references, validation, and transaction APIs
- The scope of javax→jakarta migration would affect hundreds of source files and is a separate major upgrade
- Spring 5.x is familiar to the OpenMRS development team

### Consequences
- May have runtime incompatibilities on Java 25 (Spring 5.x officially tested to Java 21)
- Compilation should succeed
- A future migration to Spring 6 is still needed for full Java 25 runtime support

---

## ADR-002: Keep Hibernate 5.x Instead of Migrating to Hibernate 6.x
**Date**: 2026-06-01  
**Status**: Accepted

### Context
Hibernate 5.6.x uses javax.persistence. Hibernate 6.x uses jakarta.persistence and has major API changes.

### Decision
Upgrade to Hibernate 5.6.15.Final (latest 5.6.x), keeping javax.persistence.

### Rationale
- Migrating to Hibernate 6.x requires jakarta.persistence namespace throughout all @Entity classes
- Hibernate 6.x has significant API changes (HQL, Criteria API, etc.)
- Keeping 5.x minimizes source code changes
- All 5.6.x libraries include javax.persistence as a dependency (classpath availability)

### Consequences
- JPMS --add-opens flags required for Hibernate reflection access
- May have runtime issues on Java 25 (not officially tested)

---

## ADR-003: Remove PowerMock
**Date**: 2026-06-01  
**Status**: Accepted

### Context
PowerMock 2.0.9 (2021) is incompatible with Java 17+ at runtime. The project declares PowerMock
as a dependency but inspection shows only ONE test file (ModuleFileParserTest.java) has a comment
mentioning PowerMock - there's no actual PowerMock test code usage.

### Decision
Remove all PowerMock dependencies (powermock-reflect, powermock-module-junit4, powermock-api-mockito2).

### Rationale
- PowerMock uses javassist/ASM for bytecode manipulation that doesn't support Java 25 class format
- Mockito 5.x has built-in inline mock support replacing PowerMock's primary use case
- Inspection shows no actual PowerMock API usage in test files
- Removal reduces dependency count and build complexity

### Consequences
- If future tests need static/final mocking, Mockito 5.x inline mocks can be used instead
- No source code changes required

---

## ADR-004: Upgrade Groovy from 2.4.x to 4.0.x
**Date**: 2026-06-01  
**Status**: Accepted

### Context
Groovy 2.4.21 doesn't support Java 25 (class file version 71.0). OpenMRS uses Groovy for 
dynamic script execution in services.

### Decision
Upgrade to Groovy 4.0.24 (latest stable 4.x series).

### Rationale
- Groovy 4.x supports Java 25 class file format
- groovy-all artifact still available in 4.x
- OpenMRS primarily uses Groovy for simple script evaluation, which hasn't changed significantly

### Consequences
- Groovy 4.x uses org.apache.groovy namespace for some internal classes
- Dynamic script behavior should be backward compatible for basic use cases
- May need code adjustments if advanced Groovy 2.x APIs are used

---

## ADR-005: Upgrade H2 from 1.4.200 to 2.3.232
**Date**: 2026-06-01  
**Status**: Accepted

### Context
H2 1.4.200 is the last 1.x release (2019). H2 2.x has different SQL compatibility modes
and uses a different JDBC URL format for compatibility with Hibernate 5.x.

### Decision
Upgrade to H2 2.3.232 with LEGACY compatibility mode in test configurations.

### Rationale
- H2 1.4.200 has known issues with modern Java
- H2 2.x has better Java 25 support
- The LEGACY compatibility mode: `INIT=SET MODE LEGACY` handles backward compatibility

### Consequences
- Test datasource configurations may need updating with `MODE=LEGACY` in JDBC URL
- Some H2 SQL syntax differences may affect test data scripts

---

## ADR-006: Use source/target=25 instead of --release=25
**Date**: 2026-06-01  
**Status**: Accepted

### Context
Maven compiler plugin supports two modes:
- `<source>25</source><target>25</target>`: Enables Java 25 syntax, allows old APIs on classpath
- `<release>25</release>`: Restricts to Java 25 standard library only (no javax.* not in JDK)

### Decision
Use `<source>25</source><target>25</target>` mode.

### Rationale
- javax.persistence, javax.servlet etc. are NOT in JDK 25 but ARE in Maven dependencies
- Using --release 25 would break compilation of all Hibernate/Spring code that uses javax.*
- source/target mode allows explicit Maven dependencies to provide these APIs

### Consequences
- Compiled code may use APIs not available in the JDK runtime (but available via classpath)
- This is the correct approach for legacy javax applications running on modern JDK

---

## ADR-007: Add JPMS --add-opens for Runtime Reflection Access
**Date**: 2026-06-01  
**Status**: Accepted

### Context
Java 9+ module system restricts reflective access to internal JDK classes.
Spring, Hibernate, and AspectJ use reflection to access internal JDK APIs.

### Decision
Add comprehensive --add-opens flags to the Maven Surefire argLine for test execution,
and to the maven-compiler-plugin compilerArgs for compilation.

### Rationale
- Spring AOP, Hibernate proxy generation, and AspectJ weaving all require internal access
- --add-opens is the official migration mechanism for pre-module libraries
- Required for tests to run (not just compilation)

### Consequences
- JVM will log warnings about illegal reflective access (suppressed with --add-opens)
- Must be maintained as JDK evolves and access patterns change

---

## ADR-009: Remove commons-fileupload, Use Jakarta Servlet Part API
**Date**: 2026-06-01  
**Status**: Accepted

### Context
`commons-fileupload 1.5` (the last 1.x version) depends on `javax.servlet.http.HttpServletRequest`. With Spring 6 + `jakarta.servlet-api`, there's no `javax.servlet` on the classpath, causing compilation failure.

### Decision
Replace `commons-fileupload` usage in `StartupErrorFilter` with the Jakarta Servlet 6 `HttpServletRequest.getParts()` API. Remove `commons-fileupload` from web module dependencies.

### Rationale
- Jakarta Servlet 6 has native multipart support via `HttpServletRequest.getParts()` (since Servlet 3.0)
- Eliminates third-party dependency for a simple file upload use case
- The only usage was in `StartupErrorFilter.doPost()` for module file uploads during error recovery

### Consequences
- Web module no longer depends on `commons-fileupload`
- File upload in `StartupErrorFilter` now requires Servlet container multipart config
- Simpler code using standard Jakarta Servlet API

---

## ADR-010: Hibernate 6.x API Breaking Changes Handled
**Date**: 2026-06-01  
**Status**: Accepted

### Key Hibernate 6.x API Changes Made
- `ClassMetadata` removed → use `SessionFactory.getRuntimeMetamodels().getMappingMetamodel().getEntityDescriptor()`
- `EntityMode` removed from Interceptor methods → `Serializable id` → `Object id`  
- `EmptyInterceptor` removed → implement `Interceptor` interface directly (has default methods)
- `EmptyInterceptor.instantiate(String, EntityMode, Serializable)` → removed method
- `EmptyInterceptor.onPrepareStatement(String)` → removed from interface
- `Session.getClassMetadata()` → removed
- `PostgreSQL82Dialect` → `PostgreSQLDialect`
- `SessionFactoryImplementor.getDialect()` → `getJdbcServices().getDialect()`
- `Property.getColumnIterator()` → `getColumns()`, returns List
- `Session.createFilter()` → removed (use HQL)
- `Session.disconnect()`/`reconnect()` → removed
- `PersistentSet` moved from `collection.internal` to `collection.spi`
- `Session.getIdentifier()` → returns `Object` not `Serializable`
- `IdentityGenerator` replaced with `BeforeExecutionGenerator` for custom generators
- `StandardBasicTypes.STRING/INTEGER` → use `String.class`/`Integer.class` directly in `addScalar()`
- `NativeQuery.setString()/setInteger()` → `setParameter()` (all typed setters removed)

---

## ADR-008: Upgrade Mockito from 3.12.4 to 5.14.2
**Date**: 2026-06-01  
**Status**: Accepted

### Context
Mockito 3.x uses older ByteBuddy that has limited Java 25 support.
Mockito 5.x uses modern ByteBuddy with full Java 25 support.

### Decision
Upgrade to Mockito 5.14.2 (latest stable).

### Rationale
- Mockito 5.x has inline mocking enabled by default (replaces PowerMock for static/final)
- Better Java 25 bytecode compatibility
- No API breaking changes for basic mock, verify, when patterns

### Consequences  
- Mockito 5.x requires Java 11+ (our target is Java 25 so this is fine)
- Some rarely-used Mockito 3.x APIs were removed in 5.x - unlikely to affect OpenMRS tests
