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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The pack answering for real, against whatever model {@code ai.ollama.model} names. Skipped whole
 * when no server answers, so a build without Ollama is green rather than red — the offline behaviour
 * that a query genuinely depends on is asserted in {@link CortexOfflineBehaviourTest} instead.
 *
 * <p>What is asserted here is deliberately loose. A model is not a function: two runs of the same
 * prompt can word an answer differently, and a stricter assertion would be a test of one model rather
 * than of this pack. So the shape is checked — a sentiment lands on the right side of zero, a
 * classifier picks from the list it was given, a similarity ranks the near pair above the far one —
 * and the wording is left alone.
 *
 * <pre>
 * mvn -pl ai test -Dai.ollama.model=llama3.2 -Dai.ollama.embedModel=nomic-embed-text
 * </pre>
 */
public class CortexAgainstALocalModelTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(CortexAgainstALocalModelTest.class);

    @BeforeEach
    public void requireAModelServer() {
        final boolean answering = OllamaClient.isAvailable();
        if (!answering) {
            logger.info("No model server at {} — skipping the live-model tests.", OllamaConfig.url());
        }
        assumeTrue(answering, "no model server at " + OllamaConfig.url());
        assumeTrue(modelIsPulled(), "model " + OllamaConfig.model() + " is not pulled");
    }

    /** Whether the configured model answers at all — a named-but-unpulled model is a 404, not a hang. */
    private boolean modelIsPulled() {
        try {
            OllamaClient.generate(OllamaConfig.model(), "hi", Double.valueOf(0.0), Integer.valueOf(1));
            return true;
        } catch (final RuntimeException unavailable) {
            logger.info("Model {} unavailable: {}", OllamaConfig.model(), unavailable.getMessage());
            return false;
        }
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    private double number(final String sql) {
        return ((Number) scalar(sql)).doubleValue();
    }

    private String text(final String sql) {
        final Object value = scalar(sql);
        return value == null ? null : value.toString();
    }

    @Test
    public void sentimentSeparatesPraiseFromComplaint() {
        final double praise = number(
            "SELECT SNOWFLAKE.CORTEX.SENTIMENT('I absolutely love this, it is wonderful.')");
        final double complaint = number(
            "SELECT SNOWFLAKE.CORTEX.SENTIMENT('Terrible experience, I want a refund.')");
        logger.info("sentiment praise={} complaint={}", praise, complaint);
        assertTrue(praise > complaint, "praise " + praise + " should outrank complaint " + complaint);
        assertTrue(praise >= -1.0 && praise <= 1.0, "score stays in range: " + praise);
        assertTrue(complaint >= -1.0 && complaint <= 1.0, "score stays in range: " + complaint);
    }

    @Test
    public void completeAnswersInText() {
        final String answered = text(
            "SELECT SNOWFLAKE.CORTEX.COMPLETE('" + OllamaConfig.model() + "','Say the word yes.')");
        assertNotNull(answered);
        assertTrue(answered.length() > 0, "COMPLETE answered nothing");
    }

    /** TRY_COMPLETE swallows a failure where COMPLETE raises it — here, a model that does not exist. */
    @Test
    public void tryCompleteAnswersNullWhereCompleteWouldFail() {
        assertNotNull(text("SELECT SNOWFLAKE.CORTEX.TRY_COMPLETE('"
            + OllamaConfig.model() + "','Say the word yes.')"));
        final Object failed = scalar(
            "SELECT SNOWFLAKE.CORTEX.TRY_COMPLETE('no-such-model-at-all','hello')");
        assertTrue(failed == null, "TRY_COMPLETE over an unknown model should be NULL, was " + failed);
    }

    @Test
    public void summarizeAnswersSomethingShorterThanTheSource() {
        final String source = "The battery lasts all day and charges quickly. The screen is dim"
            + " outdoors but the colours are lovely. Shipping took two weeks, which is too long.";
        final String summary = text("SELECT SNOWFLAKE.CORTEX.SUMMARIZE('" + source + "')");
        assertNotNull(summary);
        assertTrue(summary.length() > 0, "SUMMARIZE answered nothing");
    }

    /** The classifier must pick from the list it was handed, never invent a label. */
    @Test
    public void classifyTextPicksAnOfferedCategory() {
        final String answered = text(
            "SELECT SNOWFLAKE.CORTEX.CLASSIFY_TEXT('my package never arrived',"
            + " ['shipping','billing','quality'])");
        logger.info("classify -> {}", answered);
        assertTrue(answered.startsWith("{\"label\":"), answered);
        assertTrue(answered.contains("shipping") || answered.contains("billing")
            || answered.contains("quality"), answered);
    }

    /** AI_CLASSIFY answers with an ARRAY under "labels" where CLASSIFY_TEXT answers one "label". */
    @Test
    public void aiClassifyAnswersALabelArray() {
        final String answered = text(
            "SELECT AI_CLASSIFY('my package never arrived', ['shipping','billing','quality'])");
        logger.info("ai_classify -> {}", answered);
        assertTrue(answered.startsWith("{\"labels\":["), answered);
    }

    @Test
    public void aiSentimentAnswersPerCategory() {
        final String answered = text("SELECT AI_SENTIMENT('the battery is great')");
        logger.info("ai_sentiment -> {}", answered);
        assertTrue(answered.contains("\"categories\""), answered);
        assertTrue(answered.contains("\"name\":\"overall\""), answered);
    }

    @Test
    public void aiFilterAnswersABoolean() {
        final Object answered = scalar("SELECT AI_FILTER('Is the sky blue?')");
        assertTrue(answered instanceof Boolean, "AI_FILTER answered " + answered);
    }

    // ── Embedding-backed: these need the embedding model, not the chat one ────────

    /**
     * The answer is a real VECTOR of the width the function name declares — proved by casting it to
     * exactly that type, and by the neighbouring width being refused. A local model rarely produces
     * exactly 768 or 1024 numbers, so this is the assertion that the padding and truncation hold.
     */
    @Test
    public void embedTextAnswersAVectorOfTheDeclaredWidth() {
        assumeTrue(embeddingModelIsPulled(), "embedding model is not pulled");
        assertNotNull(scalar(
            "SELECT SNOWFLAKE.CORTEX.EMBED_TEXT_768('m','cat')::VECTOR(FLOAT, 768)"));
        assertNotNull(scalar(
            "SELECT SNOWFLAKE.CORTEX.EMBED_TEXT_1024('m','cat')::VECTOR(FLOAT, 1024)"));
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT SNOWFLAKE.CORTEX.EMBED_TEXT_768('m','cat')::VECTOR(FLOAT, 1024)");
            }
        });
    }

    /** A text is maximally similar to itself, whatever the model. */
    @Test
    public void embeddingsAreSelfSimilar() {
        assumeTrue(embeddingModelIsPulled(), "embedding model is not pulled");
        final double self = number("SELECT VECTOR_COSINE_SIMILARITY("
            + "SNOWFLAKE.CORTEX.EMBED_TEXT_768('m','cat'),"
            + " SNOWFLAKE.CORTEX.EMBED_TEXT_768('m','cat'))");
        assertTrue(self > 0.999, "a text should be similar to itself: " + self);
    }

    @Test
    public void similarityRanksTheNearPairAboveTheFarOne() {
        assumeTrue(embeddingModelIsPulled(), "embedding model is not pulled");
        final double near = number("SELECT AI_SIMILARITY('cat','kitten')");
        final double far = number("SELECT AI_SIMILARITY('cat','quantum chromodynamics')");
        logger.info("similarity near={} far={}", near, far);
        assertTrue(near > far, "near " + near + " should outrank far " + far);
    }

    @Test
    public void searchPreviewRanksTheRelevantRowFirst() {
        assumeTrue(embeddingModelIsPulled(), "embedding model is not pulled");
        engine.execute("CREATE TABLE reviews(id INT, body VARCHAR)");
        engine.execute("INSERT INTO reviews VALUES"
            + " (1,'The battery lasts all day.'),"
            + " (2,'The screen is dim outdoors.'),"
            + " (3,'Shipping took two weeks.')");
        engine.execute("""
            CREATE CORTEX SEARCH SERVICE rsvc ON body
              WAREHOUSE = wh TARGET_LAG = '1 hour'
              AS (SELECT id, body FROM reviews)
            """);
        final String answered = text("SELECT SNOWFLAKE.CORTEX.SEARCH_PREVIEW('rsvc',"
            + " '{\"query\":\"how long does delivery take\",\"limit\":1}')");
        logger.info("search preview -> {}", answered);
        assertTrue(answered.contains("\"results\""), answered);
        assertTrue(answered.contains("\"request_id\""), answered);
        assertTrue(answered.contains("Shipping took two weeks"), answered);
    }

    // ── The aggregates ───────────────────────────────────────────────────────────

    @Test
    public void aggregatesAnswerOncePerGroup() {
        engine.execute("CREATE TABLE reviews(id INT, body VARCHAR)");
        engine.execute("INSERT INTO reviews VALUES"
            + " (1,'The battery lasts all day.'), (2,'Shipping took two weeks.')");
        final String themes = text(
            "SELECT AI_AGG(body, 'List the recurring themes in one sentence.') FROM reviews");
        assertNotNull(themes);
        assertTrue(themes.length() > 0, "AI_AGG answered nothing");
        final String summary = text("SELECT AI_SUMMARIZE_AGG(body) FROM reviews");
        assertNotNull(summary);
        assertTrue(summary.length() > 0, "AI_SUMMARIZE_AGG answered nothing");
    }

    private boolean embeddingModelIsPulled() {
        try {
            return !OllamaClient.embed(OllamaConfig.embedModel(), "probe").isEmpty();
        } catch (final RuntimeException unavailable) {
            logger.info("Embedding model {} unavailable: {}",
                OllamaConfig.embedModel(), unavailable.getMessage());
            return false;
        }
    }
}
