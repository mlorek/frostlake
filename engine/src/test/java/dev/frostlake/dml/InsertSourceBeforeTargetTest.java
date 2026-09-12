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

package dev.frostlake.dml;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An INSERT is checked in live's order: the source query's relations, every target, the source query
 * itself, then each column list, counted first and read name by name. A missing source relation is
 * reported ahead of a missing target, and a missing target ahead of the source's column errors. Every
 * check runs before a row is written, in a single-table and a multi-table INSERT alike. Every cell is
 * live-verified.
 */
public class InsertSourceBeforeTargetTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    /** With a current database: the source relation, then the target, then everything else. */
    @Test
    public void aMissingSourceRelationIsReportedFirst() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P441_DB");
            engine.execute("CREATE OR REPLACE TABLE P441_DB.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE TABLE P441_DB.PUBLIC.SRC (x INT)");
            assertRefused("INSERT INTO nosuch SELECT * FROM nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT INTO nosuch SELECT 1",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT INTO T (nosuchcol) SELECT * FROM nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT INTO T (nosuchcol) SELECT 1",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT INTO nosuch (x) SELECT * FROM nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT INTO T SELECT nosuchcol FROM SRC",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT INTO nosuch SELECT nosuchcol FROM SRC",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT ALL INTO nosuch VALUES (1) SELECT 1 FROM nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT ALL INTO T VALUES (1) INTO nosuch VALUES (2) SELECT 1",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT FIRST WHEN 1 = 1 THEN INTO nosuch VALUES (1) SELECT 1 FROM nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT INTO nosuch VALUES (1)",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT INTO nosuch SELECT * FROM T WHERE nosuchcol = 1",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT INTO T SELECT * FROM SRC, nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT INTO nosuch WITH c AS (SELECT 1) SELECT * FROM c",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT INTO nosuch SELECT * FROM (SELECT * FROM nosuch2)",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT OVERWRITE INTO nosuch SELECT * FROM nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT INTO T SELECT 1, 2",
                "Insert value list does not match column list expecting 1 but got 2");
            assertRefused("INSERT INTO nosuch SELECT 1, 2 FROM nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT INTO T (x, x) SELECT 1 FROM nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT INTO T (x, nosuchcol) SELECT 1, 2",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT INTO nosuch SELECT nosuchfn(1)",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT INTO T SELECT nosuchfn(1) FROM nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT ALL INTO T INTO nosuch SELECT 1",
                "Table 'NOSUCH' does not exist or not authorized.");
            engine.execute("INSERT INTO T SELECT * FROM SRC");
            assertRefused("INSERT INTO nosuch (x) VALUES (1)",
                "Table 'NOSUCH' does not exist or not authorized.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P441_DB");
        }
    }

    /** With none: a schema-qualified source is refused as a SELECT ahead of the target. */
    @Test
    public void withNoCurrentDatabaseTheSourceIsNamedFirst() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P441_DB");
            engine.execute("CREATE OR REPLACE TABLE P441_DB.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE TABLE P441_DB.PUBLIC.SRC (x INT)");
            engine.execute("CREATE OR REPLACE DATABASE P441_IDLE");
            engine.execute("DROP DATABASE P441_IDLE");
            assertRefused("INSERT INTO t SELECT * FROM PUBLIC.u",
                "Cannot perform SELECT. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("INSERT INTO PUBLIC.t SELECT * FROM PUBLIC.u",
                "Cannot perform SELECT. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("INSERT INTO PUBLIC.T SELECT 1",
                "Cannot perform INSERT. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("INSERT INTO PUBLIC.T SELECT * FROM P441_DB.PUBLIC.SRC",
                "Cannot perform INSERT. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("INSERT INTO nosuch SELECT 1",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT INTO t SELECT * FROM P441_DB.PUBLIC.nosuch2",
                "Object 'P441_DB.PUBLIC.NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT INTO P441_DB.PUBLIC.T SELECT * FROM PUBLIC.u",
                "Cannot perform SELECT. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("INSERT INTO P441_DB.PUBLIC.T SELECT * FROM u",
                "Object 'U' does not exist or not authorized.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P441_DB");
        }
    }

    /** Every target and column list is checked before a row is routed, so an empty source still refuses. */
    @Test
    public void aMultiTableInsertChecksEveryTargetBeforeARow() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P441E_DB");
            engine.execute("CREATE OR REPLACE TABLE P441E_DB.PUBLIC.T (x INT)");
            assertRefused("INSERT ALL INTO nosuch SELECT 1 WHERE 1 = 0",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT ALL INTO T INTO nosuch SELECT 1 WHERE 1 = 0",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT FIRST WHEN 1 = 1 THEN INTO nosuch SELECT 1 WHERE 1 = 0",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT ALL INTO T (nosuchcol) SELECT 1 WHERE 1 = 0",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT INTO nosuch SELECT 1 WHERE 1 = 0",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT INTO T (nosuchcol) SELECT 1 WHERE 1 = 0",
                "invalid identifier 'NOSUCHCOL'");
            engine.execute("CREATE OR REPLACE TABLE P441E_DB.PUBLIC.SRC (x INT)");
            assertRefused("INSERT ALL INTO nosuch SELECT nosuchcol FROM SRC",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT ALL INTO T (nosuchcol) SELECT * FROM nosuch2",
                "Object 'NOSUCH2' does not exist or not authorized.");
            assertRefused("INSERT ALL INTO T VALUES (1) SELECT nosuchcol FROM SRC",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT FIRST WHEN nosuchcol = 1 THEN INTO T SELECT 1",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT ALL INTO nosuch SELECT nosuchfn(1)",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT ALL INTO T (nosuchcol) SELECT nosuchcol FROM SRC",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT ALL INTO nosuch INTO T (nosuchcol) SELECT 1",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT ALL INTO T (nosuchcol) INTO nosuch SELECT 1",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT FIRST WHEN x = 1 THEN INTO nosuch ELSE INTO T SELECT 1 AS x",
                "Table 'NOSUCH' does not exist or not authorized.");
            assertRefused("INSERT ALL INTO T VALUES (nosuchcol) SELECT 1 AS x",
                "invalid identifier 'NOSUCHCOL'");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P441E_DB");
        }
    }

    /** The source's own column, function and WHERE errors come ahead of the column list; VALUES comes after it. */
    @Test
    public void theSourceQueryIsCheckedBeforeTheColumnList() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P441F_DB");
            engine.execute("CREATE OR REPLACE TABLE P441F_DB.PUBLIC.T (x INT)");
            engine.execute("CREATE OR REPLACE TABLE P441F_DB.PUBLIC.SRC (x INT)");
            assertRefused("INSERT INTO T (nosuchcol) SELECT nosuchcol FROM SRC",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT INTO T (nosuchcol) SELECT nosuchfn(1)",
                "Unknown function NOSUCHFN.");
            assertRefused("INSERT INTO T (x, x) SELECT nosuchcol FROM SRC",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT INTO T (nosuchcol) SELECT 1, 2",
                "Insert value list does not match column list expecting 1 but got 2");
            assertRefused("INSERT INTO T (x) SELECT nosuchcol FROM SRC",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT INTO T (nosuchcol) VALUES (nosuchfn(1))",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT INTO T (x, x) SELECT 1, 2",
                "duplicate column name 'X'");
            assertRefused("INSERT INTO T (nosuchcol) SELECT * FROM SRC WHERE nosuchcol2 = 1",
                "invalid identifier 'NOSUCHCOL2'");
            assertRefused("INSERT INTO T SELECT nosuchcol FROM SRC WHERE 1 = 0",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT INTO T (nosuchcol) SELECT 1 WHERE 1 = 0",
                "invalid identifier 'NOSUCHCOL'");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P441F_DB");
        }
    }

    /** The count first, then each name in order: an unknown one, then a repeated one; "x" names no X. */
    @Test
    public void theColumnListIsCountedThenReadInOrder() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P441G_DB");
            engine.execute("CREATE OR REPLACE TABLE P441G_DB.PUBLIC.T (x INT, y INT)");
            assertRefused("INSERT INTO T (x, x) VALUES (1, 2)",
                "duplicate column name 'X'");
            assertRefused("INSERT INTO T (x, x) SELECT 1",
                "Insert value list does not match column list expecting 2 but got 1");
            assertRefused("INSERT ALL INTO T (x, x) SELECT 1, 2",
                "duplicate column name 'X'");
            assertRefused("INSERT INTO T (x, X) SELECT 1, 2",
                "duplicate column name 'X'");
            assertRefused("INSERT INTO T (\"x\", x) SELECT 1, 2",
                "invalid identifier '\"x\"'");
            assertRefused("INSERT INTO T (x, nosuchcol, x) SELECT 1, 2, 3",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT INTO T (x, x, nosuchcol) SELECT 1, 2, 3",
                "duplicate column name 'X'");
            assertRefused("INSERT INTO T (x, y, x) VALUES (1, 2, 3)",
                "duplicate column name 'X'");
            assertRefused("INSERT INTO T (nosuchcol) VALUES (1, 2)",
                "Insert value list does not match column list expecting 1 but got 2");
            assertRefused("INSERT INTO T (x) VALUES (1, 2)",
                "Insert value list does not match column list expecting 1 but got 2");
            assertRefused("INSERT ALL INTO T (nosuchcol) VALUES (1) SELECT 1",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("INSERT ALL INTO T (x) VALUES (1, 2) SELECT 1",
                "Insert value list does not match column list expecting 1 but got 2");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P441G_DB");
        }
    }
}
