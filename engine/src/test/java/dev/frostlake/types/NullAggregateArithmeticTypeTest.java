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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * MAX, MIN and ANY_VALUE over the word NULL answer an untyped NULL, and arithmetic over one is typed as over the
 * word: MAX(NULL) + 1 is NUMBER(19,0); a membership over an empty query is NULL for such a value (all
 * live-verified).
 */
public class NullAggregateArithmeticTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void memberships() {
        engine.execute("CREATE OR REPLACE TABLE pq1 (n INT)");
        assertAnswer("null", "SELECT ANY_VALUE(NULL) IN (SELECT 1 WHERE FALSE)");
        assertAnswer("null", "SELECT (MAX(NULL) + 1) IN (SELECT 1 WHERE FALSE)");
        assertAnswer("null", "SELECT NULLIF(1, 1) IN (SELECT 1 WHERE FALSE)");
        assertAnswer("null", "SELECT NULL IN (SELECT 1 WHERE FALSE)");
        assertAnswer("null", "SELECT (NULL + 1) IN (SELECT 1 WHERE FALSE)");
        assertAnswer("null", "SELECT NULLIF(1, 1) NOT IN (SELECT 1 WHERE FALSE)");
        assertAnswer("null", "SELECT CAST(NULL AS INT) IN (SELECT 1 WHERE FALSE)");
        assertAnswer("null", "SELECT x IN (SELECT 1 WHERE FALSE) FROM (SELECT NULL AS x)");
        assertAnswer("null", "SELECT ANY_VALUE(NULL) = ANY (SELECT 1 WHERE FALSE)");
        assertAnswer("null", "SELECT IFF(TRUE, NULL, 1) IN (SELECT 1 WHERE FALSE)");
        assertAnswer("false", "SELECT NULLIF(2, 1) IN (SELECT 1 WHERE FALSE)");
        assertAnswer("null", "SELECT ANY_VALUE(NULL) IN (SELECT n FROM pq1)");
        assertAnswer("NUMBER(30,0)[SB8]|NUMBER(36,6)[SB16]|NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(SUM(NULL)), SYSTEM$TYPEOF(AVG(NULL)), SYSTEM$TYPEOF(MAX(NULL) + 1)");
        assertAnswer("NULL[LOB]|NULL[LOB]|NUMBER(18,0)[SB8]",
            "SELECT SYSTEM$TYPEOF(MAX(NULL)), SYSTEM$TYPEOF(MIN(NULL)), SYSTEM$TYPEOF(COUNT(NULL))");
        assertAnswer("NUMBER(19,0)[SB1]|NUMBER(1,0)[SB1]|NULL[LOB]",
            "SELECT SYSTEM$TYPEOF(NULL + 1), SYSTEM$TYPEOF(NULLIF(1, 1)), SYSTEM$TYPEOF(ANY_VALUE(NULL))");
        assertShape("S:NUMBER(30,0), A:NUMBER(36,6), M:NUMBER(19,0), P:NUMBER(19,0)",
            "SELECT SUM(NULL) s, AVG(NULL) a, MAX(NULL) + 1 m, NULL + 1 p");
    }

    @Test
    public void arithmetic() {
        engine.execute("CREATE OR REPLACE TABLE tn (i INT)");
        engine.execute("INSERT INTO tn VALUES (1)");
        assertAnswer("NUMBER(19,0)[SB1]|NUMBER(19,0)[SB1]|NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MIN(NULL) + 1), SYSTEM$TYPEOF(ANY_VALUE(NULL) + 1), SYSTEM$TYPEOF(MAX(NULL) * 2)");
        assertAnswer("NUMBER(19,1)[SB1]|NUMBER(19,0)[SB1]|NUMBER(24,6)[SB1]",
            "SELECT SYSTEM$TYPEOF(MAX(NULL) + 1.5), SYSTEM$TYPEOF(MAX(NULL) - 1), SYSTEM$TYPEOF(MAX(NULL) / 2)");
        assertAnswer("VARCHAR(134217728)[LOB]|NUMBER(18,0)[SB1]|NUMBER(19,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MAX(NULL) || 'x'), SYSTEM$TYPEOF(-MAX(NULL)), SYSTEM$TYPEOF(MAX(NULL) + 1000000)");
        assertAnswer("NUMBER(19,0)[SB1]|NUMBER(19,1)[SB1]|NUMBER(24,6)[SB1]",
            "SELECT SYSTEM$TYPEOF(NULL * 2), SYSTEM$TYPEOF(NULL + 1.5), SYSTEM$TYPEOF(NULL / 2)");
        assertShape("A:NUMBER(19,0), B:NUMBER(19,1), C:NUMBER(19,0), D:NUMBER(19,0)",
            "SELECT MIN(NULL) + 1 a, MAX(NULL) + 1.5 b, MAX(NULL) * 2 c, NULL * 2 d");
        assertAnswer("NUMBER(19,0)[SB1]|NUMBER(19,0)[SB1]|FLOAT[DOUBLE]",
            "SELECT SYSTEM$TYPEOF(MAX(NULL) + MAX(NULL)), SYSTEM$TYPEOF(NULL + NULL), SYSTEM$TYPEOF(MAX(NULL) + "
                + "1::FLOAT)");
        assertAnswer("NUMBER(1,0)[SB1]|BOOLEAN[ROWINDEX]|NUMBER(18,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(COALESCE(MAX(NULL), 1)), SYSTEM$TYPEOF(MAX(NULL) = 1), SYSTEM$TYPEOF(-NULL)");
        assertAnswer("REFUSED SQL compilation error: | [TN.I] is not a valid group by expression",
            "SELECT SYSTEM$TYPEOF(MAX(NULL) + i) FROM tn");
        assertAnswer("NUMBER(2,0)[SB1]|NUMBER(2,0)[SB1]",
            "SELECT SYSTEM$TYPEOF(MAX(NULL) % 2), SYSTEM$TYPEOF(NULL % 2)");
        assertShape("A:NUMBER(19,0), B:NUMBER(24,6), C:NUMBER(18,0), D:NUMBER(19,0)",
            "SELECT MAX(NULL) - 1 a, MAX(NULL) / 2 b, -MAX(NULL) c, MAX(NULL) + MAX(NULL) d");
        assertAnswer("NUMBER(19,0)[SB1]", "SELECT SYSTEM$TYPEOF(MAX(NULL) + 1) FROM tn GROUP BY i");
        assertAnswer("null|NUMBER(21,2)[SB1]", "SELECT MAX(NULL) + 1, SYSTEM$TYPEOF(ANY_VALUE(NULL) * 1.25)");
    }
}
