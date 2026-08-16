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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Which keyword words may NAME a column, measured against a live account word by word over every
 * keyword the grammar lexes.
 *
 * <p>Snowflake reserves 64 words. Everything else it knows — {@code auto}, {@code limit},
 * {@code primary}, {@code show}, {@code merge}, the type names, the procedural keywords — is an
 * ordinary name. Frostlake refused 157 of them, purely because it tokenises them for some statement
 * it supports, which is the mirror image of the usual fidelity bug: refusing SQL the account accepts.
 *
 * <p>THREE POSITIONS, and they do not agree. A word may be usable in a column DEFINITION but not as a
 * REFERENCE — live defines a column called {@code case} or {@code default} and then cannot select it,
 * because the expression those words lead owns that spot. The five like that are asserted below.
 */
public class KeywordColumnNameTest extends BaseDatabaseTest {

    /** One word from each family the fix recovered — types, clauses, DDL verbs, procedural words. */
    private static final String[] NAMEABLE = {
        "auto", "limit", "top", "primary", "foreign", "show", "merge", "call", "begin", "commit",
        "declare", "let", "return", "loop", "while", "until", "open", "fetch", "raise", "execution",
        "int", "varchar", "float", "boolean", "variant", "object", "array", "geography", "timestamp_tz",
        "task", "pipe", "sequence", "procedure", "function", "view", "materialized", "transient",
        "over", "within", "using", "lateral", "full", "apply", "at", "asc", "desc", "end", "if",
        "no", "read", "write", "share", "usage", "monitor", "operate", "ownership",
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE kwt (" + declarations() + ")");
        engine.execute("INSERT INTO kwt VALUES (" + ones() + ")");
    }

    private static String declarations() {
        final StringBuilder out = new StringBuilder();
        for (final String word : NAMEABLE) {
            out.append(out.length() == 0 ? "" : ", ").append(word).append(" NUMBER");
        }
        return out.toString();
    }

    private static String ones() {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < NAMEABLE.length; i++) {
            out.append(i == 0 ? "" : ", ").append(i + 1);
        }
        return out.toString();
    }

    /** Every one of them declares, in a single table — the whole set at once, not one at a time. */
    @Test
    public void everyNameableKeywordDeclaresAsAColumn() {
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) AS c FROM kwt");
        rs.next();
        assertEquals(1L, ((Number) rs.getValue("c")).longValue());
    }

    /** And each can be SELECTED by name, which is a different grammar position from the definition. */
    @Test
    public void eachOneCanBeSelectedByName() {
        for (int i = 0; i < NAMEABLE.length; i++) {
            final ResultSet rs = engine.executeQuery("SELECT " + NAMEABLE[i] + " AS v FROM kwt");
            rs.next();
            assertEquals(i + 1, ((Number) rs.getValue("v")).intValue(), NAMEABLE[i] + " selected wrong");
        }
    }

    /** And named in the clauses, which is where an alias-hungry parse would swallow the keyword. */
    @Test
    public void eachOneWorksInWhereGroupByAndOrderBy() {
        for (final String word : NAMEABLE) {
            final ResultSet rs = engine.executeQuery("SELECT " + word + " FROM kwt WHERE " + word
                + " > 0 GROUP BY " + word + " ORDER BY " + word);
            assertEquals(1, rs.getRowCount(), word + " did not survive the clauses");
        }
    }

    /**
     * The words that DEFINE but cannot be REFERENCED, which is live's own asymmetry: the expression
     * they lead owns the reference position, so `SELECT cast FROM t` is a syntax error on both sides
     * while `CREATE TABLE t (cast INT)` is fine on both.
     */
    @Test
    public void theExpressionLeadingWordsDefineButCannotBeSelected() {
        for (final String word : new String[]{"case", "cast", "default", "try_cast", "when"}) {
            engine.execute("CREATE OR REPLACE TABLE kw_lead (" + word + " NUMBER)");
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT " + word + " FROM kw_lead");
                }
            }, word + " should not be selectable by name");
        }
    }

    /** A reserved word is still refused — the fix widened the names, it did not remove the line. */
    @Test
    public void aReservedWordIsStillRefused() {
        for (final String reserved : new String[]{"select", "from", "where", "order", "table", "null"}) {
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("CREATE OR REPLACE TABLE kw_bad (" + reserved + " NUMBER)");
                }
            }, reserved + " is reserved and must stay refused");
        }
    }

    /**
     * TRUE and FALSE are nameable too, but a bare reference is the LITERAL, not the column — the only
     * words in the set whose reference resolves to something other than the column. They declare, and
     * the column is reachable by qualifying it.
     */
    @Test
    public void aBooleanLiteralWinsOverAColumnOfThatName() {
        engine.execute("CREATE OR REPLACE TABLE kw_bool (false NUMBER, true NUMBER)");
        engine.execute("INSERT INTO kw_bool VALUES (7, 8)");
        final ResultSet bare = engine.executeQuery("SELECT false AS v FROM kw_bool");
        bare.next();
        assertEquals(Boolean.FALSE, bare.getValue("v"), "a bare false is the literal, not the column");
        final ResultSet qualified = engine.executeQuery("SELECT kw_bool.false AS v FROM kw_bool");
        qualified.next();
        assertEquals(7, ((Number) qualified.getValue("v")).intValue(), "qualifying reaches the column");
    }

    /** The keyword readings still win where the word is a keyword: ROLLUP/CUBE name a super-group. */
    @Test
    public void theSuperGroupKeywordsStillParseAsKeywords() {
        final ResultSet rollup = engine.executeQuery(
            "SELECT top, COUNT(*) AS c FROM kwt GROUP BY ROLLUP(top)");
        assertEquals(2, rollup.getRowCount(), "ROLLUP(top) should give the detail row and the total");
        final ResultSet cube = engine.executeQuery(
            "SELECT top, COUNT(*) AS c FROM kwt GROUP BY CUBE(top)");
        assertEquals(2, cube.getRowCount(), "CUBE(top) should give the detail row and the total");
    }
}
