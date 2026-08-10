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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The bitwise scalar family: BITAND, BITOR, BITXOR, BITNOT, BITSHIFTLEFT, BITSHIFTRIGHT — values,
 * negatives (two's complement), NULL propagation, nesting, and use over columns in aggregation.
 */
public class BitwiseFunctionsTest extends BaseDatabaseTest {

    private long one(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return ((Number) value).longValue();
    }

    @Test
    public void coreValues() {
        assertEquals(8L, one("SELECT BITAND(12, 10)"));
        assertEquals(14L, one("SELECT BITOR(12, 10)"));
        assertEquals(6L, one("SELECT BITXOR(12, 10)"));
        assertEquals(16L, one("SELECT BITSHIFTLEFT(1, 4)"));
        assertEquals(4L, one("SELECT BITSHIFTRIGHT(16, 2)"));
    }

    @Test
    public void twosComplementNegatives() {
        assertEquals(-1L, one("SELECT BITNOT(0)"));
        assertEquals(-6L, one("SELECT BITNOT(5)"));
        assertEquals(4L, one("SELECT BITAND(-4, 7)")); // ...11111100 & 00000111
        assertEquals(-2L, one("SELECT BITSHIFTLEFT(-1, 1)"));
    }

    @Test
    public void nullPropagatesThroughEveryForm() {
        assertNull(engine.executeQuery("SELECT BITAND(NULL, 7)").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT BITNOT(NULL)").getRows().get(0).getValue(0));
        assertNull(engine.executeQuery("SELECT BITSHIFTLEFT(NULL, 2)").getRows().get(0).getValue(0));
    }

    @Test
    public void nestedCallsCompose() {
        // BITAND(BITOR(4,1)=5, BITXOR(7,2)=5) = 5; shifted left 1 = 10; NOT -> -11
        assertEquals(5L, one("SELECT BITAND(BITOR(4, 1), BITXOR(7, 2))"));
        assertEquals(-11L, one("SELECT BITNOT(BITSHIFTLEFT(BITAND(BITOR(4, 1), BITXOR(7, 2)), 1))"));
    }

    @Test
    public void overColumnsInsideAggregation() {
        engine.execute("CREATE TABLE bits (n INTEGER)");
        engine.execute("INSERT INTO bits VALUES (1), (2), (3), (4), (5)");
        // BITAND(n,1) marks odd numbers: 1,0,1,0,1 -> sum 3
        assertEquals(3L, one("SELECT SUM(BITAND(n, 1)) FROM bits"));
        // flags folded with BITOR over each row's shifted bit: 2|4|8|16|32 = 62
        assertEquals(62L, one("SELECT SUM(BITSHIFTLEFT(1, n)) FROM bits"));
    }
}
