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
 * A block's value query that does not parse, in the places next to the measured ones: a declared scalar's
 * assignment reads its query alone, a bracket before the first word names its first token and then the query's
 * own fault, a call's later argument names the SELECT alone, a list item followed by a name names it and the
 * query's ')', and an untyped LET of a query of two items cannot have its type inferred (all live-verified).
 */
public class BlockValueQueryPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void placesNextToTheMeasured() {
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 38 unexpected 'y'.",
            "DECLARE x INT; BEGIN x := (SELECT 1 x y); RETURN x; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 22 unexpected '1'. | syntax error line 1 at position 27 unexpected "
                + "'y'.",
            "BEGIN RETURN (SELECT (1) x y); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'SELECT'.",
            "BEGIN RETURN COALESCE(1, (SELECT 1 x y)); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'.",
            "BEGIN RETURN (SELECT FROM t) + x; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 28 unexpected 'x'. | syntax error line 1 at position 31 unexpected "
                + "')'.",
            "BEGIN LET q := (SELECT 1, 2 x y); END;");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'x'", "DESCRIBE TABLE t1 (a) x = @s!=x");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 42 unexpected 'y'.",
            "DECLARE x VARCHAR; BEGIN x := (SELECT 1 x y); RETURN x; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 45 unexpected 'z'.",
            "DECLARE x INT; BEGIN x := (SELECT 1 FROM x y z); RETURN x; END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 16 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 28 unexpected ')'.",
            "BEGIN LET q := (SELECT 1 x y); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'. | "
                + "syntax error line 1 at position 26 unexpected ')'.",
            "BEGIN RETURN (SELECT 1 x y); END;");
        assertAnswer("1", "DECLARE x INT; BEGIN x := (SELECT 1 x); RETURN x; END;");
        assertAnswer("1", "BEGIN RETURN (SELECT (1) x); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 26 unexpected 'SELECT'.",
            "BEGIN RETURN COALESCE(1, (SELECT 1 x y), 2); END;");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 14 unexpected 'SELECT'.",
            "BEGIN RETURN (SELECT FROM t); END;");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 6 |  variable 'Q' cannot have its type "
                + "inferred from initializer",
            "BEGIN LET q := (SELECT 1, 2 x); END;");
    }
}
