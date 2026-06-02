/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ImportResource;

/**
 * Spring Boot entry point for OpenMRS.
 *
 * <p>Uses WAR packaging so the OpenMRS module classloader hierarchy is preserved.
 * Extends {@link SpringBootServletInitializer} to support both:
 * <ul>
 *   <li>Standalone execution: {@code java -jar openmrs.war}</li>
 *   <li>External container: deploy openmrs.war to any Jakarta Servlet 6 container</li>
 * </ul>
 *
 * <p>Autoconfiguration exclusions:
 * <ul>
 *   <li>{@code DataSourceAutoConfiguration} — OpenMRS owns its DataSource via C3P0/Hibernate</li>
 *   <li>{@code HibernateJpaAutoConfiguration} — OpenMRS uses SessionFactory, not EntityManager</li>
 *   <li>{@code DataSourceTransactionManagerAutoConfiguration} — OpenMRS uses HibernateTransactionManager</li>
 *   <li>{@code JpaRepositoriesAutoConfiguration} — No Spring Data JPA repositories</li>
 * </ul>
 *
 * <p>Existing Spring XML configurations are loaded via {@code @ImportResource}.
 */
@SpringBootApplication(exclude = {
	DataSourceAutoConfiguration.class,
	HibernateJpaAutoConfiguration.class,
	DataSourceTransactionManagerAutoConfiguration.class,
	JpaRepositoriesAutoConfiguration.class
})
@ImportResource({
	"classpath:applicationContext-service.xml",
	"classpath:openmrs-servlet.xml"
})
public class OpenmrsApplication extends SpringBootServletInitializer {

	public static void main(String[] args) {
		SpringApplication.run(OpenmrsApplication.class, args);
	}

	@Override
	protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
		return application.sources(OpenmrsApplication.class);
	}
}
