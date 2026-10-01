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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * What a cast to OBJECT takes, live-verified:
 *
 * <ul>
 *   <li>a VARIANT string whose text is an object reads as that object, for the cast and TO_OBJECT alike, where
 *       Frostlake refused it; any other text still fails the variant's cast, in TO_OBJECT's case too;</li>
 *   <li>a structured OBJECT or a MAP refuses every source outside the VARIANT and OBJECT families while the
 *       statement compiles, naming the source's family;</li>
 *   <li>and a VARIANT holding no object does not fit one at row time, even when its text spells one.</li>
 * </ul>
 */
public class ObjectCastSourceRulesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("""
            CREATE TABLE st (g VARCHAR(10), n NUMBER(5,0), f FLOAT, bo BOOLEAN, d DATE, ts TIMESTAMP_NTZ, bn BINARY,
                a ARRAY, o OBJECT, v VARIANT, tm TIME)""");
        engine.execute("""
            INSERT INTO st SELECT '2024-01-01', 5, 1.5, TRUE, '2024-01-01', '2024-01-01 10:00:00', TO_BINARY('41', 'HEX'),
                ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('k', 1), PARSE_JSON('{"k":2}'), '10:00:00'""");
        engine.execute("CREATE TABLE est LIKE st");
    }

    /** The one row's cells, as text. */
    private List<String> row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> cells = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            cells.add(String.valueOf(rs.getRows().get(0).getValue(i)));
        }
        return cells;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void aVariantStringSpellingAnObjectReadsAsIt() {
        assertEquals(List.of("{}", "{\"a\":1}", "{}", "OBJECT[LOB]", "{\"a\":1}", "{\"a\":1}"),
            row("""
                SELECT CAST('{}'::VARIANT AS OBJECT), CAST(' {"a": 1} '::VARIANT AS OBJECT), '{}'::VARIANT::OBJECT,
                    SYSTEM$TYPEOF(CAST('{}'::VARIANT AS OBJECT)), TO_OBJECT(' {"a":1} '::VARIANT),
                    PARSE_JSON('{"s":"{\\\\"a\\\\":1}"}'):s::OBJECT"""));
        assertEquals(List.of("[\"[1,2]\"]"), row("SELECT CAST('[1,2]'::VARIANT AS ARRAY)"));
        assertEquals("Failed to cast variant value \"[1]\" to OBJECT", refusal("SELECT CAST('[1]'::VARIANT AS OBJECT)"));
        assertEquals("Failed to cast variant value \"x\" to OBJECT", refusal("SELECT CAST('x'::VARIANT AS OBJECT)"));
        assertEquals("Failed to cast variant value \"{bad\" to OBJECT", refusal("SELECT CAST('{bad'::VARIANT AS OBJECT)"));
        assertEquals("Failed to cast variant value \"null\" to OBJECT", refusal("SELECT CAST('null'::VARIANT AS OBJECT)"));
        assertEquals("Failed to cast variant value 1 to OBJECT", refusal("SELECT TO_OBJECT(PARSE_JSON('1'))"));
        assertEquals("Failed to cast variant value \"[1]\" to OBJECT", refusal("SELECT TO_OBJECT('[1]'::VARIANT)"));
        assertEquals("Failed to cast variant value \"[1]\" to OBJECT",
            refusal("SELECT CAST(PARSE_JSON('{\"s\":\"[1]\"}'):s AS OBJECT)"));
    }

    @Test
    public void aStructuredObjectRefusesOtherFamiliesWhileCompiling() {
        final String object = "] and [STRUCTURED_OBJECT]";
        assertEquals("SQL compilation error:\nincompatible types: [TEXT" + object,
            refusal("SELECT CAST(g AS OBJECT(k INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [FIXED" + object,
            refusal("SELECT CAST(n AS OBJECT(k INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [REAL" + object,
            refusal("SELECT CAST(f AS OBJECT(k INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [BOOLEAN" + object,
            refusal("SELECT CAST(bo AS OBJECT(k INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [DATE" + object,
            refusal("SELECT CAST(d AS OBJECT(k INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [TIMESTAMP_NTZ" + object,
            refusal("SELECT CAST(ts AS OBJECT(k INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [BINARY" + object,
            refusal("SELECT CAST(bn AS OBJECT(k INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [ARRAY" + object,
            refusal("SELECT CAST(a AS OBJECT(k INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [TIME" + object,
            refusal("SELECT CAST(tm AS OBJECT(k INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [TEXT" + object,
            refusal("SELECT TRY_CAST(g AS OBJECT(k INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [STRUCTURED_ARRAY" + object,
            refusal("SELECT CAST(ARRAY_CONSTRUCT(1)::ARRAY(INT) AS OBJECT(k INT))"));
        assertEquals("SQL compilation error:\nincompatible types: [TIMESTAMP_LTZ" + object,
            refusal("SELECT CAST('2024-01-01 10:00:00'::TIMESTAMP_LTZ AS OBJECT(k INT))"));
        assertEquals("SQL compilation error:\nincompatible types: [TEXT] and [MAP]",
            refusal("SELECT CAST(g AS MAP(VARCHAR, INT)) FROM est"));
        assertEquals("SQL compilation error:\nincompatible types: [ARRAY] and [MAP]",
            refusal("SELECT CAST(a AS MAP(VARCHAR, INT)) FROM est"));
        assertEquals(List.of("{\"k\":2}", "{\"k\":1}"),
            row("SELECT CAST(v AS OBJECT(k INT)), CAST(o AS OBJECT(k INT)) FROM st"));
        assertEquals(0, engine.executeQuery("SELECT CAST(g AS ARRAY(INT)) FROM est").getRowCount());
    }

    @Test
    public void aVariantHoldingNoObjectDoesNotFitAStructuredObject() {
        final String mismatch = "Typed object schema mismatch in conversion";
        assertEquals(mismatch, refusal("SELECT CAST('{\"k\":1}'::VARIANT AS OBJECT(k INT))"));
        assertEquals(mismatch, refusal("SELECT CAST(PARSE_JSON('1') AS OBJECT(k INT))"));
        assertEquals(mismatch, refusal("SELECT CAST('{\"k\":1}'::VARIANT AS MAP(VARCHAR, INT))"));
        assertEquals(mismatch, refusal("SELECT CAST(g::VARIANT AS OBJECT(k INT)) FROM st"));
    }
}
