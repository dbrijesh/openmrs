# OpenMRS Core 2.5.15 - Java 25 Migration Project

## Project Overview
- **Project**: openmrs-core 2.5.15
- **Migration**: Java 8 → Java 25
- **Start Date**: 2026-06-01
- **JDK Location**: D:\Java\jdk-25.0.3
- **Maven Version**: Apache Maven 3.9.11

## Module Structure
```
openmrs-core-2.5.15/
├── tools/          - Build resources (formatters, Eclipse config)
├── test/           - Shared test dependencies (pom-only module)
├── api/            - Core API (domain model, services, DAOs)
├── web/            - Web layer (Spring MVC controllers, filters)
├── webapp/         - WAR packaging (web.xml, configuration)
├── liquibase/      - Database migration tooling
└── test-module/    - Test module for module system testing
```

## Key Technologies
| Technology | Current | Target |
|-----------|---------|--------|
| Java | 8 (1.8) | 25 |
| Spring Framework | 5.2.14.RELEASE | 5.3.39 |
| Hibernate Core | 5.6.0.Final | 5.6.15.Final |
| Hibernate Search | 5.11.9.Final | 5.11.12.Final |
| Hibernate Validator | 5.4.3.Final | 6.2.5.Final |
| Lucene | 5.5.5 | 5.5.5 (tied to Hibernate Search 5) |
| AspectJ | 1.9.7 | 1.9.22.1 |
| Jackson | 2.13.0 | 2.18.1 |
| JUnit | 5.8.1 | 5.11.3 |
| Mockito | 3.12.4 | 5.14.2 |
| PowerMock | 2.0.9 | REMOVED |
| Groovy | 2.4.21 | 4.0.24 |
| H2 | 1.4.200 | 2.3.232 |
| SLF4J | 1.7.32 | 1.7.36 |
| Log4j2 | 2.17.2 | 2.24.3 |
| Maven Compiler Plugin | 3.8.1 | 3.13.0 |
| Maven Surefire Plugin | 3.0.0-M7 | 3.5.2 |

## Architecture Notes
- Uses Hibernate 5.x with javax.persistence (NOT jakarta.persistence)
- Spring 5.x with javax.servlet (NOT jakarta.servlet)
- EHCache 2.x for second-level cache
- Liquibase 4.4.3 for database migrations
- OpenMRS module system (custom .omod format)
- H2 used for unit/integration tests

## Build Command
```bash
export JAVA_HOME="D:/Java/jdk-25.0.3"
export PATH="$JAVA_HOME/bin:$PATH"
cd E:/JavaUpgradeDemo/openmrs-core-2.5.15
mvn clean install -DskipTests
```

## Known Issues / Risks
1. Spring 5.3.x not officially tested on Java 25 (only up to Java 21 for 5.3.x)
2. Hibernate 5.6.x requires --add-opens for JPMS reflection access
3. Groovy 4.x has breaking API changes vs 2.x
4. H2 2.x has different SQL compatibility mode requirements
5. PowerMock removed - only used in comments, not actual test code
