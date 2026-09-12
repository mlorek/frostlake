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
 * A direct RETURN is converted to the procedure's declared RETURNS type: a value that is not an array
 * becomes the one element of an ARRAY result (a DATE, a timestamp, a text and an OBJECT alike), a text
 * under BINARY is read as hex, a VARIANT-typed variable holds a variant that a DATE conversion refuses,
 * and a BOOLEAN under an exact NUMBER is the unscaled 1. Every cell is live-verified; a container result
 * is compared without the whitespace the two harnesses print it with.
 */
public class DeclaredReturnConversionTest extends BaseDatabaseTest {

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

    /** The rows with every whitespace character removed, for a container's JSON text. */
    private String compact(final String sql) {
        return rows(sql).replaceAll("\\s+", "");
    }

    /** The five pairs that were handed back unconverted, and the BOOLEAN's own scale. */
    @Test
    public void aReturnIsConvertedToTheDeclaredType() {
        engine.execute("CREATE OR REPLACE PROCEDURE p_e1() RETURNS ARRAY LANGUAGE SQL AS $$ DECLARE x DATE := '2020-01-15'; BEGIN RETURN x; END; $$");
        assertEquals("[\"2020-01-15\"]",
            compact("CALL p_e1()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e2() RETURNS ARRAY LANGUAGE SQL AS $$ DECLARE x OBJECT := OBJECT_CONSTRUCT('a', 1); BEGIN RETURN x; END; $$");
        assertEquals("[{\"a\":1}]",
            compact("CALL p_e2()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e7() RETURNS ARRAY LANGUAGE SQL AS $$ DECLARE x VARCHAR := 'ab'; BEGIN RETURN x; END; $$");
        assertEquals("[\"ab\"]",
            compact("CALL p_e7()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e8() RETURNS ARRAY LANGUAGE SQL AS $$ DECLARE x TIMESTAMP_NTZ := '2020-01-15 10:00:00'; BEGIN RETURN x; END; $$");
        assertEquals("[\"2020-01-1510:00:00.000\"]",
            compact("CALL p_e8()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e3() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN 'abc'; END; $$");
        assertRefused("CALL p_e3()",
            "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : The following string is not a legal hex-encoded value: 'abc'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_e15() RETURNS BINARY LANGUAGE SQL AS $$ DECLARE x VARCHAR := 'zz'; BEGIN RETURN x; END; $$");
        assertRefused("CALL p_e15()",
            "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : The following string is not a legal hex-encoded value: 'zz'");
        engine.execute("CREATE OR REPLACE PROCEDURE p_e4() RETURNS DATE LANGUAGE SQL AS $$ DECLARE x VARIANT := 5; BEGIN RETURN x; END; $$");
        assertRefused("CALL p_e4()",
            "Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 38 : Failed to cast variant value 5 to DATE");
        engine.execute("CREATE OR REPLACE PROCEDURE p_e5() RETURNS NUMBER(5,2) LANGUAGE SQL AS $$ BEGIN RETURN TRUE; END; $$");
        assertEquals("1",
            rows("CALL p_e5()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e19() RETURNS NUMBER(5,2) LANGUAGE SQL AS $$ DECLARE x BOOLEAN := TRUE; BEGIN RETURN x; END; $$");
        assertEquals("1",
            rows("CALL p_e19()"));
    }

    /** The pairs that already converted keep their answers. */
    @Test
    public void theConvertingPairsKeepTheirAnswers() {
        engine.execute("CREATE OR REPLACE PROCEDURE p_e6() RETURNS BINARY LANGUAGE SQL AS $$ BEGIN RETURN '616263'; END; $$");
        assertEquals("616263",
            rows("CALL p_e6()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e9() RETURNS ARRAY LANGUAGE SQL AS $$ DECLARE x ARRAY := ARRAY_CONSTRUCT(1); BEGIN RETURN x; END; $$");
        assertEquals("[1]",
            compact("CALL p_e9()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e10() RETURNS ARRAY LANGUAGE SQL AS $$ DECLARE x VARIANT := PARSE_JSON('[1,2]'); BEGIN RETURN x; END; $$");
        assertEquals("[1,2]",
            compact("CALL p_e10()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e11() RETURNS OBJECT LANGUAGE SQL AS $$ DECLARE x VARIANT := PARSE_JSON('{\"a\":1}'); BEGIN RETURN x; END; $$");
        assertEquals("{\"a\":1}",
            compact("CALL p_e11()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e12() RETURNS DATE LANGUAGE SQL AS $$ DECLARE x VARIANT := '2020-01-15'; BEGIN RETURN x; END; $$");
        assertEquals("2020-01-15",
            rows("CALL p_e12()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e13() RETURNS NUMBER(5,2) LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        assertEquals("1.00",
            rows("CALL p_e13()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e14() RETURNS NUMBER(5,2) LANGUAGE SQL AS $$ BEGIN RETURN 1.5; END; $$");
        assertEquals("1.50",
            rows("CALL p_e14()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e16() RETURNS ARRAY LANGUAGE SQL AS $$ DECLARE x NUMBER(5,2) := 1.5; BEGIN RETURN x; END; $$");
        assertEquals("[1.5]",
            compact("CALL p_e16()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e17() RETURNS ARRAY LANGUAGE SQL AS $$ DECLARE x BOOLEAN := TRUE; BEGIN RETURN x; END; $$");
        assertEquals("[true]",
            compact("CALL p_e17()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e18() RETURNS DATE LANGUAGE SQL AS $$ DECLARE x VARIANT := PARSE_JSON('\"2020-01-15\"'); BEGIN RETURN x; END; $$");
        assertEquals("2020-01-15",
            rows("CALL p_e18()"));
        engine.execute("CREATE OR REPLACE PROCEDURE p_e20() RETURNS NUMBER LANGUAGE SQL AS $$ BEGIN RETURN TRUE; END; $$");
        assertEquals("1",
            rows("CALL p_e20()"));
    }
}
