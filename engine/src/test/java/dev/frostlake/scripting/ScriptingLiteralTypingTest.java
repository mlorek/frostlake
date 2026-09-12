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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A numeric literal inside a Snowflake Scripting block is typed exactly as SQL types it: an exact number,
 * trailing zeros no part of its value and an exponent only a way of writing it — {@code RETURN 1.0} is 1,
 * {@code 1.10} is 1.1, {@code 1e0} is 1 and {@code 1e20} the whole number — and a variable assigned one holds
 * it as such. An arithmetic result keeps SQL's own scale ({@code 1.5 - 0.5} is 1.0, {@code 10/10} is
 * 1.000000), and a declared type still converts ({@code NUMBER(3,1) DEFAULT 1.0} is 1.0). Every expectation
 * is live-verified.
 */
public class ScriptingLiteralTypingTest extends BaseDatabaseTest {

    /** The block's returned value, as text. */
    private String block(final String body) {
        return String.valueOf(engine.executeQuery("EXECUTE IMMEDIATE $$ " + body + " $$").getRows().get(0).getValue(0));
    }

    private String returned(final String expression) {
        return block("BEGIN RETURN " + expression + "; END;");
    }

    @Test
    public void aLiteralIsTheNumberItWrites() {
        assertEquals("1", returned("1.0"));
        assertEquals("1.1", returned("1.10"));
        assertEquals("100", returned("100"));
        assertEquals("100", returned("1.0e2"));
        assertEquals("1", returned("1e0"));
        assertEquals("0", returned("0.0"));
        assertEquals("-1.5", returned("-1.50"));
        assertEquals("0.5", returned(".5"));
        assertEquals("100000000000000000000", returned("1e20"));
        assertEquals("12345678901234567890.1", returned("12345678901234567890.10"));
    }

    @Test
    public void arithmeticKeepsSqlsScaleAndADeclaredTypeStillConverts() {
        assertEquals("1.0", returned("1.5 - 0.5"));
        assertEquals("5.0", returned("2.50 * 2"));
        assertEquals("1.000000", returned("10/10"));
        assertEquals("1", block("BEGIN LET x := 1.0; RETURN x; END;"));
        assertEquals("1", block("BEGIN LET x := 1.0; RETURN x + 0; END;"));
        assertEquals("1.0", block("DECLARE x NUMBER(3,1) DEFAULT 1.0; BEGIN RETURN x; END;"));
    }
}
