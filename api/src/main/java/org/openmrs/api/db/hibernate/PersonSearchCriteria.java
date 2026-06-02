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

import org.openmrs.api.AdministrationService;
import org.openmrs.api.context.Context;
import org.openmrs.util.OpenmrsConstants;

/**
 * Helper class for building HQL WHERE clause fragments used in person/patient searches.
 * <p>
 * The legacy Hibernate Criteria API was removed in Hibernate 6. This class has been rewritten
 * to return HQL predicate strings instead of {@code Criterion} objects.
 * </p>
 * <p>
 * Callers are responsible for joining {@code names} (alias "name") and
 * {@code attributes}/{@code attribute.attributeType} (aliases "attribute"/"attributeType")
 * before appending the fragments returned here.
 * </p>
 */
public class PersonSearchCriteria {

	/**
	 * Returns the HQL match-mode string (EXACT or ANYWHERE) configured for person attribute search.
	 *
	 * @return "exact" or "anywhere"
	 */
	String getAttributeMatchMode() {
		AdministrationService adminService = Context.getAdministrationService();
		String matchModeProperty = adminService.getGlobalProperty(
		    OpenmrsConstants.GLOBAL_PROPERTY_PERSON_ATTRIBUTE_SEARCH_MATCH_MODE, "");
		return matchModeProperty.equals(OpenmrsConstants.GLOBAL_PROPERTY_PERSON_ATTRIBUTE_SEARCH_MATCH_ANYWHERE)
		        ? "anywhere" : "exact";
	}

	/**
	 * Returns an HQL predicate fragment that matches a person attribute value.
	 * The fragment references the aliases "attribute" and "attributeType".
	 *
	 * @param paramName   unique HQL parameter name (without the colon)
	 * @param matchMode   "exact" or "anywhere"
	 * @param includeVoided whether to skip the voided check on the attribute
	 * @return HQL predicate fragment (does NOT include a leading "AND")
	 */
	String getAttributeHqlPredicate(String paramName, String matchMode, boolean includeVoided) {
		StringBuilder sb = new StringBuilder();
		sb.append("(attributeType.searchable = true");
		if (!includeVoided) {
			sb.append(" and attribute.voided = false");
		}
		if ("anywhere".equals(matchMode)) {
			sb.append(" and lower(attribute.value) like :").append(paramName);
		} else {
			// EXACT – use = with lower() for case-insensitive comparison
			sb.append(" and lower(attribute.value) = :").append(paramName);
		}
		sb.append(")");
		return sb.toString();
	}

	/**
	 * Returns the parameter value to bind for an attribute search.
	 *
	 * @param value     the raw search value
	 * @param matchMode "exact" or "anywhere"
	 * @return the value to pass to {@code query.setParameter(paramName, ...)}
	 */
	String getAttributeParamValue(String value, String matchMode) {
		if ("anywhere".equals(matchMode)) {
			return "%" + value.toLowerCase() + "%";
		}
		return value.toLowerCase();
	}

	/**
	 * Returns an HQL predicate fragment that matches a person name (given/middle/family/family2)
	 * using START match mode.
	 * The fragment references the alias "name".
	 *
	 * @param paramName     unique HQL parameter name (without the colon)
	 * @param includeVoided whether to skip the voided check on name
	 * @return HQL predicate fragment (does NOT include a leading "AND")
	 */
	String getNameHqlPredicate(String paramName, boolean includeVoided) {
		StringBuilder sb = new StringBuilder();
		sb.append("(");
		if (!includeVoided) {
			sb.append("name.voided = false and ");
		}
		sb.append("(lower(name.givenName) like :").append(paramName)
		  .append(" or lower(name.middleName) like :").append(paramName)
		  .append(" or lower(name.familyName) like :").append(paramName)
		  .append(" or lower(name.familyName2) like :").append(paramName)
		  .append("))");
		return sb.toString();
	}

	/**
	 * Returns the parameter value to bind for a name START-match search.
	 *
	 * @param value the raw search value
	 * @return lower-cased value with trailing "%"
	 */
	String getNameStartParamValue(String value) {
		return value.toLowerCase() + "%";
	}
}
