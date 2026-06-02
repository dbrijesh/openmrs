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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.FlushMode;
import org.hibernate.SessionFactory;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.openmrs.Concept;
import org.openmrs.ConceptAnswer;
import org.openmrs.ConceptAttribute;
import org.openmrs.ConceptAttributeType;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptComplex;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptDescription;
import org.openmrs.ConceptMap;
import org.openmrs.ConceptMapType;
import org.openmrs.ConceptName;
import org.openmrs.ConceptNameTag;
import org.openmrs.ConceptNumeric;
import org.openmrs.ConceptProposal;
import org.openmrs.ConceptReferenceTerm;
import org.openmrs.ConceptReferenceTermMap;
import org.openmrs.ConceptSearchResult;
import org.openmrs.ConceptSet;
import org.openmrs.ConceptSource;
import org.openmrs.ConceptStopWord;
import org.openmrs.Drug;
import org.openmrs.DrugIngredient;
import org.openmrs.OpenmrsObject;
import org.openmrs.api.APIException;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ConceptDAO;
import org.openmrs.api.db.DAOException;
import org.openmrs.api.db.hibernate.search.LuceneQuery;
import org.openmrs.collection.ListPart;
import org.openmrs.util.ConceptMapTypeComparator;
import org.openmrs.util.OpenmrsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Hibernate class for Concepts, Drugs, and related classes. <br>
 * <br>
 * Use the {@link ConceptService} to access these methods
 *
 * @see ConceptService
 */
public class HibernateConceptDAO implements ConceptDAO {

	private static final Logger log = LoggerFactory.getLogger(HibernateConceptDAO.class);

	private SessionFactory sessionFactory;

	/**
	 * Sets the session factory
	 *
	 * @param sessionFactory
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptComplex(java.lang.Integer)
	 */
	@Override
	public ConceptComplex getConceptComplex(Integer conceptId) {
		ConceptComplex cc;
		Object obj = sessionFactory.getCurrentSession().get(ConceptComplex.class, conceptId);
		// If Concept has already been read & cached, we may get back a Concept instead of
		// ConceptComplex.  If this happens, we need to clear the object from the cache
		// and re-fetch it as a ConceptComplex
		if (obj != null && !obj.getClass().equals(ConceptComplex.class)) {
			// remove from cache
			sessionFactory.getCurrentSession().evict(obj);
			// session.get() did not work here, we need to perform a query to get a ConceptComplex
			Query<ConceptComplex> query = sessionFactory.getCurrentSession()
			        .createQuery("from ConceptComplex where conceptId = :conceptId", ConceptComplex.class)
			        .setParameter("conceptId", conceptId);
			obj = query.uniqueResult();
		}
		cc = (ConceptComplex) obj;

		return cc;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConcept(org.openmrs.Concept)
	 */
	@Override
	public Concept saveConcept(Concept concept) throws DAOException {
		if ((concept.getConceptId() != null) && (concept.getConceptId() > 0)) {
			// this method checks the concept_numeric, concept_derived, etc tables
			// to see if a row exists there or not.  This is needed because hibernate
			// doesn't like to insert into concept_numeric but update concept in the
			// same go.  It assumes that its either in both tables or no tables
			insertRowIntoSubclassIfNecessary(concept);
		}

		sessionFactory.getCurrentSession().saveOrUpdate(concept);
		return concept;
	}

	/**
	 * Convenience method that will check this concept for subtype values (ConceptNumeric,
	 * ConceptDerived, etc) and insert a line into that subtable if needed. This prevents a
	 * hibernate ConstraintViolationException
	 *
	 * @param concept the concept that will be inserted
	 */
	private void insertRowIntoSubclassIfNecessary(Concept concept) {

		// check the concept_numeric table
		if (concept instanceof ConceptNumeric) {

			String select = "SELECT 1 from concept_numeric WHERE concept_id = :conceptId";
			NativeQuery<?> query = sessionFactory.getCurrentSession().createNativeQuery(select);
			query.setParameter("conceptId", concept.getConceptId());

			// Converting to concept numeric:  A single concept row exists, but concept numeric has not been populated yet.
			if (query.uniqueResult() == null) {
				// we have to evict the current concept out of the session because
				// the user probably had to change the class of this object to get it
				// to now be a numeric
				// (must be done before the "insert into...")
				sessionFactory.getCurrentSession().clear();

				//Just in case this was changed from concept_complex to numeric
				//We need to add a delete line for each concept sub class that is not concept_numeric
				deleteSubclassConcept("concept_complex", concept.getConceptId());

				String insert = "INSERT INTO concept_numeric (concept_id, allow_decimal) VALUES (:conceptId, false)";
				query = sessionFactory.getCurrentSession().createNativeQuery(insert);
				query.setParameter("conceptId", concept.getConceptId());
				query.executeUpdate();

			} else {
				// Converting from concept numeric:  The concept and concept numeric rows both exist, so we need to delete concept_numeric.

				// concept is changed from numeric to something else
				// hence row should be deleted from the concept_numeric
				if (!concept.isNumeric()) {
					deleteSubclassConcept("concept_numeric", concept.getConceptId());
				}
			}
		}
		// check the concept complex table
		else if (concept instanceof ConceptComplex) {

			String select = "SELECT 1 FROM concept_complex WHERE concept_id = :conceptId";
			NativeQuery<?> query = sessionFactory.getCurrentSession().createNativeQuery(select);
			query.setParameter("conceptId", concept.getConceptId());

			// Converting to concept complex:  A single concept row exists, but concept complex has not been populated yet.
			if (query.uniqueResult() == null) {
				// we have to evict the current concept out of the session because
				// the user probably had to change the class of this object to get it
				// to now be a ConceptComplex
				// (must be done before the "insert into...")
				sessionFactory.getCurrentSession().clear();

				//Just in case this was changed from concept_numeric to complex
				//We need to add a delete line for each concept sub class that is not concept_complex
				deleteSubclassConcept("concept_numeric", concept.getConceptId());

				// Add an empty row into the concept_complex table
				String insert = "INSERT INTO concept_complex (concept_id) VALUES (:conceptId)";
				query = sessionFactory.getCurrentSession().createNativeQuery(insert);
				query.setParameter("conceptId", concept.getConceptId());
				query.executeUpdate();

			} else {
				// Converting from concept complex:  The concept and concept complex rows both exist, so we need to delete the concept_complex row.
				// no stub insert is needed because either a concept row doesn't exist OR a concept_complex row does exist

				// concept is changed from complex to something else
				// hence row should be deleted from the concept_complex
				if (!concept.isComplex()) {
					deleteSubclassConcept("concept_complex", concept.getConceptId());
				}
			}
		}
	}

	/**
	 * Deletes a concept from a sub class table
	 *
	 * @param tableName the sub class table name
	 * @param conceptId the concept id
	 */
	private void deleteSubclassConcept(String tableName, Integer conceptId) {
		String delete = "DELETE FROM " + tableName + " WHERE concept_id = :conceptId";
		NativeQuery<?> query = sessionFactory.getCurrentSession().createNativeQuery(delete);
		query.setParameter("conceptId", conceptId);
		query.executeUpdate();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeConcept(org.openmrs.Concept)
	 */
	@Override
	public void purgeConcept(Concept concept) throws DAOException {
		sessionFactory.getCurrentSession().delete(concept);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConcept(java.lang.Integer)
	 */
	@Override
	public Concept getConcept(Integer conceptId) throws DAOException {
		return (Concept) sessionFactory.getCurrentSession().get(Concept.class, conceptId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptName(java.lang.Integer)
	 */
	@Override
	public ConceptName getConceptName(Integer conceptNameId) throws DAOException {
		return (ConceptName) sessionFactory.getCurrentSession().get(ConceptName.class, conceptNameId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptAnswer(java.lang.Integer)
	 */
	@Override
	public ConceptAnswer getConceptAnswer(Integer conceptAnswerId) throws DAOException {
		return (ConceptAnswer) sessionFactory.getCurrentSession().get(ConceptAnswer.class, conceptAnswerId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConcepts(java.lang.String, boolean, boolean)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public List<Concept> getAllConcepts(String sortBy, boolean asc, boolean includeRetired) throws DAOException {

		boolean isNameField = false;

		try {
			Concept.class.getDeclaredField(sortBy);
		}
		catch (NoSuchFieldException e) {
			try {
				ConceptName.class.getDeclaredField(sortBy);
				isNameField = true;
			}
			catch (NoSuchFieldException e2) {
				sortBy = "conceptId";
			}
		}

		String hql = "";
		if (isNameField) {
			hql += "select concept";
		}

		hql += " from Concept as concept";
		boolean hasWhereClause = false;
		if (isNameField) {
			hasWhereClause = true;
			//This assumes every concept has a unique(avoid duplicates) fully specified name
			//which should be true for a clean concept dictionary
			hql += " left join concept.names as names where names.conceptNameType = 'FULLY_SPECIFIED'";
		}

		if (!includeRetired) {
			if (hasWhereClause) {
				hql += " and";
			} else {
				hql += " where";
			}
			hql += " concept.retired = false";

		}

		if (isNameField) {
			hql += " order by names." + sortBy;
		} else {
			hql += " order by concept." + sortBy;
		}

		hql += asc ? " asc" : " desc";
		Query<Concept> query = sessionFactory.getCurrentSession().createQuery(hql, Concept.class);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveDrug(org.openmrs.Drug)
	 */
	@Override
	public Drug saveDrug(Drug drug) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(drug);
		return drug;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrug(java.lang.Integer)
	 */
	@Override
	public Drug getDrug(Integer drugId) throws DAOException {
		return (Drug) sessionFactory.getCurrentSession().get(Drug.class, drugId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugs(java.lang.String, org.openmrs.Concept, boolean)
	 */
	@Override
	public List<Drug> getDrugs(String drugName, Concept concept, boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM Drug drug WHERE 1=1");
		if (!includeRetired) {
			hql.append(" AND drug.retired = false");
		}
		if (concept != null) {
			hql.append(" AND drug.concept = :concept");
		}
		if (drugName != null) {
			if (Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive()) {
				hql.append(" AND lower(drug.name) = lower(:drugName)");
			} else {
				hql.append(" AND drug.name = :drugName");
			}
		}
		Query<Drug> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Drug.class);
		if (concept != null) {
			query.setParameter("concept", concept);
		}
		if (drugName != null) {
			query.setParameter("drugName", drugName);
		}
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugsByIngredient(org.openmrs.Concept)
	 */
	@Override
	public List<Drug> getDrugsByIngredient(Concept ingredient) {
		String hql = "SELECT DISTINCT drug FROM Drug drug LEFT JOIN drug.ingredients ingredients "
		        + "WHERE drug.concept = :ingredient OR ingredients.ingredient = :ingredient";
		Query<Drug> query = sessionFactory.getCurrentSession().createQuery(hql, Drug.class);
		query.setParameter("ingredient", ingredient);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugs(java.lang.String)
	 */
	@Override
	public List<Drug> getDrugs(final String phrase) throws DAOException {
		LuceneQuery<Drug> drugQuery = newDrugQuery(phrase, true, false, Context.getLocale(), false, null, false);

		if (drugQuery == null) {
			return Collections.emptyList();
		}

		return drugQuery.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptClass(java.lang.Integer)
	 */
	@Override
	public ConceptClass getConceptClass(Integer i) throws DAOException {
		return (ConceptClass) sessionFactory.getCurrentSession().get(ConceptClass.class, i);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptClasses(java.lang.String)
	 */
	@Override
	public List<ConceptClass> getConceptClasses(String name) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM ConceptClass cc WHERE 1=1");
		if (name != null) {
			hql.append(" AND cc.name = :name");
		}
		Query<ConceptClass> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), ConceptClass.class);
		if (name != null) {
			query.setParameter("name", name);
		}
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptClasses(boolean)
	 */
	@Override
	public List<ConceptClass> getAllConceptClasses(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM ConceptClass cc WHERE 1=1");
		if (!includeRetired) {
			hql.append(" AND cc.retired = false");
		}
		Query<ConceptClass> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), ConceptClass.class);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptClass(org.openmrs.ConceptClass)
	 */
	@Override
	public ConceptClass saveConceptClass(ConceptClass cc) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(cc);
		return cc;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeConceptClass(org.openmrs.ConceptClass)
	 */
	@Override
	public void purgeConceptClass(ConceptClass cc) throws DAOException {
		sessionFactory.getCurrentSession().delete(cc);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptNameTag(ConceptNameTag)
	 */
	@Override
	public void deleteConceptNameTag(ConceptNameTag cnt) throws DAOException {
		sessionFactory.getCurrentSession().delete(cnt);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptDatatype(java.lang.Integer)
	 */
	@Override
	public ConceptDatatype getConceptDatatype(Integer i) {
		return (ConceptDatatype) sessionFactory.getCurrentSession().get(ConceptDatatype.class, i);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptDatatypes(boolean)
	 */
	@Override
	public List<ConceptDatatype> getAllConceptDatatypes(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM ConceptDatatype cd WHERE 1=1");
		if (!includeRetired) {
			hql.append(" AND cd.retired = false");
		}
		Query<ConceptDatatype> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), ConceptDatatype.class);
		return query.list();
	}

	/**
	 * @param name the name of the ConceptDatatype
	 * @return a List of ConceptDatatype whose names start with the passed name
	 */
	public List<ConceptDatatype> getConceptDatatypes(String name) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM ConceptDatatype cd WHERE 1=1");
		if (name != null) {
			hql.append(" AND cd.name like :name");
		}
		Query<ConceptDatatype> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), ConceptDatatype.class);
		if (name != null) {
			query.setParameter("name", name + "%");
		}
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptDatatypeByName(String)
	 */
	@Override
	public ConceptDatatype getConceptDatatypeByName(String name) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM ConceptDatatype cd WHERE 1=1");
		if (name != null) {
			hql.append(" AND cd.name = :name");
		}
		Query<ConceptDatatype> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), ConceptDatatype.class);
		if (name != null) {
			query.setParameter("name", name);
		}
		return query.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptDatatype(org.openmrs.ConceptDatatype)
	 */
	@Override
	public ConceptDatatype saveConceptDatatype(ConceptDatatype cd) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(cd);
		return cd;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeConceptDatatype(org.openmrs.ConceptDatatype)
	 */
	@Override
	public void purgeConceptDatatype(ConceptDatatype cd) throws DAOException {
		sessionFactory.getCurrentSession().delete(cd);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptNumeric(java.lang.Integer)
	 */
	@Override
	public ConceptNumeric getConceptNumeric(Integer i) {
		ConceptNumeric cn;
		Object obj = sessionFactory.getCurrentSession().get(ConceptNumeric.class, i);
		// If Concept has already been read & cached, we may get back a Concept instead of
		// ConceptNumeric.  If this happens, we need to clear the object from the cache
		// and re-fetch it as a ConceptNumeric
		if (obj != null && !obj.getClass().equals(ConceptNumeric.class)) {
			// remove from cache
			sessionFactory.getCurrentSession().evict(obj);
			// session.get() did not work here, we need to perform a query to get a ConceptNumeric
			Query<ConceptNumeric> query = sessionFactory.getCurrentSession()
			        .createQuery("from ConceptNumeric where conceptId = :conceptId", ConceptNumeric.class)
			        .setParameter("conceptId", i);
			obj = query.uniqueResult();
		}
		cn = (ConceptNumeric) obj;

		return cn;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConcepts(java.lang.String, java.util.Locale, boolean,
	 *      java.util.List, java.util.List)
	 */
	@Override
	public List<Concept> getConcepts(final String name, final Locale loc, final boolean searchOnPhrase,
	        final List<ConceptClass> classes, final List<ConceptDatatype> datatypes) throws DAOException {

		final Locale locale;
		if (loc == null) {
			locale = Context.getLocale();
		} else {
			locale = loc;
		}

		LuceneQuery<ConceptName> conceptNameQuery = newConceptNameLuceneQuery(name, !searchOnPhrase,
				Collections.singletonList(locale),
		    false, false, classes, null, datatypes, null, null);

		List<ConceptName> names = conceptNameQuery.list();

		return new ArrayList<>(transformNamesToConcepts(names));
	}

	private LinkedHashSet<Concept> transformNamesToConcepts(List<ConceptName> names) {
		LinkedHashSet<Concept> concepts = new LinkedHashSet<>();

		for (ConceptName name : names) {
			concepts.add(name.getConcept());
		}

		return concepts;
	}

	private String newConceptNameQuery(final String name, final boolean searchKeywords, final Set<Locale> locales,
	        final boolean searchExactLocale) {
		final String escapedName = LuceneQuery.escapeQuery(name).replace("AND", "and").replace("OR", "or").replace("NOT", "not");
		final List<String> tokenizedName = tokenizeConceptName(escapedName, locales);

		final StringBuilder query = new StringBuilder();

		query.append("(concept.conceptMappings.conceptReferenceTerm.code:(").append(escapedName).append(")^0.4 OR (");
		final StringBuilder nameQuery = newNameQuery(tokenizedName, escapedName, searchKeywords);
		query.append(nameQuery);
		query.append(" localePreferred:true)^0.4 OR (");
		query.append(nameQuery);
		query.append(")^0.2)");

		List<String> localeQueries = new ArrayList<>();
		for (Locale locale : locales) {
			if (searchExactLocale) {
				localeQueries.add(locale.toString());
			} else {
				String localeQuery = locale.getLanguage() + "* ";
				if (!StringUtils.isBlank(locale.getCountry())) {
					localeQuery += " OR " + locale + "^2 ";
				}
				localeQueries.add(localeQuery);
			}
		}
		query.append(" locale:(");
		query.append(StringUtils.join(localeQueries, " OR "));
		query.append(")");
		query.append(" voided:false");

		return query.toString();
	}

	private StringBuilder newNameQuery(final List<String> tokenizedName, final String escapedName,
	        final boolean searchKeywords) {
		final StringBuilder query = new StringBuilder();
		query.append("(");
		if (searchKeywords) {
			//Put exact phrase higher
			query.append(" name:(\"").append(escapedName).append("\")^0.7");

			if (!tokenizedName.isEmpty()) {
				query.append(" OR (");
				for (String token : tokenizedName) {
					query.append(" (name:(");

					//Include exact
					query.append(token);
					query.append(")^0.6 OR name:(");

					//Include partial
					query.append(token);
					query.append("*)^0.3 OR name:(");

					//Include similar
					query.append(token);
					query.append("~0.8)^0.1)");
				}
				query.append(")^0.3");
			}
		} else {
			query.append(" name:\"").append(escapedName).append("\"");
		}
		query.append(")");
		return query;
	}

	private List<String> tokenizeConceptName(final String escapedName, final Set<Locale> locales) {
		List<String> words = new ArrayList<>(Arrays.asList(escapedName.trim().split(" ")));

		Set<String> stopWords = new HashSet<>();
		for (Locale locale : locales) {
			stopWords.addAll(Context.getConceptService().getConceptStopWords(locale));
		}

		List<String> tokenizedName = new ArrayList<>();

		for (String word : words) {
			word = word.trim();

			if (!word.isEmpty() && !stopWords.contains(word.toUpperCase())) {
				tokenizedName.add(word);
			}
		}

		return tokenizedName;
	}

	/**
	 * gets questions for the given answer concept
	 *
	 * @see org.openmrs.api.db.ConceptDAO#getConceptsByAnswer(org.openmrs.Concept)
	 */
	@Override
	public List<Concept> getConceptsByAnswer(Concept concept) {
		String q = "select c from Concept c join c.answers ca where ca.answerConcept = :answer";
		Query<Concept> query = sessionFactory.getCurrentSession().createQuery(q, Concept.class);
		query.setParameter("answer", concept);

		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getPrevConcept(org.openmrs.Concept)
	 */
	@Override
	public Concept getPrevConcept(Concept c) {
		Integer i = c.getConceptId();

		List<Concept> concepts = sessionFactory.getCurrentSession()
		        .createQuery("FROM Concept c WHERE c.conceptId < :conceptId ORDER BY c.conceptId DESC", Concept.class)
		        .setParameter("conceptId", i)
		        .setMaxResults(1)
		        .list();

		if (concepts.isEmpty()) {
			return null;
		}
		return concepts.get(0);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getNextConcept(org.openmrs.Concept)
	 */
	@Override
	public Concept getNextConcept(Concept c) {
		Integer i = c.getConceptId();

		List<Concept> concepts = sessionFactory.getCurrentSession()
		        .createQuery("FROM Concept c WHERE c.conceptId > :conceptId ORDER BY c.conceptId ASC", Concept.class)
		        .setParameter("conceptId", i)
		        .setMaxResults(1)
		        .list();

		if (concepts.isEmpty()) {
			return null;
		}
		return concepts.get(0);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptsWithDrugsInFormulary()
	 */
	@Override
	public List<Concept> getConceptsWithDrugsInFormulary() {
		Query<Concept> query = sessionFactory.getCurrentSession().createQuery(
		    "select distinct concept from Drug d where d.retired = false", Concept.class);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeDrug(org.openmrs.Drug)
	 */
	@Override
	public void purgeDrug(Drug drug) throws DAOException {
		sessionFactory.getCurrentSession().delete(drug);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptProposal(org.openmrs.ConceptProposal)
	 */
	@Override
	public ConceptProposal saveConceptProposal(ConceptProposal cp) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(cp);
		return cp;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#purgeConceptProposal(org.openmrs.ConceptProposal)
	 */
	@Override
	public void purgeConceptProposal(ConceptProposal cp) throws DAOException {
		sessionFactory.getCurrentSession().delete(cp);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptProposals(boolean)
	 */
	@Override
	public List<ConceptProposal> getAllConceptProposals(boolean includeCompleted) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM ConceptProposal cp WHERE 1=1");
		if (!includeCompleted) {
			hql.append(" AND cp.state = :state");
		}
		hql.append(" ORDER BY cp.originalText ASC");
		Query<ConceptProposal> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), ConceptProposal.class);
		if (!includeCompleted) {
			query.setParameter("state", OpenmrsConstants.CONCEPT_PROPOSAL_UNMAPPED);
		}
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptProposal(java.lang.Integer)
	 */
	@Override
	public ConceptProposal getConceptProposal(Integer conceptProposalId) throws DAOException {
		return (ConceptProposal) sessionFactory.getCurrentSession().get(ConceptProposal.class, conceptProposalId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptProposals(java.lang.String)
	 */
	@Override
	public List<ConceptProposal> getConceptProposals(String text) throws DAOException {
		Query<ConceptProposal> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptProposal cp WHERE cp.state = :state AND cp.originalText = :text", ConceptProposal.class);
		query.setParameter("state", OpenmrsConstants.CONCEPT_PROPOSAL_UNMAPPED);
		query.setParameter("text", text);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getProposedConcepts(java.lang.String)
	 */
	@Override
	public List<Concept> getProposedConcepts(String text) throws DAOException {
		Query<Concept> query = sessionFactory.getCurrentSession().createQuery(
		    "SELECT DISTINCT cp.mappedConcept FROM ConceptProposal cp WHERE cp.state != :state AND cp.originalText = :text AND cp.mappedConcept IS NOT NULL",
		    Concept.class);
		query.setParameter("state", OpenmrsConstants.CONCEPT_PROPOSAL_UNMAPPED);
		query.setParameter("text", text);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptSetsByConcept(org.openmrs.Concept)
	 */
	@Override
	public List<ConceptSet> getConceptSetsByConcept(Concept concept) {
		Query<ConceptSet> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptSet cs WHERE cs.conceptSet = :concept ORDER BY cs.sortWeight ASC", ConceptSet.class);
		query.setParameter("concept", concept);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getSetsContainingConcept(org.openmrs.Concept)
	 */
	@Override
	public List<ConceptSet> getSetsContainingConcept(Concept concept) {
		Query<ConceptSet> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptSet cs WHERE cs.concept = :concept", ConceptSet.class);
		query.setParameter("concept", concept);
		return query.list();
	}

	/**
	 * returns a list of n-generations of parents of a concept in a concept set
	 *
	 * @param current
	 * @return List&lt;Concept&gt;
	 * @throws DAOException
	 */
	private List<Concept> getParents(Concept current) throws DAOException {
		List<Concept> parents = new ArrayList<>();
		if (current != null) {
			Query<Concept> query = sessionFactory.getCurrentSession().createQuery(
			    "FROM Concept c JOIN c.conceptSets sets WHERE sets.concept = :current", Concept.class);
			query.setParameter("current", current);
			List<Concept> immedParents = query.list();
			for (Concept c : immedParents) {
				parents.addAll(getParents(c));
			}
			parents.add(current);
			if (log.isDebugEnabled()) {
				log.debug("parents found: ");
				for (Concept c : parents) {
					log.debug("id: {}", c.getConceptId());
				}
			}
		}
		return parents;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getLocalesOfConceptNames()
	 */
	@Override
	public Set<Locale> getLocalesOfConceptNames() {
		Set<Locale> locales = new HashSet<>();

		Query<Locale> query = sessionFactory.getCurrentSession().createQuery(
		    "select distinct locale from ConceptName", Locale.class);

		for (Locale locale : query.list()) {
			locales.add(locale);
		}

		return locales;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptNameTag(java.lang.Integer)
	 */
	@Override
	public ConceptNameTag getConceptNameTag(Integer i) {
		return (ConceptNameTag) sessionFactory.getCurrentSession().get(ConceptNameTag.class, i);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptNameTagByName(java.lang.String)
	 */
	@Override
	public ConceptNameTag getConceptNameTagByName(String name) {
		List<ConceptNameTag> list = sessionFactory.getCurrentSession()
		        .createQuery("FROM ConceptNameTag cnt WHERE cnt.tag = :tag", ConceptNameTag.class)
		        .setParameter("tag", name)
		        .list();

		if (list.isEmpty()) {
			return null;
		}

		return list.get(0);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptNameTags()
	 */
	@Override
	public List<ConceptNameTag> getAllConceptNameTags() {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptNameTag cnt order by cnt.tag", ConceptNameTag.class).list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptSource(java.lang.Integer)
	 */
	@Override
	public ConceptSource getConceptSource(Integer conceptSourceId) {
		return (ConceptSource) sessionFactory.getCurrentSession().get(ConceptSource.class, conceptSourceId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptSources(boolean)
	 */
	@Override
	public List<ConceptSource> getAllConceptSources(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM ConceptSource cs WHERE 1=1");
		if (!includeRetired) {
			hql.append(" AND cs.retired = false");
		}
		Query<ConceptSource> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), ConceptSource.class);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptSource(org.openmrs.ConceptSource)
	 */
	@Override
	public ConceptSource deleteConceptSource(ConceptSource cs) throws DAOException {
		sessionFactory.getCurrentSession().delete(cs);
		return cs;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptSource(org.openmrs.ConceptSource)
	 */
	@Override
	public ConceptSource saveConceptSource(ConceptSource conceptSource) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(conceptSource);
		return conceptSource;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptNameTag(org.openmrs.ConceptNameTag)
	 */
	@Override
	public ConceptNameTag saveConceptNameTag(ConceptNameTag nameTag) {
		if (nameTag == null) {
			return null;
		}

		sessionFactory.getCurrentSession().saveOrUpdate(nameTag);
		return nameTag;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getMaxConceptId()
	 */
	public Integer getMinConceptId() {
		Query<Integer> query = sessionFactory.getCurrentSession().createQuery(
		    "select min(conceptId) from Concept", Integer.class);
		return query.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getMaxConceptId()
	 */
	@Override
	public Integer getMaxConceptId() {
		Query<Integer> query = sessionFactory.getCurrentSession().createQuery(
		    "select max(conceptId) from Concept", Integer.class);
		return query.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#conceptIterator()
	 */
	@Override
	public Iterator<Concept> conceptIterator() {
		return new ConceptIterator();
	}

	/**
	 * An iterator that loops over all concepts in the dictionary one at a time
	 */
	private class ConceptIterator implements Iterator<Concept> {

		Concept currentConcept = null;

		Concept nextConcept;

		public ConceptIterator() {
			final int firstConceptId = getMinConceptId();
			nextConcept = getConcept(firstConceptId);
		}

		/**
		 * @see java.util.Iterator#hasNext()
		 */
		@Override
		public boolean hasNext() {
			return nextConcept != null;
		}

		/**
		 * @see java.util.Iterator#next()
		 */
		@Override
		public Concept next() {
			if (currentConcept != null) {
				sessionFactory.getCurrentSession().evict(currentConcept);
			}

			currentConcept = nextConcept;
			nextConcept = getNextConcept(currentConcept);

			return currentConcept;
		}

		/**
		 * @see java.util.Iterator#remove()
		 */
		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}

	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptsByMapping(String, String, boolean)
	 */
	@Override
	@Deprecated
	public List<Concept> getConceptsByMapping(String code, String sourceName, boolean includeRetired) {
		List<Concept> concepts = createSearchConceptMapQuery(code, sourceName, includeRetired, Concept.class);
		return concepts.stream().distinct().collect(Collectors.toList());
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptIdsByMapping(String, String, boolean)
	 */
	@Override
	public List<Integer> getConceptIdsByMapping(String code, String sourceName, boolean includeRetired) {
		List<Integer> conceptIds = createSearchConceptMapQuery(code, sourceName, includeRetired, Integer.class);
		return conceptIds.stream().distinct().collect(Collectors.toList());
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptByUuid(java.lang.String)
	 */
	@Override
	public Concept getConceptByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Concept c where c.uuid = :uuid", Concept.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptClassByUuid(java.lang.String)
	 */
	@Override
	public ConceptClass getConceptClassByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptClass cc where cc.uuid = :uuid", ConceptClass.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	@Override
	public ConceptAnswer getConceptAnswerByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptAnswer cc where cc.uuid = :uuid", ConceptAnswer.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	@Override
	public ConceptName getConceptNameByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptName cc where cc.uuid = :uuid", ConceptName.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	@Override
	public ConceptSet getConceptSetByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptSet cc where cc.uuid = :uuid", ConceptSet.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	@Override
	public ConceptSource getConceptSourceByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptSource cc where cc.uuid = :uuid", ConceptSource.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptDatatypeByUuid(java.lang.String)
	 */
	@Override
	public ConceptDatatype getConceptDatatypeByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptDatatype cd where cd.uuid = :uuid", ConceptDatatype.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptNumericByUuid(java.lang.String)
	 */
	@Override
	public ConceptNumeric getConceptNumericByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptNumeric cn where cn.uuid = :uuid", ConceptNumeric.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptProposalByUuid(java.lang.String)
	 */
	@Override
	public ConceptProposal getConceptProposalByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptProposal cp where cp.uuid = :uuid", ConceptProposal.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugByUuid(java.lang.String)
	 */
	@Override
	public Drug getDrugByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from Drug d where d.uuid = :uuid", Drug.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	@Override
	public DrugIngredient getDrugIngredientByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from DrugIngredient d where d.uuid = :uuid", DrugIngredient.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptUuids()
	 */
	@Override
	public Map<Integer, String> getConceptUuids() {
		Map<Integer, String> ret = new HashMap<>();
		Query<Object[]> q = sessionFactory.getCurrentSession().createQuery(
		    "select conceptId, uuid from Concept", Object[].class);
		List<Object[]> list = q.list();
		for (Object[] o : list) {
			ret.put((Integer) o[0], (String) o[1]);
		}
		return ret;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptDescriptionByUuid(java.lang.String)
	 */
	@Override
	public ConceptDescription getConceptDescriptionByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptDescription cd where cd.uuid = :uuid", ConceptDescription.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptNameTagByUuid(java.lang.String)
	 */
	@Override
	public ConceptNameTag getConceptNameTagByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptNameTag cnt where cnt.uuid = :uuid", ConceptNameTag.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptMapsBySource(ConceptSource)
	 */
	@Override
	public List<ConceptMap> getConceptMapsBySource(ConceptSource conceptSource) throws DAOException {
		Query<ConceptMap> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptMap cm WHERE cm.conceptReferenceTerm.conceptSource = :source", ConceptMap.class);
		query.setParameter("source", conceptSource);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptSourceByName(java.lang.String)
	 */
	@Override
	public ConceptSource getConceptSourceByName(String conceptSourceName) throws DAOException {
		Query<ConceptSource> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptSource source WHERE source.name = :name", ConceptSource.class);
		query.setParameter("name", conceptSourceName);
		return query.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptSourceByUniqueId(java.lang.String)
	 */
	@Override
	public ConceptSource getConceptSourceByUniqueId(String uniqueId) {
		if (StringUtils.isBlank(uniqueId)) {
			return null;
		}
		Query<ConceptSource> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptSource cs WHERE cs.uniqueId = :uniqueId", ConceptSource.class);
		query.setParameter("uniqueId", uniqueId);
		return query.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptSourceByHL7Code(java.lang.String)
	 */
	@Override
	public ConceptSource getConceptSourceByHL7Code(String hl7Code) {
		if (StringUtils.isBlank(hl7Code)) {
			return null;
		}
		Query<ConceptSource> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptSource cs WHERE cs.hl7Code = :hl7Code", ConceptSource.class);
		query.setParameter("hl7Code", hl7Code);
		return query.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getSavedConceptDatatype(org.openmrs.Concept)
	 */
	@Override
	public ConceptDatatype getSavedConceptDatatype(Concept concept) {
		NativeQuery<ConceptDatatype> sql = sessionFactory.getCurrentSession().createNativeQuery(
		    "select datatype.* from " + "concept_datatype datatype, " + "concept concept " + "where "
		            + "datatype.concept_datatype_id = concept.datatype_id " + "and concept.concept_id=:conceptId",
		    ConceptDatatype.class);
		sql.setParameter("conceptId", concept.getConceptId());
		return sql.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getSavedConceptName(org.openmrs.ConceptName)
	 */
	@Override
	public ConceptName getSavedConceptName(ConceptName conceptName) {
		sessionFactory.getCurrentSession().refresh(conceptName);
		return conceptName;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptStopWords(java.util.Locale)
	 */
	@Override
	public List<String> getConceptStopWords(Locale locale) throws DAOException {

		locale = (locale == null ? Context.getLocale() : locale);

		Query<String> query = sessionFactory.getCurrentSession().createQuery(
		    "SELECT csw.value FROM ConceptStopWord csw WHERE csw.locale = :locale", String.class);
		query.setParameter("locale", locale);

		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptStopWord(org.openmrs.ConceptStopWord)
	 */
	@Override
	public ConceptStopWord saveConceptStopWord(ConceptStopWord conceptStopWord) throws DAOException {
		if (conceptStopWord != null) {
			Query<ConceptStopWord> query = sessionFactory.getCurrentSession().createQuery(
			    "FROM ConceptStopWord csw WHERE csw.value = :value AND csw.locale = :locale", ConceptStopWord.class);
			query.setParameter("value", conceptStopWord.getValue());
			query.setParameter("locale", conceptStopWord.getLocale());
			List<ConceptStopWord> stopWordList = query.list();

			if (!stopWordList.isEmpty()) {
				throw new DAOException("Duplicate ConceptStopWord Entry");
			}
			sessionFactory.getCurrentSession().saveOrUpdate(conceptStopWord);
		}

		return conceptStopWord;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptStopWord(java.lang.Integer)
	 */
	@Override
	public void deleteConceptStopWord(Integer conceptStopWordId) throws DAOException {
		if (conceptStopWordId == null) {
			throw new DAOException("conceptStopWordId is null");
		}
		Object csw = sessionFactory.getCurrentSession()
		        .createQuery("FROM ConceptStopWord csw WHERE csw.conceptStopWordId = :id", ConceptStopWord.class)
		        .setParameter("id", conceptStopWordId)
		        .uniqueResult();
		if (csw == null) {
			throw new DAOException("Concept Stop Word not found or already deleted");
		}
		sessionFactory.getCurrentSession().delete(csw);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getAllConceptStopWords()
	 */
	@Override
	public List<ConceptStopWord> getAllConceptStopWords() {
		return sessionFactory.getCurrentSession()
		        .createQuery("FROM ConceptStopWord", ConceptStopWord.class).list();
	}

	/**
	 * @see ConceptService#getCountOfDrugs(String, Concept, boolean, boolean, boolean)
	 */
	@Override
	public Long getCountOfDrugs(String drugName, Concept concept, boolean searchKeywords, boolean searchDrugConceptNames,
	        boolean includeRetired) throws DAOException {
		LuceneQuery<Drug> drugsQuery = newDrugQuery(drugName, searchKeywords, searchDrugConceptNames, Context.getLocale(),
		    false, concept, includeRetired);

		if (drugsQuery == null) {
			return 0L;
		}

		return drugsQuery.resultSize();
	}

	/**
	 * <strong>Should</strong> return a drug if either the drug name or concept name matches the phase not both
	 * <strong>Should</strong> return distinct drugs
	 * <strong>Should</strong> return a drug, if phrase match concept_name No need to match both concept_name and
	 *         drug_name
	 * <strong>Should</strong> return drug when phrase match drug_name even searchDrugConceptNames is false
	 * <strong>Should</strong> return a drug if phrase match drug_name No need to match both concept_name and
	 *         drug_name
	 */
	@Override
	public List<Drug> getDrugs(String drugName, Concept concept, boolean searchKeywords, boolean searchDrugConceptNames,
	        boolean includeRetired, Integer start, Integer length) throws DAOException {
		LuceneQuery<Drug> drugsQuery = newDrugQuery(drugName, searchKeywords, searchDrugConceptNames, Context.getLocale(),
		    false, concept, includeRetired);

		if (drugsQuery == null) {
			return Collections.emptyList();
		}

		return drugsQuery.listPart(start, length).getList();
	}

	private LuceneQuery<Drug> newDrugQuery(String drugName, boolean searchKeywords, boolean searchDrugConceptNames,
	        Locale locale, boolean exactLocale, Concept concept, boolean includeRetired) {
		if (StringUtils.isBlank(drugName) && concept == null) {
			return null;
		}
		if (locale == null) {
			locale = Context.getLocale();
		}

		StringBuilder query = new StringBuilder();
		if (!StringUtils.isBlank(drugName)) {
			String escapedName = LuceneQuery.escapeQuery(drugName);
			List<String> tokenizedName = Arrays.asList(escapedName.trim().split("\\+"));
			query.append("(");
			query.append(newNameQuery(tokenizedName, escapedName, searchKeywords));
			query.append(")^0.3 OR drugReferenceMaps.conceptReferenceTerm.code:(\"").append(escapedName).append("\")^0.6");
		}

		if (concept != null) {
			query.append(" OR concept.conceptId:(").append(concept.getConceptId()).append(")^0.1");
		} else if (searchDrugConceptNames) {
			LuceneQuery<ConceptName> conceptNameQuery = newConceptNameLuceneQuery(drugName, searchKeywords,
					Collections.singletonList(locale), exactLocale, includeRetired, null, null, null, null, null);
			List<Object[]> conceptIds = conceptNameQuery.listProjection("concept.conceptId");
			if (!conceptIds.isEmpty()) {
				CollectionUtils.transform(conceptIds, input -> ((Object[]) input)[0].toString());
				//The default Lucene clauses limit is 1024. We arbitrarily chose to use 512 here as it does not make sense to return more hits by concept name anyway.
				int maxSize = (conceptIds.size() < 512) ? conceptIds.size() : 512;
				query.append(" OR concept.conceptId:(").append(StringUtils.join(conceptIds.subList(0, maxSize), " OR "))
				        .append(")^0.1");
			}
		}

		LuceneQuery<Drug> drugsQuery = LuceneQuery
		        .newQuery(Drug.class, sessionFactory.getCurrentSession(), query.toString());
		if (!includeRetired) {
			drugsQuery.include("retired", false);
		}
		return drugsQuery;
	}

	/**
	 * @see ConceptDAO#getConcepts(String, List, boolean, List, List, List, List, Concept, Integer,
	 *      Integer)
	 */
	@Override
	public List<ConceptSearchResult> getConcepts(final String phrase, final List<Locale> locales,
	        final boolean includeRetired, final List<ConceptClass> requireClasses, final List<ConceptClass> excludeClasses,
	        final List<ConceptDatatype> requireDatatypes, final List<ConceptDatatype> excludeDatatypes,
	        final Concept answersToConcept, final Integer start, final Integer size) throws DAOException {

		LuceneQuery<ConceptName> query = newConceptNameLuceneQuery(phrase, true, locales, false, includeRetired,
		    requireClasses, excludeClasses, requireDatatypes, excludeDatatypes, answersToConcept);

		ListPart<ConceptName> names = query.listPart(start, size);

		List<ConceptSearchResult> results = new ArrayList<>();

		for (ConceptName name : names.getList()) {
			results.add(new ConceptSearchResult(phrase, name.getConcept(), name));
		}

		return results;
	}

	@Override
	public Integer getCountOfConcepts(final String phrase, List<Locale> locales, boolean includeRetired,
	        List<ConceptClass> requireClasses, List<ConceptClass> excludeClasses, List<ConceptDatatype> requireDatatypes,
	        List<ConceptDatatype> excludeDatatypes, Concept answersToConcept) throws DAOException {

		LuceneQuery<ConceptName> query = newConceptNameLuceneQuery(phrase, true, locales, false, includeRetired,
		    requireClasses, excludeClasses, requireDatatypes, excludeDatatypes, answersToConcept);

		Long size = query.resultSize();
		return size.intValue();
	}

	private LuceneQuery<ConceptName> newConceptNameLuceneQuery(final String phrase, boolean searchKeywords,
	        List<Locale> locales, boolean searchExactLocale, boolean includeRetired, List<ConceptClass> requireClasses,
	        List<ConceptClass> excludeClasses, List<ConceptDatatype> requireDatatypes,
	        List<ConceptDatatype> excludeDatatypes, Concept answersToConcept) {
		final StringBuilder query = new StringBuilder();

		if (!StringUtils.isBlank(phrase)) {
			final Set<Locale> searchLocales;

			if (locales == null) {
				searchLocales = new HashSet<>(Collections.singletonList(Context.getLocale()));
			} else {
				searchLocales = new HashSet<>(locales);
			}

			query.append(newConceptNameQuery(phrase, searchKeywords, searchLocales, searchExactLocale));
		}

		LuceneQuery<ConceptName> luceneQuery = LuceneQuery.newQuery(ConceptName.class, sessionFactory.getCurrentSession(),
		    query.toString()).include("concept.conceptClass.conceptClassId", transformToIds(requireClasses)).exclude(
		    "concept.conceptClass.conceptClassId", transformToIds(excludeClasses)).include(
		    "concept.datatype.conceptDatatypeId", transformToIds(requireDatatypes)).exclude(
		    "concept.datatype.conceptDatatypeId", transformToIds(excludeDatatypes));

		if (answersToConcept != null) {
			Collection<ConceptAnswer> answers = answersToConcept.getAnswers(false);

			if (answers != null && !answers.isEmpty()) {
				List<Integer> ids = new ArrayList<>();
				for (ConceptAnswer conceptAnswer : answersToConcept.getAnswers(false)) {
					ids.add(conceptAnswer.getAnswerConcept().getId());
				}
				luceneQuery.include("concept.conceptId", ids.toArray(new Object[0]));
			}
		}

		if (!includeRetired) {
			luceneQuery.include("concept.retired", false);
		}

		luceneQuery.skipSame("concept.conceptId");

		return luceneQuery;
	}

	private String[] transformToIds(final List<? extends OpenmrsObject> items) {
		if (items == null || items.isEmpty()) {
			return new String[0];
		}

		String[] ids = new String[items.size()];
		for (int i = 0; i < items.size(); i++) {
			ids[i] = items.get(i).getId().toString();
		}
		return ids;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptMapTypes(boolean, boolean)
	 */
	@Override
	public List<ConceptMapType> getConceptMapTypes(boolean includeRetired, boolean includeHidden) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM ConceptMapType cmt WHERE 1=1");
		if (!includeRetired) {
			hql.append(" AND cmt.retired = false");
		}
		if (!includeHidden) {
			hql.append(" AND cmt.isHidden = false");
		}

		Query<ConceptMapType> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), ConceptMapType.class);
		List<ConceptMapType> conceptMapTypes = query.list();
		conceptMapTypes.sort(new ConceptMapTypeComparator());

		return conceptMapTypes;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptMapType(java.lang.Integer)
	 */
	@Override
	public ConceptMapType getConceptMapType(Integer conceptMapTypeId) throws DAOException {
		return (ConceptMapType) sessionFactory.getCurrentSession().get(ConceptMapType.class, conceptMapTypeId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptMapTypeByUuid(java.lang.String)
	 */
	@Override
	public ConceptMapType getConceptMapTypeByUuid(String uuid) throws DAOException {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptMapType cmt where cmt.uuid = :uuid", ConceptMapType.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptMapTypeByName(java.lang.String)
	 */
	@Override
	public ConceptMapType getConceptMapTypeByName(String name) throws DAOException {
		Query<ConceptMapType> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptMapType cmt WHERE lower(cmt.name) = lower(:name)", ConceptMapType.class);
		query.setParameter("name", name);
		return query.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptMapType(org.openmrs.ConceptMapType)
	 */
	@Override
	public ConceptMapType saveConceptMapType(ConceptMapType conceptMapType) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(conceptMapType);
		return conceptMapType;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptMapType(org.openmrs.ConceptMapType)
	 */
	@Override
	public void deleteConceptMapType(ConceptMapType conceptMapType) throws DAOException {
		sessionFactory.getCurrentSession().delete(conceptMapType);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTerms(boolean)
	 */
	@Override
	public List<ConceptReferenceTerm> getConceptReferenceTerms(boolean includeRetired) throws DAOException {
		StringBuilder hql = new StringBuilder("FROM ConceptReferenceTerm crt WHERE 1=1");
		if (!includeRetired) {
			hql.append(" AND crt.retired = false");
		}
		Query<ConceptReferenceTerm> query = sessionFactory.getCurrentSession().createQuery(
		    hql.toString(), ConceptReferenceTerm.class);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTerm(java.lang.Integer)
	 */
	@Override
	public ConceptReferenceTerm getConceptReferenceTerm(Integer conceptReferenceTermId) throws DAOException {
		return (ConceptReferenceTerm) sessionFactory.getCurrentSession().get(ConceptReferenceTerm.class,
		    conceptReferenceTermId);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTermByUuid(java.lang.String)
	 */
	@Override
	public ConceptReferenceTerm getConceptReferenceTermByUuid(String uuid) throws DAOException {
		return sessionFactory.getCurrentSession()
		        .createQuery("from ConceptReferenceTerm crt where crt.uuid = :uuid", ConceptReferenceTerm.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTermsBySource(ConceptSource)
	 */
	@Override
	public List<ConceptReferenceTerm> getConceptReferenceTermsBySource(ConceptSource conceptSource) throws DAOException {
		Query<ConceptReferenceTerm> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptReferenceTerm crt WHERE crt.conceptSource = :source", ConceptReferenceTerm.class);
		query.setParameter("source", conceptSource);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTermByName(java.lang.String,
	 *      org.openmrs.ConceptSource)
	 */
	@Override
	public ConceptReferenceTerm getConceptReferenceTermByName(String name, ConceptSource conceptSource) throws DAOException {
		Query<ConceptReferenceTerm> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptReferenceTerm crt WHERE lower(crt.name) = lower(:name) AND crt.conceptSource = :source",
		    ConceptReferenceTerm.class);
		query.setParameter("name", name);
		query.setParameter("source", conceptSource);
		List<ConceptReferenceTerm> terms = query.list();
		if (terms.isEmpty()) {
			return null;
		} else if (terms.size() > 1) {
			throw new APIException("ConceptReferenceTerm.foundMultipleTermsWithNameInSource", new Object[] { name,
			        conceptSource.getName() });
		}
		return terms.get(0);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTermByCode(java.lang.String,
	 *      org.openmrs.ConceptSource)
	 */
	@Override
	public ConceptReferenceTerm getConceptReferenceTermByCode(String code, ConceptSource conceptSource) throws DAOException {
		Query<ConceptReferenceTerm> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptReferenceTerm crt WHERE crt.code = :code AND crt.conceptSource = :source",
		    ConceptReferenceTerm.class);
		query.setParameter("code", code);
		query.setParameter("source", conceptSource);
		List<ConceptReferenceTerm> terms = query.list();
		if (terms.isEmpty()) {
			return null;
		} else if (terms.size() > 1) {
			throw new APIException("ConceptReferenceTerm.foundMultipleTermsWithCodeInSource", new Object[] { code,
			        conceptSource.getName() });
		}
		return terms.get(0);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#saveConceptReferenceTerm(org.openmrs.ConceptReferenceTerm)
	 */
	@Override
	public ConceptReferenceTerm saveConceptReferenceTerm(ConceptReferenceTerm conceptReferenceTerm) throws DAOException {
		sessionFactory.getCurrentSession().saveOrUpdate(conceptReferenceTerm);
		return conceptReferenceTerm;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptReferenceTerm(org.openmrs.ConceptReferenceTerm)
	 */
	@Override
	public void deleteConceptReferenceTerm(ConceptReferenceTerm conceptReferenceTerm) throws DAOException {
		sessionFactory.getCurrentSession().delete(conceptReferenceTerm);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getCountOfConceptReferenceTerms(String, ConceptSource, boolean)
	 */
	@Override
	public Long getCountOfConceptReferenceTerms(String query, ConceptSource conceptSource, boolean includeRetired)
	        throws DAOException {
		String hql = buildConceptReferenceTermHql(query, conceptSource, includeRetired, true);
		Query<Long> q = sessionFactory.getCurrentSession().createQuery(hql, Long.class);
		setConceptReferenceTermParams(q, query, conceptSource, includeRetired);
		return q.uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptReferenceTerms(String, ConceptSource, Integer,
	 *      Integer, boolean)
	 */
	@Override
	public List<ConceptReferenceTerm> getConceptReferenceTerms(String query, ConceptSource conceptSource, Integer start,
	        Integer length, boolean includeRetired) throws APIException {
		String hql = buildConceptReferenceTermHql(query, conceptSource, includeRetired, false);
		Query<ConceptReferenceTerm> q = sessionFactory.getCurrentSession().createQuery(hql, ConceptReferenceTerm.class);
		setConceptReferenceTermParams(q, query, conceptSource, includeRetired);

		if (start != null) {
			q.setFirstResult(start);
		}
		if (length != null && length > 0) {
			q.setMaxResults(length);
		}

		return q.list();
	}

	/**
	 * Builds an HQL string for searching ConceptReferenceTerms
	 */
	private String buildConceptReferenceTermHql(String query, ConceptSource conceptSource, boolean includeRetired,
	        boolean countOnly) {
		StringBuilder hql = new StringBuilder();
		if (countOnly) {
			hql.append("SELECT COUNT(crt) FROM ConceptReferenceTerm crt WHERE 1=1");
		} else {
			hql.append("FROM ConceptReferenceTerm crt WHERE 1=1");
		}
		if (conceptSource != null) {
			hql.append(" AND crt.conceptSource = :conceptSource");
		}
		if (!includeRetired) {
			hql.append(" AND crt.retired = false");
		}
		if (query != null) {
			hql.append(" AND (lower(crt.name) like :query OR lower(crt.code) like :query)");
		}
		return hql.toString();
	}

	/**
	 * Sets parameters on a query built by buildConceptReferenceTermHql
	 */
	private void setConceptReferenceTermParams(Query<?> q, String query, ConceptSource conceptSource,
	        boolean includeRetired) {
		if (conceptSource != null) {
			q.setParameter("conceptSource", conceptSource);
		}
		if (query != null) {
			q.setParameter("query", "%" + query.toLowerCase() + "%");
		}
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getReferenceTermMappingsTo(ConceptReferenceTerm)
	 */
	@Override
	public List<ConceptReferenceTermMap> getReferenceTermMappingsTo(ConceptReferenceTerm term) throws DAOException {
		Query<ConceptReferenceTermMap> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM ConceptReferenceTermMap crtm WHERE crtm.termB = :term", ConceptReferenceTermMap.class);
		query.setParameter("term", term);
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#isConceptReferenceTermInUse(org.openmrs.ConceptReferenceTerm)
	 */
	@Override
	public boolean isConceptReferenceTermInUse(ConceptReferenceTerm term) throws DAOException {
		Long count = sessionFactory.getCurrentSession().createQuery(
		    "SELECT COUNT(cm) FROM ConceptMap cm WHERE cm.conceptReferenceTerm = :term", Long.class)
		        .setParameter("term", term).uniqueResult();
		if (count > 0) {
			return true;
		}

		count = sessionFactory.getCurrentSession().createQuery(
		    "SELECT COUNT(crtm) FROM ConceptReferenceTermMap crtm WHERE crtm.termB = :term", Long.class)
		        .setParameter("term", term).uniqueResult();
		return count > 0;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#isConceptMapTypeInUse(org.openmrs.ConceptMapType)
	 */
	@Override
	public boolean isConceptMapTypeInUse(ConceptMapType mapType) throws DAOException {
		Long count = sessionFactory.getCurrentSession().createQuery(
		    "SELECT COUNT(cm) FROM ConceptMap cm WHERE cm.conceptMapType = :mapType", Long.class)
		        .setParameter("mapType", mapType).uniqueResult();
		if (count > 0) {
			return true;
		}

		count = sessionFactory.getCurrentSession().createQuery(
		    "SELECT COUNT(crtm) FROM ConceptReferenceTermMap crtm WHERE crtm.conceptMapType = :mapType", Long.class)
		        .setParameter("mapType", mapType).uniqueResult();
		return count > 0;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptsByName(java.lang.String, java.util.Locale,
	 *      java.lang.Boolean)
	 */
	@Override
	public List<Concept> getConceptsByName(final String name, final Locale locale, final Boolean exactLocale) {

		List<Locale> locales = new ArrayList<>();
		if (locale == null) {
			locales.add(Context.getLocale());
		} else {
			locales.add(locale);
		}

		boolean searchExactLocale = (exactLocale == null) ? false : exactLocale;

		LuceneQuery<ConceptName> conceptNameQuery = newConceptNameLuceneQuery(name, true, locales, searchExactLocale, false,
		    null, null, null, null, null);

		List<ConceptName> names = conceptNameQuery.list();

		return new ArrayList<>(transformNamesToConcepts(names));
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptByName(java.lang.String)
	 */
	@Override
	public Concept getConceptByName(final String name) {
		Locale locale = Context.getLocale();
		Locale language = new Locale(locale.getLanguage());

		StringBuilder hql = new StringBuilder(
		        "FROM ConceptName cn WHERE (cn.locale = :locale OR cn.locale = :language)");
		if (Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive()) {
			hql.append(" AND lower(cn.name) = lower(:name)");
		} else {
			hql.append(" AND cn.name = :name");
		}
		hql.append(" AND cn.voided = false AND cn.concept.retired = false");

		Query<ConceptName> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), ConceptName.class);
		query.setParameter("locale", locale);
		query.setParameter("language", language);
		query.setParameter("name", name);

		List<ConceptName> list = query.list();
		LinkedHashSet<Concept> concepts = transformNamesToConcepts(list);

		if (concepts.size() == 1) {
			return concepts.iterator().next();
		} else if (list.isEmpty()) {
			log.warn("No concept found for '" + name + "'");
		} else {
			log.warn("Multiple concepts found for '" + name + "'");

			for (Concept concept : concepts) {
				for (ConceptName conceptName : concept.getNames(locale)) {
					if (conceptName.getName().equalsIgnoreCase(name)) {
						return concept;
					}
				}
				for (ConceptName indexTerm : concept.getIndexTermsForLocale(locale)) {
					if (indexTerm.getName().equalsIgnoreCase(name)) {
						return concept;
					}
				}
			}
		}

		return null;
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDefaultConceptMapType()
	 */
	@Override
	public ConceptMapType getDefaultConceptMapType() throws DAOException {
		FlushMode previousFlushMode = sessionFactory.getCurrentSession().getHibernateFlushMode();
		sessionFactory.getCurrentSession().setHibernateFlushMode(FlushMode.MANUAL);
		try {
			//Defaults to same-as if the gp is not set.
			String defaultConceptMapType = Context.getAdministrationService().getGlobalProperty(
			    OpenmrsConstants.GP_DEFAULT_CONCEPT_MAP_TYPE);
			if (defaultConceptMapType == null) {
				throw new DAOException("The default concept map type is not set. You need to set the '"
				        + OpenmrsConstants.GP_DEFAULT_CONCEPT_MAP_TYPE + "' global property.");
			}

			ConceptMapType conceptMapType = getConceptMapTypeByName(defaultConceptMapType);
			if (conceptMapType == null) {
				throw new DAOException("The default concept map type (name: " + defaultConceptMapType
				        + ") does not exist! You need to set the '" + OpenmrsConstants.GP_DEFAULT_CONCEPT_MAP_TYPE
				        + "' global property.");
			}
			return conceptMapType;
		}
		finally {
			sessionFactory.getCurrentSession().setHibernateFlushMode(previousFlushMode);
		}
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#isConceptNameDuplicate(org.openmrs.ConceptName)
	 */
	@Override
	public boolean isConceptNameDuplicate(ConceptName name) {
		if (name.getVoided()) {
			return false;
		}
		if (name.getConcept() != null) {
			if (name.getConcept().getRetired()) {
				return false;
			}

			//If it is not a default name of a concept, it cannot be a duplicate.
			//Note that a concept may not have a default name for the given locale, if just a short name or
			//a search term is set.
			if (!name.equals(name.getConcept().getName(name.getLocale()))) {
				return false;
			}
		}

		Locale nameLocale = name.getLocale();
		Locale languageOnly = new Locale(nameLocale.getLanguage());

		StringBuilder hql = new StringBuilder(
		        "FROM ConceptName cn WHERE cn.voided = false AND (cn.locale = :locale OR cn.locale = :languageOnly)");
		if (Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive()) {
			hql.append(" AND lower(cn.name) = lower(:name)");
		} else {
			hql.append(" AND cn.name = :name");
		}

		Query<ConceptName> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), ConceptName.class);
		query.setParameter("locale", nameLocale);
		query.setParameter("languageOnly", languageOnly);
		query.setParameter("name", name.getName());

		List<ConceptName> candidateNames = query.list();

		for (ConceptName candidateName : candidateNames) {
			if (candidateName.getConcept().getRetired()) {
				continue;
			}
			if (candidateName.getConcept().equals(name.getConcept())) {
				continue;
			}

			//If it is a default name for a concept
			if (candidateName.getConcept().getName(candidateName.getLocale()).equals(candidateName)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @see ConceptDAO#getDrugs(String, java.util.Locale, boolean, boolean)
	 */
	@Override
	public List<Drug> getDrugs(String searchPhrase, Locale locale, boolean exactLocale, boolean includeRetired) {
		LuceneQuery<Drug> drugQuery = newDrugQuery(searchPhrase, true, true, locale, exactLocale, null, includeRetired);

		return drugQuery.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugsByMapping(String, ConceptSource, Collection,
	 *      boolean)
	 */
	@Override
	public List<Drug> getDrugsByMapping(String code, ConceptSource conceptSource,
	        Collection<ConceptMapType> withAnyOfTheseTypes, boolean includeRetired) throws DAOException {

		StringBuilder hql = buildSearchDrugByMappingHql(code, conceptSource, includeRetired);
		// match with any of the supplied collection of conceptMapTypes
		if (!withAnyOfTheseTypes.isEmpty()) {
			hql.append(" AND map.conceptMapType IN :mapTypes");
		}
		Query<Drug> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Drug.class);
		setSearchDrugByMappingParams(query, code, conceptSource, includeRetired);
		if (!withAnyOfTheseTypes.isEmpty()) {
			query.setParameterList("mapTypes", withAnyOfTheseTypes);
		}
		return query.getResultList();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getDrugs
	 */
	@Override
	public Drug getDrugByMapping(String code, ConceptSource conceptSource,
	        Collection<ConceptMapType> withAnyOfTheseTypesOrOrderOfPreference) throws DAOException {

		if (!withAnyOfTheseTypesOrOrderOfPreference.isEmpty()) {
			for (ConceptMapType conceptMapType : withAnyOfTheseTypesOrOrderOfPreference) {
				StringBuilder hql = buildSearchDrugByMappingHql(code, conceptSource, true);
				hql.append(" AND map.conceptMapType = :mapType");
				Query<Drug> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Drug.class);
				setSearchDrugByMappingParams(query, code, conceptSource, true);
				query.setParameter("mapType", conceptMapType);
				List<Drug> drugs = query.getResultList();
				if (drugs.size() > 1) {
					throw new DAOException("There are multiple matches for the highest-priority ConceptMapType");
				} else if (drugs.size() == 1) {
					return drugs.get(0);
				}
			}
		} else {
			StringBuilder hql = buildSearchDrugByMappingHql(code, conceptSource, true);
			Query<Drug> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), Drug.class);
			setSearchDrugByMappingParams(query, code, conceptSource, true);
			List<Drug> drugs = query.getResultList();
			if (drugs.size() > 1) {
				throw new DAOException("There are multiple matches for the highest-priority ConceptMapType");
			} else if (drugs.size() == 1) {
				return drugs.get(0);
			}
		}
		return null;
	}

	/**
	 * @see ConceptDAO#getAllConceptAttributeTypes()
	 */
	@Override
	public List<ConceptAttributeType> getAllConceptAttributeTypes() {
		return sessionFactory.getCurrentSession()
		        .createQuery("FROM ConceptAttributeType", ConceptAttributeType.class).list();
	}

	/**
	 * @see ConceptDAO#saveConceptAttributeType(ConceptAttributeType)
	 */
	@Override
	public ConceptAttributeType saveConceptAttributeType(ConceptAttributeType conceptAttributeType) {
		sessionFactory.getCurrentSession().saveOrUpdate(conceptAttributeType);
		return conceptAttributeType;
	}

	/**
	 * @see ConceptDAO#getConceptAttributeType(Integer)
	 */
	@Override
	public ConceptAttributeType getConceptAttributeType(Integer id) {
		return (ConceptAttributeType) sessionFactory.getCurrentSession().get(ConceptAttributeType.class, id);
	}

	/**
	 * @see ConceptDAO#getConceptAttributeTypeByUuid(String)
	 */
	@Override
	public ConceptAttributeType getConceptAttributeTypeByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("FROM ConceptAttributeType cat WHERE cat.uuid = :uuid", ConceptAttributeType.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#deleteConceptAttributeType(org.openmrs.ConceptAttributeType)
	 */
	@Override
	public void deleteConceptAttributeType(ConceptAttributeType conceptAttributeType) {
		sessionFactory.getCurrentSession().delete(conceptAttributeType);
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptAttributeTypes(String)
	 */
	@Override
	public List<ConceptAttributeType> getConceptAttributeTypes(String name) {
		StringBuilder hql = new StringBuilder("FROM ConceptAttributeType cat WHERE 1=1");
		if (name != null) {
			hql.append(" AND lower(cat.name) like :name");
		}
		Query<ConceptAttributeType> query = sessionFactory.getCurrentSession().createQuery(
		    hql.toString(), ConceptAttributeType.class);
		if (name != null) {
			query.setParameter("name", "%" + name.toLowerCase() + "%");
		}
		return query.list();
	}

	/**
	 * @see org.openmrs.api.db.ConceptDAO#getConceptAttributeTypeByName(String)
	 */
	@Override
	public ConceptAttributeType getConceptAttributeTypeByName(String exactName) {
		return sessionFactory.getCurrentSession()
		        .createQuery("FROM ConceptAttributeType cat WHERE cat.name = :name", ConceptAttributeType.class)
		        .setParameter("name", exactName).uniqueResult();
	}

	/**
	 * @see ConceptDAO#getConceptAttributeByUuid(String)
	 */
	@Override
	public ConceptAttribute getConceptAttributeByUuid(String uuid) {
		return sessionFactory.getCurrentSession()
		        .createQuery("FROM ConceptAttribute ca WHERE ca.uuid = :uuid", ConceptAttribute.class)
		        .setParameter("uuid", uuid).uniqueResult();
	}

	/**
	 * @see ConceptDAO#getConceptAttributeCount(ConceptAttributeType)
	 */
	@Override
	public long getConceptAttributeCount(ConceptAttributeType conceptAttributeType) {
		return sessionFactory.getCurrentSession()
		        .createQuery("SELECT COUNT(ca) FROM ConceptAttribute ca WHERE ca.attributeType = :attrType", Long.class)
		        .setParameter("attrType", conceptAttributeType)
		        .uniqueResult();
	}

	@Override
	public List<Concept> getConceptsByClass(ConceptClass conceptClass) {
		Query<Concept> query = sessionFactory.getCurrentSession().createQuery(
		    "FROM Concept c WHERE c.conceptClass = :conceptClass", Concept.class);
		query.setParameter("conceptClass", conceptClass);
		return query.list();
	}

	/**
	 * Builds an HQL query for searching drugs by mapping
	 */
	private StringBuilder buildSearchDrugByMappingHql(String code, ConceptSource conceptSource, boolean includeRetired) {
		StringBuilder hql = new StringBuilder(
		        "SELECT DISTINCT drug FROM Drug drug JOIN drug.drugReferenceMaps map");
		boolean needsTerm = (code != null || conceptSource != null);
		if (needsTerm) {
			hql.append(" JOIN map.conceptReferenceTerm term");
		}
		hql.append(" WHERE 1=1");
		if (code != null) {
			hql.append(" AND term.code = :code");
		}
		if (conceptSource != null) {
			hql.append(" AND term.conceptSource = :conceptSource");
		}
		if (!includeRetired) {
			hql.append(" AND drug.retired = false");
		}
		return hql;
	}

	/**
	 * Sets parameters for a drug-by-mapping query
	 */
	private void setSearchDrugByMappingParams(Query<Drug> query, String code, ConceptSource conceptSource,
	        boolean includeRetired) {
		if (code != null) {
			query.setParameter("code", code);
		}
		if (conceptSource != null) {
			query.setParameter("conceptSource", conceptSource);
		}
	}

	/**
	 * Creates an HQL query for searching concept maps and returns results of the given type.
	 * For getConceptsByMapping, pass Concept.class; for getConceptIdsByMapping, pass Integer.class.
	 */
	@SuppressWarnings("unchecked")
	private <T> List<T> createSearchConceptMapQuery(String code, String sourceName, boolean includeRetired,
	        Class<T> resultType) {
		boolean caseSensitive = Context.getAdministrationService().isDatabaseStringComparisonCaseSensitive();

		StringBuilder hql = new StringBuilder();
		if (resultType == Integer.class) {
			hql.append("SELECT cm.concept.conceptId FROM ConceptMap cm");
		} else {
			hql.append("SELECT cm.concept FROM ConceptMap cm");
		}
		hql.append(" JOIN cm.conceptReferenceTerm term");
		hql.append(" JOIN term.conceptSource source");
		hql.append(" JOIN cm.concept concept");
		hql.append(" WHERE 1=1");

		if (caseSensitive) {
			hql.append(" AND lower(term.code) = lower(:code)");
			hql.append(" AND (lower(source.name) = lower(:sourceName) OR lower(source.hl7Code) = lower(:sourceName))");
		} else {
			hql.append(" AND term.code = :code");
			hql.append(" AND (source.name = :sourceName OR source.hl7Code = :sourceName)");
		}

		if (!includeRetired) {
			hql.append(" AND concept.retired = false");
		} else {
			hql.append(" ORDER BY concept.retired ASC");
		}

		Query<T> query = sessionFactory.getCurrentSession().createQuery(hql.toString(), resultType);
		query.setParameter("code", code);
		query.setParameter("sourceName", sourceName);

		return query.list();
	}
}
