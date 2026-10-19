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
 * A SQL NULL element of an ARRAY, printed undefined, stays one through a cast to a structured ARRAY and a write to a
 * structured ARRAY column, and a JSON null element becomes one. The conversion made the first a JSON null, which reads
 * as a VARIANT null rather than SQL NULL, and kept the second.
 */
public class StructuredArrayNullElementTest {

    private static final String MISMATCH = "Typed object schema mismatch in conversion";

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE an_db");
        engine.execute("USE SCHEMA an_db.PUBLIC");
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
    public void aSqlNullElementStaysUndefined() {
        // Live-verified on qa-develop.
        assertEquals("[undefined,1]", first("SELECT ARRAY_CONSTRUCT(NULL, 1)::ARRAY(NUMBER)"));
        assertEquals("[undefined]", first("SELECT [NULL]::ARRAY(VARCHAR)"));
        assertEquals("[undefined]", first("SELECT [NULL]::ARRAY(TIMESTAMP_NTZ(3))"));
        assertEquals("[undefined,\"2024-01-01\"]", first("SELECT ARRAY_CONSTRUCT(NULL, '2024-01-01')::ARRAY(DATE)"));
        assertEquals("true", first("SELECT (ARRAY_CONSTRUCT(NULL, 1)::ARRAY(NUMBER))[0] IS NULL"),
            "the element reads as SQL NULL, as the plain array's does");
    }

    @Test
    public void aJsonNullElementBecomesUndefined() {
        // Live-verified on qa-develop: a JSON null is no value of a typed element.
        assertEquals("[undefined,1]", first("SELECT PARSE_JSON('[null, 1]')::ARRAY(NUMBER)"));
        assertEquals("true", first("SELECT (PARSE_JSON('[null, 1]')::ARRAY(NUMBER))[0] IS NULL"));
        assertEquals("null", first("SELECT TYPEOF((PARSE_JSON('[null, 1]')::ARRAY(NUMBER))[0])"));
        assertEquals("[[undefined],undefined]", first("SELECT PARSE_JSON('[[null], null]')::ARRAY(ARRAY(NUMBER))"));
        // An OBJECT field and a MAP value keep their JSON null (live-verified).
        assertEquals("{\"k\":null}", first("SELECT PARSE_JSON('{\"k\": null}')::OBJECT(k NUMBER)"));
        assertEquals("{\"k\":null}", first("SELECT PARSE_JSON('{\"k\": null}')::MAP(VARCHAR, NUMBER)"));
        engine.execute("CREATE TABLE ja (a ARRAY(NUMBER))");
        engine.execute("INSERT INTO ja (a) SELECT PARSE_JSON('[null, 1]')::ARRAY(NUMBER)");
        assertEquals("[undefined,1]", first("SELECT a FROM ja"));
    }

    @Test
    public void nestedArraysKeepItInsideObjectsAndMaps() {
        assertEquals("[[undefined,1]]", first("SELECT [[NULL, 1]]::ARRAY(ARRAY(NUMBER))"));
        assertEquals("{\"a\":[undefined,1]}", first("SELECT {'a': [NULL, 1]}::OBJECT(a ARRAY(NUMBER))"));
        assertEquals("{\"m\":[undefined]}", first("SELECT {'m': ARRAY_CONSTRUCT(NULL)}::MAP(VARCHAR, ARRAY(VARCHAR))"));
    }

    @Test
    public void anObjectsNullsAreUnchanged() {
        // Live-verified on qa-develop: a kept JSON null stays one, and a key the constant dropped is missing.
        assertEquals("{\"k\":null}", first("SELECT OBJECT_CONSTRUCT_KEEP_NULL('k', NULL)::OBJECT(k VARCHAR)"));
        assertEquals("{\"k\":null}", first("SELECT OBJECT_CONSTRUCT_KEEP_NULL('k', NULL)::OBJECT(k BINARY)"));
        assertEquals("{\"k\":null}", first("SELECT OBJECT_CONSTRUCT_KEEP_NULL('k', NULL)::OBJECT(k TIMESTAMP_NTZ(3))"));
        assertEquals(MISMATCH, refusal("SELECT {'k': NULL}::OBJECT(k VARCHAR)"));
        assertEquals(MISMATCH, refusal("SELECT {}::OBJECT(k VARCHAR)"));
        assertEquals(MISMATCH, refusal("SELECT {'x':'v','b':NULL}::OBJECT(x VARCHAR, b BINARY)"));
    }

    @Test
    public void aWriteToAStructuredArrayColumnKeepsIt() throws Exception {
        engine.execute("CREATE TABLE sa (a ARRAY(NUMBER))");
        engine.execute("INSERT INTO sa (a) SELECT ARRAY_CONSTRUCT(NULL, 1)::ARRAY(NUMBER)");
        final ResultSet stored = engine.executeQuery("SELECT a, a[0] IS NULL, ARRAY_SIZE(a) FROM sa");
        assertEquals("[undefined,1]", String.valueOf(stored.getRows().get(0).getValue(0)));
        assertEquals(Boolean.TRUE, stored.getRows().get(0).getValue(1));
        assertEquals("2", String.valueOf(stored.getRows().get(0).getValue(2)));
        final DatabaseEngine copy = engine.cloneInstance();
        try {
            copy.execute("USE SCHEMA an_db.PUBLIC");
            assertEquals("[undefined,1]", String.valueOf(copy.executeQuery("SELECT a FROM sa").getRows().get(0)
                .getValue(0)), "an engine clone keeps it too");
        } finally {
            copy.shutdown();
        }
    }
}
