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
 * A TABLE query with words after its name in a routine's block body written without quotes reads as the same
 * block standing alone: the word after the name, then the token after it (all live-verified).
 */
public class RoutineBlockBodyLinesTest extends MeasuredCellsTestSupport {

    @Test
    public void tableQueryInABody() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT)");
        engine.execute("INSERT INTO t VALUES (1)");
        engine.execute("CREATE OR REPLACE VIEW vw64 AS SELECT CAST(NULL AS VARCHAR) AS s, CAST('x' AS VARCHAR(5)) AS "
            + "f");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'x'. | syntax "
                + "error line 1 at position 64 unexpected 'y'.",
            "CREATE OR REPLACE FUNCTION f64() RETURNS INT AS BEGIN TABLE t x y; RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 16 unexpected 'y'.",
            "BEGIN TABLE t x y; RETURN 1; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'x'. | syntax "
                + "error line 1 at position 17 unexpected 'RETURN'.",
            "BEGIN TABLE t x; RETURN 1; END;");
        assertShape("S:VARCHAR(134217728), F:VARCHAR(5)", "SELECT * FROM vw64");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'x'. | syntax "
                + "error line 1 at position 65 unexpected 'RETURN'.",
            "CREATE OR REPLACE FUNCTION f65() RETURNS INT AS BEGIN TABLE t x; RETURN 1; END");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 62 unexpected 'x'. | syntax "
                + "error line 1 at position 64 unexpected 'y'.",
            "CREATE OR REPLACE FUNCTION f66() RETURNS INT AS BEGIN TABLE t x y z; RETURN 1; END");
    }
}
