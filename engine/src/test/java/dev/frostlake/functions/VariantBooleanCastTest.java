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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A VARIANT becomes a BOOLEAN — through {@code ::BOOLEAN}, CAST or TO_BOOLEAN — only when it holds one: a
 * JSON boolean, a JSON null (NULL), or a string TO_BOOLEAN would read ('yes', 'F', '1', trimmed). Every
 * other value fails the row with {@code Failed to cast variant value <text> to BOOLEAN} — a number
 * included, where a SQL NUMBER converts, and a string that is no boolean spelling, which a SQL text refuses
 * in other words. The cast compiles, so a query over no rows is never refused. Every expectation is
 * live-verified.
 */
public class VariantBooleanCastTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE rt (v VARIANT)");
        engine.execute("INSERT INTO rt SELECT PARSE_JSON('{\"a\":true}')");
    }

    private String cells(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder all = new StringBuilder();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            if (i > 0) {
                all.append(", ");
            }
            all.append(String.valueOf(rs.getRows().get(0).getValue(i)).toLowerCase());
        }
        return all.toString();
    }

    private void assertFailsToCast(final String sql, final String text) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql);
        assertTrue(String.valueOf(refused.getMessage()).contains("Failed to cast variant value " + text + " to BOOLEAN"),
            sql + " -> " + refused.getMessage());
    }

    @Test
    public void aVariantHoldingABooleanOrABooleanSpellingConverts() {
        assertEquals("true, false", cells("SELECT PARSE_JSON('true')::BOOLEAN, PARSE_JSON('false')::BOOLEAN"));
        assertEquals("null", cells("SELECT PARSE_JSON('null')::BOOLEAN"));
        assertEquals("true, false, true, true", cells("SELECT PARSE_JSON('\"true\"')::BOOLEAN, PARSE_JSON('\"F\"')::BOOLEAN, "
            + "PARSE_JSON('\"1\"')::BOOLEAN, PARSE_JSON('\" true \"')::BOOLEAN"));
        assertEquals("true, false", cells("SELECT PARSE_JSON('\"yes\"')::BOOLEAN, PARSE_JSON('\"off\"')::BOOLEAN"));
        assertEquals("true, true", cells("SELECT TO_VARIANT(TRUE)::BOOLEAN, TO_VARIANT('yes')::BOOLEAN"));
        assertEquals("true, false", cells("SELECT TO_BOOLEAN(PARSE_JSON('true')), TO_BOOLEAN(PARSE_JSON('\"no\"'))"));
        // AS_BOOLEAN reads without converting: anything but a boolean is NULL, never a failure.
        assertEquals("null", cells("SELECT AS_BOOLEAN(PARSE_JSON('{\"a\":1}'))"));
        // The cast compiles, so no row, no refusal.
        assertEquals(0, engine.executeQuery("SELECT v::BOOLEAN FROM rt WHERE 1 = 0").getRowCount());
    }

    @Test
    public void anythingElseFailsTheRow() {
        assertFailsToCast("SELECT IFF(v::BOOLEAN, 1, 2) FROM rt", "{\"a\":true}");
        assertFailsToCast("SELECT CASE WHEN v::BOOLEAN THEN 1 ELSE 2 END FROM rt", "{\"a\":true}");
        assertFailsToCast("SELECT PARSE_JSON('[true]')::BOOLEAN", "[true]");
        assertFailsToCast("SELECT PARSE_JSON('{}')::BOOLEAN", "{}");
        assertFailsToCast("SELECT PARSE_JSON('[]')::BOOLEAN", "[]");
        assertFailsToCast("SELECT CAST(PARSE_JSON('[1]') AS BOOLEAN)", "[1]");
        // A number is no boolean here, though a SQL NUMBER converts.
        assertFailsToCast("SELECT PARSE_JSON('1')::BOOLEAN", "1");
        assertFailsToCast("SELECT PARSE_JSON('0')::BOOLEAN", "0");
        assertFailsToCast("SELECT PARSE_JSON('1.5')::BOOLEAN", "1.5");
        assertFailsToCast("SELECT TO_VARIANT(1)::BOOLEAN", "1");
        // A string that is no boolean spelling, quoted as the variant holds it.
        assertFailsToCast("SELECT PARSE_JSON('\"12\"')::BOOLEAN", "\"12\"");
        assertFailsToCast("SELECT PARSE_JSON('\"abc\"')::BOOLEAN", "\"abc\"");
        assertFailsToCast("SELECT PARSE_JSON('\"\"')::BOOLEAN", "\"\"");
        assertFailsToCast("SELECT TO_BOOLEAN(PARSE_JSON('{\"a\":1}'))", "{\"a\":1}");
        assertFailsToCast("SELECT TO_BOOLEAN(PARSE_JSON('1'))", "1");
    }
}
