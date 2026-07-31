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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A STRING value whose content merely LOOKS like JSON structure ('[]', '{"a":1}') travels as the
 * QUOTED carrier form after a variant path access (see JsonPathExtractor), so ::ARRAY keeps
 * wrapping it as a string. Re-embedding that carrier into an object/array (OBJECT_AGG,
 * ARRAY_AGG, OBJECT_CONSTRUCT, object literals) must restore the plain STRING member — it was
 * double-encoded instead, e.g. OBJECT_AGG stored {"plans":"\"[]\""} where Snowflake stores
 * {"plans":"[]"}. The vendor loader hits this with an attribute_value of "[]" aggregated by
 * OBJECT_AGG into a properties dict.
 */
public class StructuralStringEmbedTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE props (doc VARIANT)");
        engine.execute("""
            INSERT INTO props SELECT PARSE_JSON(
            '[{"attribute_name":"assigned_plans","attribute_value":"[]"},
              {"attribute_name":"account_name","attribute_value":"CS-x"},
              {"attribute_name":"quirk","attribute_value":"{\\\\"a\\\\":1}"}]')
            """);
    }

    @Test
    public void objectAggKeepsStructuralLookingStringAsString() {
        assertEquals("{\"account_name\":\"CS-x\",\"assigned_plans\":\"[]\",\"quirk\":\"{\\\"a\\\":1}\"}",
            scalar("SELECT OBJECT_AGG(value:attribute_name, value:attribute_value)::VARCHAR"
                + " FROM props, TABLE(FLATTEN(doc))"));
    }

    @Test
    public void arrayAggKeepsStructuralLookingStringAsString() {
        assertEquals("[\"[]\"]",
            scalar("SELECT ARRAY_AGG(value:attribute_value)::VARCHAR FROM props, TABLE(FLATTEN(doc))"
                + " WHERE value:attribute_name = 'assigned_plans'"));
    }

    @Test
    public void objectConstructKeepsStructuralLookingStringAsString() {
        assertEquals("{\"plans\":\"[]\"}",
            scalar("SELECT OBJECT_CONSTRUCT('plans', value:attribute_value)::VARCHAR"
                + " FROM props, TABLE(FLATTEN(doc)) WHERE value:attribute_name = 'assigned_plans'"));
    }

    @Test
    public void objectLiteralKeepsStructuralLookingStringAsString() {
        engine.execute("CREATE TABLE one AS SELECT value:attribute_value AS v"
            + " FROM props, TABLE(FLATTEN(doc)) WHERE value:attribute_name = 'assigned_plans'");
        assertEquals("{\"plans\":\"[]\"}", scalar("SELECT {'plans': v}::VARCHAR FROM one"));
    }

    @Test
    public void stringNullStaysAStringWhenEmbedded() {
        // {"v": "null"} carries a real STRING, not a JSON null — Snowflake keeps it a string member.
        engine.execute("CREATE TABLE nul (doc VARIANT)");
        engine.execute("INSERT INTO nul SELECT PARSE_JSON('{\"str_null\":\"null\",\"real_null\":null}')");
        assertEquals("{\"lastlogin\":\"null\"}",
            scalar("SELECT OBJECT_AGG('lastlogin', doc:str_null)::VARCHAR FROM nul"));
        assertEquals("{\"k\":null}",
            scalar("SELECT OBJECT_AGG('k', doc:real_null)::VARCHAR FROM nul"));
        assertEquals("null", scalar("SELECT doc:str_null::VARCHAR FROM nul"));
        assertEquals(null, scalar("SELECT doc:real_null::VARCHAR FROM nul"));
        assertEquals("false", scalar("SELECT IS_NULL_VALUE(doc:str_null) FROM nul"));
        assertEquals("true", scalar("SELECT IS_NULL_VALUE(doc:real_null) FROM nul"));
        assertEquals("VARCHAR", scalar("SELECT TYPEOF(doc:str_null) FROM nul"));
    }

    @Test
    public void pathAccessRoundTripStillCastsCleanly() {
        // The carrier itself keeps working: ::VARCHAR decodes, ::ARRAY wraps (TO_ARRAY semantics).
        assertEquals("[]",
            scalar("SELECT value:attribute_value::VARCHAR FROM props, TABLE(FLATTEN(doc))"
                + " WHERE value:attribute_name = 'assigned_plans'"));
        assertEquals("[\"[]\"]",
            scalar("SELECT value:attribute_value::ARRAY::VARCHAR FROM props, TABLE(FLATTEN(doc))"
                + " WHERE value:attribute_name = 'assigned_plans'"));
    }

    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final Object v = rs.getRows().get(0).getValue(0);
        return v == null ? null : v.toString();
    }
}
