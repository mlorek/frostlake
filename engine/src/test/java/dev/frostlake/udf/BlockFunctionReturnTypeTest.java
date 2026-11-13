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

import dev.frostlake.query.MeasuredCellsTestSupport;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * A block-bodied SQL function's RETURN against the type it declares, on the call (live-verified): a constant of a type
 * the declared one does not take is refused where the call stands — {@code Declared return type 'TIME(9)' is
 * incompatible with actual return type 'NUMBER(1,0)'} — a string is cast as the body runs, and a VECTOR function
 * refuses a constant by its own type and any other RETURN as a return type a block does not support. VARIANT, ARRAY
 * and OBJECT are refused when the function is created.
 */
public class BlockFunctionReturnTypeTest extends MeasuredCellsTestSupport {

    /** What a VECTOR function answers for a RETURN that is no constant. */
    private static final String NO_VECTOR = "REFUSED Unsupported return type for Snowscript UDF: vector";

    /** Each declared type, the RETURN's expression, and what a call answers. */
    private static final String[][] CALLS = {
        {"TIME", "1", declared("TIME(9)", "NUMBER(1,0)")},
        {"TIME", "'a'", uncaught("Time 'a' is not recognized")},
        {"TIME", "'10:00:00'", "10:00"},
        {"TIME", "TRUE", declared("TIME(9)", "BOOLEAN")},
        {"TIME", "1.5", declared("TIME(9)", "NUMBER(2,1)")},
        {"TIME", "TO_TIME('10:00')", "10:00"},
        {"TIME", "NULL", "null"},
        {"TIME", "'2020-01-01'", uncaught("Time '2020-01-01' is not recognized")},
        {"BINARY", "1", declared("BINARY(67108864)", "NUMBER(1,0)")},
        {"BINARY", "'a'", uncaught("The following string is not a legal hex-encoded value: 'a'")},
        {"BINARY", "'10:00:00'", uncaught("The following string is not a legal hex-encoded value: '10:00:00'")},
        {"BINARY", "TRUE", declared("BINARY(67108864)", "BOOLEAN")},
        {"BINARY", "1.5", declared("BINARY(67108864)", "NUMBER(2,1)")},
        {"BINARY", "NULL", "null"},
        {"BINARY", "TO_BINARY('00', 'HEX')", "00"},
        {"BINARY", "'2020-01-01'", uncaught("The following string is not a legal hex-encoded value: '2020-01-01'")},
        {"DATE", "1", declared("DATE", "NUMBER(1,0)")},
        {"DATE", "'a'", uncaught("Date 'a' is not recognized")},
        {"DATE", "'10:00:00'", uncaught("Date '10:00:00' is not recognized")},
        {"DATE", "TRUE", declared("DATE", "BOOLEAN")},
        {"DATE", "1.5", declared("DATE", "NUMBER(2,1)")},
        {"DATE", "NULL", "null"},
        {"DATE", "'2020-01-01'", "2020-01-01"},
        {"VECTOR(INT, 3)", "1", unsupported("FIXED")},
        {"VECTOR(INT, 3)", "'a'", unsupported("TEXT")},
        {"VECTOR(INT, 3)", "'10:00:00'", unsupported("TEXT")},
        {"VECTOR(INT, 3)", "TRUE", unsupported("BOOLEAN")},
        {"VECTOR(INT, 3)", "1.5", unsupported("FIXED")},
        {"VECTOR(INT, 3)", "CURRENT_DATE()", NO_VECTOR},
        {"VECTOR(INT, 3)", "TO_TIME('10:00')", NO_VECTOR},
        {"VECTOR(INT, 3)", "PARSE_JSON('1')", NO_VECTOR},
        {"VECTOR(INT, 3)", "[1, 2]", NO_VECTOR},
        {"VECTOR(INT, 3)", "{'a': 1}", NO_VECTOR},
        {"VECTOR(INT, 3)", "NULL", NO_VECTOR},
        {"VECTOR(INT, 3)", "TO_BINARY('00', 'HEX')", NO_VECTOR},
        {"VECTOR(INT, 3)", "'2020-01-01'", unsupported("TEXT")},
        {"BOOLEAN", "1", "true"},
        {"BOOLEAN", "'a'", uncaught("Boolean value 'a' is not recognized")},
        {"BOOLEAN", "'10:00:00'", uncaught("Boolean value '10:00:00' is not recognized")},
        {"BOOLEAN", "TRUE", "true"},
        {"BOOLEAN", "1.5", "true"},
        {"BOOLEAN", "PARSE_JSON('1')", "true"},
        {"BOOLEAN", "NULL", "null"},
        {"BOOLEAN", "TO_BINARY('00', 'HEX')", "REFUSED Boolean value '00' is not recognized"},
        {"BOOLEAN", "'2020-01-01'", uncaught("Boolean value '2020-01-01' is not recognized")},
        {"FLOAT", "1", "1.0"},
        {"FLOAT", "'a'", uncaught("Numeric value 'a' is not recognized")},
        {"FLOAT", "'10:00:00'", uncaught("Numeric value '10:00:00' is not recognized")},
        {"FLOAT", "TRUE", declared("FLOAT", "BOOLEAN")},
        {"FLOAT", "1.5", "1.5"},
        {"FLOAT", "PARSE_JSON('1')", "1.0"},
        {"FLOAT", "NULL", "null"},
        {"FLOAT", "TO_BINARY('00', 'HEX')", "0.0"},
        {"FLOAT", "'2020-01-01'", uncaught("Numeric value '2020-01-01' is not recognized")},
        {"VARCHAR", "1", "1"},
        {"VARCHAR", "'a'", "a"},
        {"VARCHAR", "'10:00:00'", "10:00:00"},
        {"VARCHAR", "TRUE", "true"},
        {"VARCHAR", "1.5", "1.5"},
        {"VARCHAR", "PARSE_JSON('1')", "1"},
        {"VARCHAR", "NULL", "null"},
        {"VARCHAR", "TO_BINARY('00', 'HEX')", "00"},
        {"VARCHAR", "'2020-01-01'", "2020-01-01"},
        {"NUMBER(10,2)", "1", "1.00"},
        {"NUMBER(10,2)", "'a'", uncaught("Numeric value 'a' is not recognized")},
        {"NUMBER(10,2)", "'10:00:00'", uncaught("Numeric value '10:00:00' is not recognized")},
        {"NUMBER(10,2)", "TRUE", "1.00"},
        {"NUMBER(10,2)", "1.5", "1.50"},
        {"NUMBER(10,2)", "PARSE_JSON('1')", "1.00"},
        {"NUMBER(10,2)", "NULL", "null"},
        {"NUMBER(10,2)", "TO_BINARY('00', 'HEX')", "0.00"},
        {"NUMBER(10,2)", "'2020-01-01'", uncaught("Numeric value '2020-01-01' is not recognized")},
        {"TIMESTAMP_TZ", "'a'", uncaught("Timestamp 'a' is not recognized")},
        {"TIMESTAMP_TZ", "'10:00:00'", uncaught("Timestamp '10:00:00' is not recognized")},
        {"TIMESTAMP_TZ", "TRUE", declared("TIMESTAMP_TZ(9)", "BOOLEAN")},
        {"TIMESTAMP_TZ", "NULL", "null"},
        {"TIMESTAMP_LTZ", "'a'", uncaught("Timestamp 'a' is not recognized")},
        {"TIMESTAMP_LTZ", "'10:00:00'", uncaught("Timestamp '10:00:00' is not recognized")},
        {"TIMESTAMP_LTZ", "TRUE", declared("TIMESTAMP_LTZ(9)", "BOOLEAN")},
        {"TIMESTAMP_LTZ", "NULL", "null"},
        {"TIMESTAMP_NTZ", "'a'", uncaught("Timestamp 'a' is not recognized")},
        {"TIMESTAMP_NTZ", "'10:00:00'", uncaught("Timestamp '10:00:00' is not recognized")},
        {"TIMESTAMP_NTZ", "TRUE", declared("TIMESTAMP_NTZ(9)", "BOOLEAN")},
        {"TIMESTAMP_NTZ", "NULL", "null"},
        {"TIMESTAMP_NTZ", "'2020-01-01'", "2020-01-01T00:00"},
    };

    /** The refusal of a constant RETURN the declared type does not take, at the call's place. */
    private static String declared(final String declared, final String actual) {
        return "REFUSED SQL compilation error: error line 1 at position 7 |  Declared return type '" + declared
            + "' is incompatible with actual return type '" + actual + "'";
    }

    /** A cast of the RETURN's string refused as the body runs. */
    private static String uncaught(final String message) {
        return "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 14 : " + message;
    }

    /** A VECTOR function's refusal of a constant, by the constant's own type. */
    private static String unsupported(final String type) {
        return "REFUSED SQL compilation error: | Unsupported data type '" + type + "'.";
    }

    @Test
    public void aReturnIsJudgedAgainstTheDeclaredTypeOnTheCall() {
        for (int i = 0; i < CALLS.length; i++) {
            final String name = "rt" + i;
            assertAnswer("Function " + name.toUpperCase(Locale.ROOT) + " successfully created.",
                "CREATE OR REPLACE FUNCTION " + name + "() RETURNS " + CALLS[i][0] + " AS $$ BEGIN RETURN "
                    + CALLS[i][1] + "; END $$");
            assertAnswer(CALLS[i][2], "SELECT " + name + "()");
        }
    }

    @Test
    public void aVariantArrayOrObjectFunctionIsRefusedWhenCreated() {
        for (final String type : new String[] {"VARIANT", "ARRAY", "OBJECT"}) {
            assertAnswer("REFUSED Unsupported return type for Snowscript UDF: " + type,
                "CREATE OR REPLACE FUNCTION rv() RETURNS " + type + " AS $$ BEGIN RETURN 1; END $$");
            assertAnswer("REFUSED SQL compilation error: | Unknown function RV.", "SELECT rv()");
        }
    }
}
