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

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections.CollectionUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.Concept;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.VisitAttribute;
import org.openmrs.VisitAttributeType;
import org.openmrs.VisitType;
import org.openmrs.api.APIException;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.VisitDAO;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hibernate specific visit related functions This class should not be used directly. All calls
 * should go through the {@link org.openmrs.api.VisitService} methods.
 *
 * @since 1.9
 */
public class HibernateVisitDAO implements VisitDAO {

	private SessionFactory sessionFactory;

	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	private Session getCurrentSession() {
		return sessionFactory.getCurrentSession();
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getAllVisitTypes()
	 */
	@Override
	@SuppressWarnings("unchecked")
	@Transactional(readOnly = true)
	public List<VisitType> getAllVisitTypes() throws APIException {
		return getCurrentSession().createQuery("FROM VisitType", VisitType.class).list();
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getAllVisitTypes(boolean)
	 */
	@Override
	public List<VisitType> getAllVisitTypes(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM VisitType vt");
		if (!includeRetired) {
			hql.append(" WHERE vt.retired = false");
		}
		return getCurrentSession().createQuery(hql.toString(), VisitType.class).list();
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getVisitType(java.lang.Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public VisitType getVisitType(Integer visitTypeId) {
		return sessionFactory.getCurrentSession().get(VisitType.class, visitTypeId);
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getVisitTypeByUuid(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public VisitType getVisitTypeByUuid(String uuid) {
		return getCurrentSession()
				.createQuery("FROM VisitType vt WHERE vt.uuid = :uuid", VisitType.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getVisitTypes(java.lang.String)
	 */
	@Override
	@SuppressWarnings("unchecked")
	@Transactional(readOnly = true)
	public List<VisitType> getVisitTypes(String fuzzySearchPhrase) {
		return getCurrentSession()
				.createQuery("FROM VisitType vt WHERE lower(vt.name) LIKE :searchPhrase ORDER BY vt.name ASC",
						VisitType.class)
				.setParameter("searchPhrase", "%" + fuzzySearchPhrase.toLowerCase() + "%")
				.list();
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#saveVisitType(org.openmrs.VisitType)
	 */
	@Override
	@Transactional
	public VisitType saveVisitType(VisitType visitType) {
		sessionFactory.getCurrentSession().saveOrUpdate(visitType);
		return visitType;
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#purgeVisitType(org.openmrs.VisitType)
	 */
	@Override
	@Transactional
	public void purgeVisitType(VisitType visitType) {
		sessionFactory.getCurrentSession().delete(visitType);
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getVisit(java.lang.Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public Visit getVisit(Integer visitId) throws DAOException {
		return getCurrentSession().get(Visit.class, visitId);
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getVisitByUuid(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public Visit getVisitByUuid(String uuid) throws DAOException {
		return getCurrentSession()
				.createQuery("FROM Visit v WHERE v.uuid = :uuid", Visit.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#saveVisit(org.openmrs.Visit)
	 */
	@Override
	@Transactional
	public Visit saveVisit(Visit visit) throws DAOException {
		getCurrentSession().saveOrUpdate(visit);
		return visit;
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#deleteVisit(org.openmrs.Visit)
	 */
	@Override
	@Transactional
	public void deleteVisit(Visit visit) throws DAOException {
		getCurrentSession().delete(visit);
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getVisits(java.util.Collection, java.util.Collection,
	 *      java.util.Collection, java.util.Collection, java.util.Date, java.util.Date,
	 *      java.util.Date, java.util.Date, java.util.Map, boolean, boolean)
	 */
	@SuppressWarnings("unchecked")
	@Override
	@Transactional(readOnly = true)
	public List<Visit> getVisits(Collection<VisitType> visitTypes, Collection<Patient> patients,
	        Collection<Location> locations, Collection<Concept> indications, Date minStartDatetime, Date maxStartDatetime,
	        Date minEndDatetime, Date maxEndDatetime, final Map<VisitAttributeType, String> serializedAttributeValues,
	        boolean includeInactive, boolean includeVoided) throws DAOException {

		StringBuilder hql = new StringBuilder("FROM Visit v WHERE 1=1");

		if (visitTypes != null) {
			hql.append(" AND v.visitType IN :visitTypes");
		}
		if (patients != null) {
			hql.append(" AND v.patient IN :patients");
		}
		if (locations != null) {
			hql.append(" AND v.location IN :locations");
		}
		if (indications != null) {
			hql.append(" AND v.indication IN :indications");
		}
		if (minStartDatetime != null) {
			hql.append(" AND v.startDatetime >= :minStartDatetime");
		}
		if (maxStartDatetime != null) {
			hql.append(" AND v.startDatetime <= :maxStartDatetime");
		}

		if (!includeInactive) {
			hql.append(" AND (v.stopDatetime IS NULL OR v.stopDatetime > :now)");
		} else {
			if (minEndDatetime != null) {
				hql.append(" AND (v.stopDatetime IS NULL OR v.stopDatetime >= :minEndDatetime)");
			}
			if (maxEndDatetime != null) {
				hql.append(" AND v.stopDatetime <= :maxEndDatetime");
			}
		}

		if (!includeVoided) {
			hql.append(" AND v.voided = false");
		}

		hql.append(" ORDER BY v.startDatetime DESC, v.visitId DESC");

		Query<Visit> query = getCurrentSession().createQuery(hql.toString(), Visit.class);

		if (visitTypes != null) {
			query.setParameterList("visitTypes", visitTypes);
		}
		if (patients != null) {
			query.setParameterList("patients", patients);
		}
		if (locations != null) {
			query.setParameterList("locations", locations);
		}
		if (indications != null) {
			query.setParameterList("indications", indications);
		}
		if (minStartDatetime != null) {
			query.setParameter("minStartDatetime", minStartDatetime);
		}
		if (maxStartDatetime != null) {
			query.setParameter("maxStartDatetime", maxStartDatetime);
		}
		if (!includeInactive) {
			query.setParameter("now", new Date());
		} else {
			if (minEndDatetime != null) {
				query.setParameter("minEndDatetime", minEndDatetime);
			}
			if (maxEndDatetime != null) {
				query.setParameter("maxEndDatetime", maxEndDatetime);
			}
		}

		List<Visit> visits = query.list();

		if (serializedAttributeValues != null) {
			CollectionUtils.filter(visits, new AttributeMatcherPredicate<Visit, VisitAttributeType>(
			        serializedAttributeValues));
		}

		return visits;
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getAllVisitAttributeTypes()
	 */
	@SuppressWarnings("unchecked")
	@Override
	@Transactional(readOnly = true)
	public List<VisitAttributeType> getAllVisitAttributeTypes() {
		return getCurrentSession().createQuery("FROM VisitAttributeType", VisitAttributeType.class).list();
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getVisitAttributeType(java.lang.Integer)
	 */
	@Override
	@Transactional(readOnly = true)
	public VisitAttributeType getVisitAttributeType(Integer id) {
		return getCurrentSession().get(VisitAttributeType.class, id);
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getVisitAttributeTypeByUuid(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public VisitAttributeType getVisitAttributeTypeByUuid(String uuid) {
		return getCurrentSession()
				.createQuery("FROM VisitAttributeType t WHERE t.uuid = :uuid", VisitAttributeType.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#saveVisitAttributeType(org.openmrs.VisitAttributeType)
	 */
	@Override
	@Transactional
	public VisitAttributeType saveVisitAttributeType(VisitAttributeType visitAttributeType) {
		getCurrentSession().saveOrUpdate(visitAttributeType);
		return visitAttributeType;
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#deleteVisitAttributeType(org.openmrs.VisitAttributeType)
	 */
	@Override
	@Transactional
	public void deleteVisitAttributeType(VisitAttributeType visitAttributeType) {
		getCurrentSession().delete(visitAttributeType);
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getVisitAttributeByUuid(java.lang.String)
	 */
	@Override
	@Transactional(readOnly = true)
	public VisitAttribute getVisitAttributeByUuid(String uuid) {
		return getCurrentSession()
				.createQuery("FROM VisitAttribute a WHERE a.uuid = :uuid", VisitAttribute.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.VisitDAO#getNextVisit(Visit, Collection, Date)
	 */
	@Override
	public Visit getNextVisit(Visit previousVisit, Collection<VisitType> visitTypes, Date maximumStartDate) {
		StringBuilder hql = new StringBuilder(
				"FROM Visit v WHERE v.voided = false"
				+ " AND v.visitId > :minVisitId"
				+ " AND v.stopDatetime IS NULL");

		if (maximumStartDate != null) {
			hql.append(" AND v.startDatetime <= :maximumStartDate");
		}
		if (CollectionUtils.isNotEmpty(visitTypes)) {
			hql.append(" AND v.visitType IN :visitTypes");
		}
		hql.append(" ORDER BY v.visitId ASC");

		Query<Visit> query = getCurrentSession().createQuery(hql.toString(), Visit.class);
		query.setParameter("minVisitId", (previousVisit != null) ? previousVisit.getVisitId() : 0);
		query.setMaxResults(1);

		if (maximumStartDate != null) {
			query.setParameter("maximumStartDate", maximumStartDate);
		}
		if (CollectionUtils.isNotEmpty(visitTypes)) {
			query.setParameterList("visitTypes", visitTypes);
		}

		return query.uniqueResult();
	}
}
