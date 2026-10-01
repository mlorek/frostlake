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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The FILE FORMAT object surface, measured cell by cell on a real account: each TYPE accepts only
 * its own option tree (an option outside it refuses {@code Option X is not valid for file format
 * type Y.}); an unknown option name is an {@code invalid parameter}; bad values refuse with the
 * invalid-value shape — a negative SKIP_HEADER echoed UNQUOTED, a bogus COMPRESSION or TYPE
 * quoted; ALTER may set options and rename but never change the TYPE; DESC answers the type's
 * whole four-column property tree with declared options overlaid; and SHOW's format_options blob
 * carries the full tree with typed JSON values.
 */
public class FileFormatOptionSurfaceTest extends BaseDatabaseTest {

    private RuntimeException refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
    }

    private String descCell(final String format, final String property, final String column) {
        final ResultSet rs = engine.executeQuery("DESC FILE FORMAT " + format);
        for (final Row row : rs.getRows()) {
            if (property.equals(String.valueOf(row.getValue(rs.getColumnIndex("property"))))) {
                return String.valueOf(row.getValue(rs.getColumnIndex(column)));
            }
        }
        return null;
    }

    @Test
    public void everyTypeAcceptsItsOwnOptions() {
        engine.execute("CREATE FILE FORMAT ff_csv TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 2"
            + " NULL_IF = ('NULL', '\\\\N') COMPRESSION = GZIP");
        engine.execute("CREATE FILE FORMAT ff_json TYPE = JSON STRIP_OUTER_ARRAY = TRUE"
            + " ALLOW_DUPLICATE = FALSE");
        engine.execute("CREATE FILE FORMAT ff_parquet TYPE = PARQUET BINARY_AS_TEXT = FALSE");
        engine.execute("CREATE FILE FORMAT ff_xml TYPE = XML STRIP_OUTER_ELEMENT = TRUE");
        engine.execute("CREATE FILE FORMAT ff_avro TYPE = AVRO COMPRESSION = AUTO");
        engine.execute("CREATE FILE FORMAT ff_bare");
        assertEquals(1, engine.executeQuery("SHOW FILE FORMATS LIKE 'ff_json'").getRowCount());
    }

    @Test
    public void crossTypeOptionsRefuseNamingBoth() {
        assertEquals("SQL compilation error:\nOption STRIP_OUTER_ARRAY is not valid for file"
                + " format type CSV.",
            refusal("CREATE FILE FORMAT ff_x1 TYPE = CSV STRIP_OUTER_ARRAY = TRUE").getMessage());
        assertEquals("SQL compilation error:\nOption SKIP_HEADER is not valid for file format"
                + " type JSON.",
            refusal("CREATE FILE FORMAT ff_x2 TYPE = JSON SKIP_HEADER = 1").getMessage());
    }

    @Test
    public void badValuesRefuseWithTheInvalidValueShape() {
        // The negative integer is echoed UNQUOTED; string values keep their quotes.
        assertEquals("SQL compilation error:\ninvalid value [-1] for parameter 'SKIP_HEADER'",
            refusal("CREATE FILE FORMAT ff_v1 TYPE = CSV SKIP_HEADER = -1").getMessage());
        assertEquals("SQL compilation error:\ninvalid value ['BOGUS'] for parameter 'COMPRESSION'",
            refusal("CREATE FILE FORMAT ff_v2 TYPE = CSV COMPRESSION = 'BOGUS'").getMessage());
        assertEquals("SQL compilation error:\ninvalid value ['NOPE'] for parameter 'TYPE'",
            refusal("CREATE FILE FORMAT ff_v3 TYPE = 'NOPE'").getMessage());
    }

    @Test
    public void unknownOptionRefusesAsInvalidParameter() {
        assertEquals("SQL compilation error:\ninvalid parameter 'NO_SUCH'",
            refusal("CREATE FILE FORMAT ff_u TYPE = CSV NO_SUCH = 1").getMessage());
    }

    @Test
    public void alterSetsOptionsButNeverTheType() {
        engine.execute("CREATE FILE FORMAT ff_alter TYPE = CSV");
        engine.execute("ALTER FILE FORMAT ff_alter SET SKIP_HEADER = 5");
        assertEquals("5", descCell("ff_alter", "SKIP_HEADER", "property_value"));

        assertEquals("SQL compilation error:\nFile format type cannot be changed.",
            refusal("ALTER FILE FORMAT ff_alter SET TYPE = JSON").getMessage());

        engine.execute("ALTER FILE FORMAT ff_alter RENAME TO ff_renamed");
        assertEquals(1, engine.executeQuery("SHOW FILE FORMATS LIKE 'ff_renamed'").getRowCount());
    }

    @Test
    public void alteringAMissingFormatNamesItFullyQualified() {
        assertEquals(hinted("SQL compilation error:\nFile format 'TEST_DB.TEST_SCHEMA.NOSUCH_FF' does"
                + " not exist or not authorized."),
            refusal("ALTER FILE FORMAT nosuch_ff SET COMMENT = 'x'").getMessage());
    }

    @Test
    public void describeAnswersThePerTypeTree() {
        engine.execute("CREATE FILE FORMAT ff_desc TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 2");
        assertEquals("|", descCell("ff_desc", "FIELD_DELIMITER", "property_value"));
        assertEquals(",", descCell("ff_desc", "FIELD_DELIMITER", "property_default"));
        assertEquals("2", descCell("ff_desc", "SKIP_HEADER", "property_value"));
        assertEquals("Boolean", descCell("ff_desc", "MULTI_LINE", "property_type"));
        if (!isLiveSnowflake()) {
            assertEquals(24, engine.executeQuery("DESC FILE FORMAT ff_desc").getRowCount());
        }

        engine.execute("CREATE FILE FORMAT ff_djson TYPE = JSON");
        // The JSON tree's NULL_IF starts EMPTY while the default column still reads [\N].
        assertEquals("[]", descCell("ff_djson", "NULL_IF", "property_value"));
        assertEquals("false", descCell("ff_djson", "STRIP_OUTER_ARRAY", "property_value"));
        if (!isLiveSnowflake()) {
            assertEquals(17, engine.executeQuery("DESC FILE FORMAT ff_djson").getRowCount());
        }
    }

    @Test
    public void showCarriesTheTypedOptionsBlob() {
        engine.execute("CREATE FILE FORMAT ff_show TYPE = CSV FIELD_DELIMITER = '|' SKIP_HEADER = 5"
            + " NULL_IF = ('NULL', '\\\\N') COMPRESSION = GZIP");
        final ResultSet rs = engine.executeQuery("SHOW FILE FORMATS LIKE 'ff_show'");
        final Row row = rs.getRows().get(0);
        final String blob = String.valueOf(row.getValue(rs.getColumnIndex("format_options")));
        assertTrue(blob.contains("\"SKIP_HEADER\":5"), blob);
        assertTrue(blob.contains("\"PARSE_HEADER\":false"), blob);
        assertTrue(blob.contains("\"FILE_EXTENSION\":null"), blob);
        assertTrue(blob.contains("\"COMPRESSION\":\"GZIP\""), blob);
        assertTrue(blob.contains("\"NULL_IF\":[\"NULL\",\"\\\\N\"]")
            || blob.contains("\"NULL_IF\":[\"NULL\",\"\\\\\\\\N\"]"), blob);
        assertEquals("CSV", String.valueOf(row.getValue(rs.getColumnIndex("type"))));
    }
}
