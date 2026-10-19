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
 * An EXCLUDE of an unqualified star written as a value or an argument — {* EXCLUDE id}, OBJECT_CONSTRUCT, HASH,
 * COUNT, ARRAY_CONSTRUCT — that names a column more than one joined relation holds is refused while the statement
 * compiles, "ambiguous column name 'ID'", whether or not a row passes; a qualified star and a USING or NATURAL
 * join's merged key are no ambiguity (all live-verified).
 */
public class StarArgumentExcludeAmbiguityTest extends MeasuredCellsTestSupport {

    @Test
    public void anExcludeNamingTwoRelationsColumnsIsAmbiguous() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE)");
        engine.execute("CREATE OR REPLACE TABLE fy (id INT, c VARCHAR)");
        engine.execute("INSERT INTO fy VALUES (1, 'q')");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT TO_JSON({* EXCLUDE id}) FROM fz f JOIN fy y ON f.id = y.id");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT OBJECT_CONSTRUCT(* EXCLUDE id) FROM fz f JOIN fy y ON f.id = y.id");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT HASH(* EXCLUDE id) FROM fz f JOIN fy y ON f.id = y.id");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT COUNT(* EXCLUDE id) FROM fz f JOIN fy y ON f.id = y.id");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT ARRAY_CONSTRUCT(* EXCLUDE id) FROM fz f JOIN fy y ON f.id = y.id");
        assertAnswer("{\"B\":true}", "SELECT TO_JSON({f.* EXCLUDE id}) FROM fz f JOIN fy y ON f.id = y.id");
        answer("SELECT HASH(f.* EXCLUDE id) FROM fz f JOIN fy y ON f.id = y.id");
        assertAnswer("REFUSED Duplicate field key 'ID'",
            "SELECT TO_JSON({* EXCLUDE b}) FROM fz f JOIN fy y ON f.id = y.id");
        assertAnswer("REFUSED Duplicate field key 'ID'",
            "SELECT TO_JSON({* EXCLUDE (b, c)}) FROM fz f JOIN fy y ON f.id = y.id");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT * EXCLUDE id FROM fz f JOIN fy y ON f.id = y.id");
        assertAnswer("{\"B\":true,\"C\":\"q\"}", "SELECT TO_JSON({* EXCLUDE ID}) FROM fz f JOIN fy y USING (id)");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT COUNT(DISTINCT * EXCLUDE id) FROM fz f JOIN fy y ON f.id = y.id");
    }

    @Test
    public void theRefusalNeedsNoRow() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (1, TRUE)");
        engine.execute("CREATE OR REPLACE TABLE fy (id INT, c VARCHAR)");
        engine.execute("INSERT INTO fy VALUES (1, 'q')");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT TO_JSON({* EXCLUDE id}) FROM fz f JOIN fy y ON f.id = y.id WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT COUNT(* EXCLUDE id) FROM fz f JOIN fy y ON f.id = y.id WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT OBJECT_CONSTRUCT(* EXCLUDE id) FROM fz f JOIN fy y ON f.id = y.id WHERE f.id > 5");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT TO_JSON({* EXCLUDE id}) FROM fz f, fy y");
        assertAnswer("REFUSED SQL compilation error: | ambiguous column name 'ID'",
            "SELECT TO_JSON({* EXCLUDE (b, id)}) FROM fz f JOIN fy y ON f.id = y.id");
        assertAnswer("{\"B\":true,\"C\":\"q\"}", "SELECT TO_JSON({* EXCLUDE id}) FROM fz f NATURAL JOIN fy y");
    }
}
