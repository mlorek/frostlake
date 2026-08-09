/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.ai;

import dev.frostlake.functions.FunctionProvider;
import dev.frostlake.functions.FunctionRegistry;

/**
 * Contributes the Cortex AI function pack to the engine via the {@code FunctionProvider} ServiceLoader
 * SPI — add {@code frostlake-ai} to the classpath and Cortex-shaped SQL resolves; without it those
 * names are unknown functions, which is also what a Snowflake account without the feature answers.
 *
 * <p>The names here are Snowflake's, spelled the way Snowflake spells them, and that spelling is not
 * uniform. {@code SENTIMENT}, {@code COMPLETE} and the rest of the original set exist only under the
 * {@code SNOWFLAKE.CORTEX} schema — a bare {@code SENTIMENT('x')} is an unknown function. The newer
 * {@code AI_} family is reachable both ways. And {@code AI_SIMILARITY}, {@code AI_AGG} and
 * {@code AI_SUMMARIZE_AGG} go the other way again: bare only, with no {@code SNOWFLAKE.CORTEX}
 * spelling at all. Registering each name exactly where Snowflake has one keeps a query that runs here
 * running there, and — just as importantly — keeps a query that Snowflake rejects rejected here.
 *
 * <p>Absent from the pack, deliberately: {@code PARSE_DOCUMENT}, {@code AI_PARSE_DOCUMENT},
 * {@code AI_TRANSCRIBE} and {@code AI_EMBED}, which read a staged FILE rather than a text argument,
 * and {@code FINETUNE}, which trains a hosted model.
 */
public class AiFunctionProvider implements FunctionProvider {

    private static final String CORTEX = "SNOWFLAKE.CORTEX.";

    @Override
    public void contribute(final FunctionRegistry registry) {
        registry.register(new CortexComplete(CORTEX + "COMPLETE"));
        registry.register(new CortexTryComplete(CORTEX + "TRY_COMPLETE"));
        registry.register(new CortexSentiment(CORTEX + "SENTIMENT"));
        registry.register(new CortexSummarize(CORTEX + "SUMMARIZE"));
        registry.register(new CortexTranslate(CORTEX + "TRANSLATE"));
        registry.register(new CortexExtractAnswer(CORTEX + "EXTRACT_ANSWER"));
        registry.register(new CortexClassifyText(CORTEX + "CLASSIFY_TEXT"));
        registry.register(new CortexCountTokens(CORTEX + "COUNT_TOKENS"));
        registry.register(new CortexEntitySentiment(CORTEX + "ENTITY_SENTIMENT"));
        registry.register(new SplitTextRecursiveCharacter(CORTEX + "SPLIT_TEXT_RECURSIVE_CHARACTER"));
        registry.register(new SplitTextMarkdownHeader(CORTEX + "SPLIT_TEXT_MARKDOWN_HEADER"));
        registry.register(new AiSummarize(CORTEX + "AI_SUMMARIZE"));
        registry.register(new AiTranslate(CORTEX + "AI_TRANSLATE"));
        registry.register(new AiCountTokens(CORTEX + "AI_COUNT_TOKENS"));
        registry.register(new CortexEmbedText(CORTEX + "EMBED_TEXT_768", 768));
        registry.register(new CortexEmbedText(CORTEX + "EMBED_TEXT_1024", 1024));
        final CortexSearchPreview searchPreview = new CortexSearchPreview(CORTEX + "SEARCH_PREVIEW");
        searchPreview.setRegistry(registry);
        registry.register(searchPreview);

        // The AI_ family: both the qualified and the bare spelling.
        registry.register(new CortexComplete(CORTEX + "AI_COMPLETE"));
        registry.register(new CortexAiClassify(CORTEX + "AI_CLASSIFY"));
        registry.register(new CortexAiFilter(CORTEX + "AI_FILTER"));
        registry.register(new CortexAiSentiment(CORTEX + "AI_SENTIMENT"));
        registry.register(new CortexComplete("AI_COMPLETE"));
        registry.register(new CortexAiClassify("AI_CLASSIFY"));
        registry.register(new CortexAiFilter("AI_FILTER"));
        registry.register(new CortexAiSentiment("AI_SENTIMENT"));
        // Measured bare as well as qualified — AI_SUMMARIZE answers bare, and AI_TRANSLATE and
        // AI_COUNT_TOKENS resolve bare too (a one-argument call is refused for its ARITY there, which
        // only a resolved function can be). These three were registered qualified-only, making
        // Frostlake stricter than the account.
        registry.register(new AiSummarize("AI_SUMMARIZE"));
        registry.register(new AiTranslate("AI_TRANSLATE"));
        registry.register(new AiCountTokens("AI_COUNT_TOKENS"));

        // Bare only — these three have no SNOWFLAKE.CORTEX spelling.
        registry.register(new AiSimilarity("AI_SIMILARITY"));
        registry.registerAggregate(new AiAgg("AI_AGG"));
        registry.registerAggregate(new AiSummarizeAgg("AI_SUMMARIZE_AGG"));
    }
}
