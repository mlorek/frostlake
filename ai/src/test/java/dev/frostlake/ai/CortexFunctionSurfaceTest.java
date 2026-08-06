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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which Cortex names exist, and under which spelling — live-verified against a real account, whose
 * error distinguishes a function that exists from one that does not even when the account may not run
 * it. The spelling is not uniform and the divergences are the point of this test: registering a name
 * Snowflake does not have would let a query run here that fails there.
 *
 * <p>None of these assertions needs a model server. A resolvable function is one whose failure is
 * anything OTHER than "unknown function" — reaching the transport is proof enough that the name
 * resolved and the arity was accepted.
 */
public class CortexFunctionSurfaceTest extends BaseDatabaseTest {

    private static final String UNKNOWN = "Unknown function";

    /**
     * Point the pack at a port nothing listens on. A name that resolves still reaches the transport and
     * fails there, which is all these assertions read — and no assertion here depends on what a model
     * would have said, so a machine that happens to be running one gets the same fast answer as a
     * machine that is not.
     */
    @BeforeEach
    public void pointAtNothing() {
        System.setProperty(OllamaConfig.URL, "http://localhost:1");
        System.setProperty(OllamaConfig.TIMEOUT_MS, "500");
    }

    @AfterEach
    public void clearOverrides() {
        System.clearProperty(OllamaConfig.URL);
        System.clearProperty(OllamaConfig.TIMEOUT_MS);
    }

    /** Runs the query and gives back whatever went wrong, or null when nothing did. */
    private String failure(final String sql) {
        try {
            engine.executeQuery(sql);
            return null;
        } catch (final RuntimeException failed) {
            return failed.getMessage() == null ? "" : failed.getMessage();
        }
    }

    private void assertResolves(final String sql) {
        final String failed = failure(sql);
        assertFalse(failed != null && failed.contains(UNKNOWN), sql + " -> " + failed);
    }

    private void assertUnknown(final String sql) {
        final String failed = failure(sql);
        assertTrue(failed != null && failed.contains(UNKNOWN), sql + " -> " + failed);
    }

    // ── The original set: qualified only ──────────────────────────────────────────

    @Test
    public void originalFunctionsResolveUnderTheCortexSchema() {
        assertResolves("SELECT SNOWFLAKE.CORTEX.SENTIMENT('x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.SUMMARIZE('x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.TRANSLATE('x','en','de')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.COMPLETE('m','x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.TRY_COMPLETE('m','x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.EXTRACT_ANSWER('src','q')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.CLASSIFY_TEXT('x', ['a','b'])");
        assertResolves("SELECT SNOWFLAKE.CORTEX.COUNT_TOKENS('m','x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.ENTITY_SENTIMENT('x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.EMBED_TEXT_768('m','x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.EMBED_TEXT_1024('m','x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.SEARCH_PREVIEW('svc','{}')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.AI_SUMMARIZE('x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.AI_TRANSLATE('x','en','de')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.AI_COUNT_TOKENS('AI_SENTIMENT','x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_RECURSIVE_CHARACTER('x','none',10,0)");
        assertResolves("SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_MARKDOWN_HEADER('# x',"
            + " OBJECT_CONSTRUCT('#','h1'), 10, 0)");
    }

    /** Bare, they do not exist — a real account answers "Unknown function SENTIMENT." */
    @Test
    public void originalFunctionsDoNotExistBare() {
        assertUnknown("SELECT SENTIMENT('x')");
        assertUnknown("SELECT SUMMARIZE('x')");
        assertUnknown("SELECT COMPLETE('m','x')");
        assertUnknown("SELECT TRY_COMPLETE('m','x')");
        assertUnknown("SELECT EXTRACT_ANSWER('src','q')");
        assertUnknown("SELECT CLASSIFY_TEXT('x', ['a','b'])");
        assertUnknown("SELECT COUNT_TOKENS('m','x')");
        assertUnknown("SELECT ENTITY_SENTIMENT('x')");
        assertUnknown("SELECT EMBED_TEXT_768('m','x')");
        assertUnknown("SELECT SEARCH_PREVIEW('svc','{}')");
        // AI_SUMMARIZE / AI_TRANSLATE / AI_COUNT_TOKENS are deliberately NOT in this list: measured,
        // they resolve BARE as well as qualified, like the rest of the AI_ family. They sat here on the
        // assumption that only the AI_COMPLETE group had a bare spelling.
    }

    /** The bare TRANSLATE is the engine's own string function, untouched by the pack. */
    @Test
    public void bareTranslateIsStillTheStringFunction() {
        assertEqualsText("aBc", "SELECT TRANSLATE('abc','b','B')");
    }

    private void assertEqualsText(final String expected, final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        assertTrue(expected.equals(String.valueOf(value)), sql + " -> " + value);
    }

    // ── The AI_ family: both spellings ────────────────────────────────────────────

    @Test
    public void aiFamilyResolvesQualifiedAndBare() {
        assertResolves("SELECT SNOWFLAKE.CORTEX.AI_COMPLETE('m','x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.AI_CLASSIFY('x', ['a','b'])");
        assertResolves("SELECT SNOWFLAKE.CORTEX.AI_FILTER('x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.AI_SENTIMENT('x')");
        assertResolves("SELECT AI_COMPLETE('m','x')");
        assertResolves("SELECT AI_CLASSIFY('x', ['a','b'])");
        assertResolves("SELECT AI_FILTER('x')");
        assertResolves("SELECT AI_SENTIMENT('x')");
        // Measured bare too, which this pack originally registered qualified-only: AI_SUMMARIZE
        // answers a bare call, and AI_TRANSLATE and AI_COUNT_TOKENS refuse a wrong-arity bare call
        // for its ARITY — which only a name that RESOLVED can do.
        assertResolves("SELECT SNOWFLAKE.CORTEX.AI_SUMMARIZE('x')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.AI_TRANSLATE('x','en','de')");
        assertResolves("SELECT SNOWFLAKE.CORTEX.AI_COUNT_TOKENS('m','x')");
        assertResolves("SELECT AI_SUMMARIZE('x')");
        assertResolves("SELECT AI_TRANSLATE('x','en','de')");
        assertResolves("SELECT AI_COUNT_TOKENS('m','x')");
    }

    /** Three of the family are bare-only: there is no SNOWFLAKE.CORTEX spelling for them. */
    @Test
    public void similarityAndTheAggregatesAreBareOnly() {
        assertResolves("SELECT AI_SIMILARITY('a','b')");
        assertUnknown("SELECT SNOWFLAKE.CORTEX.AI_SIMILARITY('a','b')");
        assertUnknown("SELECT SNOWFLAKE.CORTEX.AI_AGG('a','b')");
        assertUnknown("SELECT SNOWFLAKE.CORTEX.AI_SUMMARIZE_AGG('a')");
    }

    /** Read as an object path rather than a function: a two-part name is not a Cortex spelling. */
    @Test
    public void twoPartNameDoesNotResolve() {
        assertUnknown("SELECT CORTEX.SENTIMENT('x')");
    }

    // ── Arity, which a real account checks before it checks anything else ─────────

    @Test
    public void sentimentTakesExactlyOneArgument() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT SNOWFLAKE.CORTEX.SENTIMENT('a','b')");
            }
        });
        assertTrue(error.getMessage().contains("expected 1, got 2"), error.getMessage());
    }

    @Test
    public void countTokensTakesTwoArguments() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT SNOWFLAKE.CORTEX.COUNT_TOKENS('m')");
            }
        });
        assertTrue(error.getMessage().contains("expected 2, got 1"), error.getMessage());
    }

    @Test
    public void translateTakesThreeArguments() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT SNOWFLAKE.CORTEX.TRANSLATE('a','b')");
            }
        });
        assertTrue(error.getMessage().contains("expected 3, got 2"), error.getMessage());
    }

    @Test
    public void similarityTakesTwoArguments() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT AI_SIMILARITY('a')");
            }
        });
        assertTrue(error.getMessage().contains("expected 2, got 1"), error.getMessage());
    }
}
