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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SYSTEM$TYPEOF answers a string of no width. Its result column is VARCHAR(134217728), as TYPEOF's is; typed again it
 * reads as a bare VARCHAR, and what carries it — UPPER, a conditional it leads, a NULL beside it — stays bare, while
 * a concatenation over it widens to the length nothing bounds. A table or a view built over it stores
 * VARCHAR(16777216). Every cell is live-verified.
 */
public class SystemTypeofResultTypeTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (n INT, v VARIANT)");
        engine.execute("INSERT INTO t SELECT 1, TO_VARIANT(1) UNION ALL SELECT 2, TO_VARIANT('x')");
    }

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    /** The declared type of the result's first column. */
    private String columnType(final String sql) {
        final DataType type = engine.executeQuery(sql).getColumns().get(0).getDataType();
        return type instanceof StringType ? "VARCHAR(" + ((StringType) type).getMaxLength() + ")" : String.valueOf(type);
    }

    private String columnsOf(final String table) {
        return answer("SELECT LISTAGG(column_name || ':' || data_type || ':'"
            + " || COALESCE(character_maximum_length::VARCHAR, 'null'), ' ')"
            + " WITHIN GROUP (ORDER BY ordinal_position) FROM information_schema.columns WHERE table_name = '"
            + table + "'");
    }

    @Test
    public void theResultColumnHasTheUnknownLength() {
        assertEquals("VARCHAR(134217728)", columnType("SELECT SYSTEM$TYPEOF(1)"));
        assertEquals("VARCHAR(134217728)", columnType("SELECT SYSTEM$TYPEOF(n) FROM t"));
        assertEquals("VARCHAR(134217728)", columnType("SELECT SYSTEM$TYPEOF(1) AS c"));
        assertEquals("VARCHAR(134217728)", columnType("SELECT IFF(TRUE, SYSTEM$TYPEOF(1), 'x') AS c"));
        assertEquals("VARCHAR(134217728)", columnType("SELECT TYPEOF(v) FROM t"));
    }

    @Test
    public void typedAgainItIsABareVarchar() {
        final String[][] cells = {
            {"SELECT SYSTEM$TYPEOF(SYSTEM$TYPEOF(1))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$TYPEOF(n)) FROM t", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(UPPER(SYSTEM$TYPEOF(1)))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, SYSTEM$TYPEOF(1), 'x'))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, SYSTEM$TYPEOF(1), NULL))", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(TYPEOF(v)) FROM t", "VARCHAR[LOB]"},
            {"SELECT SYSTEM$TYPEOF(SYSTEM$TYPEOF(1) || 'x')", "VARCHAR(134217728)[LOB]"},
        };
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aTableOrViewOverItStoresTheFullWidth() {
        engine.execute("CREATE TABLE ct AS SELECT SYSTEM$TYPEOF(1) AS c, TYPEOF(TO_VARIANT(1)) AS d,"
            + " SYSTEM$TYPEOF(1) || 'x' AS e");
        assertEquals("C:TEXT:16777216 D:TEXT:16777216 E:TEXT:16777216", columnsOf("CT"));
        engine.execute("CREATE VIEW vv AS SELECT SYSTEM$TYPEOF(n) AS c FROM t");
        assertEquals("C:TEXT:16777216", columnsOf("VV"));
    }
}
