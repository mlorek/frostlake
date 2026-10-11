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
 * The || operator answers a text whatever it joins: a bare NULL beside a NULL, a number, a FLOAT or a
 * BOOLEAN, and two numbers, are VARCHAR(134217728), so a WHERE or a HAVING of one is refused as a predicate
 * that is no BOOLEAN and IFF refuses it for its condition. CONCAT's arguments are echoed as the texts the
 * plan reads them as, and CONCAT_WS as the CONCAT of its elements. Every answer is live-verified.
 */
public class ConcatenationOperandTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void theTextAConcatenationIs() {
        engine.execute(
            "CREATE OR REPLACE TABLE t (n NUMBER(5,2), n40 NUMBER(4,0), k INT)");
        engine.execute(
            "INSERT INTO t VALUES (1.5, 17, 1), (2.5, 3, 2)");
        engine.execute(
            "CREATE OR REPLACE TABLE d (k INT)");
        engine.execute(
            "INSERT INTO d VALUES (1), (2)");
        engine.execute(
            "CREATE OR REPLACE TABLE rt (n INT, f FLOAT, g VARCHAR(5), n52 NUMBER(5,2), v VARIANT)");
        engine.execute(
            "CREATE OR REPLACE VIEW v2 (x) AS SELECT n + 1 FROM t");
        engine.execute(
            "SET one = 1");
        engine.execute(
            "SET half = 0.5");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[CONCAT(SYSTEM$NULL_TO_TEXT(null), CAST(RT.N AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE CONCAT(NULL, n)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[CONCAT(CAST(RT.N AS VARCHAR(134217728)), SYSTEM$NULL_TO_TEXT(null))]",
            "SELECT * FROM rt WHERE CONCAT(n, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate [CONCAT(RT.G, "
                + "CAST(RT.N AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE CONCAT(g, n)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[CONCAT(CAST(RT.N AS VARCHAR(134217728)), CAST(RT.N52 AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE CONCAT(n, n52)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[CONCAT(CAST(RT.F AS VARCHAR(134217728)), 'a')]",
            "SELECT * FROM rt WHERE CONCAT(f, 'a')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[CONCAT(SYSTEM$NULL_TO_TEXT(null), RT.G)]",
            "SELECT * FROM rt WHERE CONCAT(NULL, g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[CONCAT(SYSTEM$NULL_TO_TEXT(null), SYSTEM$NULL_TO_TEXT(null))]",
            "SELECT * FROM rt WHERE CONCAT(NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[CONCAT(CAST(RT.N AS VARCHAR(134217728)), ',', RT.G)]",
            "SELECT * FROM rt WHERE CONCAT_WS(',', n, g)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[CONCAT(CAST(RT.V AS VARCHAR(134217728)), CAST(RT.N AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE CONCAT(v, n)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[CONCAT(CAST(RT.N AS VARCHAR(134217728)), 'a', CAST(TRUE AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE CONCAT(n, 'a', TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[UPPER(CAST(RT.N AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE UPPER(n)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate [CAST(RT.N AS "
                + "VARCHAR(134217728))]",
            "SELECT * FROM rt WHERE TO_VARCHAR(n)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[LPAD(CAST(RT.N AS VARCHAR(134217728)), 3, '0')]",
            "SELECT * FROM rt WHERE LPAD(n, 3, '0')");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[(SYSTEM$NULL_TO_TEXT(null)) || (CAST(RT.N AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE NULL || n");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[(SYSTEM$NULL_TO_TEXT(null)) || (SYSTEM$NULL_TO_TEXT(null))]",
            "SELECT * FROM rt WHERE NULL || NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate [(CAST(RT.N AS "
                + "VARCHAR(134217728))) || (SYSTEM$NULL_TO_TEXT(null))]",
            "SELECT * FROM rt WHERE n || NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate [(CAST(RT.N AS "
                + "VARCHAR(134217728))) || (CAST(RT.N AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE n || n");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate ['a' || "
                + "(CAST(RT.N AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE 'a' || n");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate [RT.G || "
                + "(SYSTEM$NULL_TO_TEXT(null))]",
            "SELECT * FROM rt WHERE g || NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[(SYSTEM$NULL_TO_TEXT(null)) || RT.G]",
            "SELECT * FROM rt WHERE NULL || g");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[(SYSTEM$NULL_TO_TEXT(null)) || 'x']",
            "SELECT * FROM rt WHERE NULL || 'x'");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(15)] for predicate [RT.G || RT.G || RT.G]",
            "SELECT * FROM rt WHERE g || g || g");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[(SYSTEM$NULL_TO_TEXT(null)) || (CAST(RT.F AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE NULL || f");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[(SYSTEM$NULL_TO_TEXT(null)) || (CAST(TRUE AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE NULL || TRUE");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[(SYSTEM$NULL_TO_TEXT(null)) || (CAST(RT.N AS VARCHAR(134217728))) || (SYSTEM$NULL_TO_TEXT(null))]",
            "SELECT * FROM rt WHERE NULL || n || NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate [(CAST(RT.N52 "
                + "AS VARCHAR(134217728))) || (CAST(RT.N AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE n52 || n");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate [(CAST(RT.V AS "
                + "VARCHAR(134217728))) || (CAST(RT.N AS VARCHAR(134217728)))]",
            "SELECT * FROM rt WHERE v || n");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[(SYSTEM$NULL_TO_TEXT(null)) || (CAST(MAX(RT.N) AS VARCHAR(134217728)))]",
            "SELECT COUNT(*) FROM rt HAVING NULL || MAX(n)");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(NULL || NULL), SYSTEM$TYPEOF(CONCAT(NULL, NULL)), SYSTEM$TYPEOF(NULL || 1)");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(NULL || n), SYSTEM$TYPEOF(n || n), SYSTEM$TYPEOF(CONCAT(NULL, n)) FROM t LIMIT 1");
        assertAnswer(
            "null|null|null",
            "SELECT NULL || NULL, NULL || 1, CONCAT(NULL, NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (VARCHAR(134217728), NUMBER(1,0), NUMBER(1,0))",
            "SELECT IFF(NULL || NULL, 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IFF': (VARCHAR(134217728), NUMBER(1,0), NUMBER(1,0))",
            "SELECT IFF(NULL || 1, 1, 2)");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[(SYSTEM$NULL_TO_TEXT(null)) || (SYSTEM$NULL_TO_TEXT(null))]",
            "SELECT 1 WHERE NULL || NULL");
        assertAnswer(
            "REFUSED SQL compilation error: | Invalid data type [VARCHAR(134217728)] for predicate "
                + "[(SYSTEM$NULL_TO_TEXT(null)) || (CAST(1 AS VARCHAR(134217728)))]",
            "SELECT 1 WHERE NULL || 1");
    }
}
