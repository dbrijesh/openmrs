# Spring Boot Migration Plan: openmrs-core 2.5.15

## Goal
Migrate from bare Spring Framework 6.2.6 WAR to Spring Boot 3.4.5 (the latest Spring Boot,
based on Spring Framework 6.x — same baseline we're already on).

Note: Spring Boot 4.0 is not GA as of 2026-06-02 (planned for late 2025/2026).
Spring Boot 3.4.x is the production-ready Spring Boot with Spring 6 + Jakarta EE.

## Current State
- Bare Spring Framework 6.2.6 with manual XML bean configuration
- web.xml-based servlet container configuration
- Manual Hibernate 6.6.4 SessionFactory setup
- Manual EHCache 3.x / JCache cache setup
- WAR deployed to embedded Cargo/Tomcat 10.1

## Target State: Spring Boot 3.4.5
- Spring Boot parent POM managing dependency versions
- @SpringBootApplication entry point in webapp module  
- Embedded Tomcat 10.1 via spring-boot-starter-web
- Existing XML Spring configs loaded via @ImportResource (no big-bang XML→Java migration)
- application.properties for datasource & core config
- WAR packaging (needed for OpenMRS module classloader system)
- Spring Boot Actuator for health/info endpoints

## Migration Phases

### Phase 1: POM Restructure (Root + All Modules)
- Add `spring-boot-starter-parent` as root parent (replaces manual version mgmt)
- Remove versions now managed by Spring Boot BOM
- Add Spring Boot starters to replace individual Spring dependencies
- Keep Hibernate Search, Groovy, EHCache (not in SB BOM)

### Phase 2: Spring Boot Application Class
- Create `OpenmrsApplication.java` in webapp module
- `@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)`
  (OpenMRS manages its own DataSource via C3P0/Hibernate)
- `@ImportResource` to load existing Spring XML configs

### Phase 3: Web Configuration (Replace web.xml)  
- Create `WebConfig.java` to register OpenMRS Filters and Servlets
- Use `FilterRegistrationBean` for all OpenMRS filters (order preserved)
- Use `ServletRegistrationBean` for DispatcherServlet, module_servlet etc.
- Embedded Tomcat auto-configured by spring-boot-starter-web
- Set `server.port=8080` and `server.servlet.context-path=/openmrs`

### Phase 4: application.properties
- Map OpenMRS DB/Hibernate properties
- Set server.port, context-path
- Configure H2 for development profile

### Phase 5: Hibernate / Transaction Config
- Keep custom `HibernateSessionFactoryBean` (extends LocalSessionFactoryBean)
- Keep `HibernateTransactionManager` from XML config
- Exclude Spring Boot's JPA/DataSource autoconfiguration
- Let OpenMRS own its Hibernate SessionFactory lifecycle

### Phase 6: Cache / Actuator
- Add spring-boot-starter-actuator for /actuator/health
- Keep JCache/EHCache 3.x config from existing CacheConfig.java

### Phase 7: WAR Packaging for Module Support
- Keep `<packaging>war</packaging>` in webapp
- Extend `SpringBootServletInitializer` for WAR deployment
- Mark spring-boot-starter-tomcat as `provided` for external container support

## Key Decisions
- Use WAR (not JAR) — OpenMRS module system needs servlet container classloader
- Keep XML configs via @ImportResource — too many beans to migrate to Java config
- Exclude DataSourceAutoConfiguration — OpenMRS owns its own datasource via Hibernate
- Exclude HibernateJpaAutoConfiguration — we use SessionFactory, not EntityManager

## File Changes
| File | Change |
|------|--------|
| pom.xml (root) | Add spring-boot-starter-parent, restructure deps |
| webapp/pom.xml | Add spring-boot-starter-web, actuator; remove cargo plugin |
| api/pom.xml | Replace manual Spring deps with spring-boot-starter |
| NEW: webapp/src/main/java/org/openmrs/web/OpenmrsApplication.java | Main class |
| NEW: webapp/src/main/java/org/openmrs/web/WebConfig.java | Filter/Servlet registration |
| NEW: webapp/src/main/resources/application.properties | Core config |
| webapp/src/main/webapp/WEB-INF/web.xml | Kept for backward compat but minimal |
