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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A comma may stand between two options of a parenthesized option list, and between two options of a CREATE FILE
 * FORMAT. The engine took such lists written with spaces alone and refused the comma.
 *
 * <p>On the account one comma between two options is read, and a comma before the first option, after the last or
 * beside another comma is a syntax error at the token that should have been an option. Every cell is live-verified.
 */
public class OptionListCommasTest extends BaseDatabaseTest {

    /** The statement's refusal on one line, or "ok". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "ok";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String syntax(final int position, final String token) {
        return "SQL compilation error:|syntax error line 1 at position " + position + " unexpected '" + token + "'.";
    }

    private String described(final String stage, final String property) {
        final ResultSet rs = engine.executeQuery("DESCRIBE STAGE " + stage);
        return cell(rs, soleRowWhere(rs, "property", property), "property_value");
    }

    @Test
    public void aCommaSeparatesTwoOptions() {
        assertEquals("ok", outcome("CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV, FIELD_DELIMITER = '|')"));
        assertEquals("|", described("pc", "FIELD_DELIMITER"));
        assertEquals("ok", outcome("CREATE OR REPLACE STAGE pc COPY_OPTIONS = (ON_ERROR = 'CONTINUE', FORCE = TRUE)"));
        assertEquals("CONTINUE", described("pc", "ON_ERROR"));
        assertEquals("ok", outcome("CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE, AUTO_REFRESH = FALSE)"));
        assertEquals("true", described("pc", "ENABLE"));
        assertEquals("ok", outcome("ALTER STAGE pc SET FILE_FORMAT = (TYPE = CSV, FIELD_DELIMITER = ';')"));
        assertEquals(";", described("pc", "FIELD_DELIMITER"));
        assertEquals("ok", outcome("ALTER STAGE pc SET COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE', FORCE = TRUE)"));
        assertEquals("SKIP_FILE", described("pc", "ON_ERROR"));
        assertEquals("ok", outcome("CREATE OR REPLACE FILE FORMAT pff TYPE = CSV, SKIP_HEADER = 1"));
        assertEquals("ok", outcome("CREATE OR REPLACE FILE FORMAT pff TYPE = CSV SKIP_HEADER = 1"));
    }

    @Test
    public void aCommaElsewhereIsASyntaxError() {
        assertEquals(syntax(54, ")"), outcome("CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE,)"));
        assertEquals(syntax(54, ","), outcome("CREATE OR REPLACE STAGE pc DIRECTORY = (ENABLE = TRUE,, AUTO_REFRESH = FALSE)"));
        assertEquals(syntax(53, ")"), outcome("CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV,)"));
        assertEquals(syntax(53, ","), outcome("CREATE OR REPLACE STAGE pc FILE_FORMAT = (TYPE = CSV,, FIELD_DELIMITER = '|')"));
        assertEquals(syntax(64, ")"), outcome("CREATE OR REPLACE STAGE pc ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE',)"));
    }
}
