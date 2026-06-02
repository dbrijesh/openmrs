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

import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Wrapper around a {@link SearchSession} that notifies registered Spring listeners whenever a
 * search is initiated for a given entity type. The search session and entity type are wrapped in a
 * {@link FullTextQueryAndEntityClass} and published as a {@link FullTextQueryCreatedEvent}.
 * <p>
 * An example use case is that a listener can record metrics or apply cross-cutting concerns before
 * search execution.
 */
public class DelegatingFullTextSession {

	private static final Logger log = LoggerFactory.getLogger(DelegatingFullTextSession.class);

	private final SearchSession delegate;

	private final ApplicationEventPublisher eventPublisher;

	public DelegatingFullTextSession(SearchSession delegate, ApplicationEventPublisher eventPublisher) {
		this.delegate = delegate;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * Returns the underlying {@link SearchSession}.
	 *
	 * @return the delegate {@link SearchSession}
	 */
	public SearchSession getSearchSession() {
		return delegate;
	}

	/**
	 * Publishes a {@link FullTextQueryCreatedEvent} for the given entity class and returns the
	 * underlying {@link SearchSession} so callers can build their query.
	 *
	 * @param entityClass the entity class to be searched
	 * @return the underlying {@link SearchSession}
	 */
	public SearchSession createSearchQuery(Class<?> entityClass) {
		log.debug("Creating search query for entity: {}", entityClass.getName());

		FullTextQueryAndEntityClass queryAndClass = new FullTextQueryAndEntityClass(delegate, entityClass);
		eventPublisher.publishEvent(new FullTextQueryCreatedEvent(queryAndClass));

		return delegate;
	}

	/**
	 * Returns a {@link MassIndexer} for the given types.
	 *
	 * @param types entity types to index; if empty, all indexed types are reindexed
	 * @return the mass indexer
	 */
	public MassIndexer createIndexer(Class<?>... types) {
		if (types == null || types.length == 0) {
			return delegate.massIndexer();
		}
		return delegate.massIndexer(types);
	}

}
