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

package dev.frostlake.types;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.jdbc.DirectConnection;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The APPROXIMATE numerics carry no precision and no scale. The engine keeps a nominal (38,9) inside
 * {@code NumericType.FLOAT}, and the question is which surfaces that pair is allowed to reach: none of
 * them. Live answers the same six-alias table on every surface —
 *
 * <pre>
 *   SHOW COLUMNS           {"type":"REAL","nullable":true}   — no numbers beside the family
 *   DESCRIBE TABLE         FLOAT
 *   INFORMATION_SCHEMA     data_type FLOAT, numeric_precision NULL, numeric_scale NULL
 *   GET_DDL                every alias written back as FLOAT
 * </pre>
 *
 * <p>and the JDBC driver reports precision 0 and scale 0 for such a column, where a NUMBER(10,2) beside
 * it reports 10 and 2. FLOAT, DOUBLE, REAL, FLOAT4, FLOAT8 and DOUBLE PRECISION are one type with six
 * spellings, so all six answer identically.
 */
public class ApproximateNumericMetadataTest extends BaseDatabaseTest {

    /** The six spellings of the approximate family, in the order the table declares them. */
    private static final String[] ALIASES = {"F", "D", "R", "F4", "F8", "DP"};

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE ft (f FLOAT, d DOUBLE, r REAL, f4 FLOAT4,"
            + " f8 FLOAT8, dp DOUBLE PRECISION, n NUMBER(10,2))");
    }

    /** SHOW COLUMNS prints the family alone — REAL with a nullability and nothing else. */
    @Test
    public void showColumnsPrintsTheFamilyWithNoNumbers() {
        final ResultSet columns = engine.executeQuery("SHOW COLUMNS IN TABLE ft");
        for (final String alias : ALIASES) {
            assertEquals("{\"type\":\"REAL\",\"nullable\":true}",
                cell(columns, soleRowWhere(columns, "column_name", alias), "data_type"), alias);
        }
        assertEquals("{\"type\":\"FIXED\",\"precision\":10,\"scale\":2,\"nullable\":true}",
            cell(columns, soleRowWhere(columns, "column_name", "N"), "data_type"),
            "a FIXED number does carry its pair, so the absence above is the family's own rule");
    }

    /** DESCRIBE spells the declared type, and for this family that is the bare word FLOAT. */
    @Test
    public void describeSpellsTheBareWord() {
        for (final String alias : ALIASES) {
            assertEquals("FLOAT", describeCell("ft", alias, "type"), alias);
        }
        assertEquals("NUMBER(10,2)", describeCell("ft", "N", "type"));
    }

    /** INFORMATION_SCHEMA leaves both numeric cells NULL, which is how it says "no pair". */
    @Test
    public void informationSchemaLeavesBothCellsNull() {
        final ResultSet rows = engine.executeQuery("""
            SELECT column_name AS c, data_type AS t,
                   COALESCE(TO_VARCHAR(numeric_precision), 'NULL') AS p,
                   COALESCE(TO_VARCHAR(numeric_scale), 'NULL') AS s
              FROM information_schema.columns
             WHERE table_name = 'FT'
             ORDER BY ordinal_position
            """);
        for (final String alias : ALIASES) {
            final Row row = soleRowWhere(rows, "c", alias);
            assertEquals("FLOAT", cell(rows, row, "t"), alias);
            assertEquals("NULL", cell(rows, row, "p"), alias);
            assertEquals("NULL", cell(rows, row, "s"), alias);
        }
        final Row fixedRow = soleRowWhere(rows, "c", "N");
        assertEquals("NUMBER", cell(rows, fixedRow, "t"));
        assertEquals("10", cell(rows, fixedRow, "p"));
        assertEquals("2", cell(rows, fixedRow, "s"));
    }

    /** GET_DDL writes every one of the six aliases back as FLOAT, unparameterised. */
    @Test
    public void getDdlWritesEveryAliasAsFloat() {
        final ResultSet ddl = engine.executeQuery("SELECT GET_DDL('TABLE', 'ft') AS g");
        ddl.next();
        final String text = String.valueOf(ddl.getValue("g"));
        for (final String alias : ALIASES) {
            assertTrue(text.contains(alias + " FLOAT,"), alias + " in: " + text);
        }
        assertTrue(text.contains("N NUMBER(10,2)"), text);
        assertTrue(!text.contains("FLOAT("), "no alias may be written with a pair: " + text);
    }

    /**
     * And over JDBC, where the nominal pair used to leak: an approximate column answers 0 and 0. The
     * assertion is engine-side, so it runs on the embedded engine only — the live account is measured
     * through its own driver, which is the source this expectation was taken from.
     */
    @Test
    public void theDriverReportsNoPrecisionOrScale() throws SQLException {
        if (isLiveSnowflake()) {
            return;
        }
        final Connection connection = new DirectConnection(engine);
        try {
            final Statement statement = connection.createStatement();
            final java.sql.ResultSet rs =
                statement.executeQuery("SELECT f, d, r, f4, f8, dp, n, f * 2 AS doubled FROM ft");
            final ResultSetMetaData meta = rs.getMetaData();
            for (int column = 1; column <= ALIASES.length; column++) {
                assertEquals(0, meta.getPrecision(column), ALIASES[column - 1]);
                assertEquals(0, meta.getScale(column), ALIASES[column - 1]);
            }
            assertEquals(10, meta.getPrecision(7), "the FIXED column still carries its pair");
            assertEquals(2, meta.getScale(7));
            assertEquals(0, meta.getPrecision(8), "a derived approximate is approximate too");
            assertEquals(0, meta.getScale(8));
            statement.close();
        } finally {
            connection.close();
        }
    }
}
