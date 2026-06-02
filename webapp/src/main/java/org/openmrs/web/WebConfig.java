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
import java.util.EnumSet;

import org.openmrs.module.web.filter.ModuleFilter;
import org.openmrs.web.filter.CookieClearingFilter;
import org.openmrs.web.filter.GZIPFilter;
import org.openmrs.web.filter.JspClassLoaderFilter;
import org.openmrs.web.filter.OpenmrsFilter;
import org.openmrs.web.filter.initialization.InitializationFilter;
import org.openmrs.web.filter.startuperror.StartupErrorFilter;
import org.openmrs.web.filter.update.UpdateFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.hibernate5.support.OpenSessionInViewFilter;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.util.IntrospectorCleanupListener;

/**
 * Spring Boot Java configuration replacing web.xml.
 * Registers all OpenMRS filters and servlets with their correct order.
 */
@Configuration
public class WebConfig {

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
	public FilterRegistrationBean<DelegatingFilterProxy> multipartFilter() {
		FilterRegistrationBean<DelegatingFilterProxy> reg = new FilterRegistrationBean<>();
		reg.setFilter(new DelegatingFilterProxy("multipartResolver"));
		reg.addUrlPatterns("/*");
		reg.setOrder(5);
		reg.setName("multipartFilter");
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
