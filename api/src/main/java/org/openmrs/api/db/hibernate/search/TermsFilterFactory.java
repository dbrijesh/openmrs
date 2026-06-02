/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.api.db.hibernate.search;

import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

/**
 * Builds a Lucene {@link Query} that filters documents by required and excluded terms.
 * <p>
 * In Hibernate Search 7.x, named full-text filters have been removed. This class is now used as a
 * plain query builder: call {@link #setIncludeTerms(Set)} and {@link #setExcludeTerms(Set)}, then
 * retrieve the constructed filter query via {@link #getQuery()} and combine it with the main query
 * using a {@link BooleanQuery}.
 */
public class TermsFilterFactory {

	private Set<Set<Term>> includeTerms = new HashSet<>();

	private Set<Term> excludeTerms = new HashSet<>();

	public void setIncludeTerms(Set<Set<Term>> terms) {
		this.includeTerms = new HashSet<>(terms);
	}

	public void setExcludeTerms(Set<Term> terms) {
		this.excludeTerms = new HashSet<>(terms);
	}

	/**
	 * Builds the filter {@link Query} from the configured include and exclude terms.
	 *
	 * @return a Lucene {@link Query} representing the terms filter
	 */
	public Query getQuery() {
		BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

		if (includeTerms.isEmpty()) {
			queryBuilder.add(new MatchAllDocsQuery(), Occur.MUST);
		} else {
			for (Set<Term> terms : includeTerms) {
				if (terms.size() == 1) {
					queryBuilder.add(new TermQuery(terms.iterator().next()), Occur.MUST);
				} else if (terms.size() > 1) {
					BooleanQuery.Builder subBuilder = new BooleanQuery.Builder();
					for (Term term : terms) {
						subBuilder.add(new TermQuery(term), Occur.SHOULD);
					}
					queryBuilder.add(subBuilder.build(), Occur.MUST);
				}
			}
		}

		for (Term term : excludeTerms) {
			queryBuilder.add(new TermQuery(term), Occur.MUST_NOT);
		}

		return queryBuilder.build();
	}

}
