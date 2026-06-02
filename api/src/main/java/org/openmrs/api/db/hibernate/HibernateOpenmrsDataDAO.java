/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db.hibernate;

import java.util.List;

import org.hibernate.query.Query;
import org.openmrs.BaseOpenmrsData;
import org.openmrs.api.db.OpenmrsDataDAO;

/**
 * Abstract class implementing basic data access methods for BaseOpenmrsData persistents
 *
 * @since 1.10
 *
 * @param <T>
 */
public class HibernateOpenmrsDataDAO<T extends BaseOpenmrsData> extends HibernateOpenmrsObjectDAO<T> implements OpenmrsDataDAO<T> {

	public HibernateOpenmrsDataDAO(Class<T> mappedClass) {
		super();
		this.mappedClass = mappedClass;
	}

	/**
	 * @see org.openmrs.api.db.OpenmrsDataDAO#getAll(boolean)
	 */
	@Override
	public List<T> getAll(boolean includeVoided) {
		StringBuilder hql = new StringBuilder("from ").append(mappedClass.getName());
		if (!includeVoided) {
			hql.append(" where voided = false");
		}
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), mappedClass).list();
	}

	/**
	 * @see org.openmrs.api.db.OpenmrsDataDAO#getAll(boolean, java.lang.Integer, java.lang.Integer)
	 */
	@Override
	public List<T> getAll(boolean includeVoided, Integer firstResult, Integer maxResults) {
		StringBuilder hql = new StringBuilder("from ").append(mappedClass.getName());
		if (!includeVoided) {
			hql.append(" where voided = false");
		}
		Query<T> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), mappedClass);
		query.setFirstResult(firstResult);
		query.setMaxResults(maxResults);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.OpenmrsDataDAO#getAllCount(boolean)
	 */
	@Override
	public int getAllCount(boolean includeVoided) {

		String hql = "select count(*)" + " from " + mappedClass.getName();

		if (!includeVoided) {
			hql += " where voided = false";
		}
		Query<Number> query = sessionFactory.getCurrentSession().createQuery(hql, Number.class);

		Number count = query.uniqueResult();

		return count == null ? 0 : count.intValue();
	}

}
