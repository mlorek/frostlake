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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A DYNAMIC table is a table that keeps itself up to date, so SHOW TABLES carries it — with
 * {@code is_dynamic} Y and the kind its transience gives it, in name order among the rest. Frostlake
 * listed it in SHOW OBJECTS and SHOW DYNAMIC TABLES but left it out of SHOW TABLES entirely.
 *
 * <p>The two listings spell transience differently, which is the reason to pin both here: SHOW TABLES
 * says TRANSIENT and SHOW OBJECTS says TABLE, for a dynamic table exactly as for a plain one.
 */
public class ShowTablesListsDynamicTablesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS COMPUTE_WH");
        engine.execute("CREATE OR REPLACE TABLE dbase (a INT)");
        engine.execute("INSERT INTO dbase VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE DYNAMIC TABLE dtp TARGET_LAG = '1 minute'"
            + " WAREHOUSE = COMPUTE_WH AS SELECT a FROM dbase");
        engine.execute("CREATE OR REPLACE TRANSIENT DYNAMIC TABLE dtt TARGET_LAG = '1 minute'"
            + " WAREHOUSE = COMPUTE_WH AS SELECT a FROM dbase");
    }

    /** Every row of a listing, as "c1, c2 | c1, c2". */
    private String rowsOf(final String sql, final String... columns) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        while (rs.next()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int c = 0; c < columns.length; c++) {
                if (c > 0) {
                    out.append(", ");
                }
                out.append(String.valueOf(rs.getValue(columns[c])));
            }
        }
        return out.toString();
    }

    /** SHOW TABLES carries both dynamic tables, in name order beside the plain one. */
    @Test
    public void showTablesCarriesADynamicTable() {
        assertEquals("DBASE, TABLE, N | DTP, TABLE, Y | DTT, TRANSIENT, Y",
            rowsOf("SHOW TABLES", "name", "kind", "is_dynamic"));
    }

    /** And its cells read as a table's do — change tracking is ON, because the refresh reads it. */
    @Test
    public void itsCellsReadAsATablesDo() {
        assertEquals("DTP, TABLE, , ON, OFF, Y, N, N, ACCOUNTADMIN, 1, , N, N",
            rowsOf("SHOW TABLES LIKE 'dtp'", "name", "kind", "cluster_by", "change_tracking",
                "automatic_clustering", "is_dynamic", "is_iceberg", "is_hybrid", "owner",
                "retention_time", "comment", "is_external", "enable_schema_evolution"));
        assertEquals("DTT, TRANSIENT, Y",
            rowsOf("SHOW TABLES LIKE 'dtt'", "name", "kind", "is_dynamic"));
    }

    /** SHOW OBJECTS spells a transient dynamic table TABLE, as it does a transient table. */
    @Test
    public void showObjectsSpellsTransienceAway() {
        assertEquals("DBASE, TABLE, N | DTP, TABLE, Y | DTT, TABLE, Y",
            rowsOf("SHOW OBJECTS", "name", "kind", "is_dynamic"));
    }

    /** The listings that were already right stay right. */
    @Test
    public void theOtherListingsAreUnchanged() {
        assertEquals("DTP | DTT", rowsOf("SHOW DYNAMIC TABLES", "name"));
        assertEquals("DTP, BASE TABLE, NO | DTT, BASE TABLE, YES",
            rowsOf("SELECT table_name, table_type, is_transient FROM information_schema.tables"
                + " WHERE table_name IN ('DTP', 'DTT') ORDER BY table_name",
                "TABLE_NAME", "TABLE_TYPE", "IS_TRANSIENT"));
    }
}
