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

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.openmrs.Concept;
import org.openmrs.ConceptName;
import org.openmrs.Encounter;
import org.openmrs.Location;
import org.openmrs.Obs;
import org.openmrs.Person;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.ObsDAO;
import org.openmrs.util.OpenmrsConstants.PERSON_TYPE;

/**
 * Hibernate specific Observation related functions This class should not be used directly. All
 * calls should go through the {@link org.openmrs.api.ObsService} methods.
 *
 * @see org.openmrs.api.db.ObsDAO
 * @see org.openmrs.api.ObsService
 */
public class HibernateObsDAO implements ObsDAO {

	protected SessionFactory sessionFactory;

	/**
	 * Set session factory that allows us to connect to the database that Hibernate knows about.
	 *
	 * @param sessionFactory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * @see org.openmrs.api.ObsService#purgeObs(org.openmrs.Obs)
	 */
	@Override
	public void deleteObs(Obs obs) throws DAOException {
		sessionFactory.getCurrentSession().delete(obs);
	}

	/**
	 * @see org.openmrs.api.ObsService#getObs(java.lang.Integer)
	 */
	@Override
	public Obs getObs(Integer obsId) throws DAOException {
		return (Obs) sessionFactory.getCurrentSession().get(Obs.class, obsId);
	}

	/**
	 * @see org.openmrs.api.db.ObsDAO#saveObs(org.openmrs.Obs)
	 */
	@Override
	public Obs saveObs(Obs obs) throws DAOException {
		if (obs.hasGroupMembers() && obs.getObsId() != null) {
			// hibernate has a problem updating child collections
			// if the parent object was already saved so we do it
			// explicitly here
			for (Obs member : obs.getGroupMembers()) {
				if (member.getObsId() == null) {
					saveObs(member);
				}
			}
		}

		sessionFactory.getCurrentSession().saveOrUpdate(obs);

		return obs;
	}

	/**
	 * @see org.openmrs.api.db.ObsDAO#getObservations(List, List, List, List, List, List, List,
	 *      Integer, Integer, Date, Date, boolean, String)
	 */
	@Override
	public List<Obs> getObservations(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<String> sortList,
	        Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate, boolean includeVoidedObs,
	        String accessionNumber) throws DAOException {

		StringBuilder hql = new StringBuilder("FROM Obs obs WHERE 1=1");
		buildObservationsHql(hql, whom, encounters, questions, answers, personTypes, locations,
		    mostRecentN, obsGroupId, fromDate, toDate, null, includeVoidedObs, accessionNumber);

		// Append ORDER BY from sortList
		if (CollectionUtils.isNotEmpty(sortList)) {
			StringBuilder orderBy = new StringBuilder();
			for (String sort : sortList) {
				if (StringUtils.isNotEmpty(sort)) {
					String[] split = sort.split(" ", 2);
					String fieldName = split[0];
					String direction = (split.length == 2 && "asc".equals(split[1])) ? "ASC" : "DESC";
					if (orderBy.length() > 0) {
						orderBy.append(", ");
					}
					orderBy.append("obs.").append(fieldName).append(" ").append(direction);
				}
			}
			if (orderBy.length() > 0) {
				hql.append(" ORDER BY ").append(orderBy);
			}
		}

		Query<Obs> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Obs.class);
		setObservationsParams(query, whom, encounters, questions, answers, personTypes, locations,
		    mostRecentN, obsGroupId, fromDate, toDate, null, includeVoidedObs, accessionNumber);

		if (mostRecentN != null && mostRecentN > 0) {
			query.setMaxResults(mostRecentN);
		}

		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ObsDAO#getObservationCount(List, List, List, List, List, List, Integer, Date, Date, List, boolean, String)
	 */
	@Override
	public Long getObservationCount(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, Integer obsGroupId,
	        Date fromDate, Date toDate, List<ConceptName> valueCodedNameAnswers, boolean includeVoidedObs,
	        String accessionNumber) throws DAOException {

		StringBuilder hql = new StringBuilder("SELECT COUNT(obs) FROM Obs obs WHERE 1=1");
		buildObservationsHql(hql, whom, encounters, questions, answers, personTypes, locations,
		    null, obsGroupId, fromDate, toDate, valueCodedNameAnswers, includeVoidedObs, accessionNumber);

		Query<Long> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Long.class);
		setObservationsParams(query, whom, encounters, questions, answers, personTypes, locations,
		    null, obsGroupId, fromDate, toDate, valueCodedNameAnswers, includeVoidedObs, accessionNumber);

		return query.uniqueResult();
	}

	/**
	 * Builds the WHERE-clause portion of the observations HQL query.
	 * sortList ordering is appended when not doing a count query.
	 */
	private void buildObservationsHql(StringBuilder hql, List<Person> whom, List<Encounter> encounters,
	        List<Concept> questions, List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations,
	        Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate,
	        List<ConceptName> valueCodedNameAnswers, boolean includeVoidedObs, String accessionNumber) {

		if (CollectionUtils.isNotEmpty(whom)) {
			hql.append(" AND obs.person IN :whom");
		}

		if (CollectionUtils.isNotEmpty(encounters)) {
			hql.append(" AND obs.encounter IN :encounters");
		}

		if (CollectionUtils.isNotEmpty(questions)) {
			hql.append(" AND obs.concept IN :questions");
		}

		if (CollectionUtils.isNotEmpty(answers)) {
			hql.append(" AND obs.valueCoded IN :answers");
		}

		if (CollectionUtils.isNotEmpty(personTypes)) {
			appendPersonTypeConditions(hql, personTypes);
		}

		if (CollectionUtils.isNotEmpty(locations)) {
			hql.append(" AND obs.location IN :locations");
		}

		if (obsGroupId != null) {
			hql.append(" AND obs.obsGroup.obsId = :obsGroupId");
		}

		if (fromDate != null) {
			hql.append(" AND obs.obsDatetime >= :fromDate");
		}

		if (toDate != null) {
			hql.append(" AND obs.obsDatetime <= :toDate");
		}

		if (CollectionUtils.isNotEmpty(valueCodedNameAnswers)) {
			hql.append(" AND obs.valueCodedName IN :valueCodedNameAnswers");
		}

		if (!includeVoidedObs) {
			hql.append(" AND obs.voided = false");
		}

		if (accessionNumber != null) {
			hql.append(" AND obs.accessionNumber = :accessionNumber");
		}
	}

	/**
	 * Appends person type subquery conditions.
	 */
	private void appendPersonTypeConditions(StringBuilder hql, List<PERSON_TYPE> personTypes) {
		if (personTypes.contains(PERSON_TYPE.PATIENT)) {
			hql.append(" AND obs.person.personId IN (SELECT p.patientId FROM Patient p)");
		}
		if (personTypes.contains(PERSON_TYPE.USER)) {
			hql.append(" AND obs.person.personId IN (SELECT u.userId FROM User u)");
		}
	}

	/**
	 * Sets parameters on an observations query.
	 */
	private void setObservationsParams(Query<?> query, List<Person> whom, List<Encounter> encounters,
	        List<Concept> questions, List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations,
	        Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate,
	        List<ConceptName> valueCodedNameAnswers, boolean includeVoidedObs, String accessionNumber) {

		if (CollectionUtils.isNotEmpty(whom)) {
			query.setParameterList("whom", whom);
		}
		if (CollectionUtils.isNotEmpty(encounters)) {
			query.setParameterList("encounters", encounters);
		}
		if (CollectionUtils.isNotEmpty(questions)) {
			query.setParameterList("questions", questions);
		}
		if (CollectionUtils.isNotEmpty(answers)) {
			query.setParameterList("answers", answers);
		}
		if (CollectionUtils.isNotEmpty(locations)) {
			query.setParameterList("locations", locations);
		}
		if (obsGroupId != null) {
			query.setParameter("obsGroupId", obsGroupId);
		}
		if (fromDate != null) {
			query.setParameter("fromDate", fromDate);
		}
		if (toDate != null) {
			query.setParameter("toDate", toDate);
		}
		if (CollectionUtils.isNotEmpty(valueCodedNameAnswers)) {
			query.setParameterList("valueCodedNameAnswers", valueCodedNameAnswers);
		}
		if (accessionNumber != null) {
			query.setParameter("accessionNumber", accessionNumber);
		}
	}

	/**
	 * @see org.openmrs.api.db.ObsDAO#getObsByUuid(java.lang.String)
	 */
	@Override
	public Obs getObsByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Obs o where o.uuid = :uuid", Obs.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ObsDAO#getRevisionObs(org.openmrs.Obs)
	 */
	@Override
	public Obs getRevisionObs(Obs initialObs) {
		Query<Obs> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM Obs obs WHERE obs.previousVersion = :initialObs", Obs.class);
		query.setParameter("initialObs", initialObs);
		return query.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ObsDAO#getSavedStatus(org.openmrs.Obs)
	 */
	@Override
	public Obs.Status getSavedStatus(Obs obs) {
		// avoid premature flushes when this internal method is called from inside a service method
		Session session = sessionFactory.getCurrentSession();
		FlushMode flushMode = session.getHibernateFlushMode();
		session.setHibernateFlushMode(FlushMode.MANUAL);
		try {
			NativeQuery<String> sql = session.createNativeQuery(
			    "select status from obs where obs_id = :obsId", String.class);
			sql.setParameter("obsId", obs.getObsId());
			return Obs.Status.valueOf(sql.uniqueResult());
		}
		finally {
			session.setHibernateFlushMode(flushMode);
		}
	}

}
