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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hex binary literals — {@code x'A1B2'} / {@code X'a1b2'} — carrying the engine's BINARY value form
 * (a real BINARY value, displayed as uppercase hex), usable in expressions, functions and INSERTs.
 */
public class HexBinaryLiteralTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void hexLiteralYieldsTheUppercaseHexValue() {
        assertEquals("A1B2", String.valueOf(scalar("SELECT x'A1B2'")));
        assertEquals("A1B2", String.valueOf(scalar("SELECT X'a1b2'")),
            "lowercase digits and X both normalize");
    }

    @Test
    public void hexLiteralMatchesTheEngineBinaryForm() {
        // BINARY values are real byte values; the literal equals TO_BINARY of the same hex.
        assertEquals(Boolean.TRUE, scalar("SELECT x'A1B2' = TO_BINARY('A1B2', 'HEX')"));
    }

    @Test
    public void hexLiteralInsertsIntoBinaryColumns() {
        engine.execute("CREATE TABLE hx (b BINARY)");
        engine.execute("INSERT INTO hx VALUES (x'48FAF43B0AFCEF9B63EE3A93EE2AC2')");
        assertEquals("48FAF43B0AFCEF9B63EE3A93EE2AC2", String.valueOf(scalar("SELECT b FROM hx")));
    }
}
