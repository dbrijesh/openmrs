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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.openmrs.Cohort;
import org.openmrs.Encounter;
import org.openmrs.EncounterRole;
import org.openmrs.EncounterType;
import org.openmrs.Location;
import org.openmrs.Patient;
import org.openmrs.Visit;
import org.openmrs.api.EncounterService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.EncounterDAO;
import org.openmrs.parameter.EncounterSearchCriteria;

/**
 * Hibernate specific dao for the {@link EncounterService} All calls should be made on the
 * Context.getEncounterService() object
 *
 * @see EncounterDAO
 * @see EncounterService
 */
public class HibernateEncounterDAO implements EncounterDAO {

	/**
	 * Hibernate session factory
	 */
	private SessionFactory sessionFactory;

	/**
	 * Set session factory
	 *
	 * @param sessionFactory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#saveEncounter(org.openmrs.Encounter)
	 */
	@Override
	public Encounter saveEncounter(Encounter encounter) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(encounter);
		return encounter;
	}

	/**
	 * @see org.openmrs.api.EncounterService#purgeEncounter(org.openmrs.Encounter)
	 */
	@Override
	public void deleteEncounter(Encounter encounter) throws DAOException {
		sessionFactory.getCurrentSession().delete(encounter);
	}

	/**
	 * @see org.openmrs.api.EncounterService#getEncounter(java.lang.Integer)
	 */
	@Override
	public Encounter getEncounter(Integer encounterId) throws DAOException {
		return (Encounter) sessionFactory.getCurrentSession().get(Encounter.class, encounterId);
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncountersByPatientId(java.lang.Integer)
	 */
	@Override
	public List<Encounter> getEncountersByPatientId(Integer patientId) throws DAOException {
		Query<Encounter> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM Encounter e WHERE e.patient.patientId = :patientId AND e.voided = false ORDER BY e.encounterDatetime DESC",
		    Encounter.class);
		query.setParameter("patientId", patientId);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncounters(org.openmrs.parameter.EncounterSearchCriteria)
	 */
	@Override
	public List<Encounter> getEncounters(EncounterSearchCriteria searchCriteria) {
		StringBuilder hql = new StringBuilder("FROM Encounter e WHERE 1=1");

		if (searchCriteria.getPatient() != null && searchCriteria.getPatient().getPatientId() != null) {
			hql.append(" AND e.patient = :patient");
		}
		if (searchCriteria.getLocation() != null && searchCriteria.getLocation().getLocationId() != null) {
			hql.append(" AND e.location = :location");
		}
		if (searchCriteria.getFromDate() != null) {
			hql.append(" AND e.encounterDatetime >= :fromDate");
		}
		if (searchCriteria.getToDate() != null) {
			hql.append(" AND e.encounterDatetime <= :toDate");
		}
		if (searchCriteria.getDateChanged() != null) {
			hql.append(" AND ((e.dateChanged IS NULL AND e.dateCreated >= :dateChanged) OR e.dateChanged >= :dateChanged)");
		}
		if (searchCriteria.getEnteredViaForms() != null && !searchCriteria.getEnteredViaForms().isEmpty()) {
			hql.append(" AND e.form IN :enteredViaForms");
		}
		if (searchCriteria.getEncounterTypes() != null && !searchCriteria.getEncounterTypes().isEmpty()) {
			hql.append(" AND e.encounterType IN :encounterTypes");
		}
		if (searchCriteria.getProviders() != null && !searchCriteria.getProviders().isEmpty()) {
			hql.append(" AND EXISTS (SELECT ep FROM EncounterProvider ep WHERE ep.encounter = e AND ep.provider IN :providers)");
		}
		if (searchCriteria.getVisitTypes() != null && !searchCriteria.getVisitTypes().isEmpty()) {
			hql.append(" AND e.visit.visitType IN :visitTypes");
		}
		if (searchCriteria.getVisits() != null && !searchCriteria.getVisits().isEmpty()) {
			hql.append(" AND e.visit IN :visits");
		}
		if (!searchCriteria.getIncludeVoided()) {
			hql.append(" AND e.voided = false");
		}
		hql.append(" ORDER BY e.encounterDatetime ASC");

		Query<Encounter> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Encounter.class);

		if (searchCriteria.getPatient() != null && searchCriteria.getPatient().getPatientId() != null) {
			query.setParameter("patient", searchCriteria.getPatient());
		}
		if (searchCriteria.getLocation() != null && searchCriteria.getLocation().getLocationId() != null) {
			query.setParameter("location", searchCriteria.getLocation());
		}
		if (searchCriteria.getFromDate() != null) {
			query.setParameter("fromDate", searchCriteria.getFromDate());
		}
		if (searchCriteria.getToDate() != null) {
			query.setParameter("toDate", searchCriteria.getToDate());
		}
		if (searchCriteria.getDateChanged() != null) {
			query.setParameter("dateChanged", searchCriteria.getDateChanged());
		}
		if (searchCriteria.getEnteredViaForms() != null && !searchCriteria.getEnteredViaForms().isEmpty()) {
			query.setParameterList("enteredViaForms", searchCriteria.getEnteredViaForms());
		}
		if (searchCriteria.getEncounterTypes() != null && !searchCriteria.getEncounterTypes().isEmpty()) {
			query.setParameterList("encounterTypes", searchCriteria.getEncounterTypes());
		}
		if (searchCriteria.getProviders() != null && !searchCriteria.getProviders().isEmpty()) {
			query.setParameterList("providers", searchCriteria.getProviders());
		}
		if (searchCriteria.getVisitTypes() != null && !searchCriteria.getVisitTypes().isEmpty()) {
			query.setParameterList("visitTypes", searchCriteria.getVisitTypes());
		}
		if (searchCriteria.getVisits() != null && !searchCriteria.getVisits().isEmpty()) {
			query.setParameterList("visits", searchCriteria.getVisits());
		}

		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#saveEncounterType(org.openmrs.EncounterType)
	 */
	@Override
	public EncounterType saveEncounterType(EncounterType encounterType) {
		sessionFactory.getCurrentSession().saveOrUpdate(encounterType);
		return encounterType;
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#deleteEncounterType(org.openmrs.EncounterType)
	 */
	@Override
	public void deleteEncounterType(EncounterType encounterType) throws DAOException {
		sessionFactory.getCurrentSession().delete(encounterType);
	}

	/**
	 * @see org.openmrs.api.EncounterService#getEncounterType(java.lang.Integer)
	 */
	@Override
	public EncounterType getEncounterType(Integer encounterTypeId) throws DAOException {
		return (EncounterType) sessionFactory.getCurrentSession().get(EncounterType.class, encounterTypeId);
	}

	/**
	 * @see org.openmrs.api.EncounterService#getEncounterType(java.lang.String)
	 */
	@Override
	public EncounterType getEncounterType(String name) throws DAOException {
		Query<EncounterType> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM EncounterType et WHERE et.retired = false AND et.name = :name", EncounterType.class);
		query.setParameter("name", name);
		return query.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getAllEncounterTypes(java.lang.Boolean)
	 */
	@Override
	public List<EncounterType> getAllEncounterTypes(Boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM EncounterType et WHERE 1=1");
		if (!includeRetired) {
			hql.append(" AND et.retired = false");
		}
		hql.append(" ORDER BY et.name ASC");
		Query<EncounterType> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), EncounterType.class);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#findEncounterTypes(java.lang.String)
	 */
	@Override
	public List<EncounterType> findEncounterTypes(String name) throws DAOException {
		Query<EncounterType> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM EncounterType et WHERE lower(et.name) like :name ORDER BY et.name ASC, et.retired ASC",
		    EncounterType.class);
		query.setParameter("name", name.toLowerCase() + "%");
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getSavedEncounterDatetime(org.openmrs.Encounter)
	 */
	@Override
	public Date getSavedEncounterDatetime(Encounter encounter) {
		//Usages of this method currently are internal and don't require a flush
		//Otherwise we end up with premature flushes of Immutable types like Obs
		//that are associated to the encounter before we void and replace them
		Session session = sessionFactory.getCurrentSession();
		FlushMode flushMode = session.getHibernateFlushMode();
		session.setHibernateFlushMode(FlushMode.MANUAL);
		try {
			NativeQuery<Date> sql = session.createNativeQuery(
			    "select encounter_datetime from encounter where encounter_id = :encounterId", Date.class);
			sql.setParameter("encounterId", encounter.getEncounterId());
			return sql.uniqueResult();
		}
		finally {
			session.setHibernateFlushMode(flushMode);
		}
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncounterByUuid(java.lang.String)
	 */
	@Override
	public Encounter getEncounterByUuid(String uuid) {
		return getClassByUuid(Encounter.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncounterTypeByUuid(java.lang.String)
	 */
	@Override
	public EncounterType getEncounterTypeByUuid(String uuid) {
		return getClassByUuid(EncounterType.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncounters(String, Integer, Integer, Integer,
	 *      boolean)
	 */
	@Override
	public List<Encounter> getEncounters(String query, Integer patientId, Integer start, Integer length,
	        boolean includeVoided) {
		if (StringUtils.isBlank(query) && patientId == null) {
			return Collections.emptyList();
		}

		// When a patientId is provided and a query string is present, use native SQL approach
		// to match location, encounterType, form, and provider name/identifier.
		// When only a patientId is provided with no query string, use a simple HQL query.
		// When no patientId, delegate to PatientSearchCriteria which itself uses Criteria API.
		// For Hibernate 6, we handle the patientId-only and patientId+query cases with HQL.
		// For the no-patientId case we fall back to PatientSearchCriteria (it uses its own queries).

		if (patientId != null) {
			StringBuilder hql = new StringBuilder(
			        "SELECT DISTINCT enc FROM Encounter enc WHERE enc.patient.patientId = :patientId");
			if (!includeVoided) {
				hql.append(" AND enc.voided = false");
			}
			if (StringUtils.isNotBlank(query)) {
				String likeQuery = "%" + query.toLowerCase() + "%";
				hql.append(" AND (");
				hql.append("  lower(enc.location.name) like :query");
				hql.append("  OR lower(enc.encounterType.name) like :query");
				hql.append("  OR lower(enc.form.name) like :query");
				hql.append("  OR EXISTS (SELECT ep FROM EncounterProvider ep JOIN ep.provider prov");
				hql.append("    LEFT JOIN prov.person person LEFT JOIN person.names personName");
				hql.append("    WHERE ep.encounter = enc AND (");
				hql.append("      lower(prov.name) like :query");
				hql.append("      OR lower(prov.identifier) like :query");
				hql.append("      OR (personName.voided = false AND (");
				hql.append("        lower(personName.givenName) like :query");
				hql.append("        OR lower(personName.middleName) like :query");
				hql.append("        OR lower(personName.familyName) like :query");
				hql.append("        OR lower(personName.familyName2) like :query");
				hql.append("      ))");
				hql.append("    )");
				hql.append("  )");
				hql.append(")");

				Query<Encounter> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Encounter.class);
				q.setParameter("patientId", patientId);
				q.setParameter("query", likeQuery);
				if (start != null) {
					q.setFirstResult(start);
				}
				if (length != null && length > 0) {
					q.setMaxResults(length);
				}
				return q.list();
			} else {
				Query<Encounter> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Encounter.class);
				q.setParameter("patientId", patientId);
				if (start != null) {
					q.setFirstResult(start);
				}
				if (length != null && length > 0) {
					q.setMaxResults(length);
				}
				return q.list();
			}
		} else {
			// No patientId: search by patient name/identifier, then return their encounters
			List<Patient> matchingPatients = new PatientSearchCriteria(sessionFactory, null).getPatients(
			    query, query, new ArrayList<>(), true, true, true);
			if (matchingPatients.isEmpty()) {
				return Collections.emptyList();
			}
			StringBuilder hql = new StringBuilder("FROM Encounter enc WHERE enc.patient IN :patients");
			if (!includeVoided) {
				hql.append(" AND enc.voided = false");
			}
			Query<Encounter> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Encounter.class);
			q.setParameterList("patients", matchingPatients);
			if (start != null) {
				q.setFirstResult(start);
			}
			if (length != null && length > 0) {
				q.setMaxResults(length);
			}
			return q.list();
		}
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getSavedEncounterLocation(org.openmrs.Encounter)
	 */
	@Override
	public Location getSavedEncounterLocation(Encounter encounter) {
		Session session = sessionFactory.getCurrentSession();
		FlushMode flushMode = session.getHibernateFlushMode();
		session.setHibernateFlushMode(FlushMode.MANUAL);
		try {
			NativeQuery<Integer> sql = session.createNativeQuery(
			    "select location_id from encounter where encounter_id = :encounterId", Integer.class);
			sql.setParameter("encounterId", encounter.getEncounterId());
			return Context.getLocationService().getLocation(sql.uniqueResult());
		}
		finally {
			session.setHibernateFlushMode(flushMode);
		}
	}

	/**
	 * @see EncounterDAO#getAllEncounters(org.openmrs.Cohort)
	 */
	@Override
	public Map<Integer, List<Encounter>> getAllEncounters(Cohort patients) {
		Map<Integer, List<Encounter>> encountersBypatient = new HashMap<>();

		List<Encounter> allEncounters = getAllEncountersForCohort(patients);

		// set up the return map
		for (Encounter encounter : allEncounters) {
			Integer patientId = encounter.getPatient().getPersonId();
			List<Encounter> encounters = encountersBypatient.get(patientId);

			if (encounters == null) {
				encounters = new ArrayList<>();
			}

			encounters.add(encounter);
			if (!encountersBypatient.containsKey(patientId)) {
				encountersBypatient.put(patientId, encounters);
			}
		}
		return encountersBypatient;
	}

	/**
	 * Fetches all encounters for the given cohort using HQL.
	 */
	private List<Encounter> getAllEncountersForCohort(Cohort patients) {
		StringBuilder hql = new StringBuilder("FROM Encounter e WHERE e.voided = false");

		if (patients != null) {
			ArrayList<Integer> patientIds = new ArrayList<>();
			patients.getMemberships().forEach(m -> patientIds.add(m.getPatientId()));
			if (!patientIds.isEmpty()) {
				hql.append(" AND e.patient.personId IN :patientIds");
			}
		}

		hql.append(" ORDER BY e.patient.personId DESC, e.encounterDatetime DESC");

		Query<Encounter> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Encounter.class);
		query.setCacheable(false);

		if (patients != null) {
			ArrayList<Integer> patientIds = new ArrayList<>();
			patients.getMemberships().forEach(m -> patientIds.add(m.getPatientId()));
			if (!patientIds.isEmpty()) {
				query.setParameterList("patientIds", patientIds);
			}
		}

		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getCountOfEncounters(java.lang.String,
	 *      java.lang.Integer, boolean)
	 */
	@Override
	public Long getCountOfEncounters(String query, Integer patientId, boolean includeVoided) {
		if (patientId != null) {
			StringBuilder hql = new StringBuilder(
			        "SELECT COUNT(DISTINCT enc.encounterId) FROM Encounter enc WHERE enc.patient.patientId = :patientId");
			if (!includeVoided) {
				hql.append(" AND enc.voided = false");
			}
			if (StringUtils.isNotBlank(query)) {
				String likeQuery = "%" + query.toLowerCase() + "%";
				hql.append(" AND (");
				hql.append("  lower(enc.location.name) like :query");
				hql.append("  OR lower(enc.encounterType.name) like :query");
				hql.append("  OR lower(enc.form.name) like :query");
				hql.append("  OR EXISTS (SELECT ep FROM EncounterProvider ep JOIN ep.provider prov");
				hql.append("    LEFT JOIN prov.person person LEFT JOIN person.names personName");
				hql.append("    WHERE ep.encounter = enc AND (");
				hql.append("      lower(prov.name) like :query");
				hql.append("      OR lower(prov.identifier) like :query");
				hql.append("      OR (personName.voided = false AND (");
				hql.append("        lower(personName.givenName) like :query");
				hql.append("        OR lower(personName.middleName) like :query");
				hql.append("        OR lower(personName.familyName) like :query");
				hql.append("        OR lower(personName.familyName2) like :query");
				hql.append("      ))");
				hql.append("    )");
				hql.append("  )");
				hql.append(")");

				Query<Long> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Long.class);
				q.setParameter("patientId", patientId);
				q.setParameter("query", likeQuery);
				return q.uniqueResult();
			} else {
				Query<Long> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Long.class);
				q.setParameter("patientId", patientId);
				return q.uniqueResult();
			}
		} else {
			// No patientId: count encounters for patients matching the name/identifier query
			List<Patient> matchingPatients = new PatientSearchCriteria(sessionFactory, null).getPatients(
			    query, query, new ArrayList<>(), true, false, true);
			if (matchingPatients.isEmpty()) {
				return 0L;
			}
			StringBuilder hql = new StringBuilder(
			        "SELECT COUNT(DISTINCT enc.encounterId) FROM Encounter enc WHERE enc.patient IN :patients");
			if (!includeVoided) {
				hql.append(" AND enc.voided = false");
			}
			Query<Long> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Long.class);
			q.setParameterList("patients", matchingPatients);
			return q.uniqueResult();
		}
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncountersByVisit(Visit, boolean)
	 */
	@Override
	public List<Encounter> getEncountersByVisit(Visit visit, boolean includeVoided) {
		StringBuilder hql = new StringBuilder("FROM Encounter e WHERE e.visit = :visit");
		if (!includeVoided) {
			hql.append(" AND e.voided = false");
		}
		hql.append(" ORDER BY e.encounterDatetime ASC");

		Query<Encounter> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Encounter.class);
		query.setParameter("visit", visit);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#saveEncounterRole(EncounterRole encounterRole)
	 */
	@Override
	public EncounterRole saveEncounterRole(EncounterRole encounterRole) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(encounterRole);
		return encounterRole;
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#deleteEncounterRole(org.openmrs.EncounterRole)
	 */
	@Override
	public void deleteEncounterRole(EncounterRole encounterRole) throws DAOException {
		sessionFactory.getCurrentSession().delete(encounterRole);
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncounterRole(Integer)
	 */
	@Override
	public EncounterRole getEncounterRole(Integer encounterRoleId) throws DAOException {
		return (EncounterRole) sessionFactory.getCurrentSession().get(EncounterRole.class, encounterRoleId);
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncounterRoleByUuid(String)
	 */
	@Override
	public EncounterRole getEncounterRoleByUuid(String uuid) {
		return getClassByUuid(EncounterRole.class, uuid);
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getAllEncounterRoles(boolean)
	 */
	@Override
	public List<EncounterRole> getAllEncounterRoles(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM EncounterRole er WHERE 1=1");
		if (!includeRetired) {
			hql.append(" AND er.retired = false");
		}
		Query<EncounterRole> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), EncounterRole.class);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncounterRoleByName(String)
	 */
	@Override
	public EncounterRole getEncounterRoleByName(String name) throws DAOException {
		Query<EncounterRole> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM EncounterRole er WHERE er.name = :name", EncounterRole.class);
		query.setParameter("name", name);
		return query.uniqueResult();
	}

	/**
	 * Convenience method since this DAO fetches several different domain objects by uuid
	 *
	 * @param uuid uuid to fetch
	 * @param clazz the class to query
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private <T> T getClassByUuid(Class<T> clazz, String uuid) {
		return (T) sessionFactory.getCurrentSession()
		        .createQuery("FROM " + clazz.getSimpleName() + " e WHERE e.uuid = :uuid", clazz)
		        .setParameter("uuid", uuid)
		        .uniqueResult();
	}

	@Override
	public List<Encounter> getEncountersNotAssignedToAnyVisit(Patient patient) throws DAOException {
		Query<Encounter> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM Encounter e WHERE e.patient = :patient AND e.visit IS NULL AND e.voided = false ORDER BY e.encounterDatetime DESC",
		    Encounter.class);
		query.setParameter("patient", patient);
		query.setMaxResults(100);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncountersByVisitsAndPatient(org.openmrs.Patient,
	 *      boolean, java.lang.String, java.lang.Integer, java.lang.Integer)
	 */
	@Override
	public List<Encounter> getEncountersByVisitsAndPatient(Patient patient, boolean includeVoided, String query,
	        Integer start, Integer length) {
		List<Encounter> encounters = getEncountersByPatientWithQuery(patient, includeVoided, query);
		List<Visit> emptyVisits = getEmptyVisitsByPatient(patient, includeVoided, query);

		if (!emptyVisits.isEmpty()) {
			for (Visit emptyVisit : emptyVisits) {
				Encounter mockEncounter = new Encounter();
				mockEncounter.setVisit(emptyVisit);
				encounters.add(mockEncounter);
			}

			encounters.sort((o1, o2) -> {
				Date o1Date = (o1.getVisit() != null) ? o1.getVisit().getStartDatetime() : o1.getEncounterDatetime();
				Date o2Date = (o2.getVisit() != null) ? o2.getVisit().getStartDatetime() : o2.getEncounterDatetime();
				return o2Date.compareTo(o1Date);
			});
		}

		if (start == null) {
			start = 0;
		}
		if (length == null) {
			length = encounters.size();
		}
		int end = start + length;
		if (end > encounters.size()) {
			end = encounters.size();
		}

		return encounters.subList(start, end);
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncountersByVisitsAndPatientCount(org.openmrs.Patient,
	 *      boolean, java.lang.String)
	 */
	@Override
	public Integer getEncountersByVisitsAndPatientCount(Patient patient, boolean includeVoided, String query) {
		Long emptyVisitCount = countEmptyVisitsByPatient(patient, includeVoided, query);
		Long encounterCount = countEncountersByPatient(patient, includeVoided, query);
		return emptyVisitCount.intValue() + encounterCount.intValue();
	}

	private Long countEmptyVisitsByPatient(Patient patient, boolean includeVoided, String query) {
		StringBuilder hql = buildEmptyVisitsHql(patient, includeVoided, query, true);
		Query<Long> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Long.class);
		setEmptyVisitsParams(q, patient, includeVoided, query);
		return q.uniqueResult();
	}

	private Long countEncountersByPatient(Patient patient, boolean includeVoided, String query) {
		StringBuilder hql = buildEncountersByPatientHql(patient, includeVoided, query, true);
		Query<Long> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Long.class);
		setEncountersByPatientParams(q, patient, includeVoided, query);
		return q.uniqueResult();
	}

	private List<Encounter> getEncountersByPatientWithQuery(Patient patient, boolean includeVoided, String query) {
		StringBuilder hql = buildEncountersByPatientHql(patient, includeVoided, query, false);
		hql.append(" ORDER BY e.visit.startDatetime DESC, e.visit.visitId DESC, e.encounterDatetime DESC, e.encounterId DESC");
		Query<Encounter> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Encounter.class);
		setEncountersByPatientParams(q, patient, includeVoided, query);
		return q.list();
	}

	private List<Visit> getEmptyVisitsByPatient(Patient patient, boolean includeVoided, String query) {
		StringBuilder hql = buildEmptyVisitsHql(patient, includeVoided, query, false);
		hql.append(" ORDER BY v.startDatetime DESC, v.visitId DESC");
		Query<Visit> q = sessionFactory.getCurrentSession().createQuery(hql.toString(), Visit.class);
		setEmptyVisitsParams(q, patient, includeVoided, query);
		return q.list();
	}

	private StringBuilder buildEmptyVisitsHql(Patient patient, boolean includeVoided, String query, boolean countOnly) {
		StringBuilder hql = new StringBuilder();
		if (countOnly) {
			hql.append("SELECT COUNT(v) FROM Visit v");
		} else {
			hql.append("FROM Visit v");
		}
		if (query != null && !StringUtils.isBlank(query)) {
			hql.append(" LEFT JOIN v.visitType visitType LEFT JOIN v.location location");
		}
		hql.append(" WHERE v.patient = :patient");
		hql.append(" AND SIZE(v.encounters) = 0");
		if (!includeVoided) {
			hql.append(" AND v.voided = false");
		}
		if (query != null && !StringUtils.isBlank(query)) {
			hql.append(" AND (lower(visitType.name) like :query OR lower(location.name) like :query)");
		}
		return hql;
	}

	private void setEmptyVisitsParams(Query<?> q, Patient patient, boolean includeVoided, String query) {
		q.setParameter("patient", patient);
		if (query != null && !StringUtils.isBlank(query)) {
			q.setParameter("query", "%" + query.toLowerCase() + "%");
		}
	}

	private StringBuilder buildEncountersByPatientHql(Patient patient, boolean includeVoided, String query,
	        boolean countOnly) {
		StringBuilder hql = new StringBuilder();
		if (countOnly) {
			hql.append("SELECT COUNT(e) FROM Encounter e");
		} else {
			hql.append("FROM Encounter e");
		}
		if (query != null && !StringUtils.isBlank(query)) {
			hql.append(" LEFT JOIN e.visit visit LEFT JOIN visit.visitType visitType LEFT JOIN visit.location visitLocation");
			hql.append(" LEFT JOIN e.location location LEFT JOIN e.encounterType encounterType");
		}
		hql.append(" WHERE e.patient = :patient");
		if (!includeVoided) {
			hql.append(" AND e.voided = false");
		}
		if (query != null && !StringUtils.isBlank(query)) {
			hql.append(" AND (lower(visitType.name) like :query OR lower(visitLocation.name) like :query");
			hql.append(" OR lower(location.name) like :query OR lower(encounterType.name) like :query)");
		}
		return hql;
	}

	private void setEncountersByPatientParams(Query<?> q, Patient patient, boolean includeVoided, String query) {
		q.setParameter("patient", patient);
		if (query != null && !StringUtils.isBlank(query)) {
			q.setParameter("query", "%" + query.toLowerCase() + "%");
		}
	}

	/**
	 * @see org.openmrs.api.db.EncounterDAO#getEncounterRolesByName(String)
	 */
	@Override
	public List<EncounterRole> getEncounterRolesByName(String name) throws DAOException {
		Query<EncounterRole> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM EncounterRole er WHERE er.name = :name", EncounterRole.class);
		query.setParameter("name", name);
		return query.list();
	}
}
