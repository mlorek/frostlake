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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SQL-UDF and bind-variable argument substitution renders values as string literals in a dialect
 * where a backslash always escapes — so backslashes must be doubled, not just quotes. Escaping
 * quotes alone corrupted any value carrying escape sequences: an OBJECT whose JSON text held a
 * nested JSON string ({@code "profile":"{\"firstName\":\"A\"}"}) reached the substituted
 * {@code PARSE_JSON('…')} with bare inner quotes and failed with "Invalid JSON" (the
 * {@code classify(value OBJECT)} shape).
 */
public class UdfParamEscapingTest extends BaseDatabaseTest {

    private Object q(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getRows().get(0).getValue(0);
    }

    @Test
    public void objectParamWithNestedJsonStringSurvivesSubstitution() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION obj_field(value OBJECT)
            RETURNS VARCHAR
            LANGUAGE SQL
            AS $$ SELECT value:profile::VARCHAR $$""");
        // The stored JSON is {"profile":"{\"firstName\":\"Nested\"}"} — escape sequences inside a
        // JSON string, exactly what ingest attribute payloads carry.
        assertEquals("{\"firstName\":\"Nested\"}",
            q("SELECT obj_field(OBJECT_CONSTRUCT('profile', '{\"firstName\":\"Nested\"}'))"));
    }

    @Test
    public void objectParamPathConditionsWork() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION classify(value OBJECT)
            RETURNS VARCHAR
            LANGUAGE SQL
            AS $$
            SELECT CASE
                WHEN NVL(UPPER(value:item_class::VARCHAR), 'NULL') <> 'NULL' THEN UPPER(value:item_class::VARCHAR)
                WHEN IS_OBJECT(value:attributes) THEN 'GENERAL'
                ELSE 'UNKNOWN'
            END
            $$""");
        assertEquals("GENERAL", q("""
            SELECT classify(OBJECT_CONSTRUCT('attributes',
                OBJECT_CONSTRUCT('inner', '{"k":"v with \\\\ backslash"}')))"""));
    }

    @Test
    public void varcharParamWithBackslashesRoundTrips() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION echo_text(v VARCHAR)
            RETURNS VARCHAR
            LANGUAGE SQL
            AS $$ SELECT v $$""");
        engine.execute("CREATE TABLE esc_src (s VARCHAR)");
        engine.execute("INSERT INTO esc_src SELECT 'C:\\\\dir' || CHR(39) || 'q'");
        final String original = (String) q("SELECT s FROM esc_src");
        assertEquals("C:\\dir'q", original);
        assertEquals(original, q("SELECT echo_text(s) FROM esc_src"));
    }

    @Test
    public void bindVariableWithBackslashRoundTrips() {
        engine.execute("CREATE TABLE esc_sink (v VARCHAR)");
        engine.execute("""
            BEGIN
                LET s VARCHAR := (SELECT 'a\\\\b' || CHR(39) || 'c');
                INSERT INTO esc_sink SELECT :s;
            END""");
        assertEquals("a\\b'c", q("SELECT v FROM esc_sink"));
    }
}
