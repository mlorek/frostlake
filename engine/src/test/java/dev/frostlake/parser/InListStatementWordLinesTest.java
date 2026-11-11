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
 * A statement word a FROM list reads after a comma is refused in an IN's query as in a compared query, and two and
 * three calls deep as live reads them (live-verified): {@code a IN (SELECT 1 FROM t, (DESCRIBE TABLE t))} is 'SELECT',
 * then 'TABLE'; {@code ABS(ABS((SELECT 1 FROM t, (CALL p()))))} 'SELECT', 'p', 'CALL', then 'p' again.
 */
public class InListStatementWordLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void statementWordsAfterAComma() {
        engine.execute("CREATE OR REPLACE TABLE rt955 (a INT)");
        engine.execute("CREATE OR REPLACE PROCEDURE p955() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        assertCells(new String[][] {
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (DESCRIBE TABLE rt955))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 63 unexpected 'TABLE'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (DESC TABLE rt955))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 59 unexpected 'TABLE'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (EXPLAIN SELECT 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 62 unexpected 'SELECT'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (CALL p955()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 59 unexpected 'p955'. | syntax error line 1 at position 64 unexpected "
                + "')'. | syntax error line 1 at position 66 unexpected ')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (UPDATE rt955 SET a = 1))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 32 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 54 unexpected 'UPDATE'. | syntax error line 1 at position 77 unexpected "
                + "')'."},
            {"SELECT 1 FROM rt955 WHERE a IN (SELECT 1 FROM rt955, (SHOW TABLES))",
                "REFUSED SQL compilation error: | Object 'SHOW' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE SHOW."},
            {"SELECT 1 FROM rt955 WHERE a = (SELECT 1 FROM rt955, (DESCRIBE TABLE rt955))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 31 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 62 unexpected 'TABLE'."},
            {"SELECT 1 FROM rt955 WHERE a NOT IN (SELECT 1 FROM rt955, (CALL p955()))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 36 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 63 unexpected 'p955'. | syntax error line 1 at position 68 unexpected "
                + "')'. | syntax error line 1 at position 70 unexpected ')'."},
            {"SELECT ABS(ABS((SELECT 1 FROM rt955, (CALL p955()))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 43 unexpected 'p955'. | syntax error line 1 at position 38 unexpected "
                + "'CALL'. | syntax error line 1 at position 43 unexpected 'p955'."},
            {"SELECT ABS(ABS((SELECT 1 FROM rt955, (DESCRIBE TABLE rt955))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 47 unexpected 'TABLE'. | syntax error line 1 at position 38 unexpected "
                + "'DESCRIBE'. | syntax error line 1 at position 47 unexpected 'TABLE'."},
            {"SELECT ABS((SELECT 1 FROM rt955, (CALL p955())))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 12 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 39 unexpected 'p955'. | syntax error line 1 at position 43 unexpected "
                + "'('."},
            {"SELECT ABS(ABS(ABS((SELECT 1 FROM rt955, (CALL p955())))))",
                "REFUSED SQL compilation error: | syntax error line 1 at position 20 unexpected 'SELECT'. | syntax "
                + "error line 1 at position 47 unexpected 'p955'. | syntax error line 1 at position 42 unexpected "
                + "'CALL'. | syntax error line 1 at position 51 unexpected '('."},
        });
    }
}
