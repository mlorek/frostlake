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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A qualified star reads only its own query's FROM. With no FROM its qualifier names no object, whatever the
 * queries around it read, and the select is refused as "Object 'FZ' does not exist or not authorized." ahead
 * of every name, function, subquery and clause rule; only a window frame's rule comes first. The qualifier is
 * echoed by its names, quoted only where a name needs it. The braced star still answers the empty object.
 * Every cell is live-verified.
 */
public class QualifiedStarWithoutFromTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTable() {
        engine.execute("CREATE OR REPLACE DATABASE STAR_QUALIFIER_DB");
        engine.execute("CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
    }

    @AfterEach
    public void dropDatabase() {
        engine.execute("DROP DATABASE IF EXISTS STAR_QUALIFIER_DB");
    }

    /** The first row's first cell, empty for no rows, or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The refusal of a statement that returns no rows, on one line; empty when it succeeds. */
    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String missing(final String echo) {
        return "SQL compilation error:|Object '" + echo + "' does not exist or not authorized.";
    }

    @Test
    public void aQualifiedStarWithNoFromNamesNoObject() {
        assertEquals(missing("FZ"), answer("SELECT fz.*"));
        assertEquals(missing("NOSUCH"), answer("SELECT nosuch.*"));
        assertEquals(missing("FZ"), answer("SELECT 1, fz.*"));
        assertEquals(missing("FZ"), answer("SELECT fz.* EXCLUDE (id)"));
        assertEquals(missing("FZ"), answer("SELECT fz.* WHERE 1 = 1"));
        assertEquals(missing("FZ"), answer("SELECT fz.* UNION ALL SELECT 1"));
        assertEquals(missing("FZ"), answer("SELECT 1 UNION ALL SELECT fz.*"));
        assertEquals(missing("FZ"), answer("SELECT SUM(1), fz.*"));
        assertEquals(missing("FZ"), answer("SELECT fz.*, ROW_NUMBER() OVER (ORDER BY 1)"));
        assertEquals("{}", answer("SELECT {fz.*}"));
    }

    @Test
    public void theQualifierIsEchoedByItsNames() {
        assertEquals(missing("STAR_QUALIFIER_DB.PUBLIC.FZ"), answer("SELECT STAR_QUALIFIER_DB.PUBLIC.FZ.*"));
        assertEquals(missing("STAR_QUALIFIER_DB.PUBLIC.FZ"), answer("SELECT STAR_QUALIFIER_DB..FZ.*"));
        assertEquals(missing("PUBLIC.FZ"), answer("SELECT PUBLIC.FZ.*"));
        assertEquals(missing("FZ"), answer("SELECT \"FZ\".*"));
        assertEquals(missing("\"fz\""), answer("SELECT \"fz\".*"));
        assertEquals(missing("\"a b\""), answer("SELECT \"a b\".*"));
        assertEquals(missing("\"fz\""), answer("SELECT \"fz\".* FROM FZ"));
        assertEquals(missing("\"a b\""), answer("SELECT \"a b\".* FROM FZ"));
        assertEquals(missing("X"), answer("SELECT x.* FROM FZ"));
    }

    @Test
    public void anOuterQueryLendsNoRelationToIt() {
        assertEquals(missing("FZ"), answer("SELECT id, (SELECT fz.*) AS x FROM FZ ORDER BY id"));
        assertEquals(missing("FZ"), answer("SELECT id FROM FZ WHERE EXISTS (SELECT fz.*)"));
        assertEquals(missing("FZ"), answer("SELECT id FROM FZ WHERE id IN (SELECT fz.*)"));
        assertEquals(missing("FZ"), answer("SELECT * FROM FZ, LATERAL (SELECT fz.*)"));
        assertEquals(missing("FZ"), answer("WITH c AS (SELECT fz.*) SELECT 1"));
        assertEquals(missing("FZ"), refusal("INSERT INTO FZ SELECT fz.*"));
        assertEquals(missing("FZ"), refusal("CREATE OR REPLACE VIEW V_STAR AS SELECT fz.*"));
    }

    @Test
    public void itComesBeforeNamesFunctionsAndClausesButAfterAFrame() {
        assertEquals(missing("FZ"), answer("SELECT fz.*, nosuchcol"));
        assertEquals(missing("FZ"), answer("SELECT nosuchfn(1), fz.*"));
        assertEquals(missing("FZ"), answer("SELECT (SELECT nosuch.*), fz.*"));
        assertEquals(missing("FZ"), answer("SELECT *, fz.*"));
        assertEquals(missing("FZ"), answer("SELECT fz.* WHERE nosuch = 1"));
        assertEquals(missing("FZ"), answer("SELECT fz.* ORDER BY nosuch"));
        assertEquals(missing("FZ"), answer("SELECT fz.* GROUP BY nosuch"));
        assertEquals(missing("FZ"), answer("SELECT fz.* HAVING nosuch = 1"));
        assertEquals(missing("FZ"), answer("SELECT fz.* QUALIFY 1 = 1"));
        assertEquals(missing("FZ"), answer("SELECT fz.*, INTERVAL '1 day'"));
        assertEquals(missing("FZ"), answer("SELECT fz.*, (SELECT 1, 2) = 5"));
        assertEquals("SQL compilation error: error line 1 at position 20|Window frame requires an ORDER BY clause.",
            answer("SELECT fz.*, SUM(1) OVER (ROWS BETWEEN 1 PRECEDING AND CURRENT ROW)"));
        assertEquals("SQL compilation error: error line 1 at position 35|invalid identifier 'NOSUCHCOL'",
            answer("SELECT (SELECT fz.*) FROM FZ WHERE nosuchcol = 1"));
        assertEquals("SQL compilation error:|Unknown function NOSUCHFN.",
            answer("SELECT nosuchfn(1) FROM FZ WHERE EXISTS (SELECT fz.*)"));
    }
}
