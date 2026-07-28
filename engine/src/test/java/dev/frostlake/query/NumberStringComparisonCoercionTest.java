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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake implicitly coerces a VARCHAR compared against a NUMBER to a number — {@code 999001 =
 * '999001'} is TRUE, {@code '100' > 20} is TRUE — while two strings never coerce ({@code '01' = '1'}
 * stays FALSE). The idiom is load-bearing: staging pipelines re-declare a NUMBER key as VARCHAR in a
 * temp table and join it back to the numeric original. Both halves of the engine needed it: the
 * comparison itself ({@code ExpressionArithmetic}) and the hash-join key bucketing (a numeric-looking
 * string must land in the same bucket as its number, or the equi-join silently returns nothing).
 */
public class NumberStringComparisonCoercionTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void aNumberKeyJoinsToItsVarcharCopy() {
        engine.execute("CREATE TABLE items (name VARCHAR, def_key NUMBER(38,0))");
        engine.execute("CREATE TABLE defs (def_key VARCHAR, cls VARCHAR)");
        engine.execute("INSERT INTO items VALUES ('i-1', 999001), ('i-2', 999001), ('i-3', 7)");
        engine.execute("INSERT INTO defs VALUES (999001, 'WIDE'), ('7', 'NARROW')");
        final ResultSet rs = engine.executeQuery(
            "SELECT i.name, d.cls FROM items i INNER JOIN defs d ON d.def_key = i.def_key ORDER BY i.name");
        assertEquals(3, rs.getRowCount());
        assertEquals("WIDE", String.valueOf(rs.getRows().get(0).getValue(1)));
        assertEquals("NARROW", String.valueOf(rs.getRows().get(2).getValue(1)));
    }

    @Test
    public void whereCoercesVarcharAgainstANumberLiteral() {
        engine.execute("CREATE TABLE t (k VARCHAR)");
        engine.execute("INSERT INTO t VALUES ('999001'), ('other')");
        assertEquals(1, engine.executeQuery("SELECT k FROM t WHERE k = 999001").getRowCount());
    }

    @Test
    public void literalComparisonsMatchSnowflake() {
        assertEquals(true, scalar("SELECT 999001 = '999001'"));
        assertEquals(true, scalar("SELECT '01' = 1"));            // numeric coercion
        assertEquals(false, scalar("SELECT '01' = '1'"));         // two strings: no coercion
        assertEquals(true, scalar("SELECT '100' > 20"));          // ordering coerces too
        assertEquals(false, scalar("SELECT 'abc' = 1"));          // non-numeric string: no match
    }
}
