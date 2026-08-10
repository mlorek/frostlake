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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which keywords may NAME something, measured position by position against a live account. The answer
 * is not one list: it depends on what else could stand in that spot.
 *
 * <pre>
 *   position                                CASE CAST CONSTRAINT DEFAULT WHEN   the 8 join words
 *   table / view / schema / RENAME TO / CTE  yes  yes    yes       yes    yes        yes
 *   bare FROM reference                      yes  yes    yes       yes    yes        NO
 *   qualified FROM reference (w.t)           yes  yes    yes       yes    yes        yes
 *   column DEFINITION                        yes  yes    NO        yes    yes        yes
 *   expression (SELECT w FROM t)             NO   NO     NO        NO     NO         yes
 * </pre>
 *
 * <p>The join words are the mirror image of the other five: they are ordinary names everywhere EXCEPT
 * a bare FROM, where the join grammar owns the word — live reads `FROM asof` as the start of an ASOF
 * JOIN and refuses it at end of input, while `FROM asof.t` reaches a missing schema. And the five are
 * names everywhere a name is the ONLY thing that can appear, but never in an expression: live refuses
 * `SELECT case FROM t` EVEN WHEN a column of that name exists, so defining one and referencing it are
 * different vocabularies.
 */
public class NameKeywordPositionTest extends BaseDatabaseTest {

    private static final String[] NAME_WORDS = {"CASE", "CAST", "CONSTRAINT", "DEFAULT", "WHEN"};

    private static final String[] JOIN_WORDS = {
        "ASOF", "FULL", "LATERAL", "LEFT", "MATCH_CONDITION", "NATURAL", "RIGHT", "USING"};

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE kw (a INT)");
        engine.execute("INSERT INTO kw VALUES (1)");
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return null;
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    /** All five name the objects live lets them name. */
    @Test
    public void theFiveWordsNameEveryObject() {
        for (final String word : NAME_WORDS) {
            assertEquals(null, refusal("CREATE TABLE " + word + " (a INT)"), word + " table");
            assertEquals(null, refusal("ALTER TABLE " + word + " RENAME TO r_" + word),
                word + " rename source");
            assertEquals(null, refusal("ALTER TABLE r_" + word + " RENAME TO " + word),
                word + " rename target");
            assertEquals(null, refusal("CREATE VIEW v_" + word + " AS SELECT a FROM " + word),
                word + " view over it");
            assertEquals(null, refusal("DROP VIEW v_" + word), word + " drop view");
            assertEquals(null, refusal("DROP TABLE " + word), word + " drop table");
        }
    }

    /** They name a CTE too, and the CTE is readable by that name. */
    @Test
    public void theFiveWordsNameACte() {
        for (final String word : NAME_WORDS) {
            final ResultSet rs = engine.executeQuery(
                "WITH " + word + " AS (SELECT 7 AS x) SELECT x FROM " + word);
            rs.next();
            assertEquals(7, ((Number) rs.getValue("x")).intValue(), word + " as a CTE name");
        }
    }

    /** A schema takes the same five, and creating one makes it current — so it is checked apart. */
    @Test
    public void theFiveWordsNameASchema() {
        for (final String word : NAME_WORDS) {
            assertEquals(null, refusal("CREATE SCHEMA IF NOT EXISTS s_" + word), word + " schema");
        }
        engine.execute("USE SCHEMA test_schema");
    }

    /**
     * But none of them is a column REFERENCE — not even when a column of that name exists. CONSTRAINT
     * cannot even BE a column: live refuses `CREATE TABLE t (constraint INT)`, where the word leads a
     * table constraint, so only the other four are defined here.
     */
    @Test
    public void theFiveWordsAreNoColumnReference() {
        engine.execute("CREATE TABLE defs (CASE INT, CAST INT, DEFAULT INT, WHEN INT)");
        for (final String word : NAME_WORDS) {
            assertTrue(refusal("SELECT " + word + " FROM defs") != null,
                word + " must not be selectable by name");
            assertTrue(refusal("SELECT a FROM kw WHERE " + word + " = 1") != null,
                word + " must not be a WHERE reference");
        }
    }

    /** The `<name>.*` qualifier IS a name, so all five reach it — the guard must not eat this one. */
    @Test
    public void theFiveWordsQualifyAStar() {
        for (final String word : NAME_WORDS) {
            engine.execute("CREATE OR REPLACE TABLE " + word + " (a INT)");
            engine.execute("INSERT INTO " + word + " VALUES (3)");
            final ResultSet rs = engine.executeQuery(
                "SELECT " + word + ".* FROM " + word);
            rs.next();
            assertEquals(3, ((Number) rs.getValue("a")).intValue(), word + ".* must expand");
            engine.execute("DROP TABLE " + word);
        }
    }

    /** A join word is refused BARE in FROM — the join clause owns the word there. */
    @Test
    public void aJoinWordIsNoBareTableReference() {
        for (final String word : JOIN_WORDS) {
            final String message = refusal("SELECT a FROM " + word);
            assertTrue(message != null && message.contains("syntax error"),
                "FROM " + word + " must be a syntax error, but gave: " + message);
        }
    }

    /** Qualified, the same word is an ordinary container name. */
    @Test
    public void aJoinWordQualifiesAName() {
        for (final String word : JOIN_WORDS) {
            final String message = refusal("SELECT a FROM " + word + ".kw");
            assertTrue(message != null, word + ".kw should not resolve");
            assertFalse(message.contains("syntax error"),
                word + ".kw must PARSE, but gave: " + message);
        }
    }

    /** And it names a table and a CTE like any other word. */
    @Test
    public void aJoinWordNamesATableAndACte() {
        for (final String word : JOIN_WORDS) {
            assertEquals(null, refusal("CREATE TABLE " + word + " (a INT)"), word + " as a table");
            assertEquals(null, refusal("INSERT INTO " + word + " VALUES (1)"), word + " insert");
            assertEquals(null, refusal("DROP TABLE " + word), word + " drop");
            final ResultSet rs = engine.executeQuery(
                "WITH " + word + " AS (SELECT 5 AS x) SELECT x FROM \"" + word + "\"");
            rs.next();
            assertEquals(5, ((Number) rs.getValue("x")).intValue(), word + " as a CTE name");
        }
    }

    /**
     * CONSTRAINT is the one word whose two positions DIVERGE, and the whole split exists for it: live
     * READS it wherever a name is read — after a dot, in an INSERT column list, as an UPDATE SET
     * target — and refuses it only where a column is DEFINED, because there the word leads a table
     * constraint. Every other word tested behaves identically in both positions.
     */
    @Test
    public void constraintIsReadableButNotDefinable() {
        assertTrue(String.valueOf(refusal("SELECT kw.constraint FROM kw"))
            .contains("invalid identifier 'KW.CONSTRAINT'"),
            "after a dot it must PARSE and fail on the name: " + refusal("SELECT kw.constraint FROM kw"));
        assertTrue(String.valueOf(refusal("INSERT INTO kw (constraint) VALUES (1)"))
            .contains("invalid identifier 'CONSTRAINT'"),
            "in a column list: " + refusal("INSERT INTO kw (constraint) VALUES (1)"));
        assertTrue(String.valueOf(refusal("UPDATE kw SET constraint = 1"))
            .contains("invalid identifier 'CONSTRAINT'"),
            "as an UPDATE target: " + refusal("UPDATE kw SET constraint = 1"));
        // And DEFINING one is a syntax error, at the close paren — live reads `CONSTRAINT INT` as the
        // start of a table constraint and trips when the clause it expects does not arrive.
        assertTrue(String.valueOf(refusal("CREATE OR REPLACE TABLE cd (CONSTRAINT INT)"))
            .contains("syntax error line 1 at position 42 unexpected ')'"),
            "defining one: " + refusal("CREATE OR REPLACE TABLE cd (CONSTRAINT INT)"));
    }

    /** The words that behave the SAME in both positions keep doing so — the split must not move them. */
    @Test
    public void everyOtherWordAgreesInBothPositions() {
        for (final String word : new String[]{"CASE", "CAST", "DEFAULT", "WHEN", "INNER", "JOIN",
            "LEFT", "CROSS"}) {
            assertEquals(null, refusal("CREATE OR REPLACE TABLE cd_" + word + " (" + word + " INT)"),
                word + " must still DEFINE a column");
            assertTrue(String.valueOf(refusal("SELECT kw." + word + " FROM kw"))
                .contains("invalid identifier 'KW." + word + "'"),
                word + " after a dot: " + refusal("SELECT kw." + word + " FROM kw"));
        }
    }

    /** A word that names nothing anywhere stays refused in every one of these positions. */
    @Test
    public void aReservedWordIsStillNoName() {
        for (final String word : new String[]{"VALUES", "ORDER"}) {
            assertTrue(refusal("CREATE TABLE " + word + " (a INT)") != null, word + " table");
            assertTrue(refusal("CREATE VIEW " + word + " AS SELECT 1 AS x") != null, word + " view");
            assertTrue(refusal("WITH " + word + " AS (SELECT 1 AS x) SELECT x FROM kw") != null,
                word + " cte");
            assertTrue(refusal("SELECT a FROM " + word) != null, word + " from");
        }
    }
}
