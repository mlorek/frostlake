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
 * A correlated subquery over a derived relation of a shape of its own is refused before any row whatever shape its
 * correlation takes - two conditions, a non-equality, an OR - whether it stands in the select list, the HAVING or
 * the ORDER BY, and whatever join brings the relation in, an outer one included; a relation over a table function
 * is refused even under the statement's WHERE 1 = 0. A WHERE's subquery is answered, as is one in a lateral table
 * function's argument under the statement's WHERE 1 = 0, and MIN or MAX of an outer VARIANT inside a subquery is
 * refused (all live-verified).
 */
public class DerivedCorrelationShapesTest extends MeasuredCellsTestSupport {

    @Test
    public void correlationShapesOverDerivedRelations() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        engine.execute("CREATE OR REPLACE TABLE one (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO one VALUES (5, TRUE)");
        engine.execute("CREATE OR REPLACE TABLE vz (id INT, j VARIANT, a ARRAY, o OBJECT)");
        engine.execute("INSERT INTO vz SELECT 5, PARSE_JSON('{\"a\":1}'), ARRAY_CONSTRUCT(1, 2), "
            + "OBJECT_CONSTRUCT('k', 1)");
        engine.execute("INSERT INTO vz SELECT 7, PARSE_JSON('[1,2]'), ARRAY_CONSTRUCT(3), OBJECT_CONSTRUCT('k', 2)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id AND g.v > fz.id) AS x FROM (SELECT id + 0 AS id FROM fz "
                + "WHERE 1 = 0) fz");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT v FROM g WHERE g.id > fz.id) AS x FROM (SELECT id + 0 AS id FROM fz WHERE 1 = 0) fz");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT v FROM g WHERE g.id > fz.id) AS x FROM fz WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT v FROM g WHERE g.id = fz.id OR g.v = fz.id) AS x FROM (SELECT id + 0 AS id FROM fz "
                + "WHERE 1 = 0) fz");
        assertAnswer("",
            "SELECT id FROM (SELECT id + 0 AS id FROM fz WHERE 1 = 0) fz WHERE (SELECT v FROM g WHERE g.id = fz.id) "
                + "> 0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 80",
            "SELECT id FROM (SELECT id + 0 AS id FROM fz WHERE 1 = 0) fz GROUP BY id HAVING (SELECT v FROM g WHERE "
                + "g.id = fz.id) > 0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 70",
            "SELECT id FROM (SELECT id + 0 AS id FROM fz WHERE 1 = 0) fz ORDER BY (SELECT v FROM g WHERE g.id = "
                + "fz.id)");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 14",
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz WHERE 1 = 0) d LEFT JOIN one o "
                + "ON TRUE");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 14",
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM one o LEFT JOIN (SELECT * FROM fz WHERE 1 = 0) d "
                + "ON TRUE");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 14",
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT * FROM fz WHERE 1 = 0) d FULL JOIN one o "
                + "ON TRUE");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 14",
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT value::INT AS id FROM TABLE(FLATTEN(INPUT "
                + "=> PARSE_JSON('[5,7]'))) WHERE 1 = 0) d");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 14",
            "SELECT d.id, (SELECT v FROM g WHERE g.id = d.id) FROM (SELECT value::INT AS id FROM TABLE(FLATTEN(INPUT "
                + "=> PARSE_JSON('[5,7]')))) d WHERE 1 = 0");
        assertAnswer("",
            "SELECT d.id, f.value FROM (SELECT id FROM fz) d, TABLE(FLATTEN(INPUT => ARRAY_CONSTRUCT(d.id, (SELECT v "
                + "FROM g WHERE g.id = d.id)))) f WHERE 1 = 0");
        assertAnswer("",
            "SELECT d.id, f.value FROM (SELECT id + 0 AS id FROM fz) d, TABLE(FLATTEN(INPUT => "
                + "ARRAY_CONSTRUCT(d.id))) f WHERE 1 = 0");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MIN(vz.j) FROM g) AS x FROM vz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Function MAX does not support ARRAY argument type",
            "SELECT id, (SELECT MAX(vz.a) FROM g) AS x FROM vz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Function MAX does not support OBJECT argument type",
            "SELECT id, (SELECT MAX(vz.o) FROM g WHERE g.id = 5) AS x FROM vz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated at line 1, "
                + "position 12",
            "SELECT id, (SELECT MIN(vz.j) FROM g WHERE g.id = vz.id) AS x FROM vz ORDER BY id");
        assertAnswer("5|65 ;; 7|65", "SELECT id, (SELECT MAX(v) + MIN(vz.id) FROM g) AS x FROM vz ORDER BY id");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
