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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A fault straight after a select item's alias in a query that is not the statement's own, the shapes that
 * were left: an empty pair of parentheses, a list of values, an '=' and an IN after the alias, an EXISTS's
 * query, a joined query, and the same faults in a block's statement (live-verified).
 */
public class SubqueryAliasFaultMoreLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void parenthesesAfterTheAlias() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('.",
            "SELECT a FROM (SELECT 1 x ()) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 46 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x () FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected '2'. | syntax error line 1 at position 41 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (), 2) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('.",
            "SELECT a FROM (SELECT 1 x ()) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x () y) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('.",
            "WITH c AS (SELECT 1 x ()) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected '2'. | syntax error line 1 at position 50 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1, 2) FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected '2'. | syntax error line 1 at position 42 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1, 2)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected '2'. | syntax error line 1 at position 45 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1, 2, 3)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected '2'. | syntax error line 1 at position 53 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1, 2), 3 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 31 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (a, b)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 31 unexpected '2'. | syntax error line 1 at position 44 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x ((1, 2))) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected '2'. | syntax error line 1 at position 44 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1, 2) y) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 30 unexpected '2'. | syntax error line 1 at position 44 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1, 2)) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 29 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x (1,)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '('. | syntax error "
                + "line 1 at position 41 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x (1 2)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('. | syntax error "
                + "line 1 at position 23 unexpected '1'. | syntax error line 1 at position 28 unexpected ')'.",
            "WITH c AS (SELECT 1 x (1, 2)) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '('. | syntax error "
                + "line 1 at position 23 unexpected '1'. | syntax error line 1 at position 29 unexpected 'FROM'.",
            "WITH c AS (SELECT 1 x (1, 2) FROM FZ) SELECT * FROM c WHERE a b");
    }

    @Test
    public void anOperatorAfterTheAlias() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '='. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x = 1 FROM FZ) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '='. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x = 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '='. | syntax error "
                + "line 1 at position 31 unexpected '2'. | syntax error line 1 at position 42 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x = 1, 2) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '<'. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x < 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '+'. | syntax error "
                + "line 1 at position 39 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x + 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '=='. | syntax error "
                + "line 1 at position 40 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x == 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'IS'. | syntax error "
                + "line 1 at position 43 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x IS NULL) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'IN'. | syntax error "
                + "line 1 at position 42 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x IN (1)) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'AND'. | syntax error "
                + "line 1 at position 34 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x AND TRUE) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected '='. | syntax error "
                + "line 1 at position 42 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 AS x = 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '='. | syntax error "
                + "line 1 at position 41 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x = 1) d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected '='. | syntax error "
                + "line 1 at position 50 unexpected ')'.",
            "SELECT a FROM (SELECT 1 x = 1 FROM FZ WHERE id = 1) WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '='. | syntax error "
                + "line 1 at position 51 unexpected 'b'.",
            "WITH c AS (SELECT 1 x = 1) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 22 unexpected '='. | syntax error "
                + "line 1 at position 59 unexpected 'b'.",
            "WITH c AS (SELECT 1 x = 1 FROM FZ) SELECT * FROM c WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '='.",
            "SELECT 1 x = 1");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 11 unexpected '='.",
            "SELECT 1 x = 1 FROM FZ WHERE a b");
    }

    @Test
    public void existsAndOtherSubqueries() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ''y''. | syntax error "
                + "line 1 at position 61 unexpected 'b'.",
            "SELECT a FROM FZ WHERE EXISTS (SELECT 1 x 'y' FROM FZ) AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ''y''. | syntax error "
                + "line 1 at position 53 unexpected 'b'.",
            "SELECT a FROM FZ WHERE EXISTS (SELECT 1 x 'y') AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'y'. | syntax error "
                + "line 1 at position 59 unexpected 'b'.",
            "SELECT a FROM FZ WHERE EXISTS (SELECT 1 x y FROM FZ) AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected '('. | syntax error "
                + "line 1 at position 61 unexpected 'b'.",
            "SELECT a FROM FZ WHERE EXISTS (SELECT 1 x (1) FROM FZ) AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 46 unexpected ''y''. | syntax error "
                + "line 1 at position 65 unexpected 'b'.",
            "SELECT a FROM FZ WHERE NOT EXISTS (SELECT 1 x 'y' FROM FZ) AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected ''y''.",
            "SELECT a FROM FZ WHERE EXISTS (SELECT 1 x 'y' FROM FZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 52 unexpected ''y''. | syntax error "
                + "line 1 at position 76 unexpected 'b'.",
            "SELECT a FROM FZ WHERE a = 1 AND EXISTS (SELECT 1 x 'y' FROM FZ) ORDER BY a b c");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 37 unexpected ')'.",
            "SELECT EXISTS (SELECT 1 x 'y' FROM FZ), 2 z w FROM FZ");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 60 unexpected 'b'.",
            "SELECT a FROM FZ WHERE id IN (SELECT 1 x 'y' FROM FZ) AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 58 unexpected 'b'.",
            "SELECT a FROM FZ WHERE id IN (SELECT 1 x y FROM FZ) AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 59 unexpected 'b'.",
            "SELECT a FROM FZ WHERE id = (SELECT 1 x 'y' FROM FZ) AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 57 unexpected 'b'.",
            "SELECT a FROM FZ WHERE id = (SELECT 1 x y FROM FZ) AND a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 63 unexpected 'b'.",
            "SELECT a FROM FZ WHERE id > ANY (SELECT 1 x 'y' FROM FZ) AND a b");
    }

    @Test
    public void joinedQueriesAndBlocks() {
        answer(
            "CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''y''. | syntax error "
                + "line 1 at position 41 unexpected 'ON'.",
            "SELECT a FROM FZ JOIN (SELECT 1 x 'y') d ON d.x = FZ.id WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 34 unexpected ''y''. | syntax error "
                + "line 1 at position 45 unexpected ')'.",
            "SELECT a FROM FZ JOIN (SELECT 1 x 'y' FROM FZ) d ON d.x = FZ.id WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected ''y''. | syntax error "
                + "line 1 at position 45 unexpected 'b'.",
            "SELECT a FROM FZ, (SELECT 1 x 'y') d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'y'. | syntax error "
                + "line 1 at position 44 unexpected 'ON'.",
            "SELECT a FROM FZ LEFT JOIN (SELECT 1 x y) d ON TRUE WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected ''y''. | syntax error "
                + "line 1 at position 55 unexpected 'b'.",
            "SELECT a FROM FZ CROSS JOIN (SELECT 1 x 'y') d WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected ''y''. | syntax error "
                + "line 1 at position 64 unexpected 'b'.",
            "SELECT a FROM (SELECT 1 x 'y') d JOIN FZ ON d.x = FZ.id WHERE a b");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('. | syntax error "
                + "line 1 at position 44 unexpected 'b'.",
            "BEGIN SELECT a FROM (SELECT 1 x ()) WHERE a b; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '('. | syntax error "
                + "line 1 at position 36 unexpected '2'. | syntax error line 1 at position 48 unexpected 'b'. | "
                + "syntax error line 1 at position 51 unexpected 'END'.",
            "BEGIN SELECT a FROM (SELECT 1 x (1, 2)) WHERE a b; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected '='. | syntax error "
                + "line 1 at position 45 unexpected 'b'. | syntax error line 1 at position 48 unexpected 'END'.",
            "BEGIN SELECT a FROM (SELECT 1 x = 1) WHERE a b; END;");
        assertAnswer(
            "REFUSED SQL compilation error: | syntax error line 1 at position 48 unexpected ''y''. | syntax error "
                + "line 1 at position 59 unexpected ')'.",
            "BEGIN SELECT a FROM FZ WHERE EXISTS (SELECT 1 x 'y' FROM FZ) AND a b; END;");
    }
}
