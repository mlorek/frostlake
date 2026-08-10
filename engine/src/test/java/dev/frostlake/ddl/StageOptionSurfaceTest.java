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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The STAGE option surface, measured cell by cell against a real account: every internal option
 * (inline and named formats, DIRECTORY, both Snowflake encryptions, COPY_OPTIONS, TAG, COMMENT)
 * is accepted; a duplicated FILE_FORMAT refuses single-line naming the conflicting parameter; an
 * unknown option refuses as an invalid STAGE property; a bogus format TYPE refuses with the
 * invalid-value shape; cloud credentials without a location refuse {@code location not
 * specified}; non-Snowflake encryption on an internal stage refuses with the internal-stage
 * sentence; and ALTER supports SET FILE_FORMAT/COPY_OPTIONS (parenthesized), RENAME TO, and
 * refuses UNSET as an unsupported feature. DESC STAGE answers live's five-column property tree,
 * declared options overlaid on the per-type defaults. External-stage and cloud-validation cells
 * stay with the probes: the engine deliberately cannot reach a real bucket.
 */
public class StageOptionSurfaceTest extends BaseDatabaseTest {

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
    }

    /** One DESC STAGE cell, addressed by group and property. */
    private String descCell(final String stage, final String group, final String property,
                            final String column) {
        final ResultSet rs = engine.executeQuery("DESC STAGE " + stage);
        for (final Row row : rs.getRows()) {
            if (group.equals(String.valueOf(row.getValue(rs.getColumnIndex("parent_property"))))
                    && property.equals(String.valueOf(row.getValue(rs.getColumnIndex("property"))))) {
                return String.valueOf(row.getValue(rs.getColumnIndex(column)));
            }
        }
        return null;
    }

    @Test
    public void everyInternalOptionIsAccepted() {
        engine.execute("CREATE FILE FORMAT ff_named TYPE = CSV");
        engine.execute("CREATE TAG stage_tag");
        engine.execute("CREATE STAGE so_plain");
        engine.execute("CREATE STAGE so_fmt FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        engine.execute("CREATE STAGE so_named FILE_FORMAT = (FORMAT_NAME = 'ff_named')");
        engine.execute("CREATE STAGE so_named2 FILE_FORMAT = 'ff_named'");
        engine.execute("CREATE STAGE so_dir DIRECTORY = (ENABLE = TRUE)");
        engine.execute("CREATE STAGE so_sse ENCRYPTION = (TYPE = 'SNOWFLAKE_SSE')");
        engine.execute("CREATE STAGE so_full ENCRYPTION = (TYPE = 'SNOWFLAKE_FULL')");
        engine.execute("CREATE STAGE so_copy COPY_OPTIONS = (ON_ERROR = 'SKIP_FILE')");
        engine.execute("CREATE STAGE so_tag TAG (stage_tag = 'v')");
        engine.execute("CREATE TEMPORARY STAGE so_temp");
        assertEquals(1, engine.executeQuery("SHOW STAGES LIKE 'so_dir'").getRowCount());
    }

    @Test
    public void duplicateFileFormatRefusesNamingTheParameter() {
        assertEquals("SQL compilation error: conflicting values file format parameter 'TYPE'",
            refusal("CREATE STAGE so_dup FILE_FORMAT = (TYPE = CSV) FILE_FORMAT = (TYPE = JSON)")
                .getMessage());
    }

    @Test
    public void unknownOptionRefusesAsInvalidStageProperty() {
        assertEquals("SQL compilation error:\ninvalid property 'NO_SUCH_OPT' for 'STAGE'",
            refusal("CREATE STAGE so_bad NO_SUCH_OPT = 1").getMessage());
    }

    @Test
    public void bogusFormatTypeRefusesAsInvalidValue() {
        assertEquals("SQL compilation error:\ninvalid value ['BOGUS'] for parameter 'TYPE'",
            refusal("CREATE STAGE so_bogus FILE_FORMAT = (TYPE = 'BOGUS')").getMessage());
    }

    @Test
    public void credentialsWithoutALocationRefuse() {
        assertEquals("SQL compilation error:\ninvalid stage (SO_CRED): location not specified",
            refusal("CREATE STAGE so_cred CREDENTIALS = (AWS_KEY_ID='x' AWS_SECRET_KEY='y')")
                .getMessage());
    }

    @Test
    public void nonSnowflakeEncryptionOnAnInternalStageRefuses() {
        assertEquals("SQL compilation error:\nCannot set URL, credentials, or encryption key of"
                + " an internal or temporary stage.",
            refusal("CREATE STAGE so_enc ENCRYPTION = (TYPE = 'AWS_SSE_S3')").getMessage());
    }

    @Test
    public void alterSupportsFormatCopyOptionsAndRename() {
        engine.execute("CREATE STAGE so_alter");
        engine.execute("ALTER STAGE so_alter SET FILE_FORMAT = (TYPE = JSON)");
        assertEquals("JSON", descCell("so_alter", "STAGE_FILE_FORMAT", "TYPE", "property_value"));

        engine.execute("ALTER STAGE so_alter SET COPY_OPTIONS = (ON_ERROR = 'CONTINUE')");
        assertEquals("CONTINUE",
            descCell("so_alter", "STAGE_COPY_OPTIONS", "ON_ERROR", "property_value"));

        engine.execute("ALTER STAGE so_alter RENAME TO so_renamed");
        assertEquals(1, engine.executeQuery("SHOW STAGES LIKE 'so_renamed'").getRowCount());
        assertEquals(0, engine.executeQuery("SHOW STAGES LIKE 'so_alter'").getRowCount());
    }

    @Test
    public void alterUnsetIsAnUnsupportedFeature() {
        engine.execute("CREATE STAGE so_unset");
        assertEquals("Unsupported feature 'UNSET'.",
            refusal("ALTER STAGE so_unset UNSET COMMENT").getMessage());
    }

    @Test
    public void describeAnswersThePropertyTree() {
        engine.execute("CREATE STAGE so_desc FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 1)");
        assertEquals("CSV", descCell("so_desc", "STAGE_FILE_FORMAT", "TYPE", "property_value"));
        assertEquals("1", descCell("so_desc", "STAGE_FILE_FORMAT", "SKIP_HEADER", "property_value"));
        assertEquals("0", descCell("so_desc", "STAGE_FILE_FORMAT", "SKIP_HEADER", "property_default"));
        assertEquals("Integer", descCell("so_desc", "STAGE_FILE_FORMAT", "SKIP_HEADER", "property_type"));
        assertEquals("ABORT_STATEMENT",
            descCell("so_desc", "STAGE_COPY_OPTIONS", "ON_ERROR", "property_value"));
        assertEquals("", descCell("so_desc", "STAGE_LOCATION", "URL", "property_value"));
        assertEquals("false", descCell("so_desc", "DIRECTORY", "ENABLE", "property_value"));
        if (!isLiveSnowflake()) {
            // The full tree the engine renders — 24 format rows + 7 copy options + URL + 2
            // directory rows. A new release may append rows live, so the count stays embedded.
            assertEquals(34, engine.executeQuery("DESC STAGE so_desc").getRowCount());
        }
    }

    @Test
    public void showStagesReportsTheInternalRow() {
        engine.execute("CREATE STAGE so_show DIRECTORY = (ENABLE = TRUE)");
        final ResultSet rs = engine.executeQuery("SHOW STAGES LIKE 'so_show'");
        final Row row = rs.getRows().get(0);
        assertEquals("INTERNAL", String.valueOf(row.getValue(rs.getColumnIndex("type"))));
        assertEquals("N", String.valueOf(row.getValue(rs.getColumnIndex("has_credentials"))));
        assertEquals("N", String.valueOf(row.getValue(rs.getColumnIndex("has_encryption_key"))));
        assertEquals("Y", String.valueOf(row.getValue(rs.getColumnIndex("directory_enabled"))));
    }
}
