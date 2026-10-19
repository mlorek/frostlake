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

package dev.frostlake.stage;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ALTER STAGE … SET takes several properties at once. The engine took one, refused a second as a syntax error, took
 * any word for a property and let a URL be set on an internal stage.
 *
 * <p>On the account the properties follow one another with a comma between two of them or none, a repeated one
 * counts by its last value, and nothing is applied when one of them is refused. COMMENT takes NULL. A word that is
 * no stage property is refused by name, and a URL, credentials or an encryption key on an internal stage in one
 * sentence. IF EXISTS forgives a missing stage. Every cell is live-verified.
 */
public class AlterStageSetSeveralTest extends BaseDatabaseTest {

    private static final String INTERNAL_ONLY = "SQL compilation error:|Cannot set URL, credentials, or encryption key"
        + " of an internal or temporary stage.";

    @Override
    protected void setupTest() {
        engine.execute("CREATE STAGE ss");
    }

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

    /** SHOW STAGES' comment and directory flag for the stage. */
    private String shown() {
        final ResultSet rs = engine.executeQuery("SHOW STAGES LIKE 'SS' IN SCHEMA test_db.test_schema");
        final Row row = soleRowWhere(rs, "name", "SS");
        return cell(rs, row, "comment") + " " + cell(rs, row, "directory_enabled");
    }

    /** DESCRIBE STAGE's values for the named properties, in the order asked. */
    private List<String> described(final String... properties) {
        final ResultSet rs = engine.executeQuery("DESCRIBE STAGE ss");
        final List<String> out = new ArrayList<>();
        for (final String property : properties) {
            out.add(property + "=" + cell(rs, soleRowWhere(rs, "property", property), "property_value"));
        }
        return out;
    }

    @Test
    public void severalPropertiesAreSetAtOnce() {
        assertEquals("ok", outcome("ALTER STAGE ss SET COMMENT = 'c1', DIRECTORY = (ENABLE = TRUE)"));
        assertEquals("c1 Y", shown());
        assertEquals("ok", outcome("ALTER STAGE ss SET DIRECTORY = (ENABLE = FALSE) COMMENT = 'c2'"));
        assertEquals("c2 N", shown());
        assertEquals("ok", outcome("ALTER STAGE ss SET DIRECTORY = (ENABLE = TRUE) , COMMENT = 'c3'"));
        assertEquals("c3 Y", shown());
        assertEquals("ok", outcome("ALTER STAGE ss SET COPY_OPTIONS = (ON_ERROR = 'CONTINUE')"
            + " FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|') COMMENT = 'c4'"));
        assertEquals("c4 Y", shown());
        final List<String> expected = new ArrayList<>();
        expected.add("TYPE=CSV");
        expected.add("FIELD_DELIMITER=|");
        expected.add("ON_ERROR=CONTINUE");
        expected.add("ENABLE=true");
        assertEquals(expected, described("TYPE", "FIELD_DELIMITER", "ON_ERROR", "ENABLE"));
    }

    @Test
    public void aRepeatedPropertyCountsByItsLastValue() {
        assertEquals("ok", outcome("ALTER STAGE ss SET COMMENT = 'a' COMMENT = 'b'"));
        assertEquals("b N", shown());
        assertEquals("ok", outcome("ALTER STAGE ss SET DIRECTORY = (ENABLE = TRUE) DIRECTORY = (ENABLE = FALSE)"));
        assertEquals("b N", shown());
        assertEquals("ok", outcome("ALTER STAGE ss SET DIRECTORY = (ENABLE = FALSE ENABLE = TRUE)"));
        assertEquals("b Y", shown());
    }

    @Test
    public void commentTakesAStringOrNull() {
        engine.execute("ALTER STAGE ss SET COMMENT = 'kept'");
        assertEquals("SQL compilation error:|invalid value [5] for parameter 'COMMENT'",
            outcome("ALTER STAGE ss SET COMMENT = 5"));
        assertEquals("kept N", shown());
        assertEquals("ok", outcome("ALTER STAGE ss SET COMMENT = NULL"));
        assertEquals(" N", shown().replace("null", ""), "the comment is gone");
    }

    @Test
    public void aRefusedPropertyLeavesTheOthersUnset() {
        engine.execute("ALTER STAGE ss SET COMMENT = 'kept'");
        assertEquals("SQL compilation error:|invalid property 'FOO' for 'STAGE'", outcome("ALTER STAGE ss SET FOO = 'x'"));
        assertEquals("SQL compilation error:|invalid property 'FOO' for 'STAGE'",
            outcome("ALTER STAGE ss SET COMMENT = 'k' FOO = 'x'"));
        assertEquals("SQL compilation error:|invalid parameter 'FOO'",
            outcome("ALTER STAGE ss SET COMMENT = 'k' DIRECTORY = (ENABLE = TRUE FOO = 1)"));
        assertEquals("kept N", shown());
    }

    @Test
    public void anInternalStageTakesNoUrlCredentialsOrKey() {
        assertEquals(INTERNAL_ONLY, outcome("ALTER STAGE ss SET URL = 's3://fl-test-bucket/p/'"));
        assertEquals(INTERNAL_ONLY,
            outcome("ALTER STAGE ss SET CREDENTIALS = (AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b')"));
        assertEquals(INTERNAL_ONLY, outcome("ALTER STAGE ss SET ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')"));
        assertEquals(INTERNAL_ONLY, outcome("ALTER STAGE ss SET ENCRYPTION = (TYPE = 'AWS_SSE_KMS' KMS_KEY_ID = 'k')"));
        assertEquals(hinted("SQL compilation error:|Integration 'NOSUCH_INT' does not exist or not authorized."),
            outcome("ALTER STAGE ss SET STORAGE_INTEGRATION = nosuch_int"));
        assertEquals("SQL compilation error:|invalid value [NOPE] for parameter 'TYPE'",
            outcome("ALTER STAGE ss SET FILE_FORMAT = (TYPE = NOPE)"));
    }

    @Test
    public void theShapeIsTheParsers() {
        assertEquals(syntax(18, "<EOF>"), outcome("ALTER STAGE ss SET"));
        assertEquals(syntax(33, ","), outcome("ALTER STAGE ss SET COMMENT = 'a',, COMMENT = 'b'"));
        assertEquals(syntax(19, ","), outcome("ALTER STAGE ss SET , COMMENT = 'a'"));
        assertEquals(syntax(33, "<EOF>"), outcome("ALTER STAGE ss SET COMMENT = 'a',"));
        assertEquals("Unsupported feature 'UNSET'.", outcome("ALTER STAGE ss UNSET COMMENT"));
    }

    @Test
    public void ifExistsForgivesAMissingStage() {
        assertEquals("ok", outcome("ALTER STAGE IF EXISTS nosuch_ss SET COMMENT = 'c'"));
        assertEquals(hinted("SQL compilation error:|Stage 'TEST_DB.TEST_SCHEMA.NOSUCH_SS' does not exist or not"
            + " authorized."), outcome("ALTER STAGE nosuch_ss SET COMMENT = 'c'"));
        assertEquals("SQL compilation error:|invalid property 'FOO' for 'STAGE'",
            outcome("ALTER STAGE IF EXISTS ss SET FOO = 'x'"), "IF EXISTS forgives the stage's absence alone");
    }
}
