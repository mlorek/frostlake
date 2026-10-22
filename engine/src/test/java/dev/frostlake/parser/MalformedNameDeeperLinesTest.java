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
 * The lines of a malformed name deeper than a select item: a three-part column in a bracket, a CAST, a call inside
 * another call or a CASE's condition, a query used as a value and a LET's bracketed query; SHOW's scope name with
 * a run of dots; a name in a bracket that is a call's argument and a trailing dot inside nested calls (all
 * live-verified).
 */
public class MalformedNameDeeperLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void deeperNames() {
        engine.execute("CREATE OR REPLACE TABLE T (x INT, y INT)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected ')'. | syntax "
                + "error line 1 at position 12 unexpected 'T'. | syntax error line 1 at position 13 unexpected '.'.",
            "SELECT ABS((T..x)) FROM T");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 17 unexpected ')'. | syntax "
                + "error line 1 at position 25 unexpected ')'.",
            "SELECT CAST((T..x) AS INT) FROM T");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ')'. | syntax "
                + "error line 1 at position 30 unexpected ')'.",
            "SELECT IFF(ABS(T..x) > 0, 1, 2) FROM T");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 25 unexpected ')'. | syntax "
                + "error line 1 at position 25 unexpected ')'. | syntax error line 1 at position 31 unexpected 'THEN'.",
            "SELECT CASE WHEN ABS(T..x) = 1 THEN 1 END FROM T");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 8 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 18 unexpected '('.",
            "SELECT (SELECT ABS(T..x) FROM T)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 28 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 45 unexpected 'FROM'.",
            "SELECT 1 FROM T WHERE x IN (SELECT ABS(T..x) FROM T)");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 2 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 2 at position 26 unexpected 'FROM'.",
            "EXECUTE IMMEDIATE $$BEGIN\nLET x INT := (SELECT T..x FROM T);\nEND$$");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '.'. | syntax "
                + "error line 1 at position 21 unexpected 'y'.",
            "SHOW TABLES IN a...c y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '.'.",
            "SHOW TABLES IN a...c");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 18 unexpected '.'. | syntax "
                + "error line 1 at position 22 unexpected 'y'.",
            "SHOW TABLES IN a....c y");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 19 unexpected ')'. | syntax "
                + "error line 1 at position 18 unexpected '.'.",
            "SELECT ABS(ABS(T.x.)) FROM T");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'y'. | syntax "
                + "error line 1 at position 12 unexpected 'x'. | syntax error line 1 at position 14 unexpected 'y'.",
            "SELECT ABS((x y)) FROM T");
    }
}
