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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Where a refused statement's fault lands is a function of the STATEMENT, not of what the JVM parsed
 * before it. ANTLR shares one prediction cache across every parse, and for a statement with several
 * viable failure points that cache decided which one the parse settled on: {@code SELECT a, 1 * FROM
 * t)} reported {@code unexpected 'FROM'} at position 14 in a fresh JVM and {@code unexpected ','} at
 * position 8 once other statements had warmed it. The account answers the first of those.
 *
 * <p>A refused statement is now parsed a second time with a prediction cache of its own, and its
 * faults are read from that parse.
 */
public class RefusalPositionIsStableTest extends BaseDatabaseTest {

    /** Statements whose parse exercises enough of the grammar to warm the shared cache. */
    private static final String[] WARMING = {
        "CREATE OR REPLACE TABLE fl (id INT, entries VARIANT)",
        "INSERT INTO fl SELECT 1, PARSE_JSON('[1,2]')",
        "SELECT s.id FROM fl s, TABLE(FLATTEN(s.entries)) f",
        "SELECT s.id FROM fl s, TABLE(FLATTEN(s.entries, outer => TRUE)) f",
        "SELECT * FROM TABLE(FLATTEN(input => NULL, outer => TRUE))",
        "SELECT a FROM t WHERE a IN (SELECT b FROM t) ORDER BY a",
        "SELECT COUNT(*) FROM t GROUP BY a HAVING COUNT(*) > 0",
        "SELECT a, (a + 1) * 2 FROM (SELECT a FROM t) x"};

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
    }

    /** The refusal a statement raises, or "OK". */
    private String outcome(final String sql) {
        try {
            engine.executeQuery(sql);
            return "OK";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Runs every warming statement, ignoring what each answers. */
    private void warmThePredictionCache() {
        for (final String sql : WARMING) {
            try {
                engine.executeQuery(sql);
            } catch (final RuntimeException ignored) {
                continue;
            }
        }
    }

    /** The statement the flip was found on, with the answer the account gives. */
    @Test
    public void theFaultLandsWhereTheAccountPutsIt() {
        assertEquals("SQL compilation error: syntax error line 1 at position 14 unexpected 'FROM'."
            + " syntax error line 1 at position 20 unexpected ')'.",
            outcome("SELECT a, 1 * FROM t)"));
    }

    /** And it lands there just the same once the shared cache has been warmed. */
    @Test
    public void warmingTheCacheDoesNotMoveIt() {
        final String before = outcome("SELECT a, 1 * FROM t)");
        warmThePredictionCache();
        assertEquals(before, outcome("SELECT a, 1 * FROM t)"));
    }

    /** The other shapes keep their own answers, warm or cold. */
    @Test
    public void theOtherShapesAreStableToo() {
        final String[] refused = {
            "SELECT FROM t",
            "SELECT a FROM t WHERE",
            "SELECT a, 1 * FROM t)",
            "SELECT a FROM t GROUP BY"};
        final String[] before = new String[refused.length];
        for (int i = 0; i < refused.length; i++) {
            before[i] = outcome(refused[i]);
        }
        warmThePredictionCache();
        for (int i = 0; i < refused.length; i++) {
            assertEquals(before[i], outcome(refused[i]), refused[i]);
        }
    }

    /** A statement that PARSES is untouched by any of this. */
    @Test
    public void acceptedStatementsStillRun() {
        warmThePredictionCache();
        assertEquals("OK", outcome("SELECT a, b FROM t ORDER BY a"));
        assertEquals("OK", outcome("SELECT COUNT(*) FROM t"));
    }
}
