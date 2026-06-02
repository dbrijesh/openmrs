/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db.hibernate.search;

import java.util.List;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Root;

import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.openmrs.collection.ListPart;

/**
 * Performs JPA criteria queries.
 *
 * @since 1.11
 */
public abstract class CriteriaQuery<T> extends SearchQuery<T> {

	private final jakarta.persistence.criteria.CriteriaQuery<T> criteriaQuery;

	private final Root<T> root;

	private final CriteriaBuilder cb;

	/**
	 * @param session
	 * @param type
	 */
	public CriteriaQuery(Session session, Class<T> type) {
		super(session, type);
		cb = session.getCriteriaBuilder();
		criteriaQuery = cb.createQuery(type);
		root = criteriaQuery.from(type);
		prepareCriteria(criteriaQuery, root, cb);
	}

	/**
	 * Subclasses implement this to add WHERE clauses, joins, etc.
	 *
	 * @param criteria the JPA criteria query
	 * @param root     the root entity
	 * @param cb       the criteria builder
	 */
	public abstract void prepareCriteria(jakarta.persistence.criteria.CriteriaQuery<T> criteria, Root<T> root,
	        CriteriaBuilder cb);

	@Override
	public List<T> list() {
		criteriaQuery.select(root);
		return getSession().createQuery(criteriaQuery).list();
	}

	@Override
	public ListPart<T> listPart(Long firstResult, Long maxResults) {
		criteriaQuery.select(root);
		Query<T> query = getSession().createQuery(criteriaQuery);

		if (firstResult != null) {
			query.setFirstResult(firstResult.intValue());
		}
		if (maxResults != null) {
			query.setMaxResults(maxResults.intValue());
		}

		List<T> list = query.list();
		return ListPart.newListPart(list, firstResult, maxResults, null, null);
	}

	@Override
	public T uniqueResult() throws HibernateException {
		criteriaQuery.select(root);
		return getSession().createQuery(criteriaQuery).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.hibernate.search.SearchQuery#resultSize()
	 */
	@Override
	public long resultSize() {
		jakarta.persistence.criteria.CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
		Root<T> countRoot = countQuery.from(getType());
		countQuery.select(cb.count(countRoot));
		// Copy predicates if the main criteriaQuery has a where clause
		if (criteriaQuery.getRestriction() != null) {
			countQuery.where(criteriaQuery.getRestriction());
		}
		Long count = getSession().createQuery(countQuery).uniqueResult();
		return count != null ? count : 0L;
	}

}
