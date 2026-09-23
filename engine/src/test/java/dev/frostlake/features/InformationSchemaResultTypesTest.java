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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The result types the INFORMATION_SCHEMA views declare, as a driver's metadata reads them (live-verified over
 * every view): a name, comment or definition is VARCHAR(134217728) — spelled bare VARCHAR by SYSTEM$TYPEOF — a
 * YES / NO flag VARCHAR(3), a standard placeholder filled with NULL VARCHAR(0), a creation instant
 * TIMESTAMP_LTZ(3), and a few narrower columns their own measured width. FLATTEN's and SPLIT_TO_TABLE's text
 * columns declare the same unbounded width, and a table built over any of them stores VARCHAR(16777216).
 */
public class InformationSchemaResultTypesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t1 (a INT PRIMARY KEY, b VARCHAR(10))");
        engine.execute("CREATE VIEW v1 AS SELECT a FROM t1");
        engine.execute("CREATE SEQUENCE s1");
        engine.execute("CREATE STAGE st1");
        engine.execute("CREATE FUNCTION f1(x INT) RETURNS INT AS 'x + 1'");
    }

    /** Each result column's type as a driver's metadata spells it. */
    private String types(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final ResultSetColumn column : rs.getColumns()) {
            out.append(out.length() > 0 ? ", " : "").append(spelled(column.getDataType()));
        }
        return out.toString();
    }

    private static String spelled(final DataType type) {
        if (type instanceof StringType) {
            return "VARCHAR(" + ((StringType) type).getMaxLength() + ")";
        }
        if (type instanceof NumericType) {
            return "NUMBER(" + ((NumericType) type).getPrecision() + "," + ((NumericType) type).getScale() + ")";
        }
        if (type instanceof DateTimeType) {
            return type.getName().toUpperCase() + "(" + ((DateTimeType) type).getPrecision() + ")";
        }
        return type.getName().toUpperCase();
    }

    private String row(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                out.append(i > 0 ? ", " : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    @Test
    public void tablesDeclaresTheUnboundedTextAndItsOwnFlags() {
        final String sql = "SELECT table_name, clustering_key, is_temporary, self_referencing_column_name, created, "
            + "last_ddl_by, row_count FROM INFORMATION_SCHEMA.TABLES WHERE table_name = 'T1'";
        assertEquals("VARCHAR(134217728), VARCHAR(134217728), VARCHAR(3), VARCHAR(0), TIMESTAMP_LTZ(3), "
            + "VARCHAR(134217728), NUMBER(38,0)", types(sql));
        assertEquals("VARCHAR[LOB], VARCHAR(134217728)[LOB], VARCHAR(3)[LOB], NULL[LOB]",
            row("SELECT SYSTEM$TYPEOF(table_name), SYSTEM$TYPEOF(clustering_key), SYSTEM$TYPEOF(is_temporary), "
                + "SYSTEM$TYPEOF(self_referencing_column_name) FROM INFORMATION_SCHEMA.TABLES WHERE table_name = 'T1'"));
        assertEquals("VARCHAR[LOB], VARCHAR(134217728)[LOB], VARCHAR[LOB], VARCHAR[LOB], VARCHAR(4)[LOB]",
            row("SELECT SYSTEM$TYPEOF(UPPER(table_name)), SYSTEM$TYPEOF(table_name || 'x'), "
                + "SYSTEM$TYPEOF(COALESCE(table_name, 'x')), SYSTEM$TYPEOF(IFF(TRUE, table_name, 'x')), "
                + "SYSTEM$TYPEOF(is_temporary || 'x') FROM INFORMATION_SCHEMA.TABLES WHERE table_name = 'T1'"));
    }

    @Test
    public void theOtherViewsDeclareTheirMeasuredTypes() {
        assertEquals("VARCHAR(134217728), NUMBER(2,0), VARCHAR(2), NUMBER(38,0), VARCHAR(0)",
            types("SELECT data_type, numeric_precision_radix, is_self_referencing, identity_start, udt_name "
                + "FROM INFORMATION_SCHEMA.COLUMNS WHERE table_name = 'T1'"));
        assertEquals("VARCHAR(4), VARCHAR(2), VARCHAR(134217728)",
            types("SELECT check_option, is_updatable, view_definition FROM INFORMATION_SCHEMA.VIEWS "
                + "WHERE table_name = 'V1'"));
        assertEquals("VARCHAR(134217728), VARCHAR(3), TIMESTAMP_LTZ(3)",
            types("SELECT constraint_type, enforced, created FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                + "WHERE table_name = 'T1'"));
        assertEquals("NUMBER(1,0), VARCHAR(20), VARCHAR(19), VARCHAR(2)",
            types("SELECT numeric_scale, minimum_value, maximum_value, cycle_option FROM INFORMATION_SCHEMA.SEQUENCES"));
        assertEquals("BOOLEAN, TIMESTAMP_LTZ(3)",
            types("SELECT directory_enabled, created FROM INFORMATION_SCHEMA.STAGES"));
        assertEquals("NUMBER(38,0), VARCHAR(3), TIMESTAMP_LTZ(3)",
            types("SELECT max_batch_rows, is_external, created FROM INFORMATION_SCHEMA.FUNCTIONS"));
    }

    @Test
    public void theViewsWithNoRowsDeclareTheirMeasuredTypes() {
        assertEquals("NUMBER(38,0), TIMESTAMP_LTZ(3), VARCHAR(134217728)",
            types("SELECT row_count, last_load_time, file_name FROM INFORMATION_SCHEMA.LOAD_HISTORY LIMIT 0"));
        assertEquals("VARIANT, VARCHAR(4), VARCHAR(5)",
            types("SELECT version, owner_role_type, is_service_class FROM INFORMATION_SCHEMA.CLASSES LIMIT 0"));
        assertEquals("29, BOOLEAN", engine.executeQuery("SELECT * FROM INFORMATION_SCHEMA.CORTEX_SEARCH_SERVICES LIMIT 0")
            .getColumns().size() + ", " + types("SELECT inherit_data_governance_policies "
                + "FROM INFORMATION_SCHEMA.CORTEX_SEARCH_SERVICES LIMIT 0"));
    }

    @Test
    public void tableFunctionTextDeclaresTheUnboundedWidthAndATableStoresTheDefault() {
        assertEquals("VARCHAR(134217728), VARCHAR(134217728)",
            types("SELECT key, path FROM TABLE(FLATTEN(input => PARSE_JSON('{\"a\": 1}')))"));
        assertEquals("VARCHAR(134217728)", types("SELECT value FROM TABLE(SPLIT_TO_TABLE('a,b', ','))"));
        engine.execute("CREATE TABLE c1 AS SELECT table_name, is_temporary FROM INFORMATION_SCHEMA.TABLES "
            + "WHERE table_name = 'T1'");
        engine.execute("CREATE TABLE c2 AS SELECT key FROM TABLE(FLATTEN(input => PARSE_JSON('{\"a\": 1}')))");
        assertEquals("C1, TABLE_NAME, 16777216 | C1, IS_TEMPORARY, 3 | C2, KEY, 16777216",
            row("SELECT table_name, column_name, character_maximum_length FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE table_name IN ('C1', 'C2') ORDER BY table_name, ordinal_position"));
    }

    @Test
    public void rowValuesFollowTheDeclaredTypes() {
        engine.execute("CREATE TABLE ti (a INT AUTOINCREMENT(10, 5), b INT IDENTITY)");
        assertEquals("A, 10, 5 | B, 1, 1",
            row("SELECT column_name, identity_start, identity_increment FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE table_name = 'TI' ORDER BY ordinal_position"));
        engine.execute("CREATE TABLE f1 (x INT REFERENCES t1 (a))");
        assertEquals("FULL", row("SELECT match_option FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS"));
        engine.execute("CREATE STAGE st2 DIRECTORY = (ENABLE = TRUE)");
        assertEquals("ST1, none | ST2, on",
            row("SELECT stage_name, IFF(directory_enabled IS NULL, 'none', IFF(directory_enabled, 'on', 'off')) "
                + "FROM INFORMATION_SCHEMA.STAGES ORDER BY stage_name"));
    }
}
