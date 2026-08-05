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
 * Where a VARIANT JSON null LEAVES the semi-structured world it reads as SQL NULL, and where it stays
 * inside it, it is a value. Both halves were measured on a real Snowflake account with
 * {@code jn} standing for {@code PARSE_JSON('{"b":null}'):b}:
 *
 * <pre>
 *   scalar reading -> SQL NULL : jn+1  jn-1  jn*2  jn||'x'  CONCAT(jn,'x')  UPPER(jn)  LENGTH(jn)
 *                                ABS(jn)  TO_VARCHAR(jn)  jn::INT  jn::VARCHAR  DATEADD(day,jn,d)
 *                                COUNT(jn)=0   SUM/AVG/LISTAGG skip it
 *   value reading  -> the JSON null : TYPEOF  IS_NULL_VALUE  TO_JSON  ARRAY_CONSTRUCT
 *                                     OBJECT_CONSTRUCT  COALESCE/IFNULL/NVL  EQUAL_NULL  MAX
 * </pre>
 */
public class VariantJsonNullScalarContextTest extends BaseDatabaseTest {

    private static final String JN = "PARSE_JSON('{\"b\":null}'):b";

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void arithmeticOverAJsonNullIsSqlNullNotAnError() {
        // Live: jn + 1, jn - 1 and jn * 2 are all SQL NULL. Before the JSON null became a typed value
        // on every extraction path this threw "Cannot add: null + 1".
        assertNull(scalar("SELECT " + JN + " + 1"));
        assertNull(scalar("SELECT " + JN + " - 1"));
        assertNull(scalar("SELECT " + JN + " * 2"));
        assertNull(scalar("SELECT " + JN + " / 2"));
        // Parenthesised: unary minus binds tighter than the ':' path operator.
        assertNull(scalar("SELECT -(" + JN + ")"));
        assertNull(scalar("SELECT GET(PARSE_JSON('{\"b\":null}'),'b') + 1"));
    }

    @Test
    public void stringContextOverAJsonNullIsSqlNull() {
        // Live: jn || 'x' and CONCAT(jn,'x') are SQL NULL — not the text 'nullx'.
        assertNull(scalar("SELECT " + JN + " || 'x'"));
        assertNull(scalar("SELECT 'x' || " + JN));
        assertNull(scalar("SELECT CONCAT(" + JN + ", 'x')"));
        assertNull(scalar("SELECT UPPER(" + JN + ")"));
        assertNull(scalar("SELECT LENGTH(" + JN + ")"));
        assertNull(scalar("SELECT TO_VARCHAR(" + JN + ")"));
    }

    @Test
    public void castToAnOrdinaryTypeIsSqlNullEvenThroughCoalesce() {
        // Live: jn::INT / jn::VARCHAR / jn::BOOLEAN are SQL NULL, and COALESCE(jn, 9) RETURNS the JSON
        // null (it is not SQL NULL), so COALESCE(jn,9)::VARCHAR is SQL NULL rather than '9'.
        assertNull(scalar("SELECT " + JN + "::INT"));
        assertNull(scalar("SELECT " + JN + "::VARCHAR"));
        assertNull(scalar("SELECT " + JN + "::BOOLEAN"));
        assertNull(scalar("SELECT COALESCE(" + JN + ", 9)::VARCHAR"));
        assertNull(scalar("SELECT IFNULL(" + JN + ", 9)::VARCHAR"));
        assertNull(scalar("SELECT NVL(" + JN + ", 9)::VARCHAR"));
        // Casting to a semi-structured type keeps the JSON null.
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(" + JN + "::VARIANT)"));
    }

    @Test
    public void valueReadingFunctionsStillSeeTheJsonNull() {
        // Live: EQUAL_NULL(jn, NULL) is FALSE — the JSON null must reach it un-nulled — and the
        // constructors keep it as a JSON member.
        assertEquals(false, scalar("SELECT EQUAL_NULL(" + JN + ", NULL)"));
        assertEquals("null", scalar("SELECT TO_JSON(" + JN + ")"));
        assertEquals("[null]", scalar("SELECT TO_JSON(ARRAY_CONSTRUCT(" + JN + "))"));
        assertEquals("{\"k\":null}", scalar("SELECT TO_JSON(OBJECT_CONSTRUCT('k', " + JN + "))"));
        assertEquals(true, scalar("SELECT IS_NULL_VALUE(" + JN + ")"));
    }

    @Test
    public void valueComputingAggregatesSkipAJsonNull() {
        // Live, over the rows {"b":null} / {"b":7} / {"c":1}: COUNT(v:b) is 1 (neither the JSON null
        // nor the missing key counts) and SUM is 7.0 — a DOUBLE (
        // SYSTEM$TYPEOF is FLOAT), because the declared-VARIANT argument flips SUM to the double tier
        // even though the surviving value is a whole number.
        engine.execute("CREATE TABLE json_null_agg (id INTEGER, v VARIANT)");
        engine.execute("INSERT INTO json_null_agg SELECT 1, PARSE_JSON('{\"b\":null}')");
        engine.execute("INSERT INTO json_null_agg SELECT 2, PARSE_JSON('{\"b\":7}')");
        engine.execute("INSERT INTO json_null_agg SELECT 3, PARSE_JSON('{\"c\":1}')");

        assertEquals(1L, scalar("SELECT COUNT(v:b) FROM json_null_agg"));
        assertEquals(7.0, scalar("SELECT SUM(v:b) FROM json_null_agg"));
        // The windowed form must agree with the grouped one: over the lone JSON-null row the count
        // is 0 (live) — the frame has a row, but its argument value is missing input.
        assertEquals(0L, scalar("SELECT COUNT(v:b) OVER () FROM json_null_agg WHERE id = 1"));
        assertEquals(1L, scalar("SELECT COUNT(v:b) OVER () FROM json_null_agg WHERE id = 2"));
        // The windowed SUM/AVG share the declared-VARIANT double tier (live: SYSTEM$TYPEOF FLOAT),
        // and AVG's grouped form does too.
        assertEquals(7.0, scalar("SELECT SUM(v:b) OVER () FROM json_null_agg LIMIT 1"));
        assertEquals(7.0, scalar("SELECT AVG(v:b) OVER () FROM json_null_agg LIMIT 1"));
        assertEquals(7.0, scalar("SELECT AVG(v:b) FROM json_null_agg"));
    }
}
