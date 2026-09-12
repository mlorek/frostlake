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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A VECTOR reads as neither text nor a number, so every function that wants one refuses it at compile
 * time, listing the argument types. The conversions refuse it as their own invalid type instead, and a
 * pattern match refuses it by the operator's name. The functions that never read the value — the
 * conditionals that carry it through — take it. Live-verified.
 */
public class VectorArgumentRefusalTest extends BaseDatabaseTest {

    /** A three-element FLOAT vector, as a literal expression. */
    private static final String V = "[1,2,3]::VECTOR(FLOAT,3)";

    /** Asserts a statement is refused with a message carrying {@code fragment}. */
    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(refused.getMessage() != null && refused.getMessage().contains(fragment),
            sql + " should be refused with \"" + fragment + "\" but read: " + refused.getMessage());
    }

    /** Asserts a one-argument call over a vector is refused by its argument type. */
    private void assertArgumentTypeRefused(final String function) {
        assertRefused("SELECT " + function + "(" + V + ")",
            "Invalid argument types for function '" + function + "': (VECTOR(FLOAT, 3))");
    }

    /** The text family refuses a vector in the function's own name. */
    @Test
    public void theTextFamilyRefusesAVector() {
        for (final String function : new String[] {"LENGTH", "UPPER", "LOWER", "TRIM", "LTRIM", "RTRIM",
            "REVERSE", "INITCAP", "ASCII", "SOUNDEX", "OCTET_LENGTH"}) {
            assertArgumentTypeRefused(function);
        }
        assertRefused("SELECT SUBSTR(" + V + ", 1, 2)",
            "Invalid argument types for function 'SUBSTR': (VECTOR(FLOAT, 3), NUMBER(1,0), NUMBER(1,0))");
        assertRefused("SELECT CONTAINS(" + V + ", '1')",
            "Invalid argument types for function 'CONTAINS': (VECTOR(FLOAT, 3), VARCHAR(1))");
        assertRefused("SELECT POSITION('1', " + V + ")",
            "Invalid argument types for function 'POSITION': (VARCHAR(1), VECTOR(FLOAT, 3))");
        assertRefused("SELECT REPLACE(" + V + ", '1', 'x')",
            "Invalid argument types for function 'REPLACE': (VECTOR(FLOAT, 3), VARCHAR(1), VARCHAR(1))");
    }

    /** The numeric family refuses it too. */
    @Test
    public void theNumericFamilyRefusesAVector() {
        for (final String function : new String[] {"ABS", "CEIL", "FLOOR", "SQRT", "EXP", "LN", "SIGN"}) {
            assertArgumentTypeRefused(function);
        }
        assertRefused("SELECT ROUND(" + V + ", 1)",
            "Invalid argument types for function 'ROUND': (VECTOR(FLOAT, 3), NUMBER(1,0))");
    }

    /** So do the hashing, encoding and parsing functions. */
    @Test
    public void theHashingAndEncodingFunctionsRefuseAVector() {
        for (final String function : new String[] {"MD5", "SHA1", "BASE64_ENCODE", "HEX_ENCODE",
            "PARSE_JSON"}) {
            assertArgumentTypeRefused(function);
        }
    }

    /** A conversion refuses it as ITS OWN invalid type, quoting the call back. */
    @Test
    public void aConversionRefusesItAsAnInvalidType() {
        for (final String function : new String[] {"TO_CHAR", "TO_VARCHAR", "TO_NUMBER", "TO_BOOLEAN",
            "TO_DATE"}) {
            assertRefused("SELECT " + function + "(" + V + ")",
                "invalid type [" + function + "(");
        }
    }

    /** A pattern match refuses it by the operator's name; a NOT spelling reports the plain one. */
    @Test
    public void aPatternMatchRefusesAVector() {
        assertRefused("SELECT " + V + " LIKE 'x'",
            "Invalid argument types for function 'LIKE': (VECTOR(FLOAT, 3), VARCHAR(1))");
        assertRefused("SELECT " + V + " ILIKE 'x'",
            "Invalid argument types for function 'ILIKE': (VECTOR(FLOAT, 3), VARCHAR(1))");
        assertRefused("SELECT " + V + " NOT LIKE 'x'",
            "Invalid argument types for function 'LIKE': (VECTOR(FLOAT, 3), VARCHAR(1))");
        assertRefused("SELECT " + V + " || 'x'",
            "Invalid argument types for function '||': (VECTOR(FLOAT, 3), VARCHAR(1))");
    }

    /** A function that never reads the value carries the vector through. */
    @Test
    public void aPassThroughTakesAVector() {
        engine.executeQuery("SELECT COALESCE(" + V + ", " + V + ")");
        engine.executeQuery("SELECT IFNULL(" + V + ", " + V + ")");
        engine.executeQuery("SELECT NVL(" + V + ", " + V + ")");
        engine.executeQuery("SELECT GREATEST(" + V + ", " + V + ")");
        engine.executeQuery("SELECT LEAST(" + V + ", " + V + ")");
        engine.executeQuery("SELECT EQUAL_NULL(" + V + ", " + V + ")");
        engine.executeQuery("SELECT NULLIF(" + V + ", " + V + ")");
        engine.executeQuery("SELECT " + V + " = " + V);
        engine.executeQuery("SELECT SYSTEM$TYPEOF(" + V + ")");
    }
}
