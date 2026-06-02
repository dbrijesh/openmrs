/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.cache;

import java.util.List;

import javax.cache.Cache;
import javax.cache.CacheManager;
import javax.cache.configuration.MutableConfiguration;

import org.springframework.cache.jcache.JCacheManagerFactoryBean;

/**
 * This class creates cache configurations from apiCacheConfig.properties files in the classpath. This file should be
 * created in modules resource directory only. To configure cache in openmrs-core go to ehcache-api.xml.
 * If the configuration already exists it won't be overridden.
 * Example content for apiCacheConfig.properties:
 * userSearchLocales.maxEntriesLocalHeap=500
 * userSearchLocales.eternal=false
 * userSearchLocales.timeToIdleSeconds=300
 * userSearchLocales.timeToLiveSeconds=300
 */
public class OpenmrsCacheManagerFactoryBean extends JCacheManagerFactoryBean {

	@Override
	public CacheManager getObject() {
		CacheManager cacheManager = super.getObject();

		List<OpenmrsCacheConfiguration> cacheConfigurations = CachePropertiesUtil.getCacheConfigurations();
		cacheConfigurations.stream()
				.filter(cc -> {
					String name = cc.getProperty("name");
					return name != null && cacheManager.getCache(name) == null;
				})
				.forEach(cc -> {
					String name = cc.getProperty("name");
					MutableConfiguration<Object, Object> config = new MutableConfiguration<>()
							.setTypes(Object.class, Object.class)
							.setStoreByValue(false);

					String eternal = cc.getProperty("eternal");
					if (!"true".equalsIgnoreCase(eternal)) {
						// timeToLiveSeconds / timeToIdleSeconds can be set via the JCache expiry policy
						// but requires EHCache 3.x-specific config; basic MutableConfiguration is sufficient
						// for module-provided caches that just need a named cache to exist.
					}
					cacheManager.createCache(name, config);
				});

		return cacheManager;
	}
}
