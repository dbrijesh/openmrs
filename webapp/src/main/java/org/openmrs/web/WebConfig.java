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

import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import java.util.EnumSet;
import java.util.Properties;

import org.openmrs.api.context.Context;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.openmrs.module.web.filter.ModuleFilter;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.web.filter.CookieClearingFilter;
import org.openmrs.web.filter.GZIPFilter;
import org.openmrs.web.filter.JspClassLoaderFilter;
import org.openmrs.web.filter.OpenmrsFilter;
import org.openmrs.web.filter.initialization.InitializationFilter;
import org.openmrs.web.filter.startuperror.StartupErrorFilter;
import org.openmrs.web.filter.update.UpdateFilter;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.hibernate5.support.OpenSessionInViewFilter;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.util.IntrospectorCleanupListener;

/**
 * Spring Boot Java configuration replacing web.xml.
 * Registers all OpenMRS filters and servlets with their correct order.
 */
@Configuration
public class WebConfig {

	/**
	 * Loads openmrs-runtime.properties into the OpenMRS Context BEFORE HibernateSessionFactoryBean
	 * initializes. In web.xml deployments, Listener ran before Spring's ContextLoaderListener;
	 * in Spring Boot all beans initialize before servlet listeners fire, so we use a
	 * BeanFactoryPostProcessor (runs pre-instantiation) to restore that ordering.
	 */
	@Bean
	public static BeanFactoryPostProcessor openmrsRuntimePropertiesLoader() {
		return new BeanFactoryPostProcessor() {
			@Override
			public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
				try {
					Properties props = OpenmrsUtil.getRuntimeProperties("openmrs");
					if (props != null && !props.isEmpty()) {
						Context.setRuntimeProperties(props);
					}
				}
				catch (Exception e) {
					// No runtime properties found; InitializationFilter will show the setup wizard
				}
			}
		};
	}

	/**
	 * Spring Boot fires ApplicationReadyEvent AFTER finishBeanFactoryInitialization() completes,
	 * meaning all beans (including the OpenMRS 'context' bean with contextDAO wired in) are ready.
	 * This is the correct point to call WebDaemon.startOpenmrs() in a Spring Boot deployment,
	 * because Listener.contextInitialized() fires too early (inside Tomcat.start() / onRefresh(),
	 * before beans are initialized).
	 */
	@Bean
	public ApplicationListener<ApplicationReadyEvent> openmrsStartupListener() {
		return event -> {
			org.springframework.web.context.WebApplicationContext wac =
			        (org.springframework.web.context.WebApplicationContext) event.getApplicationContext();
			ServletContext servletContext = wac.getServletContext();
			// Only call startOpenmrs if: setup wizard not needed, not already started, and no prior error
			if (!Listener.isSetupNeeded() && !Listener.isOpenmrsStarted() && !Listener.errorOccurredAtStartup()) {
				try {
					LoggerFactory.getLogger(WebConfig.class).info(
					        "ApplicationReadyEvent: all beans ready, calling Listener.startOpenmrs");
					Listener.startOpenmrs(servletContext);
				}
				catch (Exception e) {
					Listener.setErrorAtStartup(e);
					LoggerFactory.getLogger(WebConfig.class).error("OpenMRS startup failed", e);
				}
			}
		};
	}

	// ---- Root URL + static resources from WAR web root ----

	@Bean
	public WebMvcConfigurer rootUrlConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addViewControllers(ViewControllerRegistry registry) {
				// Redirect root to index.htm ("platform running, no UI module" page)
				registry.addRedirectViewController("/", "/index.htm");
			}

			@Override
			public void addResourceHandlers(ResourceHandlerRegistry registry) {
				// Serve static pages and images from the WAR web root (src/main/webapp)
				registry.addResourceHandler("/index.htm", "/test.html").addResourceLocations("/");
				registry.addResourceHandler("/images/**").addResourceLocations("/images/");
			}
		};
	}

	// ---- Listeners ----

	@Bean
	public ServletListenerRegistrationBean<IntrospectorCleanupListener> introspectorCleanupListener() {
		return new ServletListenerRegistrationBean<>(new IntrospectorCleanupListener());
	}

	@Bean
	public ServletListenerRegistrationBean<Listener> openmrsContextListener() {
		return new ServletListenerRegistrationBean<>(new Listener());
	}

	@Bean
	public ServletListenerRegistrationBean<RequestContextListener> requestContextListener() {
		return new ServletListenerRegistrationBean<>(new RequestContextListener());
	}

	// ---- Filters (order matters - lower number = higher priority) ----

	@Bean
	public FilterRegistrationBean<CharacterEncodingFilter> charsetFilter() {
		FilterRegistrationBean<CharacterEncodingFilter> reg = new FilterRegistrationBean<>();
		CharacterEncodingFilter filter = new CharacterEncodingFilter();
		filter.setEncoding("UTF-8");
		filter.setForceEncoding(true);
		reg.setFilter(filter);
		reg.addUrlPatterns("/*");
		reg.setOrder(1);
		reg.setName("charsetFilter");
		return reg;
	}

	@Bean
	public FilterRegistrationBean<StartupErrorFilter> startupErrorFilter() {
		FilterRegistrationBean<StartupErrorFilter> reg = new FilterRegistrationBean<>();
		reg.setFilter(new StartupErrorFilter());
		reg.addUrlPatterns("/*");
		reg.setOrder(2);
		reg.setName("StartupErrorFilter");
		return reg;
	}

	@Bean
	public FilterRegistrationBean<InitializationFilter> initializationFilter() {
		FilterRegistrationBean<InitializationFilter> reg = new FilterRegistrationBean<>();
		reg.setFilter(new InitializationFilter());
		reg.addUrlPatterns("/*");
		reg.setOrder(3);
		reg.setName("InitializationFilter");
		return reg;
	}

	@Bean
	public FilterRegistrationBean<UpdateFilter> updateFilter() {
		FilterRegistrationBean<UpdateFilter> reg = new FilterRegistrationBean<>();
		reg.setFilter(new UpdateFilter());
		reg.addUrlPatterns("/*");
		reg.setOrder(4);
		reg.setName("UpdateFilter");
		return reg;
	}

	@Bean
	public FilterRegistrationBean<OpenSessionInViewFilter> hibernateFilter() {
		FilterRegistrationBean<OpenSessionInViewFilter> reg = new FilterRegistrationBean<>();
		reg.setFilter(new OpenSessionInViewFilter());
		reg.addUrlPatterns("/*");
		reg.setDispatcherTypes(EnumSet.of(
			DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR));
		reg.setOrder(6);
		reg.setName("HibernateFilter");
		return reg;
	}

	@Bean
	public FilterRegistrationBean<CookieClearingFilter> cookieClearingFilter() {
		FilterRegistrationBean<CookieClearingFilter> reg = new FilterRegistrationBean<>();
		reg.setFilter(new CookieClearingFilter());
		reg.addUrlPatterns("/*");
		reg.setOrder(7);
		reg.setName("CookieClearingFilter");
		return reg;
	}

	@Bean
	public FilterRegistrationBean<OpenmrsFilter> openmrsFilter() {
		FilterRegistrationBean<OpenmrsFilter> reg = new FilterRegistrationBean<>();
		reg.setFilter(new OpenmrsFilter());
		reg.addUrlPatterns("/*");
		reg.setDispatcherTypes(EnumSet.of(
			DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR, DispatcherType.INCLUDE));
		reg.setOrder(8);
		reg.setName("OpenmrsFilter");
		return reg;
	}

	@Bean
	public FilterRegistrationBean<ModuleFilter> moduleFilter() {
		FilterRegistrationBean<ModuleFilter> reg = new FilterRegistrationBean<>();
		reg.setFilter(new ModuleFilter());
		reg.addUrlPatterns("/*");
		reg.setOrder(9);
		reg.setName("ModuleFilter");
		return reg;
	}

	@Bean
	public FilterRegistrationBean<GZIPFilter> compressionFilter() {
		FilterRegistrationBean<GZIPFilter> reg = new FilterRegistrationBean<>();
		reg.setFilter(new GZIPFilter());
		reg.addUrlPatterns("*.css", "*.js", "*.jsp", "*.json", "*.html", "*.htm", "*.xml");
		reg.setOrder(10);
		reg.setName("compressionFilter");
		return reg;
	}

	@Bean
	public FilterRegistrationBean<JspClassLoaderFilter> jspClassLoaderFilter() {
		FilterRegistrationBean<JspClassLoaderFilter> reg = new FilterRegistrationBean<>();
		reg.setFilter(new JspClassLoaderFilter());
		reg.addUrlPatterns("*.jsp");
		reg.setDispatcherTypes(EnumSet.of(
			DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ERROR, DispatcherType.INCLUDE));
		reg.setOrder(11);
		reg.setName("jspClassLoader");
		return reg;
	}

	// ---- Servlets ----

	@Bean
	public ServletRegistrationBean<DispatcherServlet> openmrsDispatcherServlet() {
		DispatcherServlet servlet = new DispatcherServlet();
		servlet.setContextConfigLocation("");
		ServletRegistrationBean<DispatcherServlet> reg = new ServletRegistrationBean<>(servlet, "/ws/*");
		reg.setName("openmrs");
		return reg;
	}

	@Bean
	public ServletRegistrationBean<StaticDispatcherServlet> staticContentServlet() {
		ServletRegistrationBean<StaticDispatcherServlet> reg =
			new ServletRegistrationBean<>(new StaticDispatcherServlet(), "/scripts/*");
		reg.setName("openmrs_static_content");
		return reg;
	}

	@Bean
	public ServletRegistrationBean<org.openmrs.module.web.ModuleServlet> moduleServlet() {
		ServletRegistrationBean<org.openmrs.module.web.ModuleServlet> reg =
			new ServletRegistrationBean<>(new org.openmrs.module.web.ModuleServlet(), "/moduleServlet/*", "/ms/*");
		reg.setName("module_servlet");
		return reg;
	}

	@Bean
	public ServletRegistrationBean<org.openmrs.module.web.ModuleResourcesServlet> moduleResourcesServlet() {
		ServletRegistrationBean<org.openmrs.module.web.ModuleResourcesServlet> reg =
			new ServletRegistrationBean<>(new org.openmrs.module.web.ModuleResourcesServlet(), "/moduleResources/*");
		reg.setName("module_resources");
		return reg;
	}
}
