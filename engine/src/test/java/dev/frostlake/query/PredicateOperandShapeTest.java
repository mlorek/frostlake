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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The operands a predicate or a path operator takes, as live types them: BETWEEN with a NULL bound is
 * three-valued, a predicate handed to LIKE ANY is refused, the path operators refuse a base GET cannot read at
 * the colon or the bracket, and a flat tuple IN is refused for its own argument types at its IN before any
 * operator over it. Live-verified.
 */
public class PredicateOperandShapeTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount(), sql);
        return rs.getRows().get(0).getValue(0);
    }

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        return refused.getMessage();
    }

    private static String at(final int line, final int position, final String function, final String types) {
        return "SQL compilation error: error line " + line + " at position " + position
            + "\nInvalid argument types for function '" + function + "': (" + types + ")";
    }

    @Test
    public void aNullBoundLeavesBetweenUnknownUnlessTheOtherBoundFails() {
        assertNull(scalar("SELECT 1 BETWEEN 0 AND NULL AS r"));
        assertNull(scalar("SELECT NULL BETWEEN 0 AND 1 AS r"));
        assertNull(scalar("SELECT 5 BETWEEN NULL AND 6 AS r"));
        assertNull(scalar("SELECT 5 NOT BETWEEN 0 AND NULL AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT 5 BETWEEN 6 AND NULL AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 5 NOT BETWEEN 6 AND NULL AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT 5 BETWEEN NULL AND 4 AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 5 NOT BETWEEN NULL AND 4 AS r"));
        assertNull(scalar("SELECT 1 BETWEEN 0 AND 2 :x AS r"));
        assertNull(scalar("SELECT 1 BETWEEN 0 AND 2 [0] AS r"));
        assertNull(scalar("SELECT 1 BETWEEN 0 :x AND 2 AS r"));
        assertEquals(Boolean.TRUE, scalar("SELECT 1 BETWEEN 0 AND PARSE_JSON('null') AS r"));
        engine.execute("CREATE OR REPLACE TABLE pos (n INT)");
        engine.execute("INSERT INTO pos VALUES (1)");
        assertEquals(0, engine.executeQuery("SELECT n FROM pos WHERE NOT (n BETWEEN 0 AND NULL)").getRowCount());
        assertEquals(1, engine.executeQuery("SELECT n FROM pos WHERE NOT (n BETWEEN 2 AND NULL)").getRowCount());
    }

    @Test
    public void aPredicateHandedToLikeAnyIsRefused() {
        assertEquals(at(1, 19, "ILIKE_ANY", "BOOLEAN, NULL, VARCHAR(1)"), refusal("SELECT 'a' IS NULL ILIKE ANY ('a') AS r"));
        assertEquals(at(1, 19, "LIKE_ANY", "BOOLEAN, NULL, VARCHAR(1)"), refusal("SELECT 'a' IS NULL LIKE ANY ('a') AS r"));
        assertEquals(at(1, 19, "LIKE_ALL", "BOOLEAN, NULL, VARCHAR(1)"), refusal("SELECT 'a' IS NULL LIKE ALL ('a') AS r"));
        assertEquals(at(1, 19, "ILIKE_ANY", "BOOLEAN, NULL, VARCHAR(1)"), refusal("SELECT 1 IN (1, 2) ILIKE ANY ('a') AS r"));
        assertEquals(at(1, 31, "ILIKE_ANY", "BOOLEAN, NULL, VARCHAR(1)"),
            refusal("SELECT 'a' LIKE 'a' ESCAPE '!' ILIKE ANY ('a') AS r"));
        assertEquals(at(1, 27, "ILIKE_ANY", "BOOLEAN, NULL, VARCHAR(1)"),
            refusal("SELECT 1 NOT IN (SELECT 1) ILIKE ANY ('a') AS r"));
        assertEquals(at(1, 19, "ILIKE_ANY", "BOOLEAN, VARCHAR(1), VARCHAR(1)"),
            refusal("SELECT 'a' IS NULL ILIKE ANY ('a') ESCAPE '!' AS r"));
        assertEquals(at(1, 19, "ILIKE_ANY", "BOOLEAN, NULL, VARCHAR(1), VARCHAR(1)"),
            refusal("SELECT 'a' IS NULL ILIKE ANY ('a', 'b') AS r"));
        assertEquals(at(1, 11, "LIKE_ANY", "VARCHAR(1), NULL, BOOLEAN"), refusal("SELECT 'a' LIKE ANY ('a' IS NULL) AS r"));
        assertEquals(at(1, 11, "LIKE_ANY", "VARCHAR(1), NULL, VARCHAR(1), BOOLEAN"),
            refusal("SELECT 'a' LIKE ANY ('a', 'b' IS NULL) AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT TRUE ILIKE ANY ('a') AS r"));
        assertEquals(Boolean.FALSE, scalar("SELECT 'a' LIKE ANY (TRUE) AS r"));
    }

    @Test
    public void thePathOperatorsRefuseABaseGetCannotRead() {
        engine.execute("CREATE OR REPLACE TABLE pgb (s VARCHAR(5), n INT, b BOOLEAN, d DATE, tm TIME, "
            + "ts TIMESTAMP_NTZ, bi BINARY, v VARIANT)");
        engine.execute("INSERT INTO pgb SELECT 'abc', 1, TRUE, '2020-01-01', '10:00:00', '2020-01-01 10:00:00', "
            + "TO_BINARY('41', 'HEX'), TO_VARIANT(1)");
        assertEquals(at(1, 8, "GET", "VARCHAR(5), VARCHAR(1)"), refusal("SELECT s:x AS r FROM pgb"));
        assertEquals(at(1, 8, "GET", "VARCHAR(5), NUMBER(1,0)"), refusal("SELECT s[0] AS r FROM pgb"));
        assertEquals(at(1, 8, "GET", "VARCHAR(5), VARCHAR(1)"), refusal("SELECT s['x'] AS r FROM pgb"));
        assertEquals(at(1, 8, "GET", "VARCHAR(5), VARCHAR(3)"), refusal("SELECT s:abc AS r FROM pgb"));
        assertEquals(at(0, -1, "GET", "VARCHAR(5), VARCHAR(1)"), refusal("SELECT s:x.y AS r FROM pgb"));
        assertEquals(at(1, 8, "GET", "DATE, VARCHAR(1)"), refusal("SELECT d:x AS r FROM pgb"));
        assertEquals(at(1, 9, "GET", "TIME(9), NUMBER(1,0)"), refusal("SELECT tm[0] AS r FROM pgb"));
        assertEquals(at(1, 9, "GET", "TIMESTAMP_NTZ(9), VARCHAR(1)"), refusal("SELECT ts:x AS r FROM pgb"));
        assertEquals(at(1, 9, "GET", "BINARY(8388608), NUMBER(1,0)"), refusal("SELECT bi[0] AS r FROM pgb"));
        assertEquals(at(1, 14, "GET", "BOOLEAN, VARCHAR(1)"), refusal("SELECT (n = 1):x AS r FROM pgb"));
        assertEquals(at(1, 14, "GET", "BOOLEAN, NUMBER(1,0)"), refusal("SELECT (n = 1)[0] AS r FROM pgb"));
        assertEquals(at(1, 25, "GET", "VARCHAR(5), NUMBER(1,0)"), refusal("SELECT 1 FROM pgb WHERE s[0] IS NULL"));
        assertEquals(at(1, 20, "GET", "VARCHAR(1), VARCHAR(1)"), refusal("SELECT 'a' LIKE 'a' :x AS r"));
        assertEquals(at(1, 21, "GET", "VARCHAR(1), VARCHAR(1)"), refusal("SELECT 'a' RLIKE 'a' :x AS r"));
        assertEquals(at(1, 20, "GET", "VARCHAR(1), NUMBER(1,0)"), refusal("SELECT 'a' LIKE 'a' [0] AS r"));
        assertEquals(at(1, 19, "GET", "VARCHAR(2), NUMBER(1,0)"), refusal("SELECT ('a' || 'b')[1] AS r"));
        assertNull(scalar("SELECT n:x AS r FROM pgb"));
        assertNull(scalar("SELECT b[0] AS r FROM pgb"));
        assertNull(scalar("SELECT v:x AS r FROM pgb"));
        assertNull(scalar("SELECT TRUE:x AS r"));
        assertNull(scalar("SELECT NULL[0] AS r"));
    }

    @Test
    public void aFlatTupleInIsRefusedAtItsInFirst() {
        final String flat = "ROW(NUMBER(1,0), NUMBER(1,0)), NUMBER(1,0), NUMBER(1,0)";
        assertEquals(at(1, 14, "IN", flat), refusal("SELECT (1, 2) IN (1, 2) AS r"));
        assertEquals(at(0, -1, "IN", flat), refusal("SELECT (1, 2) NOT IN (1, 2) AS r"));
        assertEquals(at(1, 14, "IN", flat), refusal("SELECT (1, 2) IN (1, 2) = TRUE AS r"));
        assertEquals(at(1, 14, "IN", flat), refusal("SELECT (1, 2) IN (1, 2) LIKE 'a' AS r"));
        assertEquals(at(1, 14, "IN", flat), refusal("SELECT (1, 2) IN (1, 2) NOT LIKE 'a' AS r"));
        assertEquals(at(0, -1, "IN", flat), refusal("SELECT (1, 2) NOT IN (1, 2) LIKE 'a' AS r"));
        assertEquals(at(1, 14, "IN", flat), refusal("SELECT (1, 2) IN (1, 2) RLIKE 'a' AS r"));
        assertEquals(at(1, 18, "IN", "ROW(VARCHAR(1), VARCHAR(1)), VARCHAR(1)"), refusal("SELECT ('a', 'b') IN ('a') AS r"));
        assertEquals(at(1, 43, "IN", flat), refusal("SELECT 1 FROM (SELECT 1 AS c) WHERE (1, 2) IN (1, 2)"));
    }
}
