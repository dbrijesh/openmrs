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
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.query.Query;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifierType;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The PatientSearchCriteria class. It has API to return patients matching a name and/or identifier.
 *
 * @deprecated since 2.1.0 (in favour of Hibernate Search / Lucene)
 */
@Deprecated
public class PatientSearchCriteria {

	private static final Logger log = LoggerFactory.getLogger(PatientSearchCriteria.class);

	private final SessionFactory sessionFactory;

	private final PersonSearchCriteria personSearchCriteria;

	/**
	 * @param sessionFactory  the Hibernate session factory
	 * @param unusedCriteria  kept for binary compatibility only – ignored in this implementation
	 */
	public PatientSearchCriteria(SessionFactory sessionFactory, Object unusedCriteria) {
		this.sessionFactory = sessionFactory;
		this.personSearchCriteria = new PersonSearchCriteria();
	}

	// -----------------------------------------------------------------------
	// Public deprecated API – preserved for binary compatibility with modules
	// -----------------------------------------------------------------------

	/**
	 * Search patients by name and/or identifier.
	 *
	 * @param name
	 * @param identifier
	 * @param identifierTypes
	 * @param matchIdentifierExactly
	 * @param searchOnNamesOrIdentifiers specifies if patients matching the name OR identifier are
	 *            returned; otherwise only patients matching both are returned
	 * @return list of matching {@link Patient} objects
	 */
	public List<Patient> getPatients(String name, String identifier,
	        List<PatientIdentifierType> identifierTypes,
	        boolean matchIdentifierExactly, boolean orderByNames,
	        boolean searchOnNamesOrIdentifiers) {

		PatientSearchMode mode = getSearchMode(name, identifier, identifierTypes, searchOnNamesOrIdentifiers);

		StringBuilder hql = new StringBuilder(
		    "SELECT DISTINCT p FROM Patient p LEFT JOIN p.names name LEFT JOIN p.identifiers ids");
		List<String> conditions = new ArrayList<>();
		conditions.add("p.voided = false");

		switch (mode) {
			case PATIENT_SEARCH_BY_NAME:
				addNameConditions(conditions, name, false);
				break;

			case PATIENT_SEARCH_BY_IDENTIFIER:
				addIdentifierConditions(conditions, identifier, identifierTypes, matchIdentifierExactly, false);
				break;

			case PATIENT_SEARCH_BY_NAME_OR_IDENTIFIER: {
				String resolvedName = copySearchParameter(identifier, name);
				String resolvedId   = copySearchParameter(name, identifier);
				List<String> nameParts = new ArrayList<>();
				List<String> idParts   = new ArrayList<>();
				addNameConditions(nameParts, resolvedName, false);
				addIdentifierConditions(idParts, resolvedId, identifierTypes, matchIdentifierExactly, false);
				String nameClause = nameParts.isEmpty() ? null : "(" + String.join(" AND ", nameParts) + ")";
				String idClause   = idParts.isEmpty()   ? null : "(" + String.join(" AND ", idParts)   + ")";
				if (nameClause != null && idClause != null) {
					conditions.add("(" + nameClause + " OR " + idClause + ")");
				} else if (nameClause != null) {
					conditions.add(nameClause);
				} else if (idClause != null) {
					conditions.add(idClause);
				}
				break;
			}

			case PATIENT_SEARCH_BY_NAME_AND_IDENTIFIER:
				addNameConditions(conditions, name, false);
				addIdentifierConditions(conditions, identifier, identifierTypes, matchIdentifierExactly, false);
				break;

			default:
				break;
		}

		if (!conditions.isEmpty()) {
			hql.append(" WHERE ").append(String.join(" AND ", conditions));
		}
		if (orderByNames) {
			hql.append(" ORDER BY name.givenName ASC, name.middleName ASC, name.familyName ASC");
		}

		log.debug("PatientSearchCriteria HQL: {}", hql);

		Session session = sessionFactory.getCurrentSession();
		Query<Patient> query = session.createQuery(hql.toString(), Patient.class);
		bindNameParameters(query, name, false);
		bindIdentifierParameters(query, identifier, identifierTypes, matchIdentifierExactly, false);

		return query.getResultList();
	}

	// -----------------------------------------------------------------------
	// Helper methods
	// -----------------------------------------------------------------------

	/**
	 * @return source value when target is blank, otherwise the target value
	 */
	String copySearchParameter(String source, String target) {
		if (!StringUtils.isBlank(source) && StringUtils.isBlank(target)) {
			return source;
		}
		return target;
	}

	/**
	 * Determines the search mode based on the provided parameters.
	 */
	PatientSearchMode getSearchMode(String name, String identifier,
	        List<PatientIdentifierType> identifierTypes,
	        boolean searchOnNamesOrIdentifiers) {
		if (searchOnNamesOrIdentifiers) {
			return PatientSearchMode.PATIENT_SEARCH_BY_NAME_OR_IDENTIFIER;
		}
		if (!StringUtils.isBlank(name) && StringUtils.isBlank(identifier)
		        && CollectionUtils.isEmpty(identifierTypes)) {
			return PatientSearchMode.PATIENT_SEARCH_BY_NAME;
		}
		if (StringUtils.isBlank(name)
		        && !(StringUtils.isBlank(identifier) && CollectionUtils.isEmpty(identifierTypes))) {
			return PatientSearchMode.PATIENT_SEARCH_BY_IDENTIFIER;
		}
		return PatientSearchMode.PATIENT_SEARCH_BY_NAME_AND_IDENTIFIER;
	}

	/**
	 * Returns the configured name match-mode string.
	 *
	 * @return "anywhere" or "start"
	 */
	String getMatchMode() {
		String matchMode = Context.getAdministrationService().getGlobalProperty(
		    OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_SEARCH_MATCH_MODE,
		    OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_SEARCH_MATCH_START);
		return matchMode.equalsIgnoreCase(OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_SEARCH_MATCH_ANYWHERE)
		        ? "anywhere" : "start";
	}

	/**
	 * Returns true if the name is shorter than the minimum configured search character count.
	 */
	Boolean isShortName(String name) {
		Integer minChars = Context.getAdministrationService().getGlobalPropertyValue(
		    OpenmrsConstants.GLOBAL_PROPERTY_MIN_SEARCH_CHARACTERS,
		    OpenmrsConstants.GLOBAL_PROPERTY_DEFAULT_MIN_SEARCH_CHARACTERS);
		return name != null && name.length() < minChars;
	}

	/** Splits a query string by spaces and commas, discarding blanks. */
	String[] getQueryParts(String query) {
		if (query == null) {
			throw new IllegalArgumentException("query must not be null");
		}
		query = query.replace(",", " ");
		String[] parts = query.split(" ");
		List<String> result = new ArrayList<>();
		for (String part : parts) {
			if (part.trim().length() > 0) {
				result.add(part);
			}
		}
		return result.toArray(new String[0]);
	}

	// -----------------------------------------------------------------------
	// HQL condition builders
	// -----------------------------------------------------------------------

	private void addNameConditions(List<String> conditions, String name, boolean includeVoided) {
		if (StringUtils.isBlank(name)) {
			return;
		}
		String[] parts = getQueryParts(name);
		if (parts.length == 0) {
			return;
		}
		String matchMode = getMatchMode();
		for (int i = 0; i < parts.length; i++) {
			String part = parts[i];
			String paramBase = "namePart" + i;
			String singleClause = buildNameClause(paramBase + "s", includeVoided);
			if (i == 0) {
				conditions.add(singleClause);
			} else {
				// multi-word: match single token OR accumulated phrase
				String multiParam = paramBase + "m";
				String multiClause = buildNameClause(multiParam, includeVoided);
				conditions.add("(" + singleClause + " OR " + multiClause + ")");
			}
		}
	}

	private String buildNameClause(String paramName, boolean includeVoided) {
		StringBuilder sb = new StringBuilder("(");
		if (!includeVoided) {
			sb.append("name.voided = false AND ");
		}
		sb.append("(lower(name.givenName) like :").append(paramName)
		  .append(" OR lower(name.middleName) like :").append(paramName)
		  .append(" OR lower(name.familyName) like :").append(paramName)
		  .append(" OR lower(name.familyName2) like :").append(paramName)
		  .append("))");
		return sb.toString();
	}

	@SuppressWarnings("rawtypes")
	private void bindNameParameters(Query query, String name, boolean includeVoided) {
		if (StringUtils.isBlank(name)) {
			return;
		}
		String[] parts = getQueryParts(name);
		String matchMode = getMatchMode();
		StringBuilder accumulated = new StringBuilder();
		for (int i = 0; i < parts.length; i++) {
			if (i == 0) {
				accumulated.append(parts[i]);
			} else {
				accumulated.append(" ").append(parts[i]);
			}
			String paramValue = toNameParamValue(parts[i], matchMode);
			String singleParam = "namePart" + i + "s";
			try { query.setParameter(singleParam, paramValue); } catch (Exception ignored) {}
			if (i > 0) {
				String multiValue = toNameParamValue(accumulated.toString(), matchMode);
				String multiParam = "namePart" + i + "m";
				try { query.setParameter(multiParam, multiValue); } catch (Exception ignored) {}
			}
		}
	}

	private String toNameParamValue(String token, String matchMode) {
		if (isShortName(token)) {
			// short name: exact match (case-insensitive via lower())
			return token.toLowerCase();
		}
		if ("anywhere".equals(matchMode)) {
			return "%" + token.toLowerCase() + "%";
		}
		return token.toLowerCase() + "%"; // START
	}

	private void addIdentifierConditions(List<String> conditions, String identifier,
	        List<PatientIdentifierType> identifierTypes,
	        boolean matchIdentifierExactly, boolean includeVoided) {
		if (StringUtils.isBlank(identifier) && CollectionUtils.isEmpty(identifierTypes)) {
			return;
		}
		List<String> idConds = new ArrayList<>();
		if (!includeVoided) {
			idConds.add("ids.voided = false");
		}
		if (!StringUtils.isBlank(identifier)) {
			String escaped = HibernateUtil.escapeSqlWildcards(identifier, sessionFactory);
			if (matchIdentifierExactly) {
				idConds.add("lower(ids.identifier) = :idParam");
			} else {
				AdministrationService adminService = Context.getAdministrationService();
				String patternSearch = adminService.getGlobalProperty(
				    OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_IDENTIFIER_SEARCH_PATTERN, "");
				String regex = adminService.getGlobalProperty(
				    OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_IDENTIFIER_REGEX, "");
				if (Pattern.matches("^\\^.{1}\\*.*$", regex)) {
					// remove padding
					String padding = regex.substring(regex.indexOf("^") + 1, regex.indexOf("*"));
					Pattern padPattern = Pattern.compile("^" + padding + "+");
					escaped = padPattern.matcher(escaped).replaceFirst("");
				}
				if (org.springframework.util.StringUtils.hasLength(patternSearch)) {
					// pattern search – use IN clause; build list via HQL literal is unsafe,
					// so fall back to LIKE on each pattern as OR
					idConds.add("lower(ids.identifier) like :idParam");
				} else {
					String prefix = adminService.getGlobalProperty(
					    OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_IDENTIFIER_PREFIX, "");
					String suffix = adminService.getGlobalProperty(
					    OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_IDENTIFIER_SUFFIX, "");
					idConds.add("lower(ids.identifier) like :idParam");
				}
			}
		}
		if (!CollectionUtils.isEmpty(identifierTypes)) {
			idConds.add("ids.identifierType in :idTypes");
		}
		if (!idConds.isEmpty()) {
			conditions.add("(" + String.join(" AND ", idConds) + ")");
		}
	}

	@SuppressWarnings("rawtypes")
	private void bindIdentifierParameters(Query query, String identifier,
	        List<PatientIdentifierType> identifierTypes,
	        boolean matchIdentifierExactly, boolean includeVoided) {
		if (!StringUtils.isBlank(identifier)) {
			String escaped = HibernateUtil.escapeSqlWildcards(identifier, sessionFactory);
			AdministrationService adminService = Context.getAdministrationService();
			String regex = adminService.getGlobalProperty(
			    OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_IDENTIFIER_REGEX, "");
			if (Pattern.matches("^\\^.{1}\\*.*$", regex)) {
				String padding = regex.substring(regex.indexOf("^") + 1, regex.indexOf("*"));
				Pattern padPattern = Pattern.compile("^" + padding + "+");
				escaped = padPattern.matcher(escaped).replaceFirst("");
			}
			if (matchIdentifierExactly) {
				try { query.setParameter("idParam", escaped.toLowerCase()); } catch (Exception ignored) {}
			} else {
				String prefix = adminService.getGlobalProperty(
				    OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_IDENTIFIER_PREFIX, "");
				String suffix = adminService.getGlobalProperty(
				    OpenmrsConstants.GLOBAL_PROPERTY_PATIENT_IDENTIFIER_SUFFIX, "");
				try { query.setParameter("idParam", (prefix + escaped + suffix).toLowerCase()); } catch (Exception ignored) {}
			}
		}
		if (!CollectionUtils.isEmpty(identifierTypes)) {
			try { query.setParameter("idTypes", identifierTypes); } catch (Exception ignored) {}
		}
	}

	/**
	 * Puts @SEARCH@, @SEARCH-1@, and @CHECKDIGIT@ into the search string.
	 */
	private String replaceSearchString(String regex, String identifierSearched) {
		String returnString = regex.replaceAll("@SEARCH@", identifierSearched);
		if (identifierSearched.length() > 1) {
			returnString = returnString.replaceAll("@SEARCH-1@",
			    identifierSearched.substring(0, identifierSearched.length() - 1));
			returnString = returnString.replaceAll("@CHECKDIGIT@",
			    identifierSearched.substring(identifierSearched.length() - 1));
		} else {
			returnString = returnString.replaceAll("@SEARCH-1@", "");
			returnString = returnString.replaceAll("@CHECKDIGIT@", "");
		}
		return returnString;
	}
}
