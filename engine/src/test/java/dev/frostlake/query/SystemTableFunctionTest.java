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

import org.junit.jupiter.api.Test;

/**
 * A SYSTEM$ call inside TABLE( ... ) is an unknown table function by its name upper-cased, after its arguments'
 * names are judged, wherever the source stands (all live-verified).
 */
public class SystemTableFunctionTest extends MeasuredCellsTestSupport {

    @Test
    public void unknownTableFunction() {
        engine.execute("CREATE OR REPLACE TABLE t (a INT, b INT)");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$SEND_EMAIL",
            "SELECT * FROM TABLE(SYSTEM$SEND_EMAIL('nosuch_int', 'a@b.c', 's', 'b'))");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$TYPEOF",
            "SELECT * FROM TABLE(SYSTEM$TYPEOF(1))");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$NOSUCH_FN",
            "SELECT * FROM TABLE(SYSTEM$NOSUCH_FN())");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$TYPEOF",
            "SELECT * FROM TABLE(system$typeof(1))");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$TYPEOF",
            "SELECT * FROM TABLE(SYSTEM$TYPEOF(1)) t");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$TYPEOF",
            "SELECT * FROM t, TABLE(SYSTEM$TYPEOF(a))");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$TYPEOF",
            "SELECT * FROM LATERAL SYSTEM$TYPEOF(1)");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$GET_TAG",
            "SELECT * FROM TABLE(SYSTEM$GET_TAG('a', 'b', 'TABLE'))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 37 unexpected ')'.",
            "SELECT * FROM TABLE(SYSTEM$TYPEOF(1 +))");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 33 unexpected ')'. | syntax "
                + "error line 1 at position 34 unexpected '<EOF>'.",
            "SELECT * FROM TABLE(SYSTEM$TYPEOF)");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$TYPEOF",
            "SELECT * FROM TABLE(SYSTEM$TYPEOF(1)) WHERE nosuch = 1");
        assertAnswer("REFUSED SQL compilation error: | syntax error line 1 at position 40 unexpected 'y'.",
            "SELECT * FROM TABLE(SYSTEM$TYPEOF(1)) x y");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SYSTEM$TYPEOF",
            "SELECT * FROM t JOIN TABLE(SYSTEM$TYPEOF(1)) ON TRUE");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 34 | invalid identifier 'NOSUCH'",
            "SELECT * FROM TABLE(SYSTEM$TYPEOF(nosuch))");
    }
}
