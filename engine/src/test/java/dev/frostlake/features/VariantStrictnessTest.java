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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Strictness of the semi-structured function family over typed runtime values, matching live
 * Snowflake: TYPEOF / GET / GET_PATH / TO_JSON reject VARCHAR, BINARY and temporal arguments
 * (literal, column, or expression — at plan time, so empty tables reject too) while NUMBER and
 * BOOLEAN implicitly coerce to VARIANT; and TYPEOF reports the EXACT inner type of a typed
 * variant — a variant string {@code "123"} is VARCHAR, never sniffed into INTEGER.
 */
public class VariantStrictnessTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    /**
     * The engine now renders the full parameterized argument-type list, e.g.
     * {@code Invalid argument types for function 'TYPEOF': (VARCHAR(1))} — so call sites pass a
     * type-name prefix such as {@code "VARCHAR("} (or {@code "DATE"} for parameterless types) and
     * the helper only anchors it to the opening parenthesis of the type list.
     */
    private void assertRejected(final String sql, final String expectedTypePrefixInMessage) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            fail("expected rejection but got " + rs.getRowCount() + " rows for: " + sql);
        } catch (final RuntimeException e) {
            assertTrue(e.getMessage().contains("Invalid argument types for function")
                    && e.getMessage().contains("(" + expectedTypePrefixInMessage),
                "unexpected: " + e.getMessage());
        }
    }

    @Test
    public void typeofRejectsLiteralsAndNonVariantColumns() {
        assertRejected("SELECT TYPEOF('x')", "VARCHAR(");
        // Live-verified coercion: NUMBER and BOOLEAN arguments implicitly convert to VARIANT.
        assertEquals("INTEGER", scalar("SELECT TYPEOF(123)"));
        assertEquals("BOOLEAN", scalar("SELECT TYPEOF(TRUE)"));
        engine.execute("CREATE TABLE vst_t (s VARCHAR, n NUMBER, v VARIANT)");
        engine.execute("INSERT INTO vst_t SELECT 'x', 1, PARSE_JSON('{}')");
        assertRejected("SELECT TYPEOF(s) FROM vst_t", "VARCHAR(");
        assertEquals("INTEGER", scalar("SELECT TYPEOF(n) FROM vst_t"));
        assertNull(scalar("SELECT TYPEOF(NULL)"), "SQL NULL in, NULL out");
    }

    @Test
    public void typeofReportsTheExactInnerType() {
        assertEquals("OBJECT", scalar("SELECT TYPEOF(PARSE_JSON('{\"a\":1}'))"));
        assertEquals("ARRAY", scalar("SELECT TYPEOF(PARSE_JSON('[1,2]'))"));
        assertEquals("VARCHAR", scalar("SELECT TYPEOF(PARSE_JSON('\"txt\"'))"));
        assertEquals("VARCHAR", scalar("SELECT TYPEOF(PARSE_JSON('\"123\"'))"),
            "a variant STRING of digits is VARCHAR, not INTEGER");
        assertEquals("INTEGER", scalar("SELECT TYPEOF(PARSE_JSON('123'))"));
        assertEquals("DECIMAL", scalar("SELECT TYPEOF(PARSE_JSON('1.5'))"),
            "a plain JSON fraction is DECIMAL (live-verified; scientific notation is DOUBLE)");
        assertEquals("DOUBLE", scalar("SELECT TYPEOF(PARSE_JSON('1.5e2'))"));
        assertEquals("BOOLEAN", scalar("SELECT TYPEOF(PARSE_JSON('true'))"));
        assertEquals("NULL_VALUE", scalar("SELECT TYPEOF(PARSE_JSON('null'))"));
    }

    @Test
    public void typeofWorksOverVariantColumnsAndPaths() {
        engine.execute("CREATE TABLE vst_v (v VARIANT)");
        engine.execute("INSERT INTO vst_v SELECT PARSE_JSON('{\"o\":{\"k\":1},\"s\":\"abc\",\"n\":7}')");
        assertEquals("OBJECT", scalar("SELECT TYPEOF(v) FROM vst_v"));
        assertEquals("OBJECT", scalar("SELECT TYPEOF(v:o) FROM vst_v"));
        assertEquals("VARCHAR", scalar("SELECT TYPEOF(v:s) FROM vst_v"));
        assertEquals("INTEGER", scalar("SELECT TYPEOF(v:n) FROM vst_v"));
    }

    @Test
    public void getRejectsNonVariantFirstArguments() {
        assertRejected("SELECT GET('{\"a\":1}', 'a')", "VARCHAR(");
        engine.execute("CREATE TABLE vst_g (s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO vst_g SELECT 'x', PARSE_JSON('{\"a\":41}')");
        assertRejected("SELECT GET(s, 'a') FROM vst_g", "VARCHAR(");
        assertRejected("SELECT GET_PATH(s, 'a') FROM vst_g", "VARCHAR(");
        assertEquals("41", String.valueOf(scalar("SELECT GET(v, 'a') FROM vst_g")));
    }

    @Test
    public void toJsonRejectsNonVariantArguments() {
        assertRejected("SELECT TO_JSON('abc')", "VARCHAR(");
        engine.execute("CREATE TABLE vst_j (s VARCHAR, v VARIANT)");
        engine.execute("INSERT INTO vst_j SELECT 'x', PARSE_JSON('{\"a\":1}')");
        assertRejected("SELECT TO_JSON(s) FROM vst_j", "VARCHAR(");
        assertEquals("{\"a\":1}", scalar("SELECT TO_JSON(v) FROM vst_j"));
    }

    @Test
    public void typeofRejectsBinaryAndTemporalExpressions() {
        // TO_BINARY returns BINARY, not VARIANT — Snowflake rejects it as a TYPEOF argument, and
        // the type inferencer knows the function's declared return type.
        assertRejected("SELECT TYPEOF(TO_BINARY('AB12'))", "BINARY(");
        engine.execute("CREATE TABLE vst_d (d DATE, v VARIANT)");
        engine.execute("INSERT INTO vst_d SELECT '2026-01-01', PARSE_JSON('1')");
        // A declared DATE column is not a VARIANT either — Snowflake rejects it the same way.
        assertRejected("SELECT TYPEOF(d) FROM vst_d", "DATE");
    }

    @Test
    public void strictnessFiresAtPlanTimeAndThroughExpressions() {
        // Compile-time behavior: the rejection fires even over an EMPTY table, as in Snowflake.
        engine.execute("CREATE TABLE vst_empty (s VARCHAR, v VARIANT)");
        assertRejected("SELECT TYPEOF(s) FROM vst_empty", "VARCHAR(");
        assertRejected("SELECT TO_JSON(s) FROM vst_empty", "VARCHAR(");
        // Through expressions: UPPER of a VARCHAR column is statically VARCHAR.
        assertRejected("SELECT TYPEOF(UPPER(s)) FROM vst_empty", "VARCHAR(");
        // Casts count too, and a variant-typed expression still passes over the empty table.
        assertRejected("SELECT TYPEOF(v::VARCHAR) FROM vst_empty", "VARCHAR(");
        assertEquals(0, engine.executeQuery("SELECT TYPEOF(v) FROM vst_empty").getRowCount());
        assertEquals(0, engine.executeQuery("SELECT TYPEOF(v:field) FROM vst_empty").getRowCount());
    }

    @Test
    public void temporalStrictnessCoversVarcharExpressions() {
        engine.execute("CREATE TABLE vst_tmp (s VARCHAR)");
        // Empty table: rejected at plan time; UPPER(s) is statically VARCHAR too.
        try {
            engine.executeQuery("SELECT DATE_TRUNC('month', UPPER(s)) FROM vst_tmp");
            fail("expected temporal strictness rejection");
        } catch (final RuntimeException rejected) {
            assertTrue(rejected.getMessage().contains("does not support VARCHAR("),
                "unexpected: " + rejected.getMessage());
        }
        // A bare string CONSTANT is rejected too (live-verified); the cast is the portable form.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                scalar("SELECT DATE_TRUNC('month', '2024-04-08')");
            }
        });
        assertEquals("2024-04-01", String.valueOf(scalar("SELECT DATE_TRUNC('month', '2024-04-08'::DATE)")));
    }
}
