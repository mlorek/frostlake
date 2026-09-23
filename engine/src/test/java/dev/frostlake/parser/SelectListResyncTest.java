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

package dev.frostlake.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A first fault where a select item's alias belongs reads on from the next comma, FROM, clause word or statement, and
 * a fault right after an item's alias ends the report — except that a name there in a derived table's query reads on
 * at the next clause word or comma, and a '(' there in a CTE's query opens the main query in brackets (live-verified).
 */
public class SelectListResyncTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int position, final String token) {
        return "syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void aFaultWhereTheAliasBelongsReadsOnFromTheNextComma() {
        assertEquals(lines(at(12, "1"), at(20, "2")), refusal("SELECT 1 AS 1, 2 AS 2"));
        assertEquals(lines(at(12, "1"), at(20, "'x'")), refusal("SELECT 1 AS 1, 2 AS 'x'"));
        assertEquals(lines(at(12, "1"), at(20, "2"), at(28, "3")), refusal("SELECT 1 AS 1, 2 AS 2, 3 AS 3"));
        assertEquals(lines(at(12, "1"), at(22, "2")), refusal("SELECT 1 AS 1 x, 2 AS 2"));
        assertEquals(lines(at(11, ","), at(18, "2")), refusal("SELECT 1 AS, 2 AS 2"));
        assertEquals(lines(at(12, "1"), at(25, "3")), refusal("SELECT 1 AS 1 AS 2, 3 AS 3"));
        assertEquals(lines(at(15, "3"), at(23, "5")), refusal("SELECT 1, 2 AS 3, 4 AS 5"));
        assertEquals(lines(at(9, "2"), at(17, "3")), refusal("SELECT 1 2, 3 AS 3"));
        assertEquals(lines(at(9, "'x'"), at(19, "2")), refusal("SELECT 1 'x', 2 AS 2"));
        assertEquals(lines(at(12, "'x'"), at(21, ")")), refusal("SELECT 1 AS 'x' (1, 2), 3 AS 3"));
        assertEquals(lines(at(12, "1")), refusal("SELECT 1 AS 1, 2"));
    }

    @Test
    public void aClauseAndTheNextStatementReadOnToo() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(12, "1"), at(30, "b")), refusal("SELECT 1 AS 1 FROM fz WHERE a b"));
        assertEquals(lines(at(9, "'x'"), at(29, "b")), refusal("SELECT 1 'x' FROM fz WHERE a b"));
        assertEquals(lines(at(12, "1"), at(20, "2"), at(38, "b")), refusal("SELECT 1 AS 1, 2 AS 2 FROM fz WHERE a b"));
        assertEquals(lines(at(12, "1"), at(20, "2"), at(31, "3")), refusal("SELECT 1 AS 1, 2 AS 2 FROM fz, 3"));
        assertEquals(lines(at(12, "FROM")), refusal("SELECT 1 AS FROM fz"));
        assertEquals(lines(at(12, "1"), at(20, "2"), at(34, "y")), refusal("SELECT 1 AS 1, 2 AS 2; SELECT 1 x y"));
        assertEquals(lines(at(9, "'x'"), at(33, "y")), refusal("SELECT 1 'x' FROM fz; SELECT 1 x y"));
    }

    @Test
    public void afterAnAliasNothingMoreIsReported() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y, 2 AS 2"));
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y, HASH(* REPLACE (1 AS id)) FROM fz"));
        assertEquals(lines(at(12, "AS")), refusal("SELECT 1 id AS k FROM fz"));
        assertEquals(lines(at(10, ".")), refusal("SELECT 1 x.y FROM fz"));
        assertEquals(lines(at(14, "'y'")), refusal("SELECT 1 AS x 'y' FROM fz WHERE a b"));
        assertEquals(lines(at(11, "y")), refusal("SELECT 1 x y FROM fz WHERE a b; SELECT 1 x y"));
        assertEquals(lines(at(8, ")")), refusal("SELECT 1), 2 x y FROM fz"));
        assertEquals(lines(at(9, "ON")), refusal("SELECT 1 ON FROM fz"));
    }

    @Test
    public void inADerivedTableANameAfterAnAliasReadsOnAtTheNextClauseOrComma() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("CREATE TABLE fy (id INT)");
        assertEquals(lines(at(26, "y"), at(45, "b")), refusal("SELECT a FROM (SELECT 1 x y FROM fz) WHERE a b"));
        assertEquals(lines(at(26, "y"), at(48, ")")), refusal("SELECT a FROM (SELECT 1 x y FROM fz WHERE id = 1) WHERE a b"));
        assertEquals(lines(at(26, "y"), at(47, ")")),
            refusal("SELECT a FROM (SELECT 1 x y FROM fz GROUP BY id) WHERE a b"));
        assertEquals(lines(at(26, "y"), at(39, ")")), refusal("SELECT a FROM (SELECT 1 x y FROM fz, fy) WHERE a b"));
        assertEquals(lines(at(26, "y"), at(52, ")")),
            refusal("SELECT a FROM (SELECT 1 x y FROM fz JOIN fy ON 1 = 1) WHERE a b"));
        assertEquals(lines(at(26, "y"), at(51, "b")), refusal("SELECT a FROM (SELECT 1 x y FROM fz) d, fy WHERE a b"));
        assertEquals(lines(at(26, "y"), at(47, "b")), refusal("SELECT a FROM (SELECT 1 x y FROM fz) 5 WHERE a b"));
        assertEquals(lines(at(26, "y"), at(50, "b")), refusal("SELECT a FROM (SELECT 1 x y AS z FROM fz) WHERE a b"));
        assertEquals(lines(at(26, "y"), at(54, "w")), refusal("SELECT a FROM (SELECT 1 x y FROM fz) UNION SELECT 2 z w"));
        assertEquals(lines(at(26, "y"), at(50, "w"), at(69, "b")),
            refusal("SELECT a FROM (SELECT 1 x y FROM fz), (SELECT 2 z w FROM fy) WHERE a b"));
        assertEquals(lines(at(34, "y"), at(62, "b")),
            refusal("SELECT a FROM fy JOIN (SELECT 1 x y FROM fz) ON 1 = 1 WHERE a b"));
        assertEquals(lines(at(41, "y"), at(60, "b")),
            refusal("INSERT INTO fy SELECT a FROM (SELECT 1 x y FROM fz) WHERE a b"));
        assertEquals(lines(at(26, "'y'"), at(37, ")")), refusal("SELECT a FROM (SELECT 1 x 'y' FROM fz) WHERE a b"));
        assertEquals(lines(at(26, "2"), at(35, ")")), refusal("SELECT a FROM (SELECT 1 x 2 FROM fz) WHERE a b"));
    }

    @Test
    public void inACteABracketAfterAnAliasReadsAsTheMainQuery() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        assertEquals(lines(at(22, "("), at(23, "1"), at(32, "FROM")),
            refusal("WITH c AS (SELECT 1 x (1 AS id) FROM fz) SELECT * FROM c"));
        assertEquals(lines(at(22, "("), at(23, "1"), at(31, ")")), refusal("WITH c AS (SELECT 1 x (1 AS id)) SELECT * FROM c"));
        assertEquals(lines(at(22, "("), at(23, "id"), at(31, ")")),
            refusal("WITH c AS (SELECT 1 x (id AS k)) h FROM fz) SELECT * FROM c"));
    }

    @Test
    public void aCommaAfterAValueIsNoAliasPlace() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        final String[] refused = {
            "SELECT TOP 1 id, CAST(1, 2) FROM fz",
            "SELECT TOP 1 id, TRY_CAST(1, 2) FROM fz",
            "SELECT TOP 1 id x, CAST(1, 2) FROM fz",
            "SELECT id, TRY_CAST(b AS INT FROM fz",
        };
        for (final String sql : refused) {
            final String message = refusal(sql);
            assertTrue(message.startsWith("SQL compilation error:\nsyntax error line 1 at position "), sql + ": " + message);
        }
        assertEquals(lines(at(25, "FROM")), refusal("SELECT id, CAST(b AS INT FROM fz"));
    }

    @Test
    public void aLongChainOfFaultsIsReportedWhole() {
        engine.execute("CREATE TABLE fz (id INT, b BOOLEAN)");
        final StringBuilder items = new StringBuilder("SELECT ");
        final String[] itemLines = new String[18];
        for (int i = 1; i <= itemLines.length; i++) {
            final String number = String.valueOf(i);
            items.append(i == 1 ? "" : ", ").append(number).append(" AS ");
            itemLines[i - 1] = at(items.length(), number);
            items.append(number);
        }
        assertEquals(lines(itemLines), refusal(items.toString()));
        final StringBuilder tables = new StringBuilder("SELECT a FROM ");
        final String[] tableLines = new String[11];
        for (int i = 0; i < 10; i++) {
            tables.append(i == 0 ? "" : ", ").append("(SELECT 1 x y FROM fz)");
            tableLines[i] = at(26 + 24 * i, "y");
        }
        tableLines[10] = at(261, "b");
        assertEquals(lines(tableLines), refusal(tables.append(" WHERE a b").toString()));
    }
}
