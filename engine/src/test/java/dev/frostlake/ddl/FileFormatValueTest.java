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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A file format's ENCODING names one of the account's character sets, by the account's name or the set's own, in any
 * case and kept as written; and a Boolean option takes only the bare words TRUE and FALSE. Anything else is refused
 * with {@code invalid value [<as written>] for parameter '<NAME>'}, at CREATE and at ALTER (live-verified).
 */
public class FileFormatValueTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    /** The ENCODING a file format reports. */
    private String encodingOf(final String format) {
        engine.execute("DESC FILE FORMAT " + format);
        final Row row = engine.executeQuery("SELECT \"property_value\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"
            + " WHERE \"property\" = 'ENCODING'").getRows().get(0);
        return String.valueOf(row.getValue(0));
    }

    @Test
    public void anEncodingIsTheAccountsNameOrTheSetsKeptAsWritten() {
        final String[] names = {"ISO-8859-1", "ISO88591", "iso88591", "UTF-8", "utf8", "windows-1252", "UTF-16LE",
            "Shift_JIS", "ISO_8859_1", "UTF_8", "utf-16"};
        for (final String name : names) {
            engine.execute("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV ENCODING = '" + name + "'");
            assertEquals(name, encodingOf("ff"));
        }
    }

    @Test
    public void anyOtherEncodingIsRefused() {
        assertEquals("SQL compilation error:\ninvalid value ['latin1'] for parameter 'ENCODING'",
            refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV ENCODING = 'latin1'"));
        assertEquals("SQL compilation error:\ninvalid value ['CP1252'] for parameter 'ENCODING'",
            refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV ENCODING = 'CP1252'"));
        assertEquals("SQL compilation error:\ninvalid value [BOGUS] for parameter 'ENCODING'",
            refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV ENCODING = BOGUS"));
        assertEquals("SQL compilation error:\ninvalid value [''] for parameter 'ENCODING'",
            refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV ENCODING = ''"));
        engine.execute("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV");
        assertEquals("SQL compilation error:\ninvalid value ['BOGUS'] for parameter 'ENCODING'",
            refusal("ALTER FILE FORMAT ff SET ENCODING = 'BOGUS'"));
    }

    @Test
    public void aBooleanOptionTakesOnlyTheBareWords() {
        engine.execute("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV REPLACE_INVALID_CHARACTERS = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV REPLACE_INVALID_CHARACTERS = false");
        assertEquals("SQL compilation error:\ninvalid value ['yes'] for parameter 'TRIM_SPACE'",
            refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV TRIM_SPACE = 'yes'"));
        assertEquals("SQL compilation error:\ninvalid value [1] for parameter 'EMPTY_FIELD_AS_NULL'",
            refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV EMPTY_FIELD_AS_NULL = 1"));
        assertEquals("SQL compilation error:\ninvalid value ['true'] for parameter 'SKIP_BYTE_ORDER_MARK'",
            refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV SKIP_BYTE_ORDER_MARK = 'true'"));
        assertEquals("SQL compilation error:\ninvalid value [yes] for parameter 'REPLACE_INVALID_CHARACTERS'",
            refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV REPLACE_INVALID_CHARACTERS = yes"));
        assertEquals("SQL compilation error:\ninvalid value [2] for parameter 'SKIP_BLANK_LINES'",
            refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV SKIP_BLANK_LINES = 2"));
        assertEquals("SQL compilation error:\ninvalid value ['yes'] for parameter 'TRIM_SPACE'",
            refusal("CREATE OR REPLACE FILE FORMAT ff TYPE = JSON TRIM_SPACE = 'yes'"));
        assertEquals("SQL compilation error:\ninvalid value ['yes'] for parameter 'REPLACE_INVALID_CHARACTERS'",
            refusal("ALTER FILE FORMAT ff SET REPLACE_INVALID_CHARACTERS = 'yes'"));
    }
}
