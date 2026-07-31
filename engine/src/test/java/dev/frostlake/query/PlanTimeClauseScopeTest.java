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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scope rejections — a qualifier naming no FROM-clause key ("invalid identifier 'R.V'") and a
 * bare name carried by both sides of an ON join ("ambiguous column name 'D'") — are COMPILE-time
 * in every clause, measured cell by cell on a real account: WHERE, GROUP BY keys (ROLLUP/CUBE
 * members included), HAVING (inside aggregate arguments too), and ORDER BY all reject over EMPTY
 * inputs, and ORDER BY rejects regardless of row count (a single row must not short-circuit the
 * check away).
 *
 * <p>Countervailing rules, equally measured: SELECT aliases are legal names in every one of these
 * clauses — WHERE included — so an alias key never trips the rejection; a USING join's
 * left-preference resolution compiles quietly; and ORDER BY resolves against the SELECT output
 * FIRST, so only a key matching NO output column falls through to the FROM-scope rules — where a
 * bare ON-join duplicate is then ambiguous even though the output-first rule would have saved the
 * same spelling had the column been selected.
 */
public class PlanTimeClauseScopeTest extends BaseDatabaseTest {

    @BeforeEach
    public void fixtures() {
        engine.execute("CREATE TABLE aa (k INTEGER, d INTEGER)");
        engine.execute("CREATE TABLE bb (k INTEGER, d INTEGER)");
        engine.execute("CREATE TABLE r (k INTEGER, v INTEGER)");
    }

    private void assertRejected(final String sql, final String expectedFragment) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(error.getMessage()).contains(expectedFragment),
            "expected \"" + expectedFragment + "\", got: " + error.getMessage());
    }

    /** All first-column values in row order, joined with {@code |}. */
    private String columnValues(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder joined = new StringBuilder();
        for (final Row row : rs.getRows()) {
            if (joined.length() > 0) {
                joined.append('|');
            }
            joined.append(row.getValue(0));
        }
        return joined.toString();
    }

    @Test
    public void whereRejectsOverEmptyInputs() {
        assertRejected("SELECT aa.k FROM aa JOIN bb ON aa.k = bb.k WHERE d = 1",
            "ambiguous column name 'D'");
        assertRejected("SELECT x.v FROM r AS x WHERE r.v = 1",
            "invalid identifier 'R.V'");
    }

    @Test
    public void groupByKeysRejectOverEmptyInputs() {
        assertRejected("SELECT max(x.v) FROM r AS x GROUP BY r.k",
            "invalid identifier 'R.K'");
        assertRejected("SELECT count(*) FROM aa JOIN bb ON aa.k = bb.k GROUP BY d",
            "ambiguous column name 'D'");
    }

    @Test
    public void havingRejectsInsideAggregateArgumentsOverEmptyInputs() {
        assertRejected("SELECT max(x.v) FROM r AS x GROUP BY x.k HAVING max(r.v) > 0",
            "invalid identifier 'R.V'");
    }

    @Test
    public void orderByRejectsOverEmptyAndSingleRowInputs() {
        assertRejected("SELECT x.v FROM r AS x ORDER BY r.v",
            "invalid identifier 'R.V'");
        engine.execute("INSERT INTO r VALUES (1, 10), (2, 20)");
        // A single surviving row must not short-circuit the compile-time rejection away.
        assertRejected("SELECT x.v FROM r AS x WHERE x.k = 1 ORDER BY r.v",
            "invalid identifier 'R.V'");
    }

    /**
     * ORDER BY resolves output-first, but only for names the SELECT actually outputs: a bare
     * duplicate NOT in the output is ambiguous — empty and populated inputs alike.
     */
    @Test
    public void orderByDuplicateNotInOutputIsAmbiguous() {
        assertRejected("SELECT aa.k FROM aa JOIN bb ON aa.k = bb.k ORDER BY d",
            "ambiguous column name 'D'");
        engine.execute("INSERT INTO aa VALUES (1, 100), (2, 200)");
        engine.execute("INSERT INTO bb VALUES (1, 999)");
        assertRejected("SELECT aa.k FROM aa JOIN bb ON aa.k = bb.k ORDER BY d",
            "ambiguous column name 'D'");
    }

    /** SELECT aliases are legal names in WHERE and GROUP BY — WHERE included, measured. */
    @Test
    public void selectAliasesResolveInWhereAndGroupBy() {
        engine.execute("INSERT INTO r VALUES (1, 10), (2, 20)");
        assertEquals("20", columnValues("SELECT v AS w FROM r WHERE w > 15"));
        assertEquals("10|20", columnValues("SELECT v AS w, count(*) FROM r GROUP BY w ORDER BY w"));
        engine.execute("INSERT INTO aa VALUES (1, 100), (2, 200)");
        engine.execute("INSERT INTO bb VALUES (1, 999)");
        // The alias resolves even when the join carries same-named duplicates elsewhere.
        assertEquals("100", columnValues("SELECT aa.d AS q FROM aa JOIN bb ON aa.k = bb.k WHERE q > 0"));
    }

    /** A USING join's left-preference resolution compiles quietly over empty inputs. */
    @Test
    public void usingJoinLeftPreferenceStillCompiles() {
        assertEquals("", columnValues("SELECT aa.k FROM aa JOIN bb USING (k) WHERE d = 1"));
    }

    /**
     * A bare name known nowhere in scope is "invalid identifier 'NOSUCH'" at compile time — in the
     * SELECT list, WHERE, GROUP BY, HAVING (aggregate arguments included) and ORDER BY, all over
     * EMPTY inputs.
     */
    @Test
    public void unknownBareNamesRejectInEveryClause() {
        assertRejected("SELECT nosuch FROM r", "invalid identifier 'NOSUCH'");
        assertRejected("SELECT k FROM r WHERE nosuch = 1", "invalid identifier 'NOSUCH'");
        assertRejected("SELECT count(*) FROM r GROUP BY nosuch", "invalid identifier 'NOSUCH'");
        assertRejected("SELECT max(v) FROM r GROUP BY k HAVING nosuch > 0", "invalid identifier 'NOSUCH'");
        assertRejected("SELECT k FROM r GROUP BY k HAVING max(nosuch) > 0", "invalid identifier 'NOSUCH'");
        assertRejected("SELECT v FROM r ORDER BY nosuch", "invalid identifier 'NOSUCH'");
    }

    /**
     * Exactly six context functions work WITHOUT parentheses; any other bare function name is an
     * invalid identifier, as is CONNECT BY's LEVEL outside a hierarchical query.
     */
    @Test
    public void parenlessContextFunctionsAreExactlyTheAnsiSet() {
        engine.execute("CREATE TABLE dts (k INTEGER, dt DATE)");
        assertEquals("", columnValues(
            "SELECT k FROM dts WHERE dt < CURRENT_DATE AND dt < CURRENT_TIMESTAMP"
            + " AND dt < LOCALTIMESTAMP AND CURRENT_USER IS NOT NULL"));
        assertRejected("SELECT k FROM r WHERE uuid_string = 'x'", "invalid identifier 'UUID_STRING'");
        assertRejected("SELECT v FROM r WHERE LEVEL > 0", "invalid identifier 'LEVEL'");
    }

    /**
     * A date/time-unit bareword is legal only in ARGUMENT position ({@code DATEADD(HOUR, …)});
     * written as a top-level name it is an invalid identifier.
     */
    @Test
    public void dateTimeUnitBarewordsAreArgumentOnly() {
        engine.execute("CREATE TABLE dts2 (k INTEGER, dt DATE)");
        assertEquals("", columnValues("SELECT dateadd(hour, 1, dt) FROM dts2"));
        assertRejected("SELECT k FROM dts2 WHERE year = 1", "invalid identifier 'YEAR'");
    }

    /**
     * A star RENAME target is a referencable output name (WHERE filters on it); a lateral column
     * alias is visible only to LATER items — a forward reference is an invalid identifier — and a
     * window function's arguments may reference an earlier alias.
     */
    @Test
    public void outputNameVisibilityRules() {
        engine.execute("INSERT INTO r VALUES (1, 10), (2, 20)");
        assertEquals("2", columnValues("SELECT * RENAME (v AS w) FROM r WHERE w > 15"));
        assertRejected("SELECT y + 1 AS x, 1 AS y FROM r", "invalid identifier 'Y'");
        assertEquals("10|20", columnValues("SELECT v AS w, sum(w) OVER () FROM r ORDER BY 1"));
    }
}
