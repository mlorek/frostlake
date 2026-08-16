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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.types.DataType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * VALIDATE_UTF8 — the plain spelling of TRY_VALIDATE_UTF8, which Frostlake had never registered, so
 * every call answered "Unknown function VALIDATE_UTF8." where live returns the text.
 *
 * <p><b>The two spellings are indistinguishable on every reachable input</b>, and that is measured
 * rather than assumed. A TRY_ pair normally differs over invalid input — the TRY_ one answering NULL
 * where the plain one raises — but nothing this function accepts can carry invalid UTF-8:
 *
 * <pre>
 *   a BINARY            refused at compile time, before any byte is read
 *   a lone surrogate    refused by the string-literal reader itself, in BOTH engines
 *   a BINARY ::VARCHAR  already decoded by the time the function sees it
 * </pre>
 *
 * <p>So the branch where they would differ cannot be reached, and inventing a refusal for it would be
 * inventing behaviour nothing can observe. The lone-surrogate route was the one that was still OPEN in
 * Frostlake when this was written; it is closed now, and {@code functions/UnpairedSurrogateLiteralTest}
 * holds that refusal. If a literal ever starts building an unpaired surrogate again, the two spellings
 * would become distinguishable and BOTH would need re-measuring. It reads its argument as TEXT, which is why a BINARY is an
 * argument-type error rather than the obvious input, and why a NUMBER and a VARIANT both answer.
 *
 * <p>IS_VALID_UTF8 exists in NEITHER engine and is deliberately not added.
 */
public class ValidateUtf8Test extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE pu (bn BINARY(5), v VARCHAR(20), vt VARIANT, i INT)");
        engine.execute("INSERT INTO pu SELECT TO_BINARY('AB'), 'hello', PARSE_JSON('{\"a\": 1}'), 7");
    }

    /** The expression's value and its declared type, or the refusal. */
    private String outcome(final String expr) {
        try {
            final ResultSet rs = engine.executeQuery("SELECT " + expr + " AS c FROM pu");
            final DataType type = rs.getColumns().get(0).getDataType();
            return (rs.next() ? String.valueOf(rs.getValue(0)) : "<no rows>")
                + " | " + (type == null ? "null" : type.getName());
        } catch (final RuntimeException refused) {
            return "ERR " + String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** It answers over every text-readable argument, declaring VARCHAR. */
    @Test
    public void itAnswersOverEveryTextReadableArgument() {
        assertEquals("hello | VARCHAR", outcome("VALIDATE_UTF8(v)"));
        assertEquals("hello | VARCHAR", outcome("VALIDATE_UTF8('hello')"));
        assertEquals("7 | VARCHAR", outcome("VALIDATE_UTF8(i)"), "a NUMBER reads as its text");
        assertEquals("{\"a\":1} | VARCHAR", outcome("VALIDATE_UTF8(vt)"));
        assertEquals("null | VARCHAR", outcome("VALIDATE_UTF8(NULL)"));
    }

    /** A BINARY is an argument-type error — the function reads text, not bytes. */
    @Test
    public void aBinaryArgumentIsRefused() {
        assertEquals("ERR SQL compilation error: error line 1 at position 7 Invalid argument types"
            + " for function 'VALIDATE_UTF8': (BINARY(5))", outcome("VALIDATE_UTF8(bn)"));
    }

    /** It takes exactly one argument, and says so at the call's own position. */
    @Test
    public void itTakesExactlyOneArgument() {
        assertEquals("ERR SQL compilation error: error line 1 at position 7 not enough arguments"
            + " for function [VALIDATE_UTF8()], expected 1, got 0", outcome("VALIDATE_UTF8()"));
    }

    /** Every cell agrees with the TRY_ spelling, which is the whole claim. */
    @Test
    public void itAgreesWithTheTrySpelling() {
        assertEquals(outcome("TRY_VALIDATE_UTF8(v)"), outcome("VALIDATE_UTF8(v)"));
        assertEquals(outcome("TRY_VALIDATE_UTF8(i)"), outcome("VALIDATE_UTF8(i)"));
        assertEquals(outcome("TRY_VALIDATE_UTF8(vt)"), outcome("VALIDATE_UTF8(vt)"));
        assertEquals(outcome("TRY_VALIDATE_UTF8(NULL)"), outcome("VALIDATE_UTF8(NULL)"));
        assertEquals("AB | VARCHAR", outcome("VALIDATE_UTF8(bn::VARCHAR)"),
            "a binary cast to text is already decoded, and both spellings take it");
        assertEquals("AB | VARCHAR", outcome("TRY_VALIDATE_UTF8(bn::VARCHAR)"));
    }

    /** IS_VALID_UTF8 is in neither engine, and must stay out of this one. */
    @Test
    public void isValidUtf8IsNotAFunction() {
        assertEquals("ERR SQL compilation error: Unknown function IS_VALID_UTF8.",
            outcome("IS_VALID_UTF8(v)"));
    }
}
