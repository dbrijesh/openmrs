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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.Concept;
import org.openmrs.EncounterType;
import org.openmrs.Field;
import org.openmrs.FieldAnswer;
import org.openmrs.FieldType;
import org.openmrs.Form;
import org.openmrs.FormField;
import org.openmrs.FormResource;
import org.openmrs.api.APIException;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.FormDAO;
import org.openmrs.util.OpenmrsUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hibernate-specific Form-related functions. This class should not be used directly. All calls
 * should go through the {@link org.openmrs.api.FormService} methods.
 *
 * @see org.openmrs.api.db.FormDAO
 * @see org.openmrs.api.FormService
 */
public class HibernateFormDAO implements FormDAO {

	private static final Logger log = LoggerFactory.getLogger(HibernateFormDAO.class);

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
	 * Returns the form object originally passed in, which will have been persisted.
	 *
	 * @see org.openmrs.api.FormService#saveForm(org.openmrs.Form)
	 */
	@Override
	public Form saveForm(Form form) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(form);
		return form;
	}

	/**
	 * @see org.openmrs.api.FormService#duplicateForm(org.openmrs.Form)
	 */
	@Override
	public Form duplicateForm(Form form) throws DAOException {
		return sessionFactory.getCurrentSession().merge(form);
	}

	/**
	 * @see org.openmrs.api.FormService#purgeForm(org.openmrs.Form)
	 */
	@Override
	public void deleteForm(Form form) throws DAOException {
		sessionFactory.getCurrentSession().delete(form);
	}

	/**
	 * @see org.openmrs.api.FormService#getForm(java.lang.Integer)
	 */
	@Override
	public Form getForm(Integer formId) throws DAOException {
		return sessionFactory.getCurrentSession().get(Form.class, formId);
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormFieldsByField(org.openmrs.Field)
	 */
	@SuppressWarnings("unchecked")
	public List<FormField> getFormFields(Form form) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM FormField ff WHERE ff.form = :form", FormField.class)
				.setParameter("form", form)
				.list();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFields(java.lang.String)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Field> getFields(String search) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Field f WHERE lower(f.name) LIKE :search ORDER BY f.name ASC", Field.class)
				.setParameter("search", "%" + search.toLowerCase() + "%")
				.list();
	}

	/**
	 * @see org.openmrs.api.FormService#getFieldsByConcept(org.openmrs.Concept)
	 */
	@SuppressWarnings("unchecked")
	public List<Field> getFieldsByConcept(Concept concept) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Field f WHERE f.concept = :concept ORDER BY f.name ASC", Field.class)
				.setParameter("concept", concept)
				.list();
	}

	/**
	 * @see org.openmrs.api.FormService#getField(java.lang.Integer)
	 * @see org.openmrs.api.db.FormDAO#getField(java.lang.Integer)
	 */
	@Override
	public Field getField(Integer fieldId) throws DAOException {
		return sessionFactory.getCurrentSession().get(Field.class, fieldId);
	}

	/**
	 * @see org.openmrs.api.FormService#getAllFields(boolean)
	 * @see org.openmrs.api.db.FormDAO#getAllFields(boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Field> getAllFields(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM Field f");
		if (!includeRetired) {
			hql.append(" WHERE f.retired = false");
		}
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), Field.class).list();
	}

	/**
	 * @see org.openmrs.api.FormService#getFieldType(java.lang.Integer)
	 * @see org.openmrs.api.db.FormDAO#getFieldType(java.lang.Integer)
	 */
	@Override
	public FieldType getFieldType(Integer fieldTypeId) throws DAOException {
		return sessionFactory.getCurrentSession().get(FieldType.class, fieldTypeId);
	}

	/**
	 * @see org.openmrs.api.FormService#getAllFieldTypes()
	 * @see org.openmrs.api.db.FormDAO#getAllFieldTypes(boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<FieldType> getAllFieldTypes(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM FieldType ft");
		if (!includeRetired) {
			hql.append(" WHERE ft.retired = false");
		}
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), FieldType.class).list();
	}

	/**
	 * @see org.openmrs.api.FormService#getFormField(java.lang.Integer)
	 * @see org.openmrs.api.db.FormDAO#getFormField(java.lang.Integer)
	 */
	@Override
	public FormField getFormField(Integer formFieldId) throws DAOException {
		return sessionFactory.getCurrentSession().get(FormField.class, formFieldId);
	}

	/**
	 * @see org.openmrs.api.FormService#getFormField(org.openmrs.Form, org.openmrs.Concept,
	 *      java.util.Collection, boolean)
	 * @see org.openmrs.api.db.FormDAO#getFormField(org.openmrs.Form, org.openmrs.Concept,
	 *      java.util.Collection, boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public FormField getFormField(Form form, Concept concept, Collection<FormField> ignoreFormFields, boolean force)
	        throws DAOException {
		if (form == null) {
			log.debug("form is null, no fields will be matched");
			return null;
		}

		// get the list of all formfields with this concept for this form
		List<FormField> formFields = sessionFactory.getCurrentSession()
				.createQuery("FROM FormField ff WHERE ff.field.concept = :concept AND ff.form = :form", FormField.class)
				.setParameter("concept", concept)
				.setParameter("form", form)
				.list();

		String err = "FormField warning.  No FormField matching concept '" + concept + "' for form '" + form + "'";

		if (formFields.isEmpty()) {
			log.debug(err);
			return null;
		}

		// save the first formfield in case we're not a in a "force" situation
		FormField backupPlan = formFields.get(0);

		// remove the formfields we're supposed to ignore from the return list
		formFields.removeAll(ignoreFormFields);

		// if we ended up removing all of the formfields, check to see if we're
		// in a "force" situation
		if (formFields.isEmpty()) {
			if (!force) {
				return backupPlan;
			} else {
				log.debug(err);
				return null;
			}
		} else {
			// if formFields.size() is still greater than 0
			return formFields.get(0);
		}
	}

	/**
	 * @see org.openmrs.api.FormService#getAllForms()
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Form> getAllForms(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM Form f");
		if (!includeRetired) {
			hql.append(" WHERE f.retired = false");
		}
		hql.append(" ORDER BY f.name ASC, f.formId ASC");
		return sessionFactory.getCurrentSession().createQuery(hql.toString(), Form.class).list();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormsContainingConcept(org.openmrs.Concept)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Form> getFormsContainingConcept(Concept c) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("SELECT DISTINCT ff.form FROM FormField ff WHERE ff.field.concept = :concept", Form.class)
				.setParameter("concept", c)
				.list();
	}

	/**
	 * @see org.openmrs.api.FormService#saveField(org.openmrs.Field)
	 * @see org.openmrs.api.db.FormDAO#saveField(org.openmrs.Field)
	 */
	@Override
	public Field saveField(Field field) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(field);
		return field;
	}

	/**
	 * @see org.openmrs.api.FormService#purgeField(org.openmrs.Field)
	 * @see org.openmrs.api.db.FormDAO#deleteField(org.openmrs.Field)
	 */
	@Override
	public void deleteField(Field field) throws DAOException {
		sessionFactory.getCurrentSession().delete(field);
	}

	/**
	 * @see org.openmrs.api.FormService#saveFormField(org.openmrs.FormField)
	 */
	@Override
	public FormField saveFormField(FormField formField) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(formField);
		return formField;
	}

	/**
	 * @see org.openmrs.api.FormService#purgeFormField(org.openmrs.FormField)
	 * @see org.openmrs.api.db.FormDAO#deleteFormField(org.openmrs.FormField)
	 */
	@Override
	public void deleteFormField(FormField formField) throws DAOException {
		sessionFactory.getCurrentSession().delete(formField);
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getAllFormFields()
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<FormField> getAllFormFields() throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM FormField", FormField.class)
				.list();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFields(java.util.Collection, java.util.Collection,
	 *      java.util.Collection, java.util.Collection, java.util.Collection, java.lang.Boolean,
	 *      java.util.Collection, java.util.Collection, java.lang.Boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Field> getFields(Collection<Form> forms, Collection<FieldType> fieldTypes, Collection<Concept> concepts,
	        Collection<String> tableNames, Collection<String> attributeNames, Boolean selectMultiple,
	        Collection<FieldAnswer> containsAllAnswers, Collection<FieldAnswer> containsAnyAnswer, Boolean retired)
	        throws DAOException {

		StringBuilder hql = new StringBuilder("FROM Field f WHERE 1=1");

		if (!forms.isEmpty()) {
			hql.append(" AND f.form IN :forms");
		}
		if (!fieldTypes.isEmpty()) {
			hql.append(" AND f.fieldType IN :fieldTypes");
		}
		if (!concepts.isEmpty()) {
			hql.append(" AND f.concept IN :concepts");
		}
		if (!tableNames.isEmpty()) {
			hql.append(" AND f.tableName IN :tableNames");
		}
		if (!attributeNames.isEmpty()) {
			hql.append(" AND f.attributeName IN :attributeNames");
		}
		if (selectMultiple != null) {
			hql.append(" AND f.selectMultiple = :selectMultiple");
		}

		if (!containsAllAnswers.isEmpty()) {
			throw new APIException("Form.getFields.error", new Object[] { "containsAllAnswers" });
		}

		if (!containsAnyAnswer.isEmpty()) {
			throw new APIException("Form.getFields.error", new Object[] { "containsAnyAnswer" });
		}

		if (retired != null) {
			hql.append(" AND f.retired = :retired");
		}

		Query<Field> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Field.class);

		if (!forms.isEmpty()) {
			query.setParameterList("forms", forms);
		}
		if (!fieldTypes.isEmpty()) {
			query.setParameterList("fieldTypes", fieldTypes);
		}
		if (!concepts.isEmpty()) {
			query.setParameterList("concepts", concepts);
		}
		if (!tableNames.isEmpty()) {
			query.setParameterList("tableNames", tableNames);
		}
		if (!attributeNames.isEmpty()) {
			query.setParameterList("attributeNames", attributeNames);
		}
		if (selectMultiple != null) {
			query.setParameter("selectMultiple", selectMultiple);
		}
		if (retired != null) {
			query.setParameter("retired", retired);
		}

		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getForm(java.lang.String, java.lang.String)
	 */
	@Override
	public Form getForm(String name, String version) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Form f WHERE f.name = :name AND f.version = :version", Form.class)
				.setParameter("name", name)
				.setParameter("version", version)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getForms(java.lang.String, java.lang.Boolean,
	 *      java.util.Collection, java.lang.Boolean, java.util.Collection, java.util.Collection,
	 *      java.util.Collection)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Form> getForms(String partialName, Boolean published, Collection<EncounterType> encounterTypes,
	        Boolean retired, Collection<FormField> containingAnyFormField, Collection<FormField> containingAllFormFields,
	        Collection<Field> fields) throws DAOException {

		return getFormList(partialName, published, encounterTypes, retired, containingAnyFormField,
		    containingAllFormFields, fields);
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormCount(java.lang.String, java.lang.Boolean,
	 *      java.util.Collection, java.lang.Boolean, java.util.Collection, java.util.Collection,
	 *      java.util.Collection)
	 */
	@Override
	public Integer getFormCount(String partialName, Boolean published, Collection<EncounterType> encounterTypes,
	        Boolean retired, Collection<FormField> containingAnyFormField, Collection<FormField> containingAllFormFields,
	        Collection<Field> fields) throws DAOException {

		// Build count query using the same conditions
		StringBuilder hql = new StringBuilder("SELECT COUNT(DISTINCT f) FROM Form f");

		// JOIN for fields filter (must be in FROM clause before WHERE)
		if (!fields.isEmpty()) {
			hql.append(" JOIN f.formFields ff");
		}

		hql.append(" WHERE 1=1");
		buildFormConditions(hql, partialName, published, encounterTypes, retired, containingAnyFormField,
		    containingAllFormFields, fields);

		Query<Long> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Long.class);
		bindFormParameters(query, partialName, published, encounterTypes, retired, containingAnyFormField,
		    containingAllFormFields, fields);

		return OpenmrsUtil.convertToInteger(query.uniqueResult());
	}

	/**
	 * Fetches a list of Form objects matching the given criteria.
	 */
	private List<Form> getFormList(String partialName, Boolean published, Collection<EncounterType> encounterTypes,
	        Boolean retired, Collection<FormField> containingAnyFormField, Collection<FormField> containingAllFormFields,
	        Collection<Field> fields) {

		StringBuilder hql = new StringBuilder("SELECT DISTINCT f FROM Form f");

		// JOIN for fields filter (must be in FROM clause before WHERE)
		if (!fields.isEmpty()) {
			hql.append(" JOIN f.formFields ff");
		}

		hql.append(" WHERE 1=1");
		buildFormConditions(hql, partialName, published, encounterTypes, retired, containingAnyFormField,
		    containingAllFormFields, fields);

		Query<Form> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Form.class);
		bindFormParameters(query, partialName, published, encounterTypes, retired, containingAnyFormField,
		    containingAllFormFields, fields);

		return query.list();
	}

	/**
	 * Appends WHERE conditions for Form search to the provided HQL StringBuilder.
	 */
	private void buildFormConditions(StringBuilder hql, String partialName, Boolean published,
	        Collection<EncounterType> encounterTypes, Boolean retired, Collection<FormField> containingAnyFormField,
	        Collection<FormField> containingAllFormFields, Collection<Field> fields) {

		if (StringUtils.isNotEmpty(partialName)) {
			hql.append(" AND (f.name LIKE :nameStart OR f.name LIKE :nameAnywhere)");
		}
		if (published != null) {
			hql.append(" AND f.published = :published");
		}
		if (!encounterTypes.isEmpty()) {
			hql.append(" AND f.encounterType IN :encounterTypes");
		}
		if (retired != null) {
			hql.append(" AND f.retired = :retired");
		}

		// TODO junit test
		if (!containingAnyFormField.isEmpty()) {
			Set<Integer> anyFormFieldIds = new HashSet<>();
			for (FormField ff : containingAnyFormField) {
				anyFormFieldIds.add(ff.getFormFieldId());
			}
			hql.append(" AND f.formId IN ("
					+ "SELECT ff2.form.formId FROM FormField ff2 WHERE ff2.formFieldId IN :anyFormFieldIds"
					+ ")");
		}

		if (!containingAllFormFields.isEmpty()) {
			Set<Integer> allFormFieldIds = new HashSet<>();
			for (FormField ff : containingAllFormFields) {
				allFormFieldIds.add(ff.getFormFieldId());
			}
			hql.append(" AND ("
					+ "SELECT COUNT(DISTINCT ff3.formFieldId) FROM FormField ff3"
					+ " WHERE ff3.form.formId = f.formId AND ff3.formFieldId IN :allFormFieldIds"
					+ ") = :allFormFieldCount");
		}

		// join for fields filter was added in FROM clause; add WHERE condition here
		if (!fields.isEmpty()) {
			hql.append(" AND ff.field IN :fields");
		}
	}

	/**
	 * Binds parameter values to a query for Form search.
	 */
	private void bindFormParameters(Query<?> query, String partialName, Boolean published,
	        Collection<EncounterType> encounterTypes, Boolean retired, Collection<FormField> containingAnyFormField,
	        Collection<FormField> containingAllFormFields, Collection<Field> fields) {

		if (StringUtils.isNotEmpty(partialName)) {
			query.setParameter("nameStart", partialName + "%");
			query.setParameter("nameAnywhere", "% " + partialName + "%");
		}
		if (published != null) {
			query.setParameter("published", published);
		}
		if (!encounterTypes.isEmpty()) {
			query.setParameterList("encounterTypes", encounterTypes);
		}
		if (retired != null) {
			query.setParameter("retired", retired);
		}
		if (!containingAnyFormField.isEmpty()) {
			Set<Integer> anyFormFieldIds = new HashSet<>();
			for (FormField ff : containingAnyFormField) {
				anyFormFieldIds.add(ff.getFormFieldId());
			}
			query.setParameterList("anyFormFieldIds", anyFormFieldIds);
		}
		if (!containingAllFormFields.isEmpty()) {
			Set<Integer> allFormFieldIds = new HashSet<>();
			for (FormField ff : containingAllFormFields) {
				allFormFieldIds.add(ff.getFormFieldId());
			}
			query.setParameterList("allFormFieldIds", allFormFieldIds);
			query.setParameter("allFormFieldCount", (long) containingAllFormFields.size());
		}
		if (!fields.isEmpty()) {
			query.setParameterList("fields", fields);
		}
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFieldByUuid(java.lang.String)
	 */
	@Override
	public Field getFieldByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Field f WHERE f.uuid = :uuid", Field.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	@Override
	public FieldAnswer getFieldAnswerByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM FieldAnswer f WHERE f.uuid = :uuid", FieldAnswer.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFieldTypeByUuid(java.lang.String)
	 */
	@Override
	public FieldType getFieldTypeByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM FieldType ft WHERE ft.uuid = :uuid", FieldType.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFieldTypeByName(java.lang.String)
	 */
	@Override
	public FieldType getFieldTypeByName(String name) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM FieldType ft WHERE ft.name = :name", FieldType.class)
				.setParameter("name", name)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormByUuid(java.lang.String)
	 */
	@Override
	public Form getFormByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Form f WHERE f.uuid = :uuid", Form.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormFieldByUuid(java.lang.String)
	 */
	@Override
	public FormField getFormFieldByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM FormField ff WHERE ff.uuid = :uuid", FormField.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormsByName(java.lang.String)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Form> getFormsByName(String name) throws DAOException {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM Form f WHERE f.name = :name AND f.retired = false ORDER BY f.version DESC", Form.class)
				.setParameter("name", name)
				.list();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#deleteFieldType(org.openmrs.FieldType)
	 */
	@Override
	public void deleteFieldType(FieldType fieldType) throws DAOException {
		sessionFactory.getCurrentSession().delete(fieldType);
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#saveFieldType(org.openmrs.FieldType)
	 */
	@Override
	public FieldType saveFieldType(FieldType fieldType) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(fieldType);
		return fieldType;
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormFieldsByField(Field)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<FormField> getFormFieldsByField(Field field) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM FormField f WHERE f.field = :field", FormField.class)
				.setParameter("field", field)
				.list();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormResource(java.lang.Integer)
	 */
	@Override
	public FormResource getFormResource(Integer formResourceId) {
		return sessionFactory.getCurrentSession().get(FormResource.class, formResourceId);
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormResourceByUuid(java.lang.String)
	 */
	@Override
	public FormResource getFormResourceByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM FormResource r WHERE r.uuid = :uuid", FormResource.class)
				.setParameter("uuid", uuid)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormResource(org.openmrs.Form, java.lang.String)
	 */
	@Override
	public FormResource getFormResource(Form form, String name) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM FormResource r WHERE r.form = :form AND r.name = :name", FormResource.class)
				.setParameter("form", form)
				.setParameter("name", name)
				.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#saveFormResource(org.openmrs.FormResource)
	 */
	@Override
	public FormResource saveFormResource(FormResource formResource) {
		sessionFactory.getCurrentSession().saveOrUpdate(formResource);
		return formResource;
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#deleteFormResource(org.openmrs.FormResource)
	 */
	@Override
	public void deleteFormResource(FormResource formResource) {
		sessionFactory.getCurrentSession().delete(formResource);
	}

	/**
	 * @see org.openmrs.api.db.FormDAO#getFormResourcesForForm(org.openmrs.Form)
	 */
	@Override
	public Collection<FormResource> getFormResourcesForForm(Form form) {
		return sessionFactory.getCurrentSession()
				.createQuery("FROM FormResource r WHERE r.form = :form", FormResource.class)
				.setParameter("form", form)
				.list();
	}

}
