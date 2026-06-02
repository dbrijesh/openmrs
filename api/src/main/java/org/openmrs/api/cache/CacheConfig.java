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

import org.springframework.cache.CacheManager;
import org.springframework.cache.jcache.JCacheCacheManager;
import org.springframework.cache.jcache.JCacheManagerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

/**
 * CacheConfig provides a cache manager for the @Cacheable annotation.
 * Uses JCache (JSR-107) with EHCache 3.x as the underlying provider.
 * The config of ehCache is loaded from ehcache-api.xml and can be extended by modules through apiCacheConfig.properties.
 */
@Configuration
public class CacheConfig {

    @Bean(name = "apiCacheManagerFactoryBean")
    public JCacheManagerFactoryBean apiCacheManagerFactoryBean() {
        OpenmrsCacheManagerFactoryBean cacheManagerFactoryBean = new OpenmrsCacheManagerFactoryBean();
        cacheManagerFactoryBean.setCacheManagerUri(null); // use default JCache provider (EHCache 3.x via classpath)
        return cacheManagerFactoryBean;
    }

    @Bean(name = "apiCacheManager")
    public CacheManager cacheManager() {
        return new JCacheCacheManager(apiCacheManagerFactoryBean().getObject());
    }
}
