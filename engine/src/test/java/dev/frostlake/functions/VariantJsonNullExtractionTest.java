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
 * Extracting a PRESENT JSON null yields the VARIANT {@code NULL_VALUE}, not SQL NULL — whichever
 * operator does the extracting. A MISSING key stays SQL NULL. Every expectation here was measured on
 * a real Snowflake account on; the divergence mattered because
 * {@code WHERE v:b IS NOT NULL} selected different rows on the two engines.
 *
 * <p>Live reference values, with {@code jn} standing for {@code PARSE_JSON('{"b":null}'):b}:
 * <pre>
 *   TYPEOF(jn) = 'NULL_VALUE'   jn IS NULL = FALSE   jn IS NOT NULL = TRUE
 *   TYPEOF(PARSE_JSON('{"a":1}'):zz) = SQL NULL      PARSE_JSON('{"a":1}'):zz IS NULL = TRUE
 * </pre>
 */
public class VariantJsonNullExtractionTest extends BaseDatabaseTest {

    private static final String OBJ = "PARSE_JSON('{\"b\":null,\"a\":1}')";
    private static final String ARR = "PARSE_JSON('[1,null,2]')";

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void everyExtractionOperatorKeepsAPresentJsonNull() {
        // Live: all eight spellings answer 'NULL_VALUE' on a real account.
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(" + OBJ + ":b)"));
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(GET(" + OBJ + ",'b'))"));
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(" + OBJ + "['b'])"));
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(GET_PATH(" + OBJ + ",'b'))"));
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(GET_IGNORE_CASE(" + OBJ + ",'B'))"));
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(GET(" + ARR + ",1))"));
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(" + ARR + "[1])"));
        assertEquals("NULL_VALUE",
            scalar("SELECT TYPEOF(GET(OBJECT_CONSTRUCT_KEEP_NULL('b',NULL),'b'))"));
    }

    @Test
    public void flattenValueKeepsAJsonNullMember() {
        // Live: FLATTEN over [1,null,2] gives TYPEOF(value) INTEGER / NULL_VALUE / INTEGER, and over
        // {"a":1,"b":null} the b row's TYPEOF(value) is 'NULL_VALUE'.
        assertEquals("INTEGER,NULL_VALUE,INTEGER", scalar("""
            SELECT LISTAGG(TYPEOF(f.value), ',') WITHIN GROUP (ORDER BY f.index)
            FROM TABLE(FLATTEN(input => %s)) f""".formatted(ARR)));
        assertEquals("a=INTEGER,b=NULL_VALUE", scalar("""
            SELECT LISTAGG(f.key || '=' || TYPEOF(f.value), ',') WITHIN GROUP (ORDER BY f.key)
            FROM TABLE(FLATTEN(input => %s)) f""".formatted(OBJ)));
    }

    @Test
    public void tryParseJsonOfNullIsAJsonNullLikeParseJson() {
        // Live: TYPEOF(TRY_PARSE_JSON('null')) = 'NULL_VALUE', the same as PARSE_JSON('null').
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(TRY_PARSE_JSON('null'))"));
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(PARSE_JSON('null'))"));
    }

    @Test
    public void aMissingKeyStaysSqlNull() {
        // Live: TYPEOF(PARSE_JSON('{"a":1}'):zz) is SQL NULL and GET at an out-of-range index too.
        assertNull(scalar("SELECT TYPEOF(" + OBJ + ":zz)"));
        assertNull(scalar("SELECT TYPEOF(GET(" + OBJ + ",'zz'))"));
        assertNull(scalar("SELECT TYPEOF(GET(" + ARR + ",99))"));
        assertEquals(true, scalar("SELECT " + OBJ + ":zz IS NULL"));
        assertEquals(9L, scalar("SELECT COALESCE(" + OBJ + ":zz, 9)"));
    }

    @Test
    public void isNullIsFalseForAnExtractedJsonNull() {
        // The reason this matters: WHERE v:b IS NOT NULL must select the JSON-null row.
        assertEquals(false, scalar("SELECT GET(" + OBJ + ",'b') IS NULL"));
        assertEquals(true, scalar("SELECT GET(" + OBJ + ",'b') IS NOT NULL"));
        assertEquals(true, scalar("SELECT IS_NULL_VALUE(GET(" + ARR + ",1))"));
    }

    @Test
    public void whereClauseSelectsTheJsonNullRow() {
        // Live, over rows {"b":null} / {"b":7} / {"c":1}: v:b IS NOT NULL picks ids 1,2 and IS NULL
        // picks only id 3 (the MISSING key). TYPEOF per row is NULL_VALUE / INTEGER / SQL NULL.
        engine.execute("CREATE TABLE json_null_rows (id INTEGER, v VARIANT)");
        engine.execute("INSERT INTO json_null_rows SELECT 1, PARSE_JSON('{\"b\":null}')");
        engine.execute("INSERT INTO json_null_rows SELECT 2, PARSE_JSON('{\"b\":7}')");
        engine.execute("INSERT INTO json_null_rows SELECT 3, PARSE_JSON('{\"c\":1}')");

        assertEquals("1,2", scalar("""
            SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id)
            FROM json_null_rows WHERE v:b IS NOT NULL"""));
        assertEquals("3", scalar("""
            SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY id)
            FROM json_null_rows WHERE v:b IS NULL"""));
        // The stored value round-trips as a JSON null, not as SQL NULL.
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(v:b) FROM json_null_rows WHERE id = 1"));
        assertEquals("null", scalar("SELECT TO_JSON(v:b) FROM json_null_rows WHERE id = 1"));
    }

    @Test
    public void pathingIntoOrIndexingAJsonNullIsSqlNull() {
        // Live: v:b.deeper and v:b[0] over a JSON null are both SQL NULL — a JSON null has no members.
        assertNull(scalar("SELECT TYPEOF(" + OBJ + ":b.deeper)"));
        assertNull(scalar("SELECT TYPEOF(" + OBJ + ":b[0])"));
    }
}
