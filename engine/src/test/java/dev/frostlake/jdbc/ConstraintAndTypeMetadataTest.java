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

package dev.frostlake.jdbc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The foreign-key, type-info and UDT metadata calls — all measured against Snowflake's own driver
 * (3.20.0) rather than read off the JDBC interface, because live and the spec disagree in places and
 * live is what a client will be compared against.
 *
 * <pre>
 *   getImportedKeys / getExportedKeys / getCrossReference   14 columns, one shape, three filters
 *     KEY_SEQ 1, UPDATE_RULE 3, DELETE_RULE 3, DEFERRABILITY 7
 *     PK_NAME is the generated SYS_CONSTRAINT_&lt;uuid&gt;, FK_NAME the user's constraint name
 *   getTypeInfo                                            18 columns, EIGHT rows
 *   getUDTs                                                 7 columns, no rows
 * </pre>
 */
public class ConstraintAndTypeMetadataTest {

    private Connection connection;

    @AfterEach
    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    /** parent(id) referenced by child(pid) through the named constraint FK_CP. */
    private DatabaseMetaData related(final String url, final String database) throws Exception {
        connection = DriverManager.getConnection(url);
        final Statement st = connection.createStatement();
        st.execute("CREATE DATABASE IF NOT EXISTS " + database);
        st.execute("USE DATABASE " + database);
        st.execute("USE SCHEMA public");
        st.execute("CREATE OR REPLACE TABLE parent (id INTEGER PRIMARY KEY, label VARCHAR)");
        st.execute("CREATE OR REPLACE TABLE child (cid INTEGER PRIMARY KEY, pid INTEGER,"
            + " CONSTRAINT fk_cp FOREIGN KEY (pid) REFERENCES parent(id))");
        return connection.getMetaData();
    }

    private List<String> columnNames(final ResultSet rs) throws Exception {
        final ResultSetMetaData meta = rs.getMetaData();
        final List<String> names = new ArrayList<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            names.add(meta.getColumnName(i));
        }
        return names;
    }

    private static final List<String> FK_COLUMNS = List.of(
        "PKTABLE_CAT", "PKTABLE_SCHEM", "PKTABLE_NAME", "PKCOLUMN_NAME",
        "FKTABLE_CAT", "FKTABLE_SCHEM", "FKTABLE_NAME", "FKCOLUMN_NAME",
        "KEY_SEQ", "UPDATE_RULE", "DELETE_RULE", "FK_NAME", "PK_NAME", "DEFERRABILITY");

    /** Asserts the single expected relationship row, whichever call produced it. */
    private void assertTheRelationship(final ResultSet rs) throws Exception {
        assertEquals(FK_COLUMNS, columnNames(rs));
        assertTrue(rs.next(), "the relationship must be reported");
        assertEquals("PARENT", rs.getString("PKTABLE_NAME"));
        assertEquals("ID", rs.getString("PKCOLUMN_NAME"));
        assertEquals("CHILD", rs.getString("FKTABLE_NAME"));
        assertEquals("PID", rs.getString("FKCOLUMN_NAME"));
        assertEquals(1, rs.getInt("KEY_SEQ"));
        assertEquals(3, rs.getInt("UPDATE_RULE"), "importedKeyNoAction");
        assertEquals(3, rs.getInt("DELETE_RULE"), "importedKeyNoAction");
        assertEquals(7, rs.getInt("DEFERRABILITY"), "importedKeyNotDeferrable");
        assertEquals("FK_CP", rs.getString("FK_NAME"));
        assertTrue(String.valueOf(rs.getString("PK_NAME")).startsWith("SYS_CONSTRAINT_"),
            rs.getString("PK_NAME"));
        // The catalog is answerable only because SHOW IMPORTED KEYS now carries both database names.
        assertEquals("FK_DB1", rs.getString("PKTABLE_CAT"));
        assertEquals("FK_DB1", rs.getString("FKTABLE_CAT"));
    }

    @Test
    public void allThreeForeignKeyCallsAgree() throws Exception {
        final DatabaseMetaData md = related("jdbc:frostlake:direct:fk_meta_1", "fk_db1");
        try (ResultSet rs = md.getImportedKeys("FK_DB1", "PUBLIC", "CHILD")) {
            assertTheRelationship(rs);
        }
        try (ResultSet rs = md.getExportedKeys("FK_DB1", "PUBLIC", "PARENT")) {
            assertTheRelationship(rs);
        }
        try (ResultSet rs = md.getCrossReference("FK_DB1", "PUBLIC", "PARENT",
                "FK_DB1", "PUBLIC", "CHILD")) {
            assertTheRelationship(rs);
        }
    }

    /** Each call filters its own end: the parent imports nothing, the child exports nothing. */
    @Test
    public void eachCallFiltersItsOwnEnd() throws Exception {
        final DatabaseMetaData md = related("jdbc:frostlake:direct:fk_meta_2", "fk_db1");
        try (ResultSet rs = md.getImportedKeys("FK_DB1", "PUBLIC", "PARENT")) {
            assertFalse(rs.next(), "PARENT has no outbound foreign key");
        }
        try (ResultSet rs = md.getExportedKeys("FK_DB1", "PUBLIC", "CHILD")) {
            assertFalse(rs.next(), "nothing references CHILD");
        }
    }

    /** Eight rows, copied from live — including the cells that look wrong and are not ours to fix. */
    @Test
    public void typeInfoIsLivesEightRows() throws Exception {
        connection = DriverManager.getConnection("jdbc:frostlake:direct:type_info");
        try (ResultSet rs = connection.getMetaData().getTypeInfo()) {
            assertEquals(18, rs.getMetaData().getColumnCount());
            final List<String> seen = new ArrayList<>();
            while (rs.next()) {
                seen.add(rs.getString("TYPE_NAME") + ":" + rs.getInt("DATA_TYPE")
                    + ":" + rs.getInt("PRECISION") + ":" + rs.getInt("MAXIMUM_SCALE"));
                // Live reports these identically on every row, VARCHAR and DATE included.
                assertFalse(rs.getBoolean("CASE_SENSITIVE"), rs.getString("TYPE_NAME"));
                assertTrue(rs.getBoolean("AUTO_INCREMENT"), rs.getString("TYPE_NAME"));
                assertEquals(3, rs.getInt("SEARCHABLE"));
                assertEquals(-1, rs.getInt("NUM_PREC_RADIX"));
            }
            assertEquals(List.of(
                "NUMBER:3:38:37", "INTEGER:4:38:0", "DOUBLE:8:38:37", "VARCHAR:12:-1:-1",
                "DATE:91:-1:-1", "TIME:92:-1:-1", "TIMESTAMP:93:-1:-1", "BOOLEAN:16:-1:-1"), seen);
        }
    }

    /** Live answers getUDTs with the right columns and nothing in them, rather than refusing. */
    @Test
    public void udtsAreEmptyRatherThanUnsupported() throws Exception {
        connection = DriverManager.getConnection("jdbc:frostlake:direct:udts");
        try (ResultSet rs = connection.getMetaData().getUDTs(null, null, "%", null)) {
            assertEquals(List.of("TYPE_CAT", "TYPE_SCHEM", "TYPE_NAME", "CLASS_NAME",
                "DATA_TYPE", "REMARKS", "BASE_TYPE"), columnNames(rs));
            assertFalse(rs.next(), "Snowflake has no SQL user-defined types to list");
        }
    }
}
