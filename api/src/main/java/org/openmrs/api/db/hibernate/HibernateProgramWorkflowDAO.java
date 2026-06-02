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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

import org.hibernate.FlushMode;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;

import org.openmrs.Cohort;
import org.openmrs.Concept;
import org.openmrs.ConceptStateConversion;
import org.openmrs.Patient;
import org.openmrs.PatientProgram;
import org.openmrs.PatientProgramAttribute;
import org.openmrs.PatientState;
import org.openmrs.Program;
import org.openmrs.ProgramAttributeType;
import org.openmrs.ProgramWorkflow;
import org.openmrs.ProgramWorkflowState;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.ProgramWorkflowDAO;
import org.openmrs.customdatatype.CustomDatatypeUtil;

/**
 * Hibernate specific ProgramWorkflow related functions.<br>
 * <br>
 * This class should not be used directly. All calls should go through the
 * {@link org.openmrs.api.ProgramWorkflowService} methods.
 *
 * @see org.openmrs.api.db.ProgramWorkflowDAO
 * @see org.openmrs.api.ProgramWorkflowService
 */
public class HibernateProgramWorkflowDAO implements ProgramWorkflowDAO {

	private SessionFactory sessionFactory;

	public HibernateProgramWorkflowDAO() {
	}

	/**
	 * Hibernate Session Factory
	 *
	 * @param sessionFactory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	// **************************
	// PROGRAM
	// **************************

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#saveProgram(org.openmrs.Program)
	 */
	@Override
	public Program saveProgram(Program program) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(program);
		return program;
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getProgram(java.lang.Integer)
	 */
	@Override
	public Program getProgram(Integer programId) throws DAOException {
		return (Program) sessionFactory.getCurrentSession().get(Program.class, programId);
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getAllPrograms(boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Program> getAllPrograms(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("from Program");
		if (!includeRetired) {
			hql.append(" where retired = false");
		}
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), Program.class).list();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getProgramsByName(String, boolean)
	 */
	@Override
	public List<Program> getProgramsByName(String programName, boolean includeRetired) {
		StringBuilder hql = new StringBuilder("from Program p where p.name = :name");
		if (!includeRetired) {
			hql.append(" and p.retired = false");
		}
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), Program.class)
		        .setParameter("name", programName).list();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#findPrograms(java.lang.String)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Program> findPrograms(String nameFragment) throws DAOException {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Program p where lower(p.name) like :name order by p.name asc", Program.class)
		        .setParameter("name", "%" + nameFragment.toLowerCase() + "%").list();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#deleteProgram(org.openmrs.Program)
	 */
	@Override
	public void deleteProgram(Program program) throws DAOException {
		sessionFactory.getCurrentSession().delete(program);
	}

	// **************************
	// PATIENT PROGRAM
	// **************************

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#savePatientProgram(org.openmrs.PatientProgram)
	 */
	@Override
	public PatientProgram savePatientProgram(PatientProgram patientProgram) throws DAOException {
		CustomDatatypeUtil.saveAttributesIfNecessary(patientProgram);

		if (patientProgram.getPatientProgramId() == null) {
			sessionFactory.getCurrentSession().save(patientProgram);
		} else {
			sessionFactory.getCurrentSession().merge(patientProgram);
		}

		return patientProgram;
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getPatientProgram(java.lang.Integer)
	 */
	@Override
	public PatientProgram getPatientProgram(Integer patientProgramId) throws DAOException {
		return (PatientProgram) sessionFactory.getCurrentSession().get(PatientProgram.class, patientProgramId);
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getPatientPrograms(Patient, Program, Date, Date,
	 *      Date, Date, boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<PatientProgram> getPatientPrograms(Patient patient, Program program, Date minEnrollmentDate,
	        Date maxEnrollmentDate, Date minCompletionDate, Date maxCompletionDate, boolean includeVoided)
	        throws DAOException {
		StringBuilder hql = new StringBuilder("from PatientProgram pp where 1=1");
		if (patient != null) {
			hql.append(" and pp.patient = :patient");
		}
		if (program != null) {
			hql.append(" and pp.program = :program");
		}
		if (minEnrollmentDate != null) {
			hql.append(" and pp.dateEnrolled >= :minEnrollmentDate");
		}
		if (maxEnrollmentDate != null) {
			hql.append(" and pp.dateEnrolled <= :maxEnrollmentDate");
		}
		if (minCompletionDate != null) {
			hql.append(" and (pp.dateCompleted is null or pp.dateCompleted >= :minCompletionDate)");
		}
		if (maxCompletionDate != null) {
			hql.append(" and pp.dateCompleted <= :maxCompletionDate");
		}
		if (!includeVoided) {
			hql.append(" and pp.voided = false");
		}
		hql.append(" order by pp.dateEnrolled asc");

		Query<PatientProgram> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), PatientProgram.class);
		if (patient != null) {
			query.setParameter("patient", patient);
		}
		if (program != null) {
			query.setParameter("program", program);
		}
		if (minEnrollmentDate != null) {
			query.setParameter("minEnrollmentDate", minEnrollmentDate);
		}
		if (maxEnrollmentDate != null) {
			query.setParameter("maxEnrollmentDate", maxEnrollmentDate);
		}
		if (minCompletionDate != null) {
			query.setParameter("minCompletionDate", minCompletionDate);
		}
		if (maxCompletionDate != null) {
			query.setParameter("maxCompletionDate", maxCompletionDate);
		}
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getPatientPrograms(org.openmrs.Cohort,
	 *      java.util.Collection)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<PatientProgram> getPatientPrograms(Cohort cohort, Collection<Program> programs) {
		String hql = "from PatientProgram ";
		if (cohort != null || programs != null) {
			hql += "where ";
		}
		if (cohort != null) {
			hql += "patient.patientId in (:patientIds) ";
		}
		if (programs != null) {
			if (cohort != null) {
				hql += "and ";
			}
			hql += " program in (:programs)";
		}
		hql += " order by patient.patientId, dateEnrolled";
		Query<PatientProgram> query = sessionFactory.getCurrentSession().createQuery(hql, PatientProgram.class);
		if (cohort != null) {
			query.setParameterList("patientIds", cohort.getMemberIds());
		}
		if (programs != null) {
			query.setParameterList("programs", programs);
		}
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#deletePatientProgram(org.openmrs.PatientProgram)
	 */
	@Override
	public void deletePatientProgram(PatientProgram patientProgram) throws DAOException {
		sessionFactory.getCurrentSession().delete(patientProgram);
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#saveConceptStateConversion(org.openmrs.ConceptStateConversion)
	 */
	@Override
	public ConceptStateConversion saveConceptStateConversion(ConceptStateConversion csc) throws DAOException {
		if (csc.getConceptStateConversionId() == null) {
			sessionFactory.getCurrentSession().save(csc);
		} else {
			sessionFactory.getCurrentSession().merge(csc);
		}
		return csc;
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getAllConceptStateConversions()
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<ConceptStateConversion> getAllConceptStateConversions() throws DAOException {
		return sessionFactory.getCurrentSession().createQuery("from ConceptStateConversion", ConceptStateConversion.class)
		        .list();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getConceptStateConversion(java.lang.Integer)
	 */
	@Override
	public ConceptStateConversion getConceptStateConversion(Integer conceptStateConversionId) {
		return (ConceptStateConversion) sessionFactory.getCurrentSession().get(ConceptStateConversion.class,
		    conceptStateConversionId);
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#deleteConceptStateConversion(org.openmrs.ConceptStateConversion)
	 */
	@Override
	public void deleteConceptStateConversion(ConceptStateConversion csc) {
		sessionFactory.getCurrentSession().delete(csc);
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getConceptStateConversion(org.openmrs.ProgramWorkflow,
	 *      org.openmrs.Concept)
	 */
	@Override
	public ConceptStateConversion getConceptStateConversion(ProgramWorkflow workflow, Concept trigger) {
		ConceptStateConversion csc = null;

		if (workflow != null && trigger != null) {
			csc = sessionFactory.getCurrentSession()
			        .createQuery(
			            "from ConceptStateConversion csc where csc.programWorkflow = :workflow and csc.concept = :trigger",
			            ConceptStateConversion.class)
			        .setParameter("workflow", workflow).setParameter("trigger", trigger).uniqueResult();
		}

		return csc;
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getConceptStateConversionByUuid(java.lang.String)
	 */
	@Override
	public ConceptStateConversion getConceptStateConversionByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptStateConversion csc where csc.uuid = :uuid", ConceptStateConversion.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getPatientProgramByUuid(java.lang.String)
	 */
	@Override
	public PatientProgram getPatientProgramByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from PatientProgram pp where pp.uuid = :uuid", PatientProgram.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getProgramByUuid(java.lang.String)
	 */
	@Override
	public Program getProgramByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Program p where p.uuid = :uuid", Program.class).setParameter("uuid", uuid)
		        .uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getState(Integer)
	 */
	@Override
	public ProgramWorkflowState getState(Integer stateId) {
		return (ProgramWorkflowState) sessionFactory.getCurrentSession().get(ProgramWorkflowState.class, stateId);
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getStateByUuid(java.lang.String)
	 */
	@Override
	public ProgramWorkflowState getStateByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ProgramWorkflowState pws where pws.uuid = :uuid", ProgramWorkflowState.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	@Override
	public PatientState getPatientStateByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from PatientState pws where pws.uuid = :uuid", PatientState.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getWorkflow(Integer)
	 */
	@Override
	public ProgramWorkflow getWorkflow(Integer workflowId) {
		return (ProgramWorkflow) sessionFactory.getCurrentSession().get(ProgramWorkflow.class, workflowId);
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getWorkflowByUuid(java.lang.String)
	 */
	@Override
	public ProgramWorkflow getWorkflowByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ProgramWorkflow pw where pw.uuid = :uuid", ProgramWorkflow.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getProgramsByConcept(org.openmrs.Concept)
	 */
	@Override
	public List<Program> getProgramsByConcept(Concept concept) {
		return sessionFactory.getCurrentSession()
		        .createQuery("select distinct p from Program p where p.concept = :concept", Program.class)
		        .setParameter("concept", concept).list();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getProgramWorkflowsByConcept(org.openmrs.Concept)
	 */
	@Override
	public List<ProgramWorkflow> getProgramWorkflowsByConcept(Concept concept) {
		return sessionFactory.getCurrentSession()
		        .createQuery("select distinct w from ProgramWorkflow w where w.concept = :concept", ProgramWorkflow.class)
		        .setParameter("concept", concept).list();
	}

	/**
	 * @see org.openmrs.api.db.ProgramWorkflowDAO#getProgramWorkflowStatesByConcept(org.openmrs.Concept)
	 */
	@Override
	public List<ProgramWorkflowState> getProgramWorkflowStatesByConcept(Concept concept) {
		return sessionFactory.getCurrentSession()
		        .createQuery("select distinct s from ProgramWorkflowState s where s.concept = :concept",
		            ProgramWorkflowState.class)
		        .setParameter("concept", concept).list();
	}

	@Override
	public List<ProgramAttributeType> getAllProgramAttributeTypes() {
		return sessionFactory.getCurrentSession().createQuery("from ProgramAttributeType", ProgramAttributeType.class)
		        .list();
	}

	@Override
	public ProgramAttributeType getProgramAttributeType(Integer id) {
		return (ProgramAttributeType) sessionFactory.getCurrentSession().get(ProgramAttributeType.class, id);
	}

	@Override
	public ProgramAttributeType getProgramAttributeTypeByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ProgramAttributeType pat where pat.uuid = :uuid", ProgramAttributeType.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	@Override
	public ProgramAttributeType saveProgramAttributeType(ProgramAttributeType programAttributeType) {
		sessionFactory.getCurrentSession().saveOrUpdate(programAttributeType);
		return programAttributeType;
	}

	@Override
	public PatientProgramAttribute getPatientProgramAttributeByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from PatientProgramAttribute ppa where ppa.uuid = :uuid", PatientProgramAttribute.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	@Override
	public void purgeProgramAttributeType(ProgramAttributeType type) {
		sessionFactory.getCurrentSession().delete(type);
	}

	@Override
	public List<PatientProgram> getPatientProgramByAttributeNameAndValue(String attributeName, String attributeValue) {
		FlushMode flushMode = sessionFactory.getCurrentSession().getHibernateFlushMode();
		sessionFactory.getCurrentSession().setHibernateFlushMode(FlushMode.MANUAL);
		Query<PatientProgram> query;
		try {
			query = sessionFactory.getCurrentSession().createQuery(
			        "SELECT pp FROM patient_program pp "
			                + "INNER JOIN pp.attributes attr "
			                + "INNER JOIN attr.attributeType attr_type "
			                + "WHERE attr.valueReference = :attributeValue "
			                + "AND attr_type.name = :attributeName "
			                + "AND pp.voided = 0",
			        PatientProgram.class)
			        .setParameter("attributeName", attributeName)
			        .setParameter("attributeValue", attributeValue);
			return query.list();
		}
		finally {
			sessionFactory.getCurrentSession().setHibernateFlushMode(flushMode);
		}
	}

	@Override
	public Map<Object, Object> getPatientProgramAttributeByAttributeName(List<Integer> patientIds, String attributeName) {
		Map<Object, Object> patientProgramAttributes = new HashMap<>();
		if (patientIds.isEmpty() || attributeName == null) {
			return patientProgramAttributes;
		}
		String commaSeperatedPatientIds = StringUtils.join(patientIds, ",");
		List<Object> list = sessionFactory.getCurrentSession().createNativeQuery(
		        "SELECT p.patient_id as person_id, "
		                + " concat('{',group_concat(DISTINCT (coalesce(concat('\"',ppt.name,'\":\"', COALESCE (cn.name, ppa.value_reference),'\"'))) SEPARATOR ','),'}') AS patientProgramAttributeValue  "
		                + " from patient p "
		                + " join patient_program pp on p.patient_id = pp.patient_id and p.patient_id in (" + commaSeperatedPatientIds + ")"
		                + " join patient_program_attribute ppa on pp.patient_program_id = ppa.patient_program_id and ppa.voided=0"
		                + " join program_attribute_type ppt on ppa.attribute_type_id = ppt.program_attribute_type_id and ppt.name ='" + attributeName + "' "
		                + " LEFT OUTER JOIN concept_name cn on ppa.value_reference = cn.concept_id and cn.concept_name_type= 'FULLY_SPECIFIED' and cn.voided=0 and ppt.datatype like '%ConceptDataType%'"
		                + " group by p.patient_id")
		        .addScalar("person_id", Integer.class)
		        .addScalar("patientProgramAttributeValue", String.class)
		        .list();

		for (Object o : list) {
			Object[] arr = (Object[]) o;
			patientProgramAttributes.put(arr[0], arr[1]);
		}

		return patientProgramAttributes;

	}
}
