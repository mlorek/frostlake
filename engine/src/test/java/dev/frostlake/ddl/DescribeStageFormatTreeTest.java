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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DESCRIBE STAGE lists the property tree of the stage's own format TYPE, each written value spelled as the account
 * spells it, and a stage that names a file format lists the name alone. A format's default TYPE reads CSV whatever
 * its own is, in DESCRIBE FILE FORMAT too, and ORC takes no COMPRESSION.
 */
public class DescribeStageFormatTreeTest extends BaseDatabaseTest {

    /** The STAGE_FILE_FORMAT rows of a stage, as property=value/default. */
    private List<String> formatRows(final String stage) {
        final ResultSet rs = engine.executeQuery("DESCRIBE STAGE " + stage);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            if ("STAGE_FILE_FORMAT".equals(cell(rs, row, "parent_property"))) {
                out.add(cell(rs, row, "property") + "=" + cell(rs, row, "property_value") + "/"
                    + cell(rs, row, "property_default"));
            }
        }
        return out;
    }

    private List<String> properties(final String stage) {
        final List<String> out = new ArrayList<>();
        for (final String row : formatRows(stage)) {
            out.add(row.substring(0, row.indexOf('=')));
        }
        return out;
    }

    private String rowOf(final String stage, final String property) {
        for (final String row : formatRows(stage)) {
            if (row.startsWith(property + "=")) {
                return row;
            }
        }
        return null;
    }

    @Test
    public void theTreeIsTheOneOfTheStagesOwnType() {
        engine.execute("CREATE OR REPLACE STAGE st_json FILE_FORMAT = (TYPE = JSON)");
        assertEquals(List.of("TYPE", "FILE_EXTENSION", "DATE_FORMAT", "TIME_FORMAT", "TIMESTAMP_FORMAT",
            "BINARY_FORMAT", "TRIM_SPACE", "NULL_IF", "COMPRESSION", "ENABLE_OCTAL", "ALLOW_DUPLICATE",
            "STRIP_OUTER_ARRAY", "STRIP_NULL_VALUES", "IGNORE_UTF8_ERRORS", "REPLACE_INVALID_CHARACTERS",
            "SKIP_BYTE_ORDER_MARK", "MULTI_LINE"), properties("st_json"));
        assertEquals("TYPE=JSON/CSV", rowOf("st_json", "TYPE"));
        assertEquals("NULL_IF=[]/[\\\\N]", rowOf("st_json", "NULL_IF"));
        engine.execute("CREATE OR REPLACE STAGE st_orc FILE_FORMAT = (TYPE = ORC)");
        assertEquals(List.of("TYPE=ORC/CSV", "TRIM_SPACE=false/false", "NULL_IF=[]/[\\\\N]",
            "REPLACE_INVALID_CHARACTERS=false/false"), formatRows("st_orc"));
        engine.execute("CREATE OR REPLACE STAGE st_default");
        assertEquals("TYPE=CSV/CSV", rowOf("st_default", "TYPE"));
        assertEquals("NULL_IF=[\\\\N]/[\\\\N]", rowOf("st_default", "NULL_IF"));
        assertEquals(24, formatRows("st_default").size());
        engine.execute("CREATE OR REPLACE STAGE st_lower FILE_FORMAT = (TYPE = json)");
        // The type is listed as the statement wrote it.
        assertEquals("TYPE=json/CSV", rowOf("st_lower", "TYPE"));
        assertEquals(17, formatRows("st_lower").size());
    }

    @Test
    public void aWrittenValueIsListedAsTheAccountSpellsIt() {
        engine.execute("CREATE OR REPLACE STAGE st_j FILE_FORMAT = (TYPE = JSON STRIP_OUTER_ARRAY = TRUE"
            + " NULL_IF = ('x', 'y') COMPRESSION = GZIP)");
        assertEquals("STRIP_OUTER_ARRAY=true/false", rowOf("st_j", "STRIP_OUTER_ARRAY"));
        assertEquals("NULL_IF=[x, y]/[\\\\N]", rowOf("st_j", "NULL_IF"));
        assertEquals("COMPRESSION=GZIP/AUTO", rowOf("st_j", "COMPRESSION"));
        engine.execute("CREATE OR REPLACE STAGE st_c FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 1"
            + " TRIM_SPACE = true FIELD_OPTIONALLY_ENCLOSED_BY = '\"' NULL_IF = ('\\\\N', 'NULL'))");
        assertEquals("FIELD_DELIMITER=|/,", rowOf("st_c", "FIELD_DELIMITER"));
        assertEquals("SKIP_HEADER=1/0", rowOf("st_c", "SKIP_HEADER"));
        assertEquals("TRIM_SPACE=true/false", rowOf("st_c", "TRIM_SPACE"));
        assertEquals("FIELD_OPTIONALLY_ENCLOSED_BY=\\\"/NONE", rowOf("st_c", "FIELD_OPTIONALLY_ENCLOSED_BY"));
        assertEquals("NULL_IF=[\\\\N, NULL]/[\\\\N]", rowOf("st_c", "NULL_IF"));
        engine.execute("CREATE OR REPLACE STAGE st_e FILE_FORMAT = (TYPE = CSV NULL_IF = (''))");
        assertEquals("NULL_IF=[]/[\\\\N]", rowOf("st_e", "NULL_IF"));
        engine.execute("CREATE OR REPLACE STAGE st_n FILE_FORMAT = (TYPE = CSV NULL_IF = ())");
        assertEquals("NULL_IF=[]/[\\\\N]", rowOf("st_n", "NULL_IF"));
    }

    @Test
    public void aNewFormatReplacesTheStagesOwnWhole() {
        engine.execute("CREATE OR REPLACE STAGE st_a FILE_FORMAT = (TYPE = JSON NULL_IF = ('x', 'y'))");
        engine.execute("ALTER STAGE st_a SET FILE_FORMAT = (TYPE = CSV SKIP_HEADER = 2)");
        assertEquals("TYPE=CSV/CSV", rowOf("st_a", "TYPE"));
        assertEquals("SKIP_HEADER=2/0", rowOf("st_a", "SKIP_HEADER"));
        assertEquals("NULL_IF=[\\\\N]/[\\\\N]", rowOf("st_a", "NULL_IF"));
        assertEquals(24, formatRows("st_a").size());
    }

    @Test
    public void aStageThatNamesAFormatListsTheNameAlone() {
        engine.execute("CREATE OR REPLACE FILE FORMAT jff TYPE = JSON STRIP_OUTER_ARRAY = TRUE");
        engine.execute("CREATE OR REPLACE FILE FORMAT \"lff\" TYPE = CSV");
        engine.execute("CREATE OR REPLACE STAGE st_p FILE_FORMAT = (FORMAT_NAME = 'jff')");
        assertEquals(List.of("FORMAT_NAME=jff/"), formatRows("st_p"));
        engine.execute("CREATE OR REPLACE STAGE st_b FILE_FORMAT = jff");
        assertEquals(List.of("FORMAT_NAME=jff/"), formatRows("st_b"));
        engine.execute("CREATE OR REPLACE STAGE st_s FILE_FORMAT = 'jff'");
        assertEquals(List.of("FORMAT_NAME=jff/"), formatRows("st_s"));
        engine.execute("CREATE OR REPLACE STAGE st_u FILE_FORMAT = (FORMAT_NAME = JFF)");
        assertEquals(List.of("FORMAT_NAME=JFF/"), formatRows("st_u"));
        engine.execute("CREATE OR REPLACE STAGE st_f FILE_FORMAT = (FORMAT_NAME = 'test_db.test_schema.jff')");
        assertEquals(List.of("FORMAT_NAME=test_db.test_schema.jff/"), formatRows("st_f"));
        engine.execute("CREATE OR REPLACE STAGE st_q FILE_FORMAT = (FORMAT_NAME = '\"lff\"')");
        assertEquals(List.of("FORMAT_NAME=\\\"lff\\\"/"), formatRows("st_q"));
    }

    @Test
    public void aFormatsDefaultTypeIsCsvAndOrcTakesNoCompression() {
        engine.execute("CREATE OR REPLACE FILE FORMAT off TYPE = ORC");
        final ResultSet orc = engine.executeQuery("DESCRIBE FILE FORMAT off");
        final List<String> rows = new ArrayList<>();
        for (final Row row : orc.getRows()) {
            rows.add(cell(orc, row, "property") + "=" + cell(orc, row, "property_value") + "/"
                + cell(orc, row, "property_default"));
        }
        assertEquals(List.of("TYPE=ORC/CSV", "TRIM_SPACE=false/false", "NULL_IF=[]/[\\\\N]",
            "REPLACE_INVALID_CHARACTERS=false/false"), rows);
        engine.execute("CREATE OR REPLACE FILE FORMAT pff TYPE = PARQUET");
        final ResultSet parquet = engine.executeQuery("DESCRIBE FILE FORMAT pff");
        assertEquals("CSV", cell(parquet, soleRowWhere(parquet, "property", "TYPE"), "property_default"));
        assertEquals("SQL compilation error:\nOption COMPRESSION is not valid for file format type ORC.",
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("CREATE OR REPLACE FILE FORMAT off2 TYPE = ORC COMPRESSION = AUTO");
                }
            }).getMessage());
    }
}
