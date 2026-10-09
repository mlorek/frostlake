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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SHOW PARAMETERS IN TABLE lists the parameters a table can set on itself, each with the value in force and the
 * level it comes from: the table's own (TABLE), its schema's (SCHEMA), its database's (DATABASE) or the account
 * default (empty). The engine listed the session's parameters instead, and read a bare name only: a qualified table
 * or task was a syntax error.
 *
 * <p>Live-verified: the table and the task are named bare, qualified, quoted or through IDENTIFIER(); FOR reads as
 * IN does; LIKE is a pattern, not a substring; a STREAM keyword reads as TABLE does, and the object decides, a table
 * listing its parameters while a view and a stream are found and list none; a missing table is named as it was
 * written.
 */
public class ShowParametersInTableTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE pt (id INTEGER) DATA_RETENTION_TIME_IN_DAYS = 0");
        engine.execute("CREATE TABLE lt (id INTEGER, s STRING) MAX_DATA_EXTENSION_TIME_IN_DAYS = 5"
            + " DEFAULT_DDL_COLLATION = 'en-ci'");
        engine.execute("CREATE VIEW pv AS SELECT id FROM pt");
        engine.execute("CREATE STREAM pst ON TABLE pt");
        engine.execute("CREATE TASK ptask SCHEDULE = '60 MINUTE' AS SELECT 1");
    }

    /** Each row of a SHOW PARAMETERS as {@code key|value|default|level|type}, in key order. */
    private List<String> listed(final String show) {
        final ResultSet result = engine.executeQuery(show);
        final List<String> rows = new ArrayList<>();
        for (final Row row : result.getRows()) {
            rows.add(row.getValue(result.getColumnIndex("key")) + "|" + row.getValue(result.getColumnIndex("value"))
                + "|" + row.getValue(result.getColumnIndex("default")) + "|"
                + row.getValue(result.getColumnIndex("level")) + "|" + row.getValue(result.getColumnIndex("type")));
        }
        Collections.sort(rows);
        return rows;
    }

    /** A refusal's own sentence: the message up to the privilege hint, which names the session's role. */
    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            final String message = String.valueOf(refused.getMessage()).replace('\n', ' ');
            final int hint = message.indexOf(" Your primary role");
            return hint < 0 ? message : message.substring(0, hint);
        }
    }

    @Test
    public void aTableListsItsOwnParameters() {
        final List<String> rows = listed("SHOW PARAMETERS IN TABLE pt");
        // An account grows parameters over time, so the rows measured are required and others are let be.
        for (final String measured : new String[] {
            "ALLOW_ROW_TIMESTAMP|false|false||BOOLEAN", "ALLOW_V3_EXPRESSION_DEFAULTS|false|false||BOOLEAN",
            "CATALOG||||STRING", "CATALOG_SYNC||||STRING", "DATA_METRIC_SCHEDULE|60 MINUTES|60 MINUTES||STRING",
            "DATA_RETENTION_TIME_IN_DAYS|0|1|TABLE|NUMBER", "DEFAULT_DDL_COLLATION||||STRING",
            "ENABLE_DATA_COMPACTION|true|true||BOOLEAN", "ENABLE_ICEBERG_MERGE_ON_READ|true|true||BOOLEAN",
            "EXTERNAL_VOLUME||||STRING", "ICEBERG_DEFAULT_DDL_COLLATION||||STRING",
            "ICEBERG_MERGE_ON_READ_BEHAVIOR|auto|auto||STRING", "LOG_EVENT_LEVEL|OFF|OFF||STRING",
            "MAX_DATA_EXTENSION_TIME_IN_DAYS|14|14||NUMBER", "OPTIMIZE_DATA_LAYOUT|true|true||BOOLEAN",
            "PURGE_ON_DROP_TABLE|false|false||BOOLEAN", "QUOTED_IDENTIFIERS_IGNORE_CASE|false|false||BOOLEAN",
            "REPLACE_INVALID_CHARACTERS|false|false||BOOLEAN", "STORAGE_SERIALIZATION_POLICY|OPTIMIZED|OPTIMIZED||STRING",
            "TARGET_FILE_SIZE|AUTO|AUTO||STRING", "VENDED_CREDENTIAL_STORAGE_REGION||||STRING"}) {
            assertTrue(rows.contains(measured), measured + " in " + rows);
        }
        for (final String row : rows) {
            assertTrue(!row.startsWith("TIMEZONE|") && !row.startsWith("AUTOCOMMIT|"), "no session parameter: " + row);
        }
        final ResultSet described = engine.executeQuery("SHOW PARAMETERS LIKE 'DATA_RETENTION_TIME_IN_DAYS' IN TABLE pt");
        assertEquals("number of days to retain the old version of deleted/updated data",
            String.valueOf(described.getRows().get(0).getValue(described.getColumnIndex("description"))));
    }

    @Test
    public void theTableAndTheKeywordsAreReadAsWritten() {
        final String expected = "[CATALOG||||STRING]";
        assertEquals(expected, listed("SHOW PARAMETERS LIKE 'CATALOG' IN TABLE pt").toString());
        assertEquals(expected, listed("SHOW PARAMETERS LIKE 'CATALOG' IN TABLE test_schema.pt").toString());
        assertEquals(expected, listed("SHOW PARAMETERS LIKE 'CATALOG' IN TABLE test_db.test_schema.pt").toString());
        assertEquals(expected,
            listed("SHOW PARAMETERS LIKE 'CATALOG' IN TABLE \"TEST_DB\".\"TEST_SCHEMA\".\"PT\"").toString());
        assertEquals(expected,
            listed("SHOW PARAMETERS LIKE 'CATALOG' IN TABLE IDENTIFIER('test_db.test_schema.pt')").toString());
        assertEquals(expected, listed("SHOW PARAMETERS LIKE 'CATALOG' FOR TABLE pt").toString());
        assertEquals(expected, listed("SHOW PARAMETERS LIKE 'CATALOG' IN STREAM pt").toString(),
            "the keyword is no claim about the object");
        // A view and a stream are found, and have no parameters of their own.
        assertEquals("[]", listed("SHOW PARAMETERS LIKE 'CATALOG' IN TABLE pv").toString());
        assertEquals("[]", listed("SHOW PARAMETERS LIKE 'CATALOG' IN TABLE pst").toString());
        assertEquals("[]", listed("SHOW PARAMETERS LIKE 'CATALOG' IN STREAM pst").toString());
    }

    @Test
    public void likeIsAPatternNotASubstring() {
        assertEquals("[CATALOG||||STRING]", listed("SHOW PARAMETERS LIKE 'catalog' IN TABLE lt").toString());
        assertEquals("[CATALOG_SYNC||||STRING]", listed("SHOW PARAMETERS LIKE 'CATALOG_SYN_' IN TABLE lt").toString());
        assertEquals("[]", listed("SHOW PARAMETERS LIKE 'TIMEZONE' IN TABLE lt").toString());
        // A schema and a database filter the same way.
        assertEquals("[CATALOG_SYNC||||STRING]",
            listed("SHOW PARAMETERS LIKE 'CATALOG_SYN_' IN SCHEMA test_db.test_schema").toString());
        assertEquals("[]", listed("SHOW PARAMETERS LIKE 'DATA' IN DATABASE test_db").toString());
        assertEquals("[LOG_EVENT_LEVEL|OFF|OFF||STRING, LOG_LEVEL|OFF|OFF||STRING, METRIC_LEVEL|NONE|NONE||STRING,"
            + " TRACE_LEVEL|OFF|OFF||STRING]",
            listed("SHOW PARAMETERS LIKE '%E%EL' IN SCHEMA test_db.test_schema").toString());
        assertEquals("[DATA_METRIC_SCHEDULE|60 MINUTES|60 MINUTES||STRING, DATA_RETENTION_TIME_IN_DAYS|1|1||NUMBER,"
            + " ENABLE_DATA_COMPACTION|true|true||BOOLEAN, MAX_DATA_EXTENSION_TIME_IN_DAYS|5|14|TABLE|NUMBER,"
            + " OPTIMIZE_DATA_LAYOUT|true|true||BOOLEAN]", listed("SHOW PARAMETERS LIKE '%DA%' IN TABLE lt").toString());
    }

    @Test
    public void aValueComesFromTheTableItsSchemaOrItsDatabase() {
        assertEquals("[DEFAULT_DDL_COLLATION|en-ci||TABLE|STRING]",
            listed("SHOW PARAMETERS LIKE 'DEFAULT_DDL_COLLATION' IN TABLE lt").toString());
        assertEquals("[MAX_DATA_EXTENSION_TIME_IN_DAYS|5|14|TABLE|NUMBER]",
            listed("SHOW PARAMETERS LIKE 'MAX_DATA_EXTENSION%' IN TABLE lt").toString());
        final String retention = "SHOW PARAMETERS LIKE 'DATA_RETENTION%' IN TABLE lt";
        assertEquals("[DATA_RETENTION_TIME_IN_DAYS|1|1||NUMBER]", listed(retention).toString());
        engine.execute("ALTER SCHEMA test_db.test_schema SET DATA_RETENTION_TIME_IN_DAYS = 3");
        assertEquals("[DATA_RETENTION_TIME_IN_DAYS|3|1|SCHEMA|NUMBER]", listed(retention).toString());
        engine.execute("ALTER TABLE lt SET DATA_RETENTION_TIME_IN_DAYS = 2");
        assertEquals("[DATA_RETENTION_TIME_IN_DAYS|2|1|TABLE|NUMBER]", listed(retention).toString());
        engine.execute("ALTER TABLE lt UNSET DATA_RETENTION_TIME_IN_DAYS");
        assertEquals("[DATA_RETENTION_TIME_IN_DAYS|3|1|SCHEMA|NUMBER]", listed(retention).toString());
        engine.execute("ALTER SCHEMA test_db.test_schema UNSET DATA_RETENTION_TIME_IN_DAYS");
        engine.execute("ALTER DATABASE test_db SET DATA_RETENTION_TIME_IN_DAYS = 4");
        assertEquals("[DATA_RETENTION_TIME_IN_DAYS|4|1|DATABASE|NUMBER]", listed(retention).toString());
        engine.execute("ALTER DATABASE test_db UNSET DATA_RETENTION_TIME_IN_DAYS");
        assertEquals("[DATA_RETENTION_TIME_IN_DAYS|1|1||NUMBER]", listed(retention).toString());
        engine.execute("ALTER TABLE lt SET MAX_DATA_EXTENSION_TIME_IN_DAYS = 7");
        assertEquals("[MAX_DATA_EXTENSION_TIME_IN_DAYS|7|14|TABLE|NUMBER]",
            listed("SHOW PARAMETERS LIKE 'MAX_DATA_EXTENSION%' IN TABLE lt").toString());
        engine.execute("ALTER TABLE lt UNSET MAX_DATA_EXTENSION_TIME_IN_DAYS");
        assertEquals("[MAX_DATA_EXTENSION_TIME_IN_DAYS|14|14||NUMBER]",
            listed("SHOW PARAMETERS LIKE 'MAX_DATA_EXTENSION%' IN TABLE lt").toString());
    }

    @Test
    public void aNameThatReachesNothingIsRefused() {
        assertEquals("SQL compilation error: Table 'NOSUCH' does not exist or not authorized.",
            refusal("SHOW PARAMETERS IN TABLE nosuch"));
        assertEquals("SQL compilation error: Table 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.",
            refusal("SHOW PARAMETERS IN TABLE test_db.test_schema.nosuch"));
        assertEquals("SQL compilation error: Schema 'TEST_DB.NOSUCHSCHEMA' does not exist or not authorized.",
            refusal("SHOW PARAMETERS IN TABLE test_db.nosuchschema.t"));
        assertEquals("SQL compilation error: Database 'NOSUCHDB' does not exist or not authorized.",
            refusal("SHOW PARAMETERS IN TABLE nosuchdb.s.t"));
        assertEquals("SQL compilation error: Object does not exist, or operation cannot be performed.",
            refusal("SHOW PARAMETERS IN TABLE a.b.c.d"));
    }

    @Test
    public void aTaskIsNamedQualifiedToo() {
        final String expected = "[SUSPEND_TASK_AFTER_NUM_FAILURES|10|10||NUMBER]";
        assertEquals(expected, listed("SHOW PARAMETERS LIKE 'SUSPEND%' IN TASK ptask").toString());
        assertEquals(expected, listed("SHOW PARAMETERS LIKE 'SUSPEND%' IN TASK test_schema.ptask").toString());
        assertEquals(expected, listed("SHOW PARAMETERS LIKE 'SUSPEND%' IN TASK test_db.test_schema.ptask").toString());
        assertEquals(expected, listed("SHOW PARAMETERS LIKE 'SUSPEND%' FOR TASK ptask").toString());
        assertEquals("SQL compilation error: Task 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.",
            refusal("SHOW PARAMETERS IN TASK nosuch"));
        assertEquals("SQL compilation error: Task 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.",
            refusal("SHOW PARAMETERS FOR TASK test_db.test_schema.nosuch"));
        assertEquals("SQL compilation error: Schema 'TEST_DB.NOSUCHSCHEMA' does not exist or not authorized.",
            refusal("SHOW PARAMETERS IN TASK test_db.nosuchschema.t"));
        assertEquals("SQL compilation error: Object does not exist, or operation cannot be performed.",
            refusal("SHOW PARAMETERS IN TASK a.b.c.d"));
    }
}
