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

import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurationContext;
import org.hibernate.search.backend.lucene.analysis.LuceneAnalysisConfigurer;

/**
 * Provides Lucene analyzer definitions for any objects in openmrs-core.
 * <p>
 * Objects such as PersonName can use the analyzers provided by this configurer to make their
 * fields searchable. This class defines the following analyzers:
 * <ul>
 *   <li>{@code phraseAnalyzer} – allows searching for an entire phrase, including whitespace</li>
 *   <li>{@code startAnalyzer} – allows searching for tokens that match at the beginning</li>
 *   <li>{@code exactAnalyzer} – allows searching for tokens that are identical</li>
 *   <li>{@code anywhereAnalyzer} – allows searching for text within tokens</li>
 *   <li>{@code soundexAnalyzer} – phonetic (Soundex) search</li>
 * </ul>
 * <p>
 * Register this class via the Hibernate Search property:
 * {@code hibernate.search.backend.analysis.configurer=org.openmrs.api.db.hibernate.search.LuceneAnalyzerFactory}
 *
 * @since 2.4.0
 */
public class LuceneAnalyzerFactory implements LuceneAnalysisConfigurer {

	@Override
	public void configure(LuceneAnalysisConfigurationContext context) {
		// phraseAnalyzer: keyword tokenizer + classic filter + lowercase + ASCII folding
		context.analyzer(LuceneAnalyzers.PHRASE_ANALYZER).custom()
			.tokenizer("keyword")
			.tokenFilter("classic")
			.tokenFilter("lowercase")
			.tokenFilter("asciifolding");

		// exactAnalyzer: whitespace tokenizer + classic filter + lowercase + ASCII folding
		context.analyzer(LuceneAnalyzers.EXACT_ANALYZER).custom()
			.tokenizer("whitespace")
			.tokenFilter("classic")
			.tokenFilter("lowercase")
			.tokenFilter("asciifolding");

		// startAnalyzer: whitespace + classic + lowercase + ASCII folding + EdgeNGram(2,20)
		context.analyzer(LuceneAnalyzers.START_ANALYZER).custom()
			.tokenizer("whitespace")
			.tokenFilter("classic")
			.tokenFilter("lowercase")
			.tokenFilter("asciifolding")
			.tokenFilter("edgeNGram")
				.param("minGramSize", "2")
				.param("maxGramSize", "20");

		// anywhereAnalyzer: whitespace + classic + lowercase + ASCII folding + NGram(2,20)
		context.analyzer(LuceneAnalyzers.ANYWHERE_ANALYZER).custom()
			.tokenizer("whitespace")
			.tokenFilter("classic")
			.tokenFilter("lowercase")
			.tokenFilter("asciifolding")
			.tokenFilter("nGram")
				.param("minGramSize", "2")
				.param("maxGramSize", "20");

		// soundexAnalyzer: standard tokenizer + classic + lowercase + Soundex phonetic filter
		context.analyzer(LuceneAnalyzers.SOUNDEX_ANALYZER).custom()
			.tokenizer("standard")
			.tokenFilter("classic")
			.tokenFilter("lowercase")
			.tokenFilter("phonetic")
				.param("encoder", "Soundex");
	}

}
