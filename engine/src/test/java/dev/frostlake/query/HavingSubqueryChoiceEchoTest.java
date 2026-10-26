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
 * An IFF or a CASE inside a HAVING subquery's own aggregate, as the nesting refusal prints it: IFF and
 * CASE_FLATTENED with their condition bare, a number converted to BOOLEAN compared with zero, a NOT in its cast,
 * a NULL branch as null, a SUM's cast and the parentheses of an operand (all live-verified).
 */
public class HavingSubqueryChoiceEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void choices() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE), (0, NULL), (NULL, TRUE)");
        engine.execute("CREATE OR REPLACE TABLE g (id INT, v INT)");
        engine.execute("INSERT INTO g VALUES (5, 50), (6, 60)");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(IFF(CORRELATION(FZ.ID) > 5, G.V, 0))] nested in [MAX(MAX(IFF(CORRELATION(FZ.ID) > 5, G.V, "
                + "0)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(IFF(fz.id > 5, g.v, 0)) FROM g)) = 60 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(IFF(CORRELATION(FZ.ID) <> 0, G.V, 0))] nested in [MAX(MAX(IFF(CORRELATION(FZ.ID) <> 0, G.V, "
                + "0)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(IFF(fz.id::BOOLEAN, g.v, 0)) FROM g)) = 60 ORDER "
                + "BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CASE_FLATTENED(CORRELATION(FZ.ID) <> 0, G.V, 0))] nested in "
                + "[MAX(MAX(CASE_FLATTENED(CORRELATION(FZ.ID) <> 0, G.V, 0)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(CASE WHEN fz.id::BOOLEAN THEN g.v ELSE 0 END) FROM "
                + "g)) = 60 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(IFF(CAST(NOT(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN)) AS BOOLEAN), G.V, 0))] nested in "
                + "[MAX(MAX(IFF(CAST(NOT(CAST(CORRELATION(FZ.ID) <> 0 AS BOOLEAN)) AS BOOLEAN), G.V, 0)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(IFF(NOT fz.id::BOOLEAN, g.v, 0)) FROM g)) = 60 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[COUNT(IFF(CORRELATION(FZ.ID) <> 0, G.V, null))] nested in [MAX(COUNT(IFF(CORRELATION(FZ.ID) <> 0, "
                + "G.V, null)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT COUNT(IFF(fz.id::BOOLEAN, g.v, NULL)) FROM g)) = 2 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX((IFF(G.V > 55, 1, "
                + "0)) + CORRELATION(FZ.ID))] nested in [MAX(MAX((IFF(G.V > 55, 1, 0)) + CORRELATION(FZ.ID)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(IFF(g.v > 55, 1, 0) + fz.id) FROM g)) > 0 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error: | [FZ.B] is not a valid group by expression",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(IFF(fz.b, g.v, 0)) FROM g)) = 60 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CASE_FLATTENED(CORRELATION(FZ.ID) > 5, G.V, 0))] nested in "
                + "[MAX(MAX(CASE_FLATTENED(CORRELATION(FZ.ID) > 5, G.V, 0)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(CASE WHEN fz.id > 5 THEN g.v ELSE 0 END) FROM g)) "
                + "= 60 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(CASE_FLATTENED(CORRELATION(FZ.ID) > 5, G.V, null))] nested in "
                + "[MAX(MAX(CASE_FLATTENED(CORRELATION(FZ.ID) > 5, G.V, null)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(CASE WHEN fz.id > 5 THEN g.v END) FROM g)) = 60 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: [MAX(CASE "
                + "CORRELATION(FZ.ID) WHEN 7 THEN G.V ELSE 0 END)] nested in [MAX(MAX(CASE CORRELATION(FZ.ID) WHEN 7 "
                + "THEN G.V ELSE 0 END))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(CASE fz.id WHEN 7 THEN g.v ELSE 0 END) FROM g)) = "
                + "60 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(IFF(CORRELATION(FZ.ID) > 5, G.V, null))] nested in [MAX(MAX(IFF(CORRELATION(FZ.ID) > 5, G.V, "
                + "null)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(IFF(fz.id > 5, g.v, NULL)) FROM g)) = 60 ORDER BY "
                + "id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[SUM(CAST(IFF(CORRELATION(FZ.ID) > 5, G.V, 0) AS NUMBER(38,0)))] nested in "
                + "[MAX(SUM(CAST(IFF(CORRELATION(FZ.ID) > 5, G.V, 0) AS NUMBER(38,0))))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT SUM(IFF(fz.id > 5, g.v, 0)) FROM g)) = 110 ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(IFF(CORRELATION(FZ.ID) > 5, G.V, 0))] nested in [MAX(MAX(IFF(CORRELATION(FZ.ID) > 5, G.V, "
                + "0)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(IFF(fz.id > 5 AND g.v > 0, g.v, 0)) FROM g)) = 60 "
                + "ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Aggregate functions cannot be nested: "
                + "[MAX(IFF(CORRELATION(FZ.ID) IS NULL, G.V, 0))] nested in [MAX(MAX(IFF(CORRELATION(FZ.ID) IS NULL, "
                + "G.V, 0)))]",
            "SELECT id FROM fz GROUP BY id HAVING MAX((SELECT MAX(IFF(fz.id IS NULL, g.v, 0)) FROM g)) = 60 ORDER BY "
                + "id");
    }
}
