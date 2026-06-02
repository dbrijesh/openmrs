# Java 25 Migration Plan: openmrs-core 2.5.15

## Goal
Upgrade openmrs-core 2.5.15 from Java 8 to Java 25 (JDK at D:\Java\jdk-25.0.3) so that the entire Maven build compiles successfully.

## Current State
- Java: 1.8 (javaCompilerVersion=1.8)
- Spring: 5.2.14.RELEASE
- Hibernate: 5.6.0.Final
- Hibernate Search: 5.11.9.Final
- Hibernate Validator: 5.4.3.Final
- Lucene: 5.5.5
- AspectJ: 1.9.7
- Jackson: 2.13.0
- JUnit: 5.8.1
- Mockito: 3.12.4
- PowerMock: 2.0.9
- Groovy: 2.4.21 (groovy-all)
- H2: 1.4.200
- SLF4J: 1.7.32
- Log4j2: 2.17.2
- EHCache: 2.10.9.2
- Maven Compiler Plugin: 3.8.1
- Maven Surefire Plugin: 2.22.2 (management) / 3.0.0-M7 (actual)

## Target Versions (Java 25 Compatible)
- Java: 25
- Spring: 5.3.39 (latest 5.x, tested up to Java 21, but source-compatible with Java 25)
- Hibernate: 5.6.15.Final (latest 5.6.x)
- Hibernate Search: 5.11.12.Final (latest 5.x)
- Hibernate Validator: 6.2.5.Final (latest javax.validation version)
- Lucene: 5.5.5 (tied to Hibernate Search 5.x)
- AspectJ: 1.9.22.1 (latest, Java 25 support)
- Jackson: 2.18.1
- JUnit: 5.11.3
- Mockito: 5.14.2 (inline mocks built-in, no PowerMock needed)
- PowerMock: REMOVED (incompatible with Java 17+)
- Groovy: 4.0.24 (latest 4.x, Java 25 compatible)
- H2: 2.3.232 (latest, needed for modern Java)
- SLF4J: 1.7.36 (latest 1.7.x, compatible with log4j-slf4j-impl)
- Log4j2: 2.24.3
- EHCache: 2.10.9.2 (keep with Hibernate 5.x)
- Maven Compiler Plugin: 3.13.0
- Maven Surefire Plugin: 3.5.2
- Maven JAR Plugin: 3.4.2
- Javassist: 3.30.2-GA

## Migration Strategy

### Approach: Source-Compatible Migration
- Use `<source>25</source><target>25</target>` (NOT `--release 25`) to allow javax.* on classpath
- Keep Spring 5.x and Hibernate 5.x (javax namespace) to avoid mass java source file changes
- Add JPMS `--add-opens` JVM flags for Spring/Hibernate reflection access
- Upgrade problematic dependencies that cause compilation failures

### Phase 1: POM Updates
1. Update root pom.xml:
   - javaCompilerVersion: 1.8 → 25
   - Update all version properties
   - Update Maven plugin versions
   - Add --add-opens to argLine for tests and compiler

2. Update sub-module poms as needed

### Phase 2: Fix Compilation Errors
- Address any source-level compilation errors from:
  - Deprecated API removal in Java 25
  - Internal JDK API usage
  - Incompatible library APIs

### Phase 3: Build Validation
- Run `mvn clean compile -DskipTests` to validate compilation
- Run `mvn clean package -DskipTests` to validate packaging
- Optionally run tests to check runtime compatibility

## Key Decisions

### Keep javax.* namespace (NOT migrate to jakarta.*)
**Rationale:** Spring 5.x and Hibernate 5.x use javax.* throughout. Full jakarta migration would require 
touching hundreds of source files and upgrading to Spring 6.x + Hibernate 6.x, which is a 
much larger breaking change. The javax.* APIs are available as explicit Maven dependencies, 
so they compile fine even on Java 25.

### Remove PowerMock
**Rationale:** PowerMock 2.0.9 (released 2021) doesn't support Java 17+ at runtime.  
Only ONE test file referenced PowerMock (ModuleFileParserTest.java) but it doesn't  
actually use PowerMock annotations - it was just a comment. The dependency can be removed.

### Upgrade Groovy to 4.x
**Rationale:** Groovy 2.4.x doesn't support Java 25 (class file version incompatibility).  
Groovy 4.x is the current stable version with Java 25 support.

### Upgrade H2 to 2.x  
**Rationale:** H2 1.4.200 is incompatible with modern JDKs and has breaking changes.
H2 2.x has a new SQL compatibility mode needed for Hibernate integration.

## JPMS Module Opens Required
Spring and Hibernate use reflection extensively. The following --add-opens flags are needed:
- java.base/java.lang=ALL-UNNAMED
- java.base/java.lang.reflect=ALL-UNNAMED  
- java.base/java.util=ALL-UNNAMED
- java.base/java.io=ALL-UNNAMED
- java.base/java.math=ALL-UNNAMED
- java.base/java.nio=ALL-UNNAMED
- java.base/sun.nio.ch=ALL-UNNAMED
- java.base/java.net=ALL-UNNAMED
- java.rmi/sun.rmi.transport=ALL-UNNAMED

## Risk Assessment
- LOW: Maven POM version changes (mechanical)
- LOW: PowerMock removal (only 1 file, no actual PowerMock usage)
- MEDIUM: Groovy 4.x API changes (OpenMRS uses it for scripting, API changes between 2.x and 4.x)
- MEDIUM: H2 2.x compatibility (SQL dialect changes)
- HIGH: Spring 5.3.x with Java 25 (not officially tested, may have runtime issues)
- HIGH: Hibernate 5.6.x with Java 25 (not officially tested, may have runtime issues)
