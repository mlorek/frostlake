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

package dev.frostlake.procedural;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CALL of an overloaded procedure runs the overload its arguments choose: the argument count first,
 * then each argument's own family, then any family that takes it in the order of the internal type
 * names - a NUMBER reaching VARCHAR, and a FLOAT or a text reaching NUMBER, only as a last resort - and an
 * untyped NULL the first name. Each overload answers its own label. Every cell is live-verified.
 */
public class OverloadedProcedureCallTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
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

    @Test
    public void numberOrText() {
        engine.execute("CREATE OR REPLACE PROCEDURE pov(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'num'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE pov(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'text'; END; $$");
        assertEquals("num",
            rows("CALL pov(5)"));
        assertEquals("text",
            rows("CALL pov('5')"));
        assertEquals("text",
            rows("CALL pov(TRUE)"));
        assertEquals("text",
            rows("CALL pov('2020-01-15'::DATE)"));
        assertEquals("text",
            rows("CALL pov(1.5::FLOAT)"));
        assertEquals("num",
            rows("CALL pov(NULL)"));
    }

    @Test
    public void numberOrDate() {
        engine.execute("CREATE OR REPLACE PROCEDURE po2(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'num'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po2(a DATE) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'date'; END; $$");
        assertEquals("num",
            rows("CALL po2(5)"));
        assertEquals("date",
            rows("CALL po2('2020-01-15'::DATE)"));
        assertEquals("date",
            rows("CALL po2('2020-01-15')"));
        assertEquals("num",
            rows("CALL po2(1.5::FLOAT)"));
        assertEquals("date",
            rows("CALL po2('2020-01-15 10:00:00'::TIMESTAMP_NTZ)"));
        assertRefused("CALL po2(TRUE)",
            "SQL compilation error: error line 0 at position -1\nInvalid argument types for function 'PO2': (BOOLEAN)");
    }

    @Test
    public void floatOrNumber() {
        engine.execute("CREATE OR REPLACE PROCEDURE po3(a FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'float'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po3(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'num'; END; $$");
        assertEquals("num",
            rows("CALL po3(5)"));
        assertEquals("num",
            rows("CALL po3(1.5)"));
        assertEquals("float",
            rows("CALL po3(1.5::FLOAT)"));
        assertEquals("float",
            rows("CALL po3('5')"));
    }

    @Test
    public void textOrBoolean() {
        engine.execute("CREATE OR REPLACE PROCEDURE po4(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'text'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po4(a BOOLEAN) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'bool'; END; $$");
        assertEquals("bool",
            rows("CALL po4(TRUE)"));
        assertEquals("bool",
            rows("CALL po4(5)"));
        assertEquals("text",
            rows("CALL po4('x')"));
        assertEquals("bool",
            rows("CALL po4(NULL)"));
    }

    @Test
    public void oneOrTwoArguments() {
        engine.execute("CREATE OR REPLACE PROCEDURE po5(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'one'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po5(a NUMBER, b NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'two'; END; $$");
        assertEquals("one",
            rows("CALL po5(1)"));
        assertEquals("two",
            rows("CALL po5(1, 2)"));
        assertRefused("CALL po5(1, 2, 3)",
            "SQL compilation error: error line 0 at position -1\ntoo many arguments for function [PO5(1, 2, 3)] expected 2, got 3");
    }

    @Test
    public void localOrZonedTimestamp() {
        engine.execute("CREATE OR REPLACE PROCEDURE po6(a TIMESTAMP_LTZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'ltz'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po6(a TIMESTAMP_TZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'tz'; END; $$");
        assertEquals("tz",
            rows("CALL po6('2020-01-15 10:00:00 +02:00'::TIMESTAMP_TZ)"));
        assertEquals("ltz",
            rows("CALL po6('2020-01-15 10:00:00'::TIMESTAMP_LTZ)"));
        assertEquals("ltz",
            rows("CALL po6('2020-01-15 10:00:00'::TIMESTAMP_NTZ)"));
        assertEquals("ltz",
            rows("CALL po6('2020-01-15')"));
    }

    @Test
    public void timeOrText() {
        engine.execute("CREATE OR REPLACE PROCEDURE po7(a TIME) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'time'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po7(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'text'; END; $$");
        assertEquals("text",
            rows("CALL po7('10:00:00')"));
        assertEquals("time",
            rows("CALL po7('10:00:00'::TIME)"));
        assertEquals("text",
            rows("CALL po7(5)"));
        assertEquals("text",
            rows("CALL po7(NULL)"));
    }

    @Test
    public void binaryOrText() {
        engine.execute("CREATE OR REPLACE PROCEDURE po8(a BINARY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'bin'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po8(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'text'; END; $$");
        assertEquals("text",
            rows("CALL po8('616263')"));
        assertEquals("bin",
            rows("CALL po8(TO_BINARY('61'))"));
        assertEquals("text",
            rows("CALL po8(5)"));
        assertEquals("bin",
            rows("CALL po8(NULL)"));
    }

    @Test
    public void arrayOrText() {
        engine.execute("CREATE OR REPLACE PROCEDURE po9(a ARRAY) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'array'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po9(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'text'; END; $$");
        assertEquals("array",
            rows("CALL po9([1])"));
        assertEquals("text",
            rows("CALL po9('x')"));
        assertEquals("array",
            rows("CALL po9(PARSE_JSON('[1]'))"));
        assertEquals("array",
            rows("CALL po9(NULL)"));
    }

    @Test
    public void objectOrVariant() {
        engine.execute("CREATE OR REPLACE PROCEDURE po10(a OBJECT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'object'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po10(a VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'variant'; END; $$");
        assertEquals("object",
            rows("CALL po10({'a': 1})"));
        assertEquals("variant",
            rows("CALL po10(PARSE_JSON('{\"a\":1}'))"));
        assertEquals("variant",
            rows("CALL po10(5)"));
        assertEquals("object",
            rows("CALL po10(NULL)"));
    }

    @Test
    public void numberTextOrDate() {
        engine.execute("CREATE OR REPLACE PROCEDURE po11(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'num'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po11(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'text'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po11(a DATE) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'date'; END; $$");
        assertEquals("num",
            rows("CALL po11(5)"));
        assertEquals("text",
            rows("CALL po11('x')"));
        assertEquals("text",
            rows("CALL po11('2020-01-15')"));
        assertEquals("date",
            rows("CALL po11('2020-01-15'::DATE)"));
        assertEquals("text",
            rows("CALL po11(TRUE)"));
        assertEquals("date",
            rows("CALL po11(NULL)"));
        assertEquals("text",
            rows("CALL po11(1.5::FLOAT)"));
    }

    @Test
    public void numberOrBoolean() {
        engine.execute("CREATE OR REPLACE PROCEDURE po12(a NUMBER) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'num'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po12(a BOOLEAN) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'bool'; END; $$");
        assertEquals("num",
            rows("CALL po12(5)"));
        assertEquals("bool",
            rows("CALL po12(TRUE)"));
        assertEquals("bool",
            rows("CALL po12('1')"));
        assertEquals("bool",
            rows("CALL po12(NULL)"));
    }

    @Test
    public void floatOrText() {
        engine.execute("CREATE OR REPLACE PROCEDURE po13(a FLOAT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'float'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po13(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'text'; END; $$");
        assertEquals("float",
            rows("CALL po13(5)"));
        assertEquals("text",
            rows("CALL po13('5')"));
        assertEquals("float",
            rows("CALL po13(NULL)"));
    }

    @Test
    public void timestampOrDate() {
        engine.execute("CREATE OR REPLACE PROCEDURE po14(a TIMESTAMP_NTZ) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'ntz'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po14(a DATE) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'date'; END; $$");
        assertEquals("date",
            rows("CALL po14('2020-01-15')"));
        assertEquals("date",
            rows("CALL po14('2020-01-15 10:00:00')"));
        assertEquals("date",
            rows("CALL po14(CURRENT_DATE())"));
        assertEquals("date",
            rows("CALL po14(NULL)"));
    }

    @Test
    public void variantOrText() {
        engine.execute("CREATE OR REPLACE PROCEDURE po15(a VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'variant'; END; $$");
        engine.execute("CREATE OR REPLACE PROCEDURE po15(a VARCHAR) RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'text'; END; $$");
        assertEquals("text",
            rows("CALL po15('x')"));
        assertEquals("variant",
            rows("CALL po15(5)"));
        assertEquals("variant",
            rows("CALL po15(PARSE_JSON('1'))"));
        assertEquals("text",
            rows("CALL po15(NULL)"));
    }
}
