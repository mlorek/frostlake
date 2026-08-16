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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The UUID type: TO_UUID, TRY_TO_UUID, a cast to UUID and a UUID column all hold the canonical
 * lower-case text, typed UUID in every piece of metadata - SYSTEM$TYPEOF's UUID[SB16], DESCRIBE's and
 * GET_DDL's UUID, INFORMATION_SCHEMA's UUID without a length and SHOW COLUMNS' {"type":"UUID"} - while a
 * string function reads it as its text. Text that is not a hyphenated UUID is refused with the
 * conversion's own sentence, inside the DML envelope on a write. Every cell is live-verified.
 */
public class UuidTypeTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** The conversions are typed UUID and answer the canonical text; string functions read that text. */
    @Test
    public void theConversionsAreTypedUuid() {
        assertEquals("UUID[SB16]",
            rows("SELECT SYSTEM$TYPEOF(TRY_TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427'))"));
        assertEquals("UUID[SB16]",
            rows("SELECT SYSTEM$TYPEOF(TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427'))"));
        assertEquals("1b4e28ba-2fa1-11d2-883f-0016d3cca427",
            rows("SELECT TRY_TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427')"));
        assertEquals("1b4e28ba-2fa1-11d2-883f-0016d3cca427",
            rows("SELECT TO_UUID('1B4E28BA-2FA1-11D2-883F-0016D3CCA427')"));
        assertEquals("null",
            rows("SELECT TRY_TO_UUID('x')"));
        assertEquals("true",
            rows("SELECT TRY_TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427') = '1b4e28ba-2fa1-11d2-883f-0016d3cca427'"));
        assertEquals("1b4e28ba-2fa1-11d2-883f-0016d3cca427",
            rows("SELECT TRY_TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427')::VARCHAR"));
        assertEquals("1b4e28ba-2fa1-11d2-883f-0016d3cca427",
            rows("SELECT '1B4E28BA-2FA1-11D2-883F-0016D3CCA427'::UUID"));
        assertEquals("VARCHAR(36)[LOB]",
            rows("SELECT SYSTEM$TYPEOF(UUID_STRING())"));
        assertEquals("UUID[SB16]",
            rows("SELECT SYSTEM$TYPEOF('1b4e28ba-2fa1-11d2-883f-0016d3cca427'::UUID)"));
        assertEquals("1b4e28ba-2fa1-11d2-883f-0016d3cca427x",
            rows("SELECT TRY_TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427') || 'x'"));
        assertEquals("1B4E28BA-2FA1-11D2-883F-0016D3CCA427",
            rows("SELECT UPPER(TRY_TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427'))"));
        assertEquals("36",
            rows("SELECT LENGTH(TRY_TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427'))"));
        assertEquals("null",
            rows("SELECT TRY_TO_UUID('{1b4e28ba-2fa1-11d2-883f-0016d3cca427}')"));
        assertEquals("null",
            rows("SELECT TRY_CAST('x' AS UUID)"));
        assertEquals("UUID[SB16]",
            rows("SELECT SYSTEM$TYPEOF(TRY_CAST('1b4e28ba-2fa1-11d2-883f-0016d3cca427' AS UUID))"));
        assertEquals("UUID[SB16]",
            rows("SELECT SYSTEM$TYPEOF(NULL::UUID)"));
        assertEquals("null",
            rows("SELECT TO_UUID(NULL)"));
        assertEquals("null",
            rows("SELECT TO_UUID('')"));
    }

    /** Text that is not a hyphenated UUID is refused, or NULL through TRY_; an untyped NULL is refused. */
    @Test
    public void invalidTextIsRefusedWithTheConversionsSentence() {
        assertRefused("SELECT TO_UUID('x')",
            "UUID 'x' is invalid, expected format is xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (8-4-4-4-12 hexadecimal digits with hyphens)");
        assertRefused("SELECT TRY_TO_UUID(NULL)",
            "SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types NULL and UUID");
        assertRefused("SELECT TRY_TO_UUID(1)",
            "SQL compilation error:\ninvalid type [TRY_TO_UUID(1)] for parameter 'TO_UUID'");
        assertRefused("SELECT TO_UUID('1B4E28BA2FA111D2883F0016D3CCA427')",
            "UUID '1B4E28BA2FA111D2883F0016D3CCA427' is invalid, expected format is xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (8-4-4-4-12 hexadecimal digits with hyphens)");
        assertRefused("SELECT 'x'::UUID",
            "UUID 'x' is invalid, expected format is xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (8-4-4-4-12 hexadecimal digits with hyphens)");
        assertRefused("SELECT TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427', 1)",
            "SQL compilation error: error line 1 at position 7\ntoo many arguments for function [TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427', 1)] expected 1, got 2");
    }

    /** A UUID column: stored lower-case, refused on bad text, and UUID in every metadata surface. */
    @Test
    public void aUuidColumnIsTypedUuidEverywhere() {
        engine.execute("CREATE OR REPLACE TABLE tu (u UUID, v VARCHAR(40))");
        engine.execute("INSERT INTO tu VALUES ('1B4E28BA-2FA1-11D2-883F-0016D3CCA427', '1B4E28BA-2FA1-11D2-883F-0016D3CCA427')");
        assertEquals("1b4e28ba-2fa1-11d2-883f-0016d3cca427, 1B4E28BA-2FA1-11D2-883F-0016D3CCA427",
            rows("SELECT u, v FROM tu"));
        assertRefused("INSERT INTO tu VALUES ('x', 'x')",
            "DML operation to table TU failed on column U with error: UUID 'x' is invalid, expected format is xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (8-4-4-4-12 hexadecimal digits with hyphens)");
        assertEquals("UUID[SB16]",
            rows("SELECT SYSTEM$TYPEOF(u) FROM tu"));
        engine.execute("SHOW COLUMNS IN TABLE tu");
        assertEquals("U, {\"type\":\"UUID\",\"nullable\":true} | V, {\"type\":\"TEXT\",\"length\":40,\"byteLength\":160,\"nullable\":true,\"fixed\":false}",
            rows("SELECT \"column_name\", \"data_type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        engine.execute("DESC TABLE tu");
        assertEquals("U, UUID | V, VARCHAR(40)",
            rows("SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        assertEquals("U, UUID, null, null | V, TEXT, 40, 160",
            rows("SELECT column_name, data_type, character_maximum_length, character_octet_length FROM information_schema.columns WHERE table_name = 'TU' ORDER BY ordinal_position"));
        assertEquals("create or replace TABLE TU (\n\tU UUID,\n\tV VARCHAR(40)\n);",
            rows("SELECT GET_DDL('TABLE', 'tu')"));
        assertRefused("CREATE OR REPLACE TABLE tu2 (u UUID(36))",
            "SQL compilation error:\nsyntax error line 1 at position 35 unexpected '('.");
        engine.execute("CREATE OR REPLACE TABLE tu3 AS SELECT TRY_TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427') AS u");
        assertEquals("U, UUID",
            rows("SELECT column_name, data_type FROM information_schema.columns WHERE table_name = 'TU3'"));
        engine.execute("CREATE OR REPLACE VIEW vu AS SELECT TRY_TO_UUID('1b4e28ba-2fa1-11d2-883f-0016d3cca427') AS c1, UUID_STRING() AS c2");
        assertEquals("C1, UUID, null | C2, TEXT, 36",
            rows("SELECT column_name, data_type, character_maximum_length FROM information_schema.columns WHERE table_name = 'VU' ORDER BY ordinal_position"));
        engine.execute("ALTER TABLE tu ADD COLUMN w UUID");
        assertEquals("UUID[SB16]",
            rows("SELECT SYSTEM$TYPEOF(w) FROM tu"));
        assertEquals("1b4e28ba-2fa1-11d2-883f-0016d3cca427, 1b4e28ba-2fa1-11d2-883f-0016d3cca427, UUID[SB16]",
            rows("SELECT TO_UUID(u), TO_VARCHAR(u), SYSTEM$TYPEOF(TO_UUID(u)) FROM tu"));
        assertEquals("UUID[SB16], 1b4e28ba-2fa1-11d2-883f-0016d3cca427",
            rows("SELECT SYSTEM$TYPEOF(v::UUID), v::UUID FROM tu"));
        assertEquals("UUID[SB16]",
            rows("SELECT SYSTEM$TYPEOF(TO_UUID(v)) FROM tu"));
        engine.execute("UPDATE tu SET w = '2B4E28BA-2FA1-11D2-883F-0016D3CCA427'");
        assertEquals("2b4e28ba-2fa1-11d2-883f-0016d3cca427",
            rows("SELECT w FROM tu"));
        assertRefused("UPDATE tu SET w = 'nope'",
            "DML operation to table TU failed on column W with error: UUID 'nope' is invalid, expected format is xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (8-4-4-4-12 hexadecimal digits with hyphens)");
    }
}
