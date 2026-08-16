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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The metadata surfaces carry declared types: a RESULT_SCAN of a SHOW or DESCRIBE declares what the
 * command declared — {@code created_on} TIMESTAMP_LTZ(3), the text columns VARCHAR(16777216), the
 * counts NUMBER(38,0) — and a scan of a SELECT keeps the query's own types; the INFORMATION_SCHEMA
 * views declare a name or a comment as the length-less VARCHAR, a YES / NO cell as VARCHAR(3), a
 * count as NUMBER(38,0) and a creation instant as TIMESTAMP_LTZ(3), with a few measured widths of
 * their own. Frostlake carried no static type through either surface.
 */
public class MetadataColumnTypingTest extends BaseDatabaseTest {

    @BeforeEach
    public void createObjects() {
        engine.execute("CREATE TABLE t1 (a NUMBER(10,2) NOT NULL COMMENT 'col', b VARCHAR(20), c DATE) COMMENT = 'tbl'");
        engine.execute("CREATE SEQUENCE s1 COMMENT = 'seq'");
        engine.execute("CREATE VIEW v1 AS SELECT a FROM t1");
    }

    private void assertTypes(final String sql, final String... expected) {
        final Row values = engine.executeQuery(sql).getRows().get(0);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], String.valueOf(values.getValue(i)), sql + " cell " + (i + 1));
        }
    }

    private void assertScanTypes(final String command, final String columns, final String... expected) {
        engine.execute(command);
        assertTypes("SELECT " + columns + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) LIMIT 1", expected);
    }

    @Test
    public void aResultScanDeclaresWhatTheCommandDeclared() {
        assertScanTypes("SHOW TABLES", "SYSTEM$TYPEOF(\"created_on\"), SYSTEM$TYPEOF(\"name\"), SYSTEM$TYPEOF(\"kind\"), "
            + "SYSTEM$TYPEOF(\"rows\"), SYSTEM$TYPEOF(\"bytes\"), SYSTEM$TYPEOF(\"comment\"), SYSTEM$TYPEOF(\"retention_time\")",
            "TIMESTAMP_LTZ(3)[SB8]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]", "NUMBER(38,0)[SB16]",
            "NUMBER(38,0)[SB16]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]");
        assertScanTypes("SHOW SEQUENCES", "SYSTEM$TYPEOF(\"created_on\"), SYSTEM$TYPEOF(\"name\"), SYSTEM$TYPEOF(\"next_value\"), "
            + "SYSTEM$TYPEOF(\"interval\"), SYSTEM$TYPEOF(\"ordered\"), SYSTEM$TYPEOF(\"comment\")",
            "TIMESTAMP_LTZ(3)[SB8]", "VARCHAR(16777216)[LOB]", "NUMBER(38,0)[SB16]", "NUMBER(38,0)[SB16]",
            "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]");
        assertScanTypes("DESCRIBE TABLE t1", "SYSTEM$TYPEOF(\"name\"), SYSTEM$TYPEOF(\"type\"), SYSTEM$TYPEOF(\"kind\"), "
            + "SYSTEM$TYPEOF(\"null?\"), SYSTEM$TYPEOF(\"default\"), SYSTEM$TYPEOF(\"primary key\"), SYSTEM$TYPEOF(\"comment\")",
            "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]",
            "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]");
        assertScanTypes("SHOW COLUMNS IN TABLE t1", "SYSTEM$TYPEOF(\"table_name\"), SYSTEM$TYPEOF(\"column_name\"), "
            + "SYSTEM$TYPEOF(\"data_type\"), SYSTEM$TYPEOF(\"null?\"), SYSTEM$TYPEOF(\"comment\"), SYSTEM$TYPEOF(\"autoincrement\")",
            "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]",
            "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]");
        assertScanTypes("SHOW SCHEMAS", "SYSTEM$TYPEOF(\"created_on\"), SYSTEM$TYPEOF(\"name\"), SYSTEM$TYPEOF(\"is_default\"), "
            + "SYSTEM$TYPEOF(\"retention_time\"), SYSTEM$TYPEOF(\"comment\")",
            "TIMESTAMP_LTZ(3)[SB8]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]",
            "VARCHAR(16777216)[LOB]");
        assertScanTypes("SHOW DATABASES", "SYSTEM$TYPEOF(\"created_on\"), SYSTEM$TYPEOF(\"name\"), "
            + "SYSTEM$TYPEOF(\"retention_time\"), SYSTEM$TYPEOF(\"comment\"), SYSTEM$TYPEOF(\"kind\")",
            "TIMESTAMP_LTZ(3)[SB8]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]",
            "VARCHAR(16777216)[LOB]");
        assertScanTypes("SHOW VIEWS", "SYSTEM$TYPEOF(\"created_on\"), SYSTEM$TYPEOF(\"name\"), SYSTEM$TYPEOF(\"text\"), "
            + "SYSTEM$TYPEOF(\"is_secure\")",
            "TIMESTAMP_LTZ(3)[SB8]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]", "VARCHAR(16777216)[LOB]");
        assertScanTypes("SELECT 1 AS x, 'ab' AS y, 1.5 AS z, CURRENT_DATE AS d",
            "SYSTEM$TYPEOF(x), SYSTEM$TYPEOF(y), SYSTEM$TYPEOF(z), SYSTEM$TYPEOF(d)",
            "NUMBER(1,0)[SB1]", "VARCHAR(2)[LOB]", "NUMBER(2,1)[SB1]", "DATE[SB4]");
    }

    @Test
    public void theInformationSchemaViewsDeclareTheirColumns() {
        assertTypes("SELECT SYSTEM$TYPEOF(table_name), SYSTEM$TYPEOF(table_type), SYSTEM$TYPEOF(row_count), SYSTEM$TYPEOF(bytes), "
            + "SYSTEM$TYPEOF(created), SYSTEM$TYPEOF(last_altered), SYSTEM$TYPEOF(comment), SYSTEM$TYPEOF(is_transient), "
            + "SYSTEM$TYPEOF(retention_time), SYSTEM$TYPEOF(table_catalog) FROM information_schema.tables WHERE table_name = 'T1'",
            "VARCHAR[LOB]", "VARCHAR(134217728)[LOB]", "NUMBER(38,0)[SB16]", "NUMBER(38,0)[SB16]", "TIMESTAMP_LTZ(3)[SB8]",
            "TIMESTAMP_LTZ(3)[SB8]", "VARCHAR[LOB]", "VARCHAR(134217728)[LOB]", "NUMBER(38,0)[SB16]", "VARCHAR[LOB]");
        assertTypes("SELECT SYSTEM$TYPEOF(column_name), SYSTEM$TYPEOF(ordinal_position), SYSTEM$TYPEOF(data_type), "
            + "SYSTEM$TYPEOF(is_nullable), SYSTEM$TYPEOF(numeric_precision), SYSTEM$TYPEOF(numeric_scale), "
            + "SYSTEM$TYPEOF(character_maximum_length), SYSTEM$TYPEOF(comment), SYSTEM$TYPEOF(column_default), "
            + "SYSTEM$TYPEOF(is_identity) FROM information_schema.columns WHERE table_name = 'T1' LIMIT 1",
            "VARCHAR[LOB]", "NUMBER(38,0)[SB16]", "VARCHAR[LOB]", "VARCHAR(3)[LOB]", "NUMBER(38,0)[SB16]", "NUMBER(38,0)[SB16]",
            "NUMBER(38,0)[SB16]", "VARCHAR[LOB]", "VARCHAR(134217728)[LOB]", "VARCHAR(3)[LOB]");
        assertTypes("SELECT SYSTEM$TYPEOF(schema_name), SYSTEM$TYPEOF(created), SYSTEM$TYPEOF(comment), SYSTEM$TYPEOF(retention_time), "
            + "SYSTEM$TYPEOF(is_managed_access), SYSTEM$TYPEOF(last_altered) FROM information_schema.schemata LIMIT 1",
            "VARCHAR[LOB]", "TIMESTAMP_LTZ(3)[SB8]", "VARCHAR[LOB]", "NUMBER(38,0)[SB16]", "VARCHAR(3)[LOB]", "TIMESTAMP_LTZ(3)[SB8]");
        assertTypes("SELECT SYSTEM$TYPEOF(database_name), SYSTEM$TYPEOF(created), SYSTEM$TYPEOF(comment), SYSTEM$TYPEOF(retention_time), "
            + "SYSTEM$TYPEOF(is_transient), SYSTEM$TYPEOF(type) FROM information_schema.databases LIMIT 1",
            "VARCHAR[LOB]", "TIMESTAMP_LTZ(3)[SB8]", "VARCHAR[LOB]", "NUMBER(38,0)[SB16]", "VARCHAR(3)[LOB]", "VARCHAR(19)[LOB]");
        assertTypes("SELECT SYSTEM$TYPEOF(table_name), SYSTEM$TYPEOF(view_definition), SYSTEM$TYPEOF(created), SYSTEM$TYPEOF(is_secure), "
            + "SYSTEM$TYPEOF(comment) FROM information_schema.views WHERE table_name = 'V1'",
            "VARCHAR[LOB]", "VARCHAR[LOB]", "TIMESTAMP_LTZ(3)[SB8]", "VARCHAR(3)[LOB]", "VARCHAR[LOB]");
        assertTypes("SELECT SYSTEM$TYPEOF(sequence_name), SYSTEM$TYPEOF(ordered), SYSTEM$TYPEOF(created), SYSTEM$TYPEOF(comment), "
            + "SYSTEM$TYPEOF(next_value), SYSTEM$TYPEOF(\"INCREMENT\"), SYSTEM$TYPEOF(numeric_precision), SYSTEM$TYPEOF(data_type) "
            + "FROM information_schema.sequences LIMIT 1",
            "VARCHAR[LOB]", "VARCHAR(3)[LOB]", "TIMESTAMP_LTZ(3)[SB8]", "VARCHAR[LOB]", "VARCHAR[LOB]", "VARCHAR[LOB]",
            "NUMBER(2,0)[SB1]", "VARCHAR(6)[LOB]");
    }
}
