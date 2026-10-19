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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A cast to a structured type converts its DATE, TIME, timestamp and BINARY leaves as the scalar cast to the leaf's
 * declared type converts them, at the declared precision, and refuses a leaf that will not convert. Those leaves kept
 * the text they were given, unchecked, and every temporal leaf's type read at precision 9.
 */
public class StructuredTemporalAndBinaryLeavesTest {

    private static final String MISMATCH = "Typed object schema mismatch in conversion";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE tl_db");
        engine.execute("USE SCHEMA tl_db.PUBLIC");
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
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void temporalLeavesConvertAtTheirDeclaredPrecision() {
        // Live-verified on qa-develop under the default output formats.
        assertEquals("{\"k\":\"2024-01-01 10:00:00.123\"}",
            first("SELECT {'k': '2024-01-01 10:00:00.123456'}::OBJECT(k TIMESTAMP_NTZ(3))"));
        assertEquals("OBJECT(k TIMESTAMP_NTZ(3))[LOB]",
            first("SELECT SYSTEM$TYPEOF({'k': '2024-01-01 10:00:00.123456'}::OBJECT(k TIMESTAMP_NTZ(3)))"));
        assertEquals("[\"2024-01-01 10:00:00.123\"]",
            first("SELECT ['2024-01-01 10:00:00.123456']::ARRAY(TIMESTAMP_NTZ(3))"));
        assertEquals("{\"t\":\"2024-01-01 10:00:00.000\"}",
            first("SELECT {'t': '2024-01-01 10:00:00'}::OBJECT(t TIMESTAMP_NTZ)"));
        assertEquals("{\"k\":\"10:00:00\"}", first("SELECT {'k': '10:00:00.123456'}::OBJECT(k TIME(3))"));
        assertEquals("{\"k\":\"2024-01-01 10:00:00.123 +0200\"}",
            first("SELECT {'k': '2024-01-01 10:00:00.123456 +02:00'}::OBJECT(k TIMESTAMP_TZ(3))"));
        assertEquals("{\"k\":\"2024-01-01\"}", first("SELECT {'k': '2024-01-01'}::OBJECT(k DATE)"));
        assertEquals(MISMATCH, refusal("SELECT {'k': 'not a timestamp'}::OBJECT(k TIMESTAMP_NTZ(3))"));
    }

    @Test
    public void aTemporalLeafRendersInTheSessionsOutputFormatWhenItIsCast() {
        engine.execute("ALTER SESSION SET TIMESTAMP_NTZ_OUTPUT_FORMAT = 'YYYY-MM-DD HH24:MI:SS.FF9'");
        assertEquals("{\"k\":\"2024-01-01 10:00:00.123000000\"}",
            first("SELECT {'k': '2024-01-01 10:00:00.123456'}::OBJECT(k TIMESTAMP_NTZ(3))"),
            "the declared precision cut the micro-seconds before the session's format printed them");
    }

    @Test
    public void binaryLeavesReadTheirTextAsHex() {
        // Live-verified on qa-develop.
        assertEquals("{\"k\":\"AB\"}", first("SELECT {'k': 'ab'}::OBJECT(k BINARY)"));
        assertEquals(MISMATCH, refusal("SELECT {'k': 'zz'}::OBJECT(k BINARY)"));
    }

    @Test
    public void aWriteToAStructuredColumnConvertsToTheColumnsLeaves() {
        engine.execute("CREATE TABLE st (o OBJECT(k TIMESTAMP_NTZ(3)), a ARRAY(TIME(0)))");
        engine.execute("INSERT INTO st (o, a) SELECT {'k': '2024-01-01 10:00:00.123456'}::OBJECT(k TIMESTAMP_NTZ(9)),"
            + " ['10:00:00.5']::ARRAY(TIME(9))");
        final ResultSet stored = engine.executeQuery("SELECT o, a, SYSTEM$TYPEOF(o), SYSTEM$TYPEOF(a) FROM st");
        assertEquals("{\"k\":\"2024-01-01 10:00:00.123\"}", String.valueOf(stored.getRows().get(0).getValue(0)));
        assertEquals("[\"10:00:00\"]", String.valueOf(stored.getRows().get(0).getValue(1)));
        assertEquals("OBJECT(k TIMESTAMP_NTZ(3))[LOB]", stored.getRows().get(0).getValue(2));
        assertEquals("ARRAY(TIME(0))[LOB]", stored.getRows().get(0).getValue(3));
    }
}
