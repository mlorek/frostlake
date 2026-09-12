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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A VARIANT used where a number is needed reads as its JSON number, a numeric JSON string, or a JSON
 * boolean as 1 / 0; an object, an array or other text fails the variant cast in live's sentence,
 * {@code Failed to cast variant value {"x":1} to REAL} — {@code … to FIXED} for the integer uses — in
 * an operator, a numeric function and an aggregate alike. Every cell is live-verified.
 */
public class VariantNumericCastTest extends BaseDatabaseTest {

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

    /** The single cell as a double. */
    private double number(final String sql) {
        return ((Number) engine.executeQuery(sql).getRows().get(0).getValue(0)).doubleValue();
    }

    /** The arithmetic operators and the unary sign over a VARIANT object fail its cast to REAL; a comparison does not convert. */
    @Test
    public void anOperatorFailsTheVariantCastToReal() {
        engine.execute("CREATE OR REPLACE TABLE tv (vo VARIANT, va VARIANT, vs VARIANT, vb VARIANT, vn VARIANT, o OBJECT)");
        engine.execute("INSERT INTO tv SELECT PARSE_JSON('{\"x\":1}'), PARSE_JSON('[1,2]'), PARSE_JSON('\"abc\"'), PARSE_JSON('true'), PARSE_JSON('7'), OBJECT_CONSTRUCT('k','v1')");
        assertRefused("SELECT vo + 1 FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT vo - 1 FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT vo * 2 FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT vo / 2 FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT vo % 2 FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT -vo FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT 1 + vo FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertEquals("true",
            rows("SELECT vo > 1 FROM tv"));
        assertEquals("false",
            rows("SELECT vo = 1 FROM tv"));
        assertEquals("false",
            rows("SELECT vo BETWEEN 0 AND 2 FROM tv"));
    }

    /** ABS, ROUND, FLOOR, CEIL, SQRT, EXP, LN, POWER, TRUNC, SIGN, MOD, DIV0 and LOG fail the same cast. */
    @Test
    public void aNumericFunctionFailsTheVariantCastToReal() {
        engine.execute("CREATE OR REPLACE TABLE tv (vo VARIANT, va VARIANT, vs VARIANT, vb VARIANT, vn VARIANT, o OBJECT)");
        engine.execute("INSERT INTO tv SELECT PARSE_JSON('{\"x\":1}'), PARSE_JSON('[1,2]'), PARSE_JSON('\"abc\"'), PARSE_JSON('true'), PARSE_JSON('7'), OBJECT_CONSTRUCT('k','v1')");
        assertRefused("SELECT ABS(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT ROUND(vo, 1) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT FLOOR(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT CEIL(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT SQRT(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT EXP(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT LN(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT POWER(vo, 2) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT TRUNC(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT SIGN(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT ABS(o::VARIANT) FROM tv",
            "Failed to cast variant value {\"k\":\"v1\"} to REAL");
        assertRefused("SELECT MOD(vo, 2) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT ROUND(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT DIV0(vo, 2) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT LOG(10, vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
    }

    /** The double-valued aggregates fail the cast to REAL, the integer ones to FIXED. */
    @Test
    public void anAggregateNamesItsNumericFamily() {
        engine.execute("CREATE OR REPLACE TABLE tv (vo VARIANT, va VARIANT, vs VARIANT, vb VARIANT, vn VARIANT, o OBJECT)");
        engine.execute("INSERT INTO tv SELECT PARSE_JSON('{\"x\":1}'), PARSE_JSON('[1,2]'), PARSE_JSON('\"abc\"'), PARSE_JSON('true'), PARSE_JSON('7'), OBJECT_CONSTRUCT('k','v1')");
        assertRefused("SELECT SUM(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT AVG(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT MEDIAN(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to FIXED");
        assertRefused("SELECT BITOR_AGG(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to FIXED");
        assertRefused("SELECT STDDEV(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT BITAND_AGG(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to FIXED");
        assertRefused("SELECT VARIANCE(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT vo::NUMBER FROM tv",
            "Failed to cast variant value {\"x\":1} to FIXED");
        assertRefused("SELECT vo::FLOAT FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT TO_NUMBER(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to FIXED");
        assertRefused("SELECT TO_DOUBLE(vo) FROM tv",
            "Failed to cast variant value {\"x\":1} to REAL");
    }

    /** An array and a string spelling no number fail the same way; a boolean reads 1 / 0 and a number or numeric string reads as itself. */
    @Test
    public void anArrayOrTextFailsAndABooleanOrNumberConverts() {
        engine.execute("CREATE OR REPLACE TABLE tv (vo VARIANT, va VARIANT, vs VARIANT, vb VARIANT, vn VARIANT, o OBJECT)");
        engine.execute("INSERT INTO tv SELECT PARSE_JSON('{\"x\":1}'), PARSE_JSON('[1,2]'), PARSE_JSON('\"abc\"'), PARSE_JSON('true'), PARSE_JSON('7'), OBJECT_CONSTRUCT('k','v1')");
        assertRefused("SELECT va + 1 FROM tv",
            "Failed to cast variant value [1,2] to REAL");
        assertRefused("SELECT ABS(va) FROM tv",
            "Failed to cast variant value [1,2] to REAL");
        assertRefused("SELECT SUM(va) FROM tv",
            "Failed to cast variant value [1,2] to REAL");
        assertRefused("SELECT FLOOR(va) FROM tv",
            "Failed to cast variant value [1,2] to REAL");
        assertRefused("SELECT vs + 1 FROM tv",
            "Failed to cast variant value \"abc\" to REAL");
        assertRefused("SELECT ABS(vs) FROM tv",
            "Failed to cast variant value \"abc\" to REAL");
        assertRefused("SELECT SUM(vs) FROM tv",
            "Failed to cast variant value \"abc\" to REAL");
        assertRefused("SELECT FLOOR(vs) FROM tv",
            "Failed to cast variant value \"abc\" to REAL");
        assertEquals(2, number("SELECT vb + 1 FROM tv"), 1e-6);
        assertEquals(1, number("SELECT ABS(vb) FROM tv"), 1e-6);
        assertEquals(1, number("SELECT SUM(vb) FROM tv"), 1e-6);
        assertEquals(8, number("SELECT vn + 1 FROM tv"), 1e-6);
        assertEquals(7, number("SELECT ABS(vn) FROM tv"), 1e-6);
        assertEquals(7, number("SELECT FLOOR(vn) FROM tv"), 1e-6);
        assertEquals(2.645751311, number("SELECT SQRT(vn) FROM tv"), 1e-6);
        assertRefused("SELECT PARSE_JSON('{\"x\":1}') + 1",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT ABS(PARSE_JSON('{\"x\":1}'))",
            "Failed to cast variant value {\"x\":1} to REAL");
        assertRefused("SELECT FLOOR(PARSE_JSON('[1]'))",
            "Failed to cast variant value [1] to REAL");
        assertRefused("SELECT PARSE_JSON('{\"a\":{\"b\":[1,2]}}') * 3",
            "Failed to cast variant value {\"a\":{\"b\":[1,2]}} to REAL");
        assertEquals(12, number("SELECT ABS(PARSE_JSON('\"12\"'))"), 1e-6);
    }
}
