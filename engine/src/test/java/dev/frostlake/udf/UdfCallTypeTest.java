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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A SQL function whose body is an expression is typed by that body over its parameters' declared types,
 * the way live types it — SYSTEM$TYPEOF reads the body's type, not the RETURNS clause — so every rule
 * that reads an argument's type sees what the call produces: TO_VARCHAR over a function returning a
 * VECTOR is refused, and so are UPPER over an OBJECT and ABS over a BINARY or a BOOLEAN. Every cell is
 * live-verified.
 */
public class UdfCallTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE FUNCTION vec_unit(a VECTOR(FLOAT,3)) RETURNS VECTOR(FLOAT,3) AS 'VECTOR_NORMALIZE(a)'");
        engine.execute("CREATE OR REPLACE FUNCTION num_udf() RETURNS NUMBER(10,2) AS '1.5'");
        engine.execute("CREATE OR REPLACE FUNCTION obj_udf() RETURNS OBJECT AS 'OBJECT_CONSTRUCT(''a'', 1)'");
        engine.execute("CREATE OR REPLACE FUNCTION str_udf() RETURNS VARCHAR(5) AS '''abc'''");
        engine.execute("CREATE OR REPLACE FUNCTION bin_udf() RETURNS BINARY AS 'TO_BINARY(''00'')'");
        engine.execute("CREATE OR REPLACE FUNCTION bool_udf() RETURNS BOOLEAN AS 'TRUE'");
        engine.execute("CREATE OR REPLACE FUNCTION date_udf() RETURNS DATE AS 'TO_DATE(''2020-01-01'')'");
        engine.execute("CREATE OR REPLACE FUNCTION var_udf() RETURNS VARIANT AS 'PARSE_JSON(''1'')'");
        engine.execute("CREATE OR REPLACE FUNCTION str_p(s VARCHAR(10)) RETURNS VARCHAR AS 's || ''x'''");
        engine.execute("CREATE OR REPLACE FUNCTION num_p(a NUMBER(10,2)) RETURNS NUMBER(10,2) AS 'a + 1'");
        engine.execute("CREATE OR REPLACE FUNCTION obj_p(o OBJECT) RETURNS OBJECT AS 'o'");
        engine.execute("CREATE OR REPLACE FUNCTION bool_p(b BOOLEAN) RETURNS BOOLEAN AS 'b'");
        engine.execute("CREATE OR REPLACE FUNCTION bin_p(b BINARY(6)) RETURNS BINARY AS 'b'");
    }

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** A numeric SYSTEM$TYPEOF answer without its storage tag, which follows the plan's value interval. */
    private String typeWithoutStorageTag(final String sql) {
        final String type = rows(sql);
        return type.substring(0, type.indexOf(')') + 1);
    }

    /** SYSTEM$TYPEOF over a call reads its body's type; TO_VARCHAR over a VECTOR-valued call is refused. */
    @Test
    public void aCallIsTypedByItsBody() {
        assertRefused("SELECT TO_VARCHAR(vec_unit([1,2,3]::VECTOR(FLOAT,3)))",
            "SQL compilation error:\ninvalid type [TO_VARCHAR(VEC_UNIT(");
        assertRefused("SELECT TO_VARCHAR(vec_unit([1,2,3]::VECTOR(FLOAT,3)))",
            "))] for parameter 'TO_VARCHAR'");
        assertEquals("VECTOR(FLOAT, 3)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(vec_unit([1,2,3]::VECTOR(FLOAT,3)))"));
        assertEquals("NUMBER(2,1)",
            typeWithoutStorageTag("SELECT SYSTEM$TYPEOF(num_udf())"));
        assertEquals("OBJECT[LOB]",
            rows("SELECT SYSTEM$TYPEOF(obj_udf())"));
        assertEquals("VARCHAR(3)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(str_udf())"));
        assertEquals("BOOLEAN[SB1]",
            rows("SELECT SYSTEM$TYPEOF(bool_udf())"));
        assertEquals("DATE[SB4]",
            rows("SELECT SYSTEM$TYPEOF(date_udf())"));
        assertEquals("VARIANT[LOB]",
            rows("SELECT SYSTEM$TYPEOF(var_udf())"));
        assertEquals("VARCHAR(11)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(str_p(s)) FROM (SELECT 'ab'::VARCHAR(10) AS s)"));
        assertEquals("NUMBER(11,2)",
            typeWithoutStorageTag("SELECT SYSTEM$TYPEOF(num_p(n)) FROM (SELECT 1.5::NUMBER(10,2) AS n)"));
        assertEquals("BINARY(6)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(bin_p(b)) FROM (SELECT X'00'::BINARY(6) AS b)"));
        assertEquals("abx",
            rows("SELECT str_p(s) FROM (SELECT 'ab'::VARCHAR(10) AS s)"));
    }

    /** The families the argument-type rules read are the body's. */
    @Test
    public void argumentTypeRulesSeeTheCallsType() {
        assertRefused("SELECT UPPER(obj_udf())",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'UPPER': (OBJECT)");
        assertRefused("SELECT ABS(bin_udf())",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ABS': (BINARY(67108864))");
        assertRefused("SELECT UPPER(bin_udf())",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'UPPER': (BINARY(67108864))");
        assertRefused("SELECT SUM(obj_udf())",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'SUM': (OBJECT)");
        assertRefused("SELECT ABS(bool_udf())",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ABS': (BOOLEAN)");
        assertRefused("SELECT TO_VARCHAR(vec_unit([1,2,3]::VECTOR(FLOAT,3)), 'x')",
            "SQL compilation error:\ninvalid type [TO_VARCHAR(VEC_UNIT(");
        assertRefused("SELECT TO_VARCHAR(vec_unit([1,2,3]::VECTOR(FLOAT,3)), 'x')",
            "), 'x')] for parameter 'TO_VARCHAR'");
        assertRefused("SELECT vec_unit([1,2,3]::VECTOR(FLOAT,3))::VARCHAR",
            "SQL compilation error:\ninvalid type [CAST(VEC_UNIT(");
        assertRefused("SELECT vec_unit([1,2,3]::VECTOR(FLOAT,3))::VARCHAR",
            ") AS VARCHAR(134217728))] for parameter 'TO_VARCHAR'");
        assertEquals("1.5",
            rows("SELECT UPPER(num_udf())"));
        assertEquals("2.5",
            rows("SELECT num_udf() + 1"));
        assertEquals("NUMBER(4,1)",
            typeWithoutStorageTag("SELECT SYSTEM$TYPEOF(num_udf() + 1)"));
        assertEquals("2020-01-02",
            rows("SELECT DATEADD(day, 1, date_udf())"));
        assertEquals("abc1",
            rows("SELECT str_udf() || 1"));
        assertRefused("SELECT UPPER(obj_p(OBJECT_CONSTRUCT('a', 1)))",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'UPPER': (OBJECT)");
        assertRefused("SELECT ABS(bool_p(TRUE))",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ABS': (BOOLEAN)");
        assertRefused("SELECT ABS(bin_p(X'00'::BINARY(6)))",
            "SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'ABS': (BINARY(6))");
    }
}
