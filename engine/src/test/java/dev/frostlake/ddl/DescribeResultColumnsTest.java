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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DESCRIBE RESULT answers in DESCRIBE TABLE's thirteen columns and spells each result column's type as DESCRIBE
 * TABLE does, parameters and all, where it used to answer three columns with bare type names. A result column is
 * nullable and has no default and no key, whatever it was read from (all live-verified).
 */
public class DescribeResultColumnsTest extends BaseDatabaseTest {

    private static final List<String> DESCRIBE_COLUMNS = Arrays.asList("name", "type", "kind", "null?", "default",
        "primary key", "unique key", "check", "expression", "comment", "policy name", "privacy domain",
        "write default");

    private List<String> headers(final ResultSet rs) {
        final List<String> names = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            names.add(rs.getColumns().get(i).getName());
        }
        return names;
    }

    /** One row's cells as text, null-safe. */
    private List<String> cells(final Row row) {
        final List<String> texts = new ArrayList<>();
        for (final Object value : row.getValues()) {
            texts.add(value == null ? null : value.toString());
        }
        return texts;
    }

    @Test
    public void aResultIsDescribedInDescribeTablesColumnsWithItsTypesParameters() {
        engine.executeQuery("SELECT 'abc'::VARCHAR(5) v5, 1.5::NUMBER(10,2) n2,"
            + " '2024-01-01 10:00:00'::TIMESTAMP_NTZ(3) ts, TO_BINARY('abcd','HEX')::BINARY(4) b, 1::DOUBLE d,"
            + " 1::INT i, NULL::INT nn");
        final ResultSet described = engine.executeQuery("DESCRIBE RESULT LAST_QUERY_ID()");
        assertEquals(DESCRIBE_COLUMNS, headers(described));
        assertEquals(Arrays.asList("V5", "VARCHAR(5)", "COLUMN", "Y", null, "N", "N", null, null, null, null, null,
            null), cells(described.getRows().get(0)));
        assertEquals("NUMBER(10,2)", cell(described, soleRowWhere(described, "name", "N2"), "type"));
        assertEquals("TIMESTAMP_NTZ(3)", cell(described, soleRowWhere(described, "name", "TS"), "type"));
        assertEquals("BINARY(4)", cell(described, soleRowWhere(described, "name", "B"), "type"));
        assertEquals("FLOAT", cell(described, soleRowWhere(described, "name", "D"), "type"));
        assertEquals("NUMBER(38,0)", cell(described, soleRowWhere(described, "name", "I"), "type"));
        assertEquals("NUMBER(38,0)", cell(described, soleRowWhere(described, "name", "NN"), "type"));
    }

    @Test
    public void aNotNullColumnWithADefaultIsDescribedAsANullableResultColumn() {
        engine.execute("CREATE TABLE h (id NUMBER(38,0) NOT NULL IDENTITY START 1 INCREMENT 1 NOORDER,"
            + " created TIMESTAMP_LTZ(9) NOT NULL DEFAULT CURRENT_TIMESTAMP(), file_name VARCHAR, checksum VARCHAR)");
        engine.execute("INSERT INTO h (file_name) VALUES ('R__020_purpose.sql')");
        assertEquals("N", describeCell("h", "ID", "null?"));
        engine.executeQuery("SELECT id, created, file_name FROM h LIMIT 1");
        final ResultSet described = engine.executeQuery("DESCRIBE RESULT LAST_QUERY_ID()");
        assertEquals(3, described.getRows().size());
        assertEquals(Arrays.asList("ID", "NUMBER(38,0)", "COLUMN", "Y", null, "N", "N", null, null, null, null, null,
            null), cells(described.getRows().get(0)));
        assertEquals(Arrays.asList("CREATED", "TIMESTAMP_LTZ(9)", "COLUMN", "Y", null, "N", "N", null, null, null,
            null, null, null), cells(described.getRows().get(1)));
        assertEquals("VARCHAR(16777216)", cell(described, soleRowWhere(described, "name", "FILE_NAME"), "type"));
    }

    @Test
    public void aCollatedColumnCarriesItsCollationInTheTypeCell() {
        // Live-verified, as DESCRIBE TABLE puts a column's collation.
        engine.executeQuery("SELECT 'a' COLLATE 'en-ci' AS c, 'b'::VARCHAR(10) COLLATE 'en-ci' AS d, 'e' AS e");
        final ResultSet described = engine.executeQuery("DESCRIBE RESULT LAST_QUERY_ID()");
        assertEquals("VARCHAR(1) COLLATE 'en-ci'", cell(described, soleRowWhere(described, "name", "C"), "type"));
        assertEquals("VARCHAR(10) COLLATE 'en-ci'", cell(described, soleRowWhere(described, "name", "D"), "type"));
        assertEquals("VARCHAR(1)", cell(described, soleRowWhere(described, "name", "E"), "type"));
    }

    @Test
    public void anExpressionWiderThanAColumnReadsAtItsOwnWidth() {
        // A result is not stored, so its string is not stopped at a column's width (SqlTypeNames.resultMetadata):
        // live reads CAST(i AS VARCHAR) as VARCHAR(134217728) when selected, where a CTAS stores VARCHAR(16777216).
        engine.executeQuery("SELECT CAST(1 AS VARCHAR) AS w");
        final ResultSet described = engine.executeQuery("DESCRIBE RESULT LAST_QUERY_ID()");
        assertEquals("VARCHAR(134217728)", cell(described, soleRowWhere(described, "name", "W"), "type"));
    }

    @Test
    public void aResultScanOfTheDescriptionReadsItsCellsByName() {
        engine.executeQuery("SELECT 7 AS only_col");
        engine.executeQuery("DESCRIBE RESULT LAST_QUERY_ID()");
        final List<Row> rows = engine.executeQuery("SELECT \"name\", \"type\", \"kind\", \"null?\", \"primary key\""
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))").getRows();
        assertEquals(1, rows.size());
        assertEquals(Arrays.asList("ONLY_COL", "NUMBER(1,0)", "COLUMN", "Y", "N"), cells(rows.get(0)));
    }
}
