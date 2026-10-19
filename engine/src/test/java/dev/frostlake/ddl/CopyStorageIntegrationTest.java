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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * STORAGE_INTEGRATION on a COPY and on a stage names an integration: it is looked up before the table, the stage
 * and the credentials the statement names, a name that reaches none is refused as any missing object is, a quoted
 * text is no name, and an unload into a stage takes no integration at all.
 */
public class CopyStorageIntegrationTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE load1 (a INT, b VARCHAR)");
        engine.execute("CREATE OR REPLACE STAGE st_default");
    }

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }, sql).getMessage();
    }

    private String missing(final String spelled) {
        return hinted("SQL compilation error:\nIntegration '" + spelled + "' does not exist or not authorized.");
    }

    @Test
    public void aNameThatReachesNoIntegrationIsRefused() {
        assertEquals(missing("SI1"),
            refusalOf("COPY INTO load1 FROM @%load1/data1/ STORAGE_INTEGRATION = si1 FILES = ('t1.csv')"));
        assertEquals(missing("NOSUCH_SI"), refusalOf("COPY INTO load1 FROM @st_default STORAGE_INTEGRATION = nosuch_si"));
        assertEquals(missing("\"si lower\""),
            refusalOf("COPY INTO load1 FROM @st_default STORAGE_INTEGRATION = \"si lower\""));
        assertEquals(missing("\"5\""), refusalOf("COPY INTO load1 FROM @st_default STORAGE_INTEGRATION = 5"));
        assertEquals(missing("A1"),
            refusalOf("COPY INTO load1 FROM @st_default STORAGE_INTEGRATION = a1 STORAGE_INTEGRATION = a2"));
        assertEquals(missing("NOSUCH_SI"),
            refusalOf("COPY INTO load1 FROM (SELECT $1, $2 FROM @st_default) STORAGE_INTEGRATION = nosuch_si"));
        assertEquals(missing("NOSUCH_SI"),
            refusalOf("COPY INTO load1 FROM @st_default PATTERN = '.*' STORAGE_INTEGRATION = nosuch_si"));
    }

    @Test
    public void itIsLookedUpBeforeWhatElseTheStatementNames() {
        assertEquals(missing("NOSUCH_SI"), refusalOf("COPY INTO nosuch_t FROM @st_default STORAGE_INTEGRATION = nosuch_si"));
        assertEquals(missing("NOSUCH_SI"), refusalOf("COPY INTO load1 FROM @nosuch_stage STORAGE_INTEGRATION = nosuch_si"));
        assertEquals(missing("NOSUCH_SI"), refusalOf("COPY INTO load1 FROM @st_default"
            + " CREDENTIALS = (AWS_KEY_ID = 'a' AWS_SECRET_KEY = 'b') STORAGE_INTEGRATION = nosuch_si"));
        // Another option's own value is judged first, wherever it stands.
        assertEquals("SQL compilation error:\ninvalid value [bogus] for parameter 'ON_ERROR'",
            refusalOf("COPY INTO load1 FROM @st_default STORAGE_INTEGRATION = nosuch_si ON_ERROR = bogus"));
        assertEquals("SQL compilation error:\ninvalid value [bogus] for parameter 'ON_ERROR'",
            refusalOf("COPY INTO load1 FROM @st_default ON_ERROR = bogus STORAGE_INTEGRATION = nosuch_si"));
    }

    @Test
    public void aQuotedTextIsNoNameAndAnUnloadTakesNone() {
        assertEquals("SQL compilation error: error line 1 at position 55\nInvalid value ''si1'' for property"
            + " 'STORAGE_INTEGRATION'. String literal identifier is unsupported for this property. Please use an"
            + " unquoted or double-quoted identifier.",
            refusalOf("COPY INTO load1 FROM @st_default STORAGE_INTEGRATION = 'si1'"));
        assertEquals("SQL compilation error: error line 1 at position 73\nInvalid value ''si1'' for property"
            + " 'STORAGE_INTEGRATION'. String literal identifier is unsupported for this property. Please use an"
            + " unquoted or double-quoted identifier.",
            refusalOf("COPY INTO load1 FROM @st_default FILES = ('a.csv') STORAGE_INTEGRATION = 'si1'"));
        assertEquals("SQL compilation error: \n COPY statements targeting a stage do not support the"
            + " STORAGE_INTEGRATION property.",
            refusalOf("COPY INTO @st_default/out FROM load1 STORAGE_INTEGRATION = nosuch_si"));
        assertEquals("Copy executed with 0 files processed.",
            String.valueOf(engine.executeQuery("COPY INTO load1 FROM @st_default").getRows().get(0).getValue(0)));
    }

    @Test
    public void aStageLooksItsIntegrationUpToo() {
        assertEquals(missing("NOSUCH_SI"), refusalOf("CREATE OR REPLACE STAGE st_si"
            + " URL = 's3://nosuch-fl-bucket/p/' STORAGE_INTEGRATION = nosuch_si"));
        assertEquals(missing("NOSUCH_SI"), refusalOf("CREATE OR REPLACE STAGE st_si3 STORAGE_INTEGRATION = nosuch_si"));
        assertEquals(missing("NOSUCH_SI"), refusalOf("ALTER STAGE st_default SET STORAGE_INTEGRATION = nosuch_si"));
    }

    @Test
    public void anIntegrationOfAnotherKindIsNoStorageIntegration() {
        if (isLiveSnowflake()) {
            // An integration is the account's to create; the sentence was read off one the account already holds.
            return;
        }
        engine.execute("CREATE OR REPLACE NOTIFICATION INTEGRATION ni_mail TYPE = EMAIL ENABLED = TRUE");
        assertEquals("SQL compilation error:\nIntegration 'NI_MAIL' is not a storage integration of type EXTERNAL_STAGE.",
            refusalOf("COPY INTO load1 FROM @st_default STORAGE_INTEGRATION = ni_mail"));
        assertEquals("SQL compilation error:\nIntegration 'NI_MAIL' is not a storage integration of type EXTERNAL_STAGE.",
            refusalOf("CREATE OR REPLACE STAGE st_si2 URL = 's3://nosuch-fl-bucket/p/' STORAGE_INTEGRATION = ni_mail"));
    }
}
