/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db;

import org.hibernate.search.mapper.orm.session.SearchSession;

/**
 * Wrapper class around a {@link SearchSession} object and the type of the entities to be queried.
 * An instance of this class is set as the source of a {@link FullTextQueryCreatedEvent} object.
 *
 * @since 2.3.0
 */
public class FullTextQueryAndEntityClass {

	private final SearchSession searchSession;

	private final Class<?> entityClass;

	public FullTextQueryAndEntityClass(SearchSession searchSession, Class<?> entityClass) {
		this.searchSession = searchSession;
		this.entityClass = entityClass;
	}

	/**
	 * Gets the search session
	 *
	 * @return the search session
	 */
	public SearchSession getSearchSession() {
		return searchSession;
	}

	/**
	 * Gets the entityClass
	 *
	 * @return the entityClass
	 */
	public Class<?> getEntityClass() {
		return entityClass;
	}

}
