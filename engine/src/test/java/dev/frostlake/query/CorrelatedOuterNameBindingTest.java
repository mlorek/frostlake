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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A subquery reads the outer row by the names the row's columns are spelled with, exactly: a quoted lower-case
 * name does not reach a column V, a bare name does not reach a column "v", and a bare name two joined outer
 * relations both carry — a table joined to itself included — is ambiguous unless a USING or NATURAL join merged
 * it, quoted as the column is spelled; the subquery's own relations still answer a name first. Every cell is
 * live-verified.
 */
public class CorrelatedOuterNameBindingTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE q (v NUMBER)");
        engine.execute("INSERT INTO q VALUES (1), (2)");
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN, s VARCHAR)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE, 'a'), (7, FALSE, 'b')");
        engine.execute("CREATE TABLE g (id INT, v INT, s VARCHAR)");
        engine.execute("INSERT INTO g VALUES (5, 50, 'a'), (6, 60, 'c')");
        engine.execute("CREATE TABLE ql (\"v\" INT)");
        engine.execute("INSERT INTO ql VALUES (3), (4)");
        engine.execute("CREATE TABLE qb (\"v\" INT, V INT)");
        engine.execute("INSERT INTO qb VALUES (3, 30), (4, 40)");
    }

    /** Every row, its cells joined by a comma and the rows by a bar, lower-cased. */
    private String rows(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    answer.append(", ");
                }
                answer.append(String.valueOf(row.getValue(i)));
            }
        }
        return answer.toString().toLowerCase();
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], rows(cell[0]), cell[0]);
        }
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            sql + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    @Test
    public void aQuotedLowerCaseNameDoesNotReachAColumnV() {
        final String[][] refused = {
            {"SELECT (SELECT \"v\") FROM q ORDER BY 1", "error line 1 at position 15\ninvalid identifier '\"v\"'"},
            {"SELECT v FROM q WHERE EXISTS (SELECT \"v\") ORDER BY v",
                "error line 1 at position 37\ninvalid identifier '\"v\"'"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 FROM g WHERE \"v\" = 1) ORDER BY v",
                "error line 1 at position 52\ninvalid identifier '\"v\"'"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE \"v\" = 1) ORDER BY v",
                "error line 1 at position 45\ninvalid identifier '\"v\"'"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE q.\"v\" = 1) ORDER BY v",
                "error line 1 at position 45\ninvalid identifier 'Q.\"v\"'"},
            {"SELECT v FROM q WHERE v IN (SELECT \"v\") ORDER BY v",
                "error line 1 at position 35\ninvalid identifier '\"v\"'"},
            {"SELECT (SELECT \"v\" + 1) FROM q ORDER BY 1", "error line 1 at position 15\ninvalid identifier '\"v\"'"},
            {"SELECT (SELECT \"v\") FROM q t ORDER BY 1", "error line 1 at position 15\ninvalid identifier '\"v\"'"},
            {"SELECT (SELECT t.\"v\") FROM q t ORDER BY 1",
                "error line 1 at position 15\ninvalid identifier 'T.\"v\"'"},
            {"SELECT (SELECT \"v\") FROM (SELECT 1 AS v) ORDER BY 1",
                "error line 1 at position 15\ninvalid identifier '\"v\"'"},
            {"DELETE FROM q WHERE EXISTS (SELECT 1 WHERE \"v\" = 1)",
                "error line 1 at position 43\ninvalid identifier '\"v\"'"},
            {"UPDATE q SET v = 3 WHERE EXISTS (SELECT 1 WHERE \"v\" = 1)",
                "error line 1 at position 48\ninvalid identifier '\"v\"'"},
        };
        for (final String[] cell : refused) {
            assertRefused(cell[0], cell[1]);
        }
        assertCells(new String[][] {
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE \"V\" = 1) ORDER BY v", "1"},
            {"SELECT v FROM q WHERE EXISTS (SELECT 1 WHERE \"Q\".v = 1) ORDER BY v", "1"},
            {"SELECT v FROM q ORDER BY 1", "1 | 2"},
        });
    }

    @Test
    public void aBareNameDoesNotReachAColumnSpelledLowerCase() {
        assertRefused("SELECT (SELECT v) FROM ql ORDER BY 1", "error line 1 at position 15\ninvalid identifier 'V'");
        assertRefused("SELECT (SELECT ql.v) FROM ql ORDER BY 1",
            "error line 1 at position 15\ninvalid identifier 'QL.V'");
        assertRefused("SELECT \"v\" FROM ql WHERE EXISTS (SELECT 1 WHERE v = 3) ORDER BY 1",
            "error line 1 at position 48\ninvalid identifier 'V'");
        assertCells(new String[][] {
            {"SELECT (SELECT \"v\") FROM ql ORDER BY 1", "3 | 4"},
            {"SELECT (SELECT ql.\"v\") FROM ql ORDER BY 1", "3 | 4"},
            {"SELECT \"v\" FROM ql WHERE EXISTS (SELECT 1 WHERE \"v\" = 3) ORDER BY 1", "3"},
            {"SELECT (SELECT \"v\"), (SELECT v) FROM qb ORDER BY 1", "3, 30 | 4, 40"},
            {"SELECT \"v\" FROM qb WHERE EXISTS (SELECT 1 WHERE \"v\" = 3) ORDER BY 1", "3"},
            {"SELECT \"v\" FROM qb WHERE EXISTS (SELECT 1 WHERE v = 40) ORDER BY 1", "4"},
            {"SELECT \"v\" FROM qb t WHERE EXISTS (SELECT 1 WHERE t.\"v\" = 3 AND t.v = 30) ORDER BY 1", "3"},
        });
    }

    @Test
    public void aBareNameTwoJoinedOuterRelationsShareIsAmbiguous() {
        final String[] ambiguous = {
            "SELECT (SELECT v) FROM q JOIN g ON TRUE ORDER BY 1",
            "SELECT q.v FROM q JOIN g ON TRUE WHERE EXISTS (SELECT v) ORDER BY 1",
            "SELECT q.v FROM q JOIN g ON TRUE WHERE EXISTS (SELECT 1 FROM fz WHERE v = 1) ORDER BY 1",
            "SELECT q.v FROM q JOIN g ON TRUE WHERE EXISTS (SELECT 1 WHERE v = 1) ORDER BY 1",
            "SELECT (SELECT v) FROM q, g ORDER BY 1",
            "SELECT (SELECT v) FROM q LEFT JOIN g ON q.v = g.id ORDER BY 1",
            "SELECT (SELECT (SELECT v)) FROM q JOIN g ON TRUE ORDER BY 1",
            "SELECT (SELECT v) FROM q JOIN (SELECT 1 AS v) d ON TRUE ORDER BY 1",
            "SELECT q.v FROM q JOIN g ON TRUE WHERE q.v IN (SELECT v) ORDER BY 1",
            "SELECT (SELECT COUNT(*) FROM fz WHERE fz.id < v) FROM q JOIN g ON TRUE ORDER BY 1",
            "SELECT (SELECT v) FROM q JOIN g ON TRUE WHERE FALSE",
        };
        for (final String sql : ambiguous) {
            assertRefused(sql, "ambiguous column name 'V'");
        }
        assertRefused("SELECT (SELECT s) FROM fz JOIN g ON fz.id = g.id ORDER BY 1", "ambiguous column name 'S'");
        assertRefused("SELECT (SELECT s) FROM fz JOIN g ON TRUE ORDER BY 1", "ambiguous column name 'S'");
    }

    @Test
    public void aTableJoinedToItselfSharesEveryBareName() {
        final String[] ambiguous = {
            "SELECT (SELECT v) FROM q a JOIN q b ON TRUE ORDER BY 1",
            "SELECT (SELECT V) FROM q a JOIN q b ON TRUE ORDER BY 1",
            "SELECT (SELECT v) FROM q, q q2 ORDER BY 1",
            "SELECT (SELECT v) FROM q JOIN q q2 ON q.v = q2.v ORDER BY 1",
            "SELECT (SELECT v) FROM q a LEFT JOIN q b ON a.v = b.v ORDER BY 1",
            "SELECT (SELECT v) FROM q, q q2, q q3 ORDER BY 1",
            "SELECT (SELECT v) FROM q a JOIN q b ON TRUE JOIN g ON TRUE ORDER BY 1",
            "SELECT q.v FROM q JOIN q q2 ON TRUE WHERE EXISTS (SELECT 1 WHERE v = 1) ORDER BY 1",
        };
        for (final String sql : ambiguous) {
            assertRefused(sql, "ambiguous column name 'V'");
        }
        assertCells(new String[][] {
            {"SELECT (SELECT a.v) FROM q a JOIN q b ON TRUE ORDER BY 1", "1 | 1 | 2 | 2"},
            {"SELECT (SELECT v) FROM q JOIN q q2 USING (v) ORDER BY 1", "1 | 2"},
            {"SELECT (SELECT v) FROM q NATURAL JOIN q q2 ORDER BY 1", "1 | 2"},
            {"SELECT (SELECT id) FROM fz JOIN fz f2 USING (s) ORDER BY 1", "5 | 7"},
        });
    }

    @Test
    public void theAmbiguousNameIsQuotedAsTheColumnIsSpelled() {
        engine.execute("CREATE TABLE qm (\"v\" INT)");
        engine.execute("INSERT INTO qm VALUES (3), (5)");
        engine.execute("CREATE TABLE qx (\"Vv\" INT)");
        engine.execute("INSERT INTO qx VALUES (1)");
        engine.execute("CREATE TABLE qy (\"Vv\" INT)");
        engine.execute("INSERT INTO qy VALUES (2), (3)");
        assertRefused("SELECT (SELECT \"v\") FROM ql JOIN qm ON TRUE ORDER BY 1", "ambiguous column name 'v'");
        assertRefused("SELECT (SELECT \"Vv\") FROM qx JOIN qy ON TRUE ORDER BY 1", "ambiguous column name 'Vv'");
        assertRefused("SELECT (SELECT \"Vv\") FROM qx a JOIN qx b ON TRUE ORDER BY 1", "ambiguous column name 'Vv'");
        assertRefused("SELECT (SELECT v) FROM q JOIN g ON TRUE ORDER BY 1", "ambiguous column name 'V'");
    }

    @Test
    public void aNameOneRelationCarriesOrTheSubqueryAnswersIsRead() {
        assertCells(new String[][] {
            {"SELECT q.v, g.v FROM q JOIN g ON TRUE WHERE EXISTS (SELECT 1 WHERE q.v = 1 AND g.v = 50) ORDER BY 1",
                "1, 50"},
            {"SELECT q.v, g.v FROM q JOIN g ON TRUE WHERE EXISTS (SELECT 1 FROM g g2 WHERE v = 50) ORDER BY 1, 2",
                "1, 50 | 1, 60 | 2, 50 | 2, 60"},
            {"SELECT (SELECT v) FROM q JOIN g USING (v) ORDER BY 1", ""},
            {"SELECT (SELECT id) FROM fz NATURAL JOIN g ORDER BY 1", "5"},
            {"SELECT (SELECT id + 1) FROM q JOIN g ON TRUE ORDER BY 1", "6 | 6 | 7 | 7"},
        });
    }
}
