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

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numeric and timestamp columns of the SHOW listings are declared as the account declares them
 * (live-verified): sizes, ids and running totals are NUMBER(38,0), small counts — an argument count, a key's
 * position, a warehouse's clusters — NUMBER(9,0), and a listing's instants TIMESTAMP_LTZ(3). A table created over
 * a listing's RESULT_SCAN copies those types.
 */
public class ShowColumnTypesTest extends BaseJdbcTest {

    private static final String WIDE = "NUMBER 38 0";
    private static final String COUNT = "NUMBER 9 0";
    private static final String INSTANT = "TIMESTAMPLTZ 29 3";

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("CREATE TABLE sct_t (a INT PRIMARY KEY, b VARCHAR)");
        statement.execute("CREATE SEQUENCE sct_seq");
        statement.execute("CREATE STREAM sct_stream ON TABLE sct_t");
        statement.execute("CREATE FUNCTION sct_fn(x INT) RETURNS INT AS $$ x $$");
        statement.execute("SET sct_var = 5");
    }

    @Override
    protected void teardownTest() throws SQLException {
        statement.execute("UNSET sct_var");
    }

    /** One column's driver type name, precision and scale. */
    private String declared(final String show, final String column) throws SQLException {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(show)) {
            final ResultSetMetaData md = rs.getMetaData();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                if (md.getColumnLabel(i).equalsIgnoreCase(column)) {
                    return md.getColumnTypeName(i) + ' ' + md.getPrecision(i) + ' ' + md.getScale(i);
                }
            }
            return "no column " + column;
        }
    }

    private void assertDeclared(final String expected, final String show, final String... columns)
            throws SQLException {
        for (final String column : columns) {
            assertEquals(expected, declared(show, column), show + " " + column);
        }
    }

    @Test
    public void sizesIdsAndTotalsAreWideNumbers() throws SQLException {
        assertDeclared(WIDE, "SHOW TABLES", "rows", "bytes", "search_optimization_progress",
            "search_optimization_bytes");
        assertDeclared(WIDE, "SHOW OBJECTS", "rows", "bytes");
        assertDeclared(WIDE, "SHOW DYNAMIC TABLES", "rows", "bytes");
        assertDeclared(WIDE, "SHOW VARIABLES", "session_id");
        assertDeclared(WIDE, "SHOW TRANSACTIONS", "id", "session", "scope");
        assertDeclared(WIDE, "SHOW LOCKS IN ACCOUNT", "session", "transaction");
        assertDeclared(WIDE, "SHOW ROLES", "assigned_to_users", "granted_to_roles", "granted_roles");
        assertDeclared(WIDE, "SHOW DATABASE ROLES IN DATABASE test_db", "granted_to_roles",
            "granted_to_database_roles", "granted_database_roles");
        assertDeclared(WIDE, "SHOW SEQUENCES", "next_value", "interval");
        assertDeclared(WIDE, "DESCRIBE SEQUENCE sct_seq", "next_value", "interval");
    }

    @Test
    public void smallCountsAreNineDigitNumbers() throws SQLException {
        assertDeclared(COUNT, "SHOW USER FUNCTIONS", "min_num_arguments", "max_num_arguments");
        assertDeclared(COUNT, "SHOW FUNCTIONS LIKE 'ABS'", "min_num_arguments", "max_num_arguments");
        assertDeclared(COUNT, "SHOW PROCEDURES", "min_num_arguments", "max_num_arguments");
        assertDeclared(COUNT, "SHOW PRIMARY KEYS", "key_sequence");
        assertDeclared(COUNT, "SHOW UNIQUE KEYS", "key_sequence");
        assertDeclared(COUNT, "SHOW IMPORTED KEYS", "key_sequence");
        assertDeclared(COUNT, "SHOW WAREHOUSES", "min_cluster_count", "max_cluster_count", "started_clusters",
            "running", "queued", "auto_suspend", "query_acceleration_max_scale_factor", "actives", "pendings",
            "failed", "suspended", "query_throughput_multiplier");
        assertDeclared(COUNT, "SHOW NETWORK POLICIES", "entries_in_allowed_ip_list", "entries_in_blocked_ip_list",
            "entries_in_allowed_network_rules", "entries_in_blocked_network_rules");
        assertDeclared(COUNT, "SHOW NETWORK RULES", "entries_in_valuelist");
        assertDeclared(COUNT, "SHOW COMPUTE POOLS", "min_nodes", "max_nodes", "num_services", "num_jobs",
            "active_nodes", "idle_nodes", "target_nodes");
        assertDeclared(COUNT, "SHOW SERVICES", "current_instances", "target_instances", "min_ready_instances",
            "min_instances", "max_instances");
    }

    @Test
    public void aListingsInstantsKeepMilliseconds() throws SQLException {
        assertDeclared(INSTANT, "SHOW STREAMS", "created_on", "stale_after");
        assertDeclared(INSTANT, "SHOW DATABASES HISTORY", "created_on", "dropped_on");
        assertDeclared(INSTANT, "SHOW SCHEMAS HISTORY", "created_on", "dropped_on");
    }

    @Test
    public void aTableOverAListingCopiesItsTypes() throws SQLException {
        statement.execute("SHOW PRIMARY KEYS IN TABLE sct_t");
        statement.execute("CREATE TABLE sct_keys AS SELECT \"key_sequence\" AS key_sequence"
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        statement.execute("SHOW USER FUNCTIONS LIKE 'SCT_FN'");
        statement.execute("CREATE TABLE sct_counts AS SELECT \"min_num_arguments\" AS min_num_arguments"
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        statement.execute("SHOW TABLES LIKE 'SCT_T'");
        statement.execute("CREATE TABLE sct_sizes AS SELECT \"rows\" AS row_count, \"search_optimization_progress\""
            + " AS progress FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertEquals("NUMBER(9,0)", describedType("sct_keys", "KEY_SEQUENCE"));
        assertEquals("NUMBER(9,0)", describedType("sct_counts", "MIN_NUM_ARGUMENTS"));
        assertEquals("NUMBER(38,0)", describedType("sct_sizes", "ROW_COUNT"));
        assertEquals("NUMBER(38,0)", describedType("sct_sizes", "PROGRESS"));
    }

    private String describedType(final String table, final String column) throws SQLException {
        try (ResultSet rs = statement.executeQuery("DESCRIBE TABLE " + table)) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return rs.getString("type");
                }
            }
        }
        assertTrue(false, "no column " + column + " in " + table);
        return null;
    }
}
