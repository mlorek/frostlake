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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plain and a structured OBJECT or ARRAY overload are two signatures on every path, as CREATE already counted them.
 * Lookup, DROP, GET_DDL, OR REPLACE and SHOW keyed signatures on the plain type name. A structured argument went to
 * the plain overload and was refused, DROP and GET_DDL took the plain overload for the structured one, and OR REPLACE
 * replaced it.
 */
public class PlainAndStructuredOverloadsTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE ov_db");
        engine.execute("USE SCHEMA ov_db.PUBLIC");
        engine.execute("CREATE FUNCTION f1(x OBJECT) RETURNS VARCHAR AS $$ 'plain' $$");
        engine.execute("CREATE FUNCTION f1(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 'structured' $$");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    private String first(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Object value : engine.executeQuery(sql).getRows().get(0).getValues()) {
            out.append(out.length() > 0 ? "|" : "").append(value);
        }
        return out.toString();
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
    public void eachArgumentReachesItsOwnOverload() {
        assertEquals("structured|plain", first("SELECT f1({'a':1}::OBJECT(a INT)), f1(OBJECT_CONSTRUCT('a', 1))"));
        engine.execute("CREATE FUNCTION f3(x ARRAY(INT)) RETURNS VARCHAR AS $$ 'structured' $$");
        engine.execute("CREATE FUNCTION f3(x ARRAY) RETURNS VARCHAR AS $$ 'plain' $$");
        assertEquals("structured|plain", first("SELECT f3([1]::ARRAY(INT)), f3(ARRAY_CONSTRUCT(1))"),
            "declared the other way round, and as ARRAYs");
    }

    @Test
    public void dropGetDdlAndOrReplaceActOnTheNamedOverload() {
        assertTrue(first("SELECT GET_DDL('FUNCTION', 'f1(OBJECT(a INT))')").contains("'structured'"));
        assertTrue(first("SELECT GET_DDL('FUNCTION', 'f1(OBJECT)')").contains("'plain'"));

        engine.execute("DROP FUNCTION f1(OBJECT(a INT))");
        assertEquals("plain", first("SELECT f1(OBJECT_CONSTRUCT('a', 1))"), "the plain overload survives");
        assertTrue(refusal("SELECT f1({'a':1}::OBJECT(a INT))").contains("Invalid argument types"));

        engine.execute("CREATE FUNCTION f2(x OBJECT) RETURNS VARCHAR AS $$ 'plain' $$");
        engine.execute("CREATE OR REPLACE FUNCTION f2(x OBJECT(a INT)) RETURNS VARCHAR AS $$ 'structured' $$");
        assertEquals("plain|structured", first("SELECT f2(OBJECT_CONSTRUCT('a', 1)), f2({'a':1}::OBJECT(a INT))"),
            "OR REPLACE of the structured overload adds it beside the plain one");
    }

    @Test
    public void showListsEachOverloadWithItsShape() {
        final ResultSet listed = engine.executeQuery("SHOW USER FUNCTIONS LIKE 'F1'");
        final StringBuilder arguments = new StringBuilder();
        while (listed.next()) {
            arguments.append(arguments.length() > 0 ? " ; " : "").append(listed.getValue("arguments"));
        }
        assertEquals("F1(X OBJECT) RETURN VARCHAR ; F1(X OBJECT(a NUMBER(38,0))) RETURN VARCHAR", arguments.toString());
    }
}
