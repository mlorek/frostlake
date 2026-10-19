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

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A BINARY value is held to a declared BINARY(n) width: in a cast, in a structured leaf and in a write of a text into a
 * BINARY(n) column. The cast and the leaf kept every byte, and a column took three bytes into a BINARY(2) when they
 * were written as hex text.
 */
public class BinaryWidthTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE bw_db");
        engine.execute("USE SCHEMA bw_db.PUBLIC");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String first(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void aCastToBinaryOfAWidthRefusesMoreBytes() {
        // Live-verified on qa-develop.
        assertEquals("Binary value 'ABCDEF' is too long and would be truncated",
            refusal("SELECT TO_BINARY('ABCDEF', 'HEX')::BINARY(2)"));
        assertEquals("ABCD", first("SELECT TO_BINARY('ABCD', 'HEX')::BINARY(2)"));
        assertEquals("null", first("SELECT TRY_CAST(TO_BINARY('ABCDEF', 'HEX') AS BINARY(2))"));
        assertEquals("ABCDEF", first("SELECT TO_BINARY('ABCDEF', 'HEX')::BINARY"), "an unsized BINARY takes any width");
    }

    @Test
    public void aStructuredBinaryLeafSpellsItsBytesInTheRefusal() {
        // Live-verified on qa-develop, the odd spelling included.
        assertEquals("String '0xAB0xCD0xEF' is too long and would be truncated",
            refusal("SELECT {'k': 'ABCDEF'}::OBJECT(k BINARY(2))"));
        assertEquals("{\"k\":\"ABCD\"}", first("SELECT {'k': 'ABCD'}::OBJECT(k BINARY(2))"));
    }

    @Test
    public void aTextWrittenToABinaryColumnIsHeldToItsWidth() {
        engine.execute("CREATE TABLE b2 (x BINARY(2))");
        assertTrue(refusal("INSERT INTO b2 (x) VALUES ('ABCDEF')")
            .contains("Binary value 'ABCDEF' is too long and would be truncated"));
        engine.execute("INSERT INTO b2 (x) VALUES ('AB')");
        assertTrue(refusal("UPDATE b2 SET x = 'ABCDEF'")
            .contains("Binary value 'ABCDEF' is too long and would be truncated"));
        assertEquals("AB", first("SELECT x FROM b2"));
    }
}
