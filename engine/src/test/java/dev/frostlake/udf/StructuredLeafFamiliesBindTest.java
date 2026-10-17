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

import dev.frostlake.DatabaseEngine;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A structured argument binds a structured parameter only when their leaves are of the same FAMILY, as the
 * binding rule says, the numbers excepted: an exact and an approximate leaf bind each other (live-verified). The
 * leaves were compared by Java class, so a TIME leaf bound a DATE parameter: both are one class in the engine.
 */
public class StructuredLeafFamiliesBindTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE lf_db");
        engine.execute("USE SCHEMA lf_db.PUBLIC");
        engine.execute("CREATE FUNCTION fd(o OBJECT(x DATE)) RETURNS VARCHAR AS $$ 'date-param' $$");
        engine.execute("CREATE FUNCTION fi(o OBJECT(x INT)) RETURNS VARCHAR AS $$ 'int-param' $$");
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
    public void aLeafOfAnotherFamilyIsRefused() {
        assertTrue(refusal("SELECT fd({'x':'10:00:00'}::OBJECT(x TIME))").contains("Invalid argument types"),
            "a TIME leaf for a DATE parameter");
        engine.execute("CREATE FUNCTION fn(o OBJECT(x TIMESTAMP_NTZ)) RETURNS VARCHAR AS $$ 'ntz-param' $$");
        assertTrue(refusal("SELECT fn({'x':'2024-01-01 10:00:00'}::OBJECT(x TIMESTAMP_LTZ))")
            .contains("Invalid argument types"), "each timestamp flavour is a family of its own");
    }

    @Test
    public void aLeafOfTheSameFamilyBindsWhateverItsWidth() {
        assertEquals("date-param", first("SELECT fd({'x':'2024-01-01'}::OBJECT(x DATE))"));
        assertEquals("int-param", first("SELECT fi({'x':1}::OBJECT(x NUMBER(10,0)))"));
        assertEquals("int-param", first("SELECT fi({'x':1.5}::OBJECT(x FLOAT))"),
            "an approximate leaf for an exact one");
        engine.execute("CREATE FUNCTION fv(o ARRAY(VARCHAR)) RETURNS VARCHAR AS $$ 'text-param' $$");
        assertEquals("text-param", first("SELECT fv(['a']::ARRAY(VARCHAR(10)))"));
    }

    @Test
    public void anExplicitCastConvertsOnlyWhatTheLeafTypeTakes() {
        // Live-verified on qa-develop: TIME to DATE is refused at the field, FLOAT to INT rounds.
        assertEquals("Typed object schema mismatch in conversion",
            refusal("SELECT {'x': '10:00:00'::TIME}::OBJECT(x TIME)::OBJECT(x DATE)"));
        assertEquals("{\"x\":2}", first("SELECT {'x': 1.5::FLOAT}::OBJECT(x FLOAT)::OBJECT(x INT)"));
        assertEquals("Typed object schema mismatch in conversion",
            refusal("SELECT {'x': 'nope'}::OBJECT(x DATE)"), "a DATE field takes only a date");
        assertEquals("{\"x\":\"2024-01-01\"}", first("SELECT {'x': '2024-01-01'}::OBJECT(x DATE)"));
    }
}
