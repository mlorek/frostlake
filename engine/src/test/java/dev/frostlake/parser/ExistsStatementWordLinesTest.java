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
 * A statement in a FROM item's parentheses in an EXISTS's query (live-verified). In a condition the word is followed by
 * an UPDATE's SET, an EXPLAIN's USING, or else by a number after a CALL's or an INSERT's first comma and then by the
 * token after the first ')' past it: the EXISTS's own ')', an alias, a JOIN, ON, or a WHERE. In a select list the word
 * stands alone, a CALL with the '(' after its name, an UPDATE with its SET and the '='.
 */
public class ExistsStatementWordLinesTest extends MeasuredCellsTestSupport {

    /** Each word after a comma, after a JOIN and after FROM, in WHERE, HAVING, NOT and OR. */
    @Test
    public void anExistsInAConditionReadsOn() {
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertCells(new String[][] {
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 83 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (DESC TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'DESC'. | syntax error "
                + "line 1 at position 79 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (SHOW TABLES))",
                "REFUSED SQL compilation error: | Object 'SHOW' does not exist or not authorized. Your primary role "
                + "<ROLE> must have at least one privilege granted on TABLE SHOW."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (EXPLAIN SELECT 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'EXPLAIN'. | syntax "
                + "error line 1 at position 77 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (UPDATE rt_ctas SET a = 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'UPDATE'. | syntax "
                + "error line 1 at position 75 unexpected 'SET'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (INSERT INTO rt_ctas VALUES (1, 'a')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'INSERT'. | syntax "
                + "error line 1 at position 95 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (USE SCHEMA s))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'USE'. | syntax error "
                + "line 1 at position 73 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (TRUNCATE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'TRUNCATE'. | syntax "
                + "error line 1 at position 83 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (MERGE INTO rt_ctas USING rt_ctas t ON TRUE "
                + "WHEN MATCHED THEN DELETE))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'MERGE'. | syntax "
                + "error line 1 at position 128 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas) x WHERE (TRUE))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 84 unexpected 'x'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, rt_ctas t2, (DESCRIBE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 95 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas JOIN (DESCRIBE TABLE rt_ctas) ON TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 88 unexpected 'ON'."},
            {"SELECT 1 FROM rt_ctas WHERE NOT EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 87 unexpected ')'."},
            {"SELECT EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'DESCRIBE'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas) x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 84 unexpected 'x'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas)) AND TRUE",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 83 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM (DESCRIBE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 74 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas) JOIN rt_ctas t3 ON "
                + "TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 84 unexpected 'JOIN'."},
            {"SELECT 1 FROM rt_ctas WHERE TRUE AND EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 92 unexpected ')'."},
        });
    }

    /** CALL, INSERT, EXPLAIN USING and aliases, in conditions and select lists. */
    @Test
    public void heldOutExistsShapes() {
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertCells(new String[][] {
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (CALL p(1, 2)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'CALL'. | syntax error "
                + "line 1 at position 70 unexpected '2'. | syntax error line 1 at position 72 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (CALL p()) x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'CALL'. | syntax error "
                + "line 1 at position 68 unexpected ')'."},
            {"SELECT EXISTS (SELECT 1 FROM rt_ctas, (DESC TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'DESC'."},
            {"SELECT EXISTS (SELECT 1 FROM rt_ctas, (CALL p()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'CALL'. | syntax error "
                + "line 1 at position 45 unexpected '('."},
            {"SELECT 1, EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'DESCRIBE'."},
            {"SELECT 1 FROM rt_ctas HAVING EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 61 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 84 unexpected ')'."},
            {"SELECT CASE WHEN EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas)) THEN 1 END",
                "REFUSED SQL compilation error: | syntax error line 1 at position 49 unexpected 'DESCRIBE'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas JOIN (CALL p()) ON TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'CALL'. | syntax error "
                + "line 1 at position 72 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas JOIN (UPDATE rt_ctas SET a = 1) ON TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'UPDATE'. | syntax "
                + "error line 1 at position 79 unexpected 'SET'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (INSERT INTO rt_ctas VALUES (1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'INSERT'. | syntax "
                + "error line 1 at position 90 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (EXPLAIN USING TEXT SELECT 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'EXPLAIN'. | syntax "
                + "error line 1 at position 68 unexpected 'USING'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas JOIN (DESCRIBE TABLE rt_ctas) t ON TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 64 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 88 unexpected 't'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas) WHERE TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 84 unexpected 'WHERE'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM (DESCRIBE TABLE rt_ctas) x)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 75 unexpected 'x'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM (INSERT INTO rt_ctas VALUES (1, 'a')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'INSERT'. | syntax "
                + "error line 1 at position 86 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE a = 1 OR EXISTS (SELECT 1 FROM rt_ctas, (DESCRIBE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 69 unexpected 'DESCRIBE'. | syntax "
                + "error line 1 at position 92 unexpected ')'."},
        });
        engine.execute("CREATE OR REPLACE TABLE rt_ctas (a INT, b VARCHAR)");
        assertCells(new String[][] {
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (CALL p(1, 'a')))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'CALL'. | syntax error "
                + "line 1 at position 74 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE NOT EXISTS (SELECT 1 FROM rt_ctas JOIN (DESC TABLE rt_ctas) d ON TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 68 unexpected 'DESC'. | syntax error "
                + "line 1 at position 88 unexpected 'd'."},
            {"SELECT EXISTS (SELECT 1 FROM rt_ctas, (UPDATE rt_ctas SET a = 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'UPDATE'. | syntax "
                + "error line 1 at position 54 unexpected 'SET'. | syntax error line 1 at position 60 unexpected '='."},
            {"SELECT EXISTS (SELECT 1 FROM rt_ctas, (CALL p(1, 2)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 39 unexpected 'CALL'. | syntax error "
                + "line 1 at position 45 unexpected '('."},
            {"SELECT EXISTS (SELECT 1 FROM (DESCRIBE TABLE rt_ctas))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 30 unexpected 'DESCRIBE'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM (UPDATE rt_ctas SET a = 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'UPDATE'. | syntax "
                + "error line 1 at position 66 unexpected 'SET'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM (EXPLAIN USING TEXT SELECT 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'EXPLAIN'. | syntax "
                + "error line 1 at position 59 unexpected 'USING'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM (CALL p()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'CALL'. | syntax error "
                + "line 1 at position 59 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM (INSERT INTO rt_ctas VALUES (1)))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'INSERT'. | syntax "
                + "error line 1 at position 81 unexpected ')'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM (DESC TABLE rt_ctas) JOIN rt_ctas t ON TRUE)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 51 unexpected 'DESC'. | syntax error "
                + "line 1 at position 71 unexpected 'JOIN'."},
            {"SELECT 1 FROM rt_ctas WHERE EXISTS (SELECT 1 FROM rt_ctas, (TRUNCATE TABLE rt_ctas) t)",
                "REFUSED SQL compilation error: | syntax error line 1 at position 60 unexpected 'TRUNCATE'. | syntax "
                + "error line 1 at position 84 unexpected 't'."},
        });
    }
}
