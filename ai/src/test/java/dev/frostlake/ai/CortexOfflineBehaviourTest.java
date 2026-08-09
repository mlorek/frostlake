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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Everything the pack answers WITHOUT asking a model: a NULL input, an empty group, and the token
 * count. These are the parts a query depends on being deterministic, and none of them needs a model
 * server to be running.
 */
public class CortexOfflineBehaviourTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    // ── NULL in, NULL out — no model call ─────────────────────────────────────────

    @Test
    public void nullSubjectAnswersNull() {
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.SENTIMENT(NULL)"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.SUMMARIZE(NULL)"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.TRANSLATE(NULL,'en','de')"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.COMPLETE('m',NULL)"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.EXTRACT_ANSWER(NULL,'q')"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.EXTRACT_ANSWER('src',NULL)"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.CLASSIFY_TEXT(NULL, ['a','b'])"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.COUNT_TOKENS('m',NULL)"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.EMBED_TEXT_768('m',NULL)"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.AI_SENTIMENT(NULL)"));
        assertNull(scalar("SELECT AI_CLASSIFY(NULL, ['a','b'])"));
        assertNull(scalar("SELECT AI_FILTER(NULL)"));
        assertNull(scalar("SELECT AI_SIMILARITY('a',NULL)"));
        assertNull(scalar("SELECT AI_SIMILARITY(NULL,'b')"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.AI_SUMMARIZE(NULL)"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.AI_TRANSLATE(NULL,'en','de')"));
    }

    /** An empty category list is nothing to choose between, so the answer is NULL rather than a guess. */
    @Test
    public void emptyCategoryListAnswersNull() {
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.CLASSIFY_TEXT('x', [])"));
        assertNull(scalar("SELECT AI_CLASSIFY('x', [])"));
    }

    /** A NULL subject alongside a non-null predicate still answers NULL. */
    @Test
    public void filterWithNullSubjectAnswersNull() {
        assertNull(scalar("SELECT AI_FILTER('is this true?', NULL)"));
    }

    // ── COUNT_TOKENS is counted here, not asked ───────────────────────────────────

    @Test
    public void countTokensCountsWordsAndPunctuation() {
        assertEquals(1L, scalar("SELECT SNOWFLAKE.CORTEX.COUNT_TOKENS('m','hello')"));
        assertEquals(2L, scalar("SELECT SNOWFLAKE.CORTEX.COUNT_TOKENS('m','hello world')"));
        assertEquals(4L, scalar("SELECT SNOWFLAKE.CORTEX.COUNT_TOKENS('m','hello there, world')"));
        assertEquals(0L, scalar("SELECT SNOWFLAKE.CORTEX.COUNT_TOKENS('m','')"));
        assertEquals(0L, scalar("SELECT SNOWFLAKE.CORTEX.COUNT_TOKENS('m','   ')"));
    }

    /**
     * AI_COUNT_TOKENS prices a named function, so where the text sits depends on which one: after the
     * model for a completion, immediately after the name for a classification.
     */
    @Test
    public void aiCountTokensReadsTheTextForTheNamedFunction() {
        assertEquals(4L, scalar(
            "SELECT SNOWFLAKE.CORTEX.AI_COUNT_TOKENS('AI_COMPLETE','m','hello there, world')"));
        assertEquals(4L, scalar(
            "SELECT SNOWFLAKE.CORTEX.AI_COUNT_TOKENS('AI_SENTIMENT','hello there, world')"));
        assertEquals(4L, scalar(
            "SELECT SNOWFLAKE.CORTEX.AI_COUNT_TOKENS('AI_TRANSLATE','hello there, world','en','de')"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.AI_COUNT_TOKENS(NULL,'x')"));
    }

    // ── The aggregates over a group with nothing in it ────────────────────────────

    @Test
    public void aggregatesOverAnEmptyGroupAnswerNull() {
        engine.execute("CREATE TABLE notes(body VARCHAR)");
        assertNull(scalar("SELECT AI_SUMMARIZE_AGG(body) FROM notes"));
        assertNull(scalar("SELECT AI_AGG(body, 'summarize') FROM notes"));
    }

    @Test
    public void aggregatesOverAnAllNullGroupAnswerNull() {
        engine.execute("CREATE TABLE notes(body VARCHAR)");
        engine.execute("INSERT INTO notes VALUES (NULL), (NULL)");
        assertNull(scalar("SELECT AI_SUMMARIZE_AGG(body) FROM notes"));
        assertNull(scalar("SELECT AI_AGG(body, 'summarize') FROM notes"));
    }

    /** A WHERE that matches nothing is an empty group too. */
    @Test
    public void aggregateOverAFilteredOutGroupAnswersNull() {
        engine.execute("CREATE TABLE notes(id INT, body VARCHAR)");
        engine.execute("INSERT INTO notes VALUES (1, 'something')");
        assertNull(scalar("SELECT AI_AGG(body, 'summarize') FROM notes WHERE id = 99"));
    }

    // ── SEARCH_PREVIEW against a name that resolves to nothing ────────────────────

    @Test
    public void searchPreviewOverAMissingServiceNamesIt() {
        String failed = null;
        try {
            engine.executeQuery("SELECT SNOWFLAKE.CORTEX.SEARCH_PREVIEW('nosuch','{\"query\":\"q\"}')");
        } catch (final RuntimeException error) {
            failed = error.getMessage();
        }
        assertEquals(true, failed != null && failed.contains(
            "Cortex Search Service nosuch does not exist or access is not authorized"), failed);
    }

    @Test
    public void searchPreviewWithANullArgumentAnswersNull() {
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.SEARCH_PREVIEW(NULL,'{}')"));
        assertNull(scalar("SELECT SNOWFLAKE.CORTEX.SEARCH_PREVIEW('svc',NULL)"));
    }
}
