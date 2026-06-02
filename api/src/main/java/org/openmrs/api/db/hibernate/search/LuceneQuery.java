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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Id;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.hibernate.Session;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.hibernate.search.backend.lucene.LuceneExtension;
import org.hibernate.search.backend.lucene.search.query.LuceneSearchQuery;
import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.openmrs.PatientIdentifier;
import org.openmrs.PersonAttribute;
import org.openmrs.PersonName;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.FullTextSessionFactory;
import org.openmrs.collection.ListPart;

/**
 * Performs Lucene queries.
 *
 * @since 1.11
 */
public abstract class LuceneQuery<T> extends SearchQuery<T> {

	private Set<Set<Term>> includeTerms = new HashSet<>();

	private Set<Term> excludeTerms = new HashSet<>();

	private Query termsFilterQuery;

	private boolean noUniqueTerms = false;

	private Set<Object> skipSameValues;

	boolean useOrQueryParser = false;

	/**
	 * Normal uses a textual match algorithm for the search.
	 * Soundex indicates to use a phonetic search strategy.
	 */
	public enum MatchType {
		NORMAL, SOUNDEX
	}

	public static <T> LuceneQuery<T> newQuery(final Class<T> type, final Session session, final String query,
	        final Collection<String> fields) {
		return newQuery(type, session, query, fields, MatchType.NORMAL);
	}

	public static <T> LuceneQuery<T> newQuery(final Class<T> type, final Session session, final String query,
	        final Collection<String> fields, MatchType matchType) {
		return new LuceneQuery<T>(type, session) {

			@Override
			protected Query prepareQuery() throws ParseException {
				if (query.isEmpty()) {
					return new MatchAllDocsQuery();
				}
				return newMultipleFieldQueryParser(fields, matchType).parse(query);
			}
		};
	}

	/**
	 * The preferred way to create a Lucene query using the query parser.
	 *
	 * @param type    filters on type
	 * @param session
	 * @param query
	 * @return the Lucene query
	 */
	public static <T> LuceneQuery<T> newQuery(final Class<T> type, final Session session, final String query) {
		return new LuceneQuery<T>(type, session) {

			@Override
			protected Query prepareQuery() throws ParseException {
				if (query.isEmpty()) {
					return new MatchAllDocsQuery();
				}
				return newQueryParser().parse(query);
			}

		};
	}

	/**
	 * Escape any characters that can be interpreted by the query parser.
	 *
	 * @param query
	 * @return the escaped query
	 */
	public static String escapeQuery(final String query) {
		return QueryParser.escape(query);
	}

	public LuceneQuery(Class<T> type, Session session) {
		super(session, type);
	}

	public LuceneQuery<T> useOrQueryParser() {
		useOrQueryParser = true;
		return this;
	}

	/**
	 * Include items with the given value in the specified field.
	 * <p>
	 * It is a filter applied before the query.
	 *
	 * @param field
	 * @param value
	 * @return the query
	 */
	public LuceneQuery<T> include(String field, Object value) {
		if (value != null) {
			include(field, new Object[] { value });
		}
		return this;
	}

	public LuceneQuery<T> include(String field, Collection<?> values) {
		if (values != null) {
			include(field, values.toArray());
		}
		return this;
	}

	/**
	 * Include items with any of the given values in the specified field.
	 * <p>
	 * It is a filter applied before the query.
	 *
	 * @param field
	 * @param values
	 * @return the query
	 */
	public LuceneQuery<T> include(String field, Object[] values) {
		if (values != null && values.length != 0) {
			Set<Term> terms = new HashSet<>();
			for (Object value : values) {
				terms.add(new Term(field, value.toString()));
			}
			includeTerms.add(terms);
		}
		return this;
	}

	/**
	 * Exclude any items with the given value in the specified field.
	 * <p>
	 * It is a filter applied before the query.
	 *
	 * @param field
	 * @param value
	 * @return the query
	 */
	public LuceneQuery<T> exclude(String field, Object value) {
		if (value != null) {
			exclude(field, new Object[] { value });
		}
		return this;
	}

	/**
	 * Exclude any items with the given values in the specified field.
	 * <p>
	 * It is a filter applied before the query.
	 *
	 * @param field
	 * @param values
	 * @return the query
	 */
	public LuceneQuery<T> exclude(String field, Object[] values) {
		if (values != null && values.length != 0) {
			for (Object value : values) {
				excludeTerms.add(new Term(field, value.toString()));
			}
		}
		return this;
	}

	/**
	 * It is called by the constructor to get an instance of a query.
	 * <p>
	 * To construct the query you can use {@link #newQueryParser()} or
	 * {@link #newMultipleFieldQueryParser(Collection, MatchType)}.
	 *
	 * @return the query
	 * @throws ParseException
	 */
	protected abstract Query prepareQuery() throws ParseException;

	/**
	 * You can use it in {@link #prepareQuery()}.
	 *
	 * @return the query parser
	 */
	protected QueryParser newQueryParser() {
		Analyzer analyzer = getLuceneAnalyzer(getType().getName());
		QueryParser queryParser = new QueryParser(null, analyzer);
		setDefaultOperator(queryParser);
		return queryParser;
	}

	protected MultiFieldQueryParser newMultipleFieldQueryParser(Collection<String> fields, MatchType matchType) {
		Analyzer analyzer;
		if (matchType == MatchType.SOUNDEX) {
			analyzer = getLuceneAnalyzer(getType().getName());
		} else if (getType().isAssignableFrom(PatientIdentifier.class)
		        || getType().isAssignableFrom(PersonName.class)
		        || getType().isAssignableFrom(PersonAttribute.class)) {
			analyzer = getLuceneAnalyzer(LuceneAnalyzers.EXACT_ANALYZER);
		} else {
			analyzer = getLuceneAnalyzer(getType().getName());
		}
		MultiFieldQueryParser queryParser = new MultiFieldQueryParser(fields.toArray(new String[0]), analyzer);
		setDefaultOperator(queryParser);
		return queryParser;
	}

	private Analyzer getLuceneAnalyzer(String analyzerName) {
		// In Hibernate Search 7.x, get analyzer from the Lucene backend
		try {
			java.util.Optional<?> opt = Search.mapping(getSession().getSessionFactory())
				.backend()
				.unwrap(org.hibernate.search.backend.lucene.LuceneBackend.class)
				.analyzer(analyzerName);
			if (opt.isPresent() && opt.get() instanceof Analyzer) {
				return (Analyzer) opt.get();
			}
		} catch (Exception e) {
			// fall through to default
		}
		return new StandardAnalyzer();
	}

	private void setDefaultOperator(QueryParser queryParser) {
		if (useOrQueryParser) {
			queryParser.setDefaultOperator(QueryParser.Operator.OR);
		} else {
			queryParser.setDefaultOperator(QueryParser.Operator.AND);
		}
	}

	/**
	 * Gives you access to the Hibernate Search session.
	 *
	 * @return the search session
	 */
	protected SearchSession getSearchSession() {
		return Context.getRegisteredComponent("fullTextSessionFactory", FullTextSessionFactory.class).getFullTextSession();
	}

	/**
	 * Skip elements whose value in the given field repeats (only the first occurrence is kept).
	 * <p>
	 * <b>Note:</b> This method must be called last when constructing a query. When called it will
	 * execute a preliminary query to collect existing field values and create a filter.
	 *
	 * @param field
	 * @return this
	 */
	public LuceneQuery<T> skipSame(String field) {
		return skipSame(field, null);
	}

	/**
	 * Skip elements whose value in the given field repeats.
	 * <p>
	 * Only first elements will be included in the results.
	 *
	 * @param field
	 * @param luceneQuery results of which should also be skipped. It works only for queries that
	 *                    also called skipSame.
	 * @return this
	 */
	public LuceneQuery<T> skipSame(String field, LuceneQuery<?> luceneQuery) {
		String idPropertyName = findIdPropertyName(getType());

		// Build and execute a preliminary query to get IDs and field values for deduplication
		Query prelimQuery = buildCombinedQuery();
		List<T> allResults = executeQuery(prelimQuery, null, null);

		skipSameValues = new HashSet<>();
		if (luceneQuery != null) {
			if (luceneQuery.skipSameValues == null) {
				throw new IllegalArgumentException(
				        "The skipSame method must be called on the given luceneQuery before calling this method.");
			}
			skipSameValues.addAll(luceneQuery.skipSameValues);
		}

		termsFilterQuery = null;
		if (!allResults.isEmpty()) {
			List<Term> terms = new ArrayList<>();
			for (T result : allResults) {
				try {
					Object fieldValue = getFieldValue(result, field);
					Object idValue = getFieldValue(result, idPropertyName);
					if (fieldValue != null && skipSameValues.add(fieldValue)) {
						if (idValue != null) {
							terms.add(new Term(idPropertyName, idValue.toString()));
						}
					}
				}
				catch (Exception e) {
					// skip on reflection errors
				}
			}
			if (!terms.isEmpty()) {
				BooleanQuery.Builder builder = new BooleanQuery.Builder();
				for (Term term : terms) {
					builder.add(new org.apache.lucene.search.TermQuery(term), Occur.SHOULD);
				}
				termsFilterQuery = builder.build();
			} else {
				noUniqueTerms = true;
			}
		}

		return this;
	}

	private Object getFieldValue(Object obj, String fieldPath) throws Exception {
		String[] parts = fieldPath.split("\\.");
		Object current = obj;
		for (String part : parts) {
			if (current == null) {
				return null;
			}
			String getter = "get" + Character.toUpperCase(part.charAt(0)) + part.substring(1);
			try {
				current = current.getClass().getMethod(getter).invoke(current);
			}
			catch (NoSuchMethodException e) {
				// Try "is" prefix for booleans
				getter = "is" + Character.toUpperCase(part.charAt(0)) + part.substring(1);
				current = current.getClass().getMethod(getter).invoke(current);
			}
		}
		return current;
	}

	@Override
	public T uniqueResult() {
		if (noUniqueTerms) {
			return null;
		}
		List<T> results = executeQuery(buildCombinedQuery(), 0, 2);
		if (results.isEmpty()) {
			return null;
		}
		if (results.size() > 1) {
			throw new org.hibernate.HibernateException("More than one result found");
		}
		return results.get(0);
	}

	@Override
	public List<T> list() {
		if (noUniqueTerms) {
			return Collections.emptyList();
		}
		return executeQuery(buildCombinedQuery(), null, null);
	}

	@Override
	public ListPart<T> listPart(Long firstResult, Long maxResults) {
		if (noUniqueTerms) {
			return ListPart.newListPart(Collections.emptyList(), firstResult, maxResults, 0L, true);
		}

		Query combinedQuery = buildCombinedQuery();
		long totalCount = countResults(combinedQuery);

		int first = firstResult != null ? firstResult.intValue() : 0;
		Integer max = maxResults != null ? maxResults.intValue() : null;

		List<T> list = executeQuery(combinedQuery, first, max);

		boolean isComplete = max == null || (first + list.size()) >= totalCount;
		return ListPart.newListPart(list, firstResult, maxResults, totalCount, isComplete);
	}

	/**
	 * @see org.openmrs.api.db.hibernate.search.SearchQuery#resultSize()
	 */
	@Override
	public long resultSize() {
		if (noUniqueTerms) {
			return 0;
		}
		return countResults(buildCombinedQuery());
	}

	public List<Object[]> listProjection(String... fields) {
		if (noUniqueTerms) {
			return Collections.emptyList();
		}
		// In HS7, native projections require the LuceneExtension projection API.
		// For broad compatibility we fall back to loading entities and extracting field values.
		List<T> entities = executeQuery(buildCombinedQuery(), null, null);
		return projectEntities(entities, fields);
	}

	public ListPart<Object[]> listPartProjection(Long firstResult, Long maxResults, String... fields) {
		if (noUniqueTerms) {
			return ListPart.newListPart(Collections.emptyList(), firstResult, maxResults, 0L, true);
		}
		Query combinedQuery = buildCombinedQuery();
		long totalCount = countResults(combinedQuery);

		int first = firstResult != null ? firstResult.intValue() : 0;
		Integer max = maxResults != null ? maxResults.intValue() : null;

		List<T> entities = executeQuery(combinedQuery, first, max);
		List<Object[]> projected = projectEntities(entities, fields);

		boolean isComplete = max == null || (first + projected.size()) >= totalCount;
		return ListPart.newListPart(projected, firstResult, maxResults, totalCount, isComplete);
	}

	public ListPart<Object[]> listPartProjection(Integer firstResult, Integer maxResults, String... fields) {
		Long first = (firstResult != null) ? Long.valueOf(firstResult) : null;
		Long max = (maxResults != null) ? Long.valueOf(maxResults) : null;
		return listPartProjection(first, max, fields);
	}

	// -------------------------------------------------------------------------
	// Private helpers
	// -------------------------------------------------------------------------

	/**
	 * Builds the combined Lucene query: the main query AND the terms filter AND the id filter (from
	 * skipSame).
	 */
	private Query buildCombinedQuery() {
		Query mainQuery;
		try {
			mainQuery = prepareQuery();
		}
		catch (ParseException e) {
			throw new IllegalStateException("Invalid query", e);
		}

		// Build filter from includeTerms / excludeTerms
		TermsFilterFactory filterFactory = new TermsFilterFactory();
		filterFactory.setIncludeTerms(includeTerms);
		filterFactory.setExcludeTerms(excludeTerms);
		Query filterQuery = filterFactory.getQuery();

		BooleanQuery.Builder combined = new BooleanQuery.Builder();
		combined.add(mainQuery, Occur.MUST);
		combined.add(filterQuery, Occur.FILTER);

		// Apply the id-whitelist filter from skipSame if present
		if (termsFilterQuery != null) {
			combined.add(termsFilterQuery, Occur.FILTER);
		}

		return combined.build();
	}

	@SuppressWarnings("unchecked")
	private List<T> executeQuery(Query luceneQuery, Integer firstResult, Integer maxResults) {
		SearchSession session = getSearchSession();

		var searchQuery = session.search(getType())
		        .extension(LuceneExtension.get())
		        .where(f -> f.fromLuceneQuery(luceneQuery));

		if (maxResults != null) {
			return (List<T>) searchQuery.fetchHits(firstResult != null ? firstResult : 0, maxResults);
		}
		return (List<T>) searchQuery.fetchAllHits();
	}

	private long countResults(Query luceneQuery) {
		SearchSession session = getSearchSession();
		return session.search(getType())
		        .extension(LuceneExtension.get())
		        .where(f -> f.fromLuceneQuery(luceneQuery))
		        .fetchTotalHitCount();
	}

	private static String findIdPropertyName(Class<?> type) {
		for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) {
				if (f.isAnnotationPresent(Id.class)) {
					return f.getName();
				}
			}
		}
		return "id";
	}

	private List<Object[]> projectEntities(List<T> entities, String[] fields) {
		List<Object[]> result = new ArrayList<>(entities.size());
		for (T entity : entities) {
			Object[] row = new Object[fields.length];
			for (int i = 0; i < fields.length; i++) {
				try {
					row[i] = getFieldValue(entity, fields[i]);
				}
				catch (Exception e) {
					row[i] = null;
				}
			}
			result.add(row);
		}
		return result;
	}

}
