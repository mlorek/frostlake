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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code DatabaseMetaData.getProcedures} / {@code getFunctions} — the two calls a JDBC tool makes to put
 * Procedures and Functions nodes under a schema. Frostlake threw {@code SQLFeatureNotSupportedException}
 * from both, which is why DBeaver showed only tables and views.
 *
 * <p>Shapes measured from Snowflake's own driver (3.20.0), and they are NOT the JDBC interface's:
 *
 * <pre>
 *   getProcedures  6 columns  PROCEDURE_CAT, PROCEDURE_SCHEM, PROCEDURE_NAME, REMARKS,
 *                             PROCEDURE_TYPE, SPECIFIC_NAME
 *                             — the spec documents NINE, three "reserved for future use"; Snowflake
 *                               omits them, so Frostlake omits them too
 *   getFunctions   6 columns  FUNCTION_CAT, FUNCTION_SCHEM, FUNCTION_NAME, REMARKS,
 *                             FUNCTION_TYPE, SPECIFIC_NAME
 *   REMARKS        the fixed strings 'user-defined procedure' / 'user-defined function'
 *   PROCEDURE_TYPE 2 (procedureReturnsResult) always — a Snowflake procedure returns a value
 *   FUNCTION_TYPE  1 for a scalar UDF, 2 for a UDTF
 * </pre>
 */
public class RoutineMetadataTest {

    private Connection connection;

    @AfterEach
    public void closeConnection() throws Exception {
        if (connection != null) {
            connection.close();
        }
    }

    /** A connection with one procedure, one scalar function and one table function. */
    private DatabaseMetaData populated(final String url, final String database) throws Exception {
        connection = DriverManager.getConnection(url);
        final Statement st = connection.createStatement();
        st.execute("CREATE DATABASE IF NOT EXISTS " + database);
        st.execute("USE DATABASE " + database);
        st.execute("USE SCHEMA public");
        st.execute("CREATE OR REPLACE PROCEDURE rm_proc(x INTEGER) RETURNS VARCHAR LANGUAGE SQL"
            + " AS $$BEGIN RETURN 'a'; END;$$");
        st.execute("CREATE OR REPLACE FUNCTION rm_scalar(x INTEGER) RETURNS INTEGER AS $$ x + 1 $$");
        st.execute("CREATE OR REPLACE FUNCTION rm_table(x INTEGER) RETURNS TABLE(a INTEGER) AS $$ SELECT 1 $$");
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

    /** The row for {@code name}, as "col=value" pieces, or null when absent. */
    private String rowFor(final ResultSet rs, final int nameColumn, final String name) throws Exception {
        while (rs.next()) {
            if (name.equalsIgnoreCase(rs.getString(nameColumn))) {
                final StringBuilder row = new StringBuilder();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                    row.append(rs.getString(i)).append('|');
                }
                return row.toString();
            }
        }
        return null;
    }

    @Test
    public void proceduresAreListedInLivesShape() throws Exception {
        final DatabaseMetaData md = populated("jdbc:frostlake:direct:routine_meta_p", "rm_p_db");
        try (ResultSet rs = md.getProcedures("RM_P_DB", "PUBLIC", "%")) {
            assertEquals(List.of("PROCEDURE_CAT", "PROCEDURE_SCHEM", "PROCEDURE_NAME",
                    "REMARKS", "PROCEDURE_TYPE", "SPECIFIC_NAME"), columnNames(rs),
                "six columns, as Snowflake's driver returns — not the interface's nine");
            final String row = rowFor(rs, 3, "RM_PROC");
            assertTrue(row != null && row.startsWith("RM_P_DB|PUBLIC|RM_PROC|user-defined procedure|2|"),
                String.valueOf(row));
        }
    }

    @Test
    public void functionsAreListedAndAUdtfIsMarked() throws Exception {
        final DatabaseMetaData md = populated("jdbc:frostlake:direct:routine_meta_f", "rm_f_db");
        try (ResultSet rs = md.getFunctions("RM_F_DB", "PUBLIC", "%")) {
            assertEquals(List.of("FUNCTION_CAT", "FUNCTION_SCHEM", "FUNCTION_NAME",
                    "REMARKS", "FUNCTION_TYPE", "SPECIFIC_NAME"), columnNames(rs));
        }
        // functionNoTable (1) for the scalar, functionReturnsTable (2) for the UDTF.
        try (ResultSet rs = md.getFunctions("RM_F_DB", "PUBLIC", "RM_SCALAR")) {
            assertEquals("RM_F_DB|PUBLIC|RM_SCALAR|user-defined function|1|RM_SCALAR|",
                rowFor(rs, 3, "RM_SCALAR"));
        }
        try (ResultSet rs = md.getFunctions("RM_F_DB", "PUBLIC", "RM_TABLE")) {
            assertEquals("RM_F_DB|PUBLIC|RM_TABLE|user-defined function|2|RM_TABLE|",
                rowFor(rs, 3, "RM_TABLE"));
        }
    }

    /** The name pattern is a LIKE, as every other metadata call here treats it. */
    @Test
    public void theNamePatternFilters() throws Exception {
        final DatabaseMetaData md = populated("jdbc:frostlake:direct:routine_meta_x", "rm_x_db");
        try (ResultSet rs = md.getFunctions("RM_X_DB", "PUBLIC", "RM\\_S%")) {
            assertTrue(rowFor(rs, 3, "RM_SCALAR") != null, "the scalar function should match RM_S%");
        }
        try (ResultSet rs = md.getProcedures("RM_X_DB", "PUBLIC", "NO_SUCH%")) {
            assertTrue(rowFor(rs, 3, "RM_PROC") == null, "a non-matching pattern returns nothing");
        }
    }

    /** A null catalog spans every database rather than failing. */
    @Test
    public void aNullCatalogSpansDatabases() throws Exception {
        final DatabaseMetaData md = populated("jdbc:frostlake:direct:routine_meta_n", "rm_n_db");
        try (ResultSet rs = md.getProcedures(null, "PUBLIC", "RM_PROC")) {
            assertTrue(rowFor(rs, 3, "RM_PROC") != null, "the procedure must be found without a catalog");
        }
    }

    /** An empty schema answers with no rows, not an error. */
    @Test
    public void anEmptySchemaListsNothing() throws Exception {
        connection = DriverManager.getConnection("jdbc:frostlake:direct:routine_meta_e");
        final Statement st = connection.createStatement();
        st.execute("CREATE DATABASE IF NOT EXISTS rm_e_db");
        st.execute("USE DATABASE rm_e_db");
        st.execute("USE SCHEMA public");
        try (ResultSet rs = connection.getMetaData().getProcedures("RM_E_DB", "PUBLIC", "%")) {
            assertTrue(rowFor(rs, 3, "ANYTHING") == null);
        }
    }
}
