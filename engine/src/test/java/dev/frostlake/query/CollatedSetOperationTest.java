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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UNION, INTERSECT and EXCEPT compare a column under the collation any of their branches gives it: values equal
 * under it are one row, reporting the smallest by raw text — from both branches for UNION, from the left branch for
 * INTERSECT and EXCEPT. The set operation's ORDER BY sorts under it, ties keeping their order, and its result column
 * carries it. Every cell is live-verified.
 */
public class CollatedSetOperationTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO ct VALUES ('a','a'),('A','A'),('b','b'),('B','B')");
        engine.execute("CREATE TABLE g1 (c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO g1 VALUES ('aB'),('Ab')");
        engine.execute("CREATE TABLE g4 (c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO g4 VALUES ('B'),('b'),('A'),('a')");
        engine.execute("CREATE TABLE lo (c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO lo VALUES ('a'),('b')");
    }

    /** Every row's first cell, joined by " | ". */
    private String rows(final String sql) {
        final StringBuilder text = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            text.append(text.length() > 0 ? " | " : "").append(row.getValue(0));
        }
        return text.toString();
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], rows(cell[0]), cell[0]);
        }
    }

    @Test
    public void equalValuesUnderTheCollationAreOneRow() {
        final String sorted = "SELECT LISTAGG(x, '|') WITHIN GROUP (ORDER BY x) FROM (";
        assertCells(new String[][] {
            {sorted + "SELECT c AS x FROM ct UNION SELECT 'A')", "A|B"},
            {"SELECT LISTAGG(c, '|') FROM (SELECT c FROM ct INTERSECT SELECT 'a')", "A"},
            {sorted + "SELECT c AS x FROM ct EXCEPT SELECT 'A')", "B"},
            {sorted + "SELECT c AS x FROM g4 UNION SELECT c FROM g1)", "A|Ab|B"},
            {"SELECT COUNT(*) FROM (SELECT c FROM ct UNION SELECT 'A')", "2"},
            {"SELECT COUNT(*) FROM (SELECT c FROM ct EXCEPT SELECT 'A')", "1"},
            {"SELECT COUNT(*) FROM (SELECT c FROM g4 UNION SELECT c FROM g1)", "3"},
            {"SELECT COUNT(*) FROM (SELECT s FROM ct UNION SELECT 'A')", "4"},
            {"SELECT COUNT(*) FROM (SELECT c FROM ct UNION ALL SELECT 'A')", "5"},
            {sorted + "SELECT 'A' AS x UNION SELECT c FROM ct)", "A|B"},
            {sorted + "SELECT s AS x FROM ct UNION SELECT c FROM ct)", "A|B"},
            {sorted + "SELECT c AS x FROM ct INTERSECT SELECT s FROM ct)", "A|B"},
            {sorted + "SELECT c AS x FROM ct EXCEPT SELECT s FROM ct WHERE s = 'a')", "B"},
            {sorted + "SELECT s AS x FROM ct EXCEPT SELECT c FROM ct WHERE c = 'A')", "B"},
            {"SELECT LISTAGG(x || y, '|') WITHIN GROUP (ORDER BY x, y) FROM (SELECT c AS x, s AS y FROM ct UNION SELECT 'A', 'a')",
                "AA|Aa|BB|bb"},
            {sorted + "SELECT c AS x FROM ct UNION SELECT c FROM ct)", "A|B"},
        });
    }

    @Test
    public void intersectAndExceptReportTheLeftBranchsValue() {
        final String sorted = "SELECT LISTAGG(x, '|') WITHIN GROUP (ORDER BY x) FROM (";
        assertCells(new String[][] {
            {sorted + "SELECT c AS x FROM lo INTERSECT SELECT c FROM ct)", "a|b"},
            {sorted + "SELECT 'a' AS x INTERSECT SELECT c FROM ct)", "a"},
            {sorted + "SELECT c AS x FROM lo EXCEPT SELECT 'B')", "a"},
            {sorted + "SELECT c AS x FROM lo UNION SELECT c FROM ct WHERE c = 'A')", "A|b"},
        });
    }

    @Test
    public void theOrderByAndTheResultColumnCarryTheCollation() {
        assertCells(new String[][] {
            {"SELECT c FROM ct UNION SELECT 'A' ORDER BY 1", "A | B"},
            {"SELECT c FROM g4 UNION SELECT c FROM g1 ORDER BY 1", "A | Ab | B"},
            {"SELECT 'A' AS x UNION SELECT c FROM ct ORDER BY 1", "A | B"},
            {"SELECT s AS x FROM ct UNION SELECT c FROM ct ORDER BY 1", "A | B"},
            {"SELECT c FROM ct UNION ALL SELECT 'x' ORDER BY 1", "a | A | b | B | x"},
            {"SELECT c FROM ct UNION ALL SELECT 'x' ORDER BY 1 DESC", "x | b | B | a | A"},
            {"SELECT COLLATION(x) FROM (SELECT c AS x FROM ct UNION SELECT 'A') LIMIT 1", "en-ci"},
            {"SELECT COLLATION(x) FROM (SELECT 'q' AS x UNION SELECT c FROM ct) LIMIT 1", "en-ci"},
        });
    }
}
