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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A string can have no width at all, which SYSTEM$TYPEOF spells as a bare {@code VARCHAR}: a cast to a bare VARCHAR,
 * the functions that declare a bare VARCHAR result, a COLLATE over an untyped NULL, and what carries such a string
 * through — LOWER, UPPER, TRIM, REVERSE, SPLIT_PART, HEX_ENCODE, MIN and MAX, a derived column, and a conditional
 * or union it LEADS. Concatenating it, cutting it with SUBSTR or LEFT, padding it, or following a sized branch gives
 * the width nothing bounds instead, VARCHAR(134217728). Every cell is live-verified.
 */
public class WidthlessStringTypeTest extends BaseDatabaseTest {

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aStringWithNoWidthIsABareVarchar() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(COLLATION('a'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULL COLLATE 'en-ci')", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULL::VARCHAR)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_DATABASE())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TO_CHAR(1))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(COLLATION('a' COLLATE 'en'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TO_VARCHAR(1))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TO_CHAR(CURRENT_DATE()))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_USER())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_ROLE())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_SCHEMA())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_WAREHOUSE())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(REPLACE('a','b','c'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CAST(1 AS VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(1::STRING)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF('a'::TEXT)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULL::STRING)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TO_JSON(PARSE_JSON('1')))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_TO_STRING([1],','))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TYPEOF(PARSE_JSON('1')))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_VERSION())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_REGION())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TO_VARCHAR(CURRENT_DATE()))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF('abc'::VARCHAR)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(PARSE_JSON('\"x\"')::VARCHAR)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(NULL::VARCHAR, 'ab'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LOWER(NULL::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(ENCRYPT_RAW(X'00', X'00', X'00')::VARCHAR)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_ACCOUNT())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LAST_QUERY_ID())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_STATEMENT())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_SESSION())", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(UPPER('a'::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, 'a'::VARCHAR, 'bb'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NVL(NULL::VARCHAR, 'x'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CASE WHEN TRUE THEN 'a'::VARCHAR ELSE 'bb' END)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TRIM('a'::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF('a'::VARCHAR COLLATE 'en-ci')", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(REPLACE('abc'::VARCHAR(3), 'b', 'c'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TO_VARCHAR('abc'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TO_VARCHAR('abc'::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(MAX('a'::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(MIN(CURRENT_USER()))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(RTRIM(CURRENT_DATABASE()))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LOWER(CURRENT_ROLE()))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULLIF('a'::VARCHAR, 'b'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(DECODE(1, 1, 'a'::VARCHAR, 'bb'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(GREATEST('a'::VARCHAR, 'bb'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(REPLACE(NULL, 'b', 'c'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TO_CHAR(NULL))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(COLLATION(NULL))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SPLIT_PART('a,b'::VARCHAR, ',', 1))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(REVERSE('ab'::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(INITCAP(CURRENT_USER()))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LTRIM(TO_CHAR(12)))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(HEX_ENCODE('a'::VARCHAR))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT 'a'::VARCHAR AS x)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT CURRENT_USER() AS x)", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT 'a'::VARCHAR AS x UNION ALL SELECT 'bbb') LIMIT 1", "VARCHAR[LOB]"},
        });
    }

    @Test
    public void aSizedStringOrOneThatLosesItsWidthSpellsTheWidth() {
        assertCells(new String[][] {
            {"SELECT SYSTEM$TYPEOF(LOWER(NULL))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF('a' COLLATE 'en-ci')", "VARCHAR(1)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(UPPER('a'))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF('a' || 'b')", "VARCHAR(2)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CONCAT('a','b'))", "VARCHAR(2)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(NULL)", "NULL[LOB]"},
            {"SELECT SYSTEM$TYPEOF(UUID_STRING())", "VARCHAR(36)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(HEX_ENCODE('a'))", "VARCHAR(8)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(BASE64_ENCODE('a'))", "VARCHAR(8)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(MD5('a'))", "VARCHAR(32)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SHA2('a'))", "VARCHAR(128)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SUBSTR('abc', 1, 2))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TRIM(' a '))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LPAD('a', 3))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CAST(NULL AS VARCHAR(10)))", "VARCHAR(10)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CAST('abc' AS VARCHAR(5)))", "VARCHAR(5)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, 'a', NULL::VARCHAR))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SPLIT_PART('a,b', ',', 1))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(INITCAP('ab'))", "VARCHAR(6)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(REVERSE('ab'))", "VARCHAR(2)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LEFT('abc', 2))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(RTRIM('a '))", "VARCHAR(2)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TRANSLATE('a','a','b'))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CHR(65))", "VARCHAR(1)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(REPEAT('a', 2))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SOUNDEX('a'))", "VARCHAR(7)[LOB]"},
            {"SELECT SYSTEM$TYPEOF('a'::VARCHAR || 'b')", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF('b' || 'a'::VARCHAR)", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CONCAT('a'::VARCHAR, 'b'))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SUBSTR('abc'::VARCHAR, 1, 2))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, 'bb', 'a'::VARCHAR))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE('ab', NULL::VARCHAR))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LPAD('a'::VARCHAR, 3))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LEFT('abc'::VARCHAR, 2))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CURRENT_DATABASE() || 'x')", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF('a'::VARCHAR(10))", "VARCHAR(10)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LOWER('abc'))", "VARCHAR(3)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_TO_STRING(ARRAY_CONSTRUCT('a'), ',') || 'x')", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF('x' || NULL::VARCHAR)", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(CONCAT_WS(',', 'a'::VARCHAR, 'b'))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(LISTAGG('a', ','))", "VARCHAR(134217728)[LOB]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT 'abc' AS x UNION ALL SELECT 'b'::VARCHAR) LIMIT 1", "VARCHAR(134217728)[LOB]"},
        });
    }

    @Test
    public void aTableOverAWidthlessStringStoresTheFullWidth() {
        engine.execute("CREATE TABLE w AS SELECT NULL::VARCHAR AS a, CURRENT_DATABASE() AS b, 'x'::VARCHAR AS c, LOWER(NULL) AS d, TO_CHAR(1) AS e");
        assertEquals("A:TEXT:16777216 B:TEXT:16777216 C:TEXT:16777216 D:TEXT:16777216 E:TEXT:16777216",
            answer("SELECT LISTAGG(column_name || ':' || data_type || ':' || COALESCE(character_maximum_length::VARCHAR, 'null'), ' ')"
                + " WITHIN GROUP (ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name = 'W'"));
    }
}
