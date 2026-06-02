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

import java.util.Date;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.Cohort;
import org.openmrs.CohortMembership;
import org.openmrs.api.db.CohortDAO;
import org.openmrs.api.db.DAOException;

/**
 * Hibernate implementation of the CohortDAO
 *
 * @see CohortDAO
 * @see org.openmrs.api.context.Context
 * @see org.openmrs.api.CohortService
 */
public class HibernateCohortDAO implements CohortDAO {

	private static final String VOIDED = "voided";
	private SessionFactory sessionFactory;

	/**
	 * Auto generated method comment
	 *
	 * @param sessionFactory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * @see org.openmrs.api.db.CohortDAO#getCohort(java.lang.Integer)
	 */
	@Override
	public Cohort getCohort(Integer id) throws DAOException {
		return sessionFactory.getCurrentSession().get(Cohort.class, id);
	}

	/**
	 * @see org.openmrs.api.db.CohortDAO#getCohortsContainingPatientId(Integer, boolean, Date)
	 */
	@Override
	public List<Cohort> getCohortsContainingPatientId(Integer patientId, boolean includeVoided,
	                                                  Date asOfDate) throws DAOException {
		StringBuilder hql = new StringBuilder(
		    "select distinct c from Cohort c join c.memberships m where m.patientId = :patientId");

		if (asOfDate != null) {
			hql.append(" and m.startDate <= :asOfDate");
			hql.append(" and (m.endDate is null or m.endDate > :asOfDate)");
		}

		if (!includeVoided) {
			hql.append(" and c.voided = false");
		}

		Query<Cohort> query = sessionFactory.getCurrentSession()
		        .createQuery(hql.toString(), Cohort.class)
		        .setParameter("patientId", patientId);

		if (asOfDate != null) {
			query.setParameter("asOfDate", asOfDate);
		}

		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.CohortDAO#getCohortByUuid(java.lang.String)
	 */
	@Override
	public Cohort getCohortByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Cohort c where c.uuid = :uuid", Cohort.class)
		        .setParameter("uuid", uuid)
		        .uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.CohortDAO#getCohortMembershipByUuid(java.lang.String)
	 */
	@Override
	public CohortMembership getCohortMembershipByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from CohortMembership m where m.uuid = :uuid", CohortMembership.class)
		        .setParameter("uuid", uuid)
		        .uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.CohortDAO#deleteCohort(org.openmrs.Cohort)
	 */
	@Override
	public Cohort deleteCohort(Cohort cohort) throws DAOException {
		sessionFactory.getCurrentSession().delete(cohort);
		return null;
	}

	/**
	 * @see org.openmrs.api.db.CohortDAO#getCohorts(java.lang.String)
	 */
	@Override
	public List<Cohort> getCohorts(String nameFragment) throws DAOException {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Cohort c where lower(c.name) like :name order by c.name asc", Cohort.class)
		        .setParameter("name", "%" + nameFragment.toLowerCase() + "%")
		        .list();
	}

	/**
	 * @see org.openmrs.api.db.CohortDAO#getAllCohorts(boolean)
	 */
	@Override
	public List<Cohort> getAllCohorts(boolean includeVoided) throws DAOException {
		String hql = includeVoided
		        ? "from Cohort c order by c.name asc"
		        : "from Cohort c where c.voided = false order by c.name asc";
		return sessionFactory.getCurrentSession()
		        .createQuery(hql, Cohort.class)
		        .list();
	}

	/**
	 * @see org.openmrs.api.db.CohortDAO#getCohort(java.lang.String)
	 */
	@Override
	public Cohort getCohort(String name) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Cohort c where c.name = :name and c.voided = false", Cohort.class)
		        .setParameter("name", name)
		        .uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.CohortDAO#saveCohort(org.openmrs.Cohort)
	 */
	@Override
	public Cohort saveCohort(Cohort cohort) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(cohort);
		return cohort;
	}

	@Override
	public List<CohortMembership> getCohortMemberships(Integer patientId, Date activeOnDate, boolean includeVoided) {
		StringBuilder hql = new StringBuilder(
		    "from CohortMembership m where m.patientId = :patientId");

		if (activeOnDate != null) {
			hql.append(" and m.startDate <= :activeOnDate");
			hql.append(" and (m.endDate is null or m.endDate >= :activeOnDate)");
		}

		if (!includeVoided) {
			hql.append(" and m.voided = false");
		}

		Query<CohortMembership> query = sessionFactory.getCurrentSession()
		        .createQuery(hql.toString(), CohortMembership.class)
		        .setParameter("patientId", patientId);

		if (activeOnDate != null) {
			query.setParameter("activeOnDate", activeOnDate);
		}

		return query.list();
	}

	@Override
	public CohortMembership saveCohortMembership(CohortMembership cohortMembership) {
		sessionFactory.getCurrentSession().saveOrUpdate(cohortMembership);
		return cohortMembership;
	}
}
