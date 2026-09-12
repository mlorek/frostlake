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
 * An UPDATE or a DELETE compiles its own names before it reads a row, so an empty table refuses what a full
 * one would: a missing relation anywhere in the statement, then a SET target (a repeated one is a
 * duplicate), then a column reference, then a function name, and an aggregate in the WHERE. A fault that
 * shows only in a value, such as 1/0, is still a row-time one. Every cell is live-verified.
 */
public class UpdateDeleteCompileTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    /** Relations in subqueries, FROM and USING, SET targets, columns and functions are compiled over no rows. */
    @Test
    public void anEmptyTableRefusesWhatAFullOneDoes() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P443_DB");
            engine.execute("CREATE OR REPLACE TABLE P443_DB.PUBLIC.T (a INT)");
            engine.execute("CREATE OR REPLACE TABLE P443_DB.PUBLIC.FULL_T (a INT)");
            engine.execute("INSERT INTO P443_DB.PUBLIC.FULL_T VALUES (1)");
            assertRefused("UPDATE T SET a = (SELECT MAX(a) FROM nosuch)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE FULL_T SET a = (SELECT MAX(a) FROM nosuch)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("DELETE FROM T WHERE a IN (SELECT a FROM nosuch)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE T SET a = 1 WHERE a = (SELECT MAX(a) FROM nosuch)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE T SET a = nosuchcol",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("UPDATE T SET a = 1 WHERE nosuchcol = 1",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("DELETE FROM T WHERE nosuchcol = 1",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("UPDATE T SET nosuchcol = 1",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("UPDATE T SET a = nosuchfn(1)",
                "Unknown function NOSUCHFN.");
            assertRefused("DELETE FROM T WHERE EXISTS (SELECT 1 FROM nosuch)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE T SET a = s.a FROM nosuch s WHERE T.a = s.a",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("DELETE FROM T USING nosuch s WHERE T.a = s.a",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE nosuch SET a = (SELECT 1 FROM nosuch2)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE nosuch SET a = nosuchcol",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("DELETE FROM nosuch WHERE a IN (SELECT a FROM nosuch2)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE T SET a = 1, a = 2",
                "duplicate column name 'A'");
            engine.execute("UPDATE T SET a = 'x'");
            assertRefused("UPDATE T SET a = (SELECT a FROM FULL_T, nosuch)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("MERGE INTO T USING nosuch s ON T.a = s.a WHEN MATCHED THEN DELETE",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE T SET a = (SELECT MAX(a) FROM nosuch) WHERE nosuchcol = 1",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE T SET a = nosuchcol WHERE a IN (SELECT a FROM nosuch)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("DELETE FROM T WHERE nosuchcol = (SELECT MAX(a) FROM nosuch)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE FULL_T SET a = nosuchcol",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("DELETE FROM FULL_T WHERE nosuchcol = 1",
                "invalid identifier 'NOSUCHCOL'");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P443_DB");
        }
    }

    /** With none, a schema-qualified subquery relation is refused as a SELECT, a bare one as missing. */
    @Test
    public void withNoCurrentDatabaseTheSubqueryIsNamedASelect() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P443_DB");
            engine.execute("CREATE OR REPLACE TABLE P443_DB.PUBLIC.T (a INT)");
            engine.execute("CREATE OR REPLACE TABLE P443_DB.PUBLIC.FULL_T (a INT)");
            engine.execute("INSERT INTO P443_DB.PUBLIC.FULL_T VALUES (1)");
            engine.execute("CREATE OR REPLACE DATABASE P443_IDLE");
            engine.execute("DROP DATABASE P443_IDLE");
            assertRefused("UPDATE P443_DB.PUBLIC.T SET a = (SELECT MAX(a) FROM PUBLIC.u)",
                "Cannot perform SELECT. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("DELETE FROM P443_DB.PUBLIC.T WHERE a IN (SELECT a FROM u)",
                "Object 'U' does not exist or not authorized.");
            assertRefused("UPDATE P443_DB.PUBLIC.T SET a = nosuchcol",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("UPDATE PUBLIC.T SET a = 1",
                "Cannot perform SELECT. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.");
            assertRefused("DELETE FROM T",
                "Object 'T' does not exist or not authorized.");
            assertRefused("UPDATE P443_DB.PUBLIC.T SET a = (SELECT MAX(a) FROM P443_DB.PUBLIC.nosuch)",
                "Object 'P443_DB.PUBLIC.NOSUCH' does not exist or not authorized.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P443_DB");
        }
    }

    /** Relations, then every SET target, then column references, then function names; values wait for rows. */
    @Test
    public void theRefusalsComeInLivesOrder() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P443B_DB");
            engine.execute("CREATE OR REPLACE TABLE P443B_DB.PUBLIC.T (a INT, b INT)");
            engine.execute("CREATE OR REPLACE TABLE P443B_DB.PUBLIC.FULL_T (a INT)");
            engine.execute("INSERT INTO P443B_DB.PUBLIC.FULL_T VALUES (1)");
            assertRefused("UPDATE T SET nosuchcol = (SELECT 1 FROM nosuch)",
                "Object 'NOSUCH' does not exist or not authorized.");
            assertRefused("UPDATE T SET nosuchcol = 1, a = nosuchcol2",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("UPDATE T SET a = nosuchcol2, nosuchcol = 1",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("UPDATE T SET a = 1, a = nosuchcol",
                "duplicate column name 'A'");
            assertRefused("UPDATE T SET a = nosuchcol, a = 1",
                "duplicate column name 'A'");
            assertRefused("UPDATE T SET a = nosuchfn(1) WHERE nosuchcol = 1",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("UPDATE T SET a = nosuchcol WHERE nosuchfn(1) = 1",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("UPDATE T SET nosuchcol = nosuchfn(1)",
                "invalid identifier 'NOSUCHCOL'");
            assertRefused("UPDATE T t2 SET t2.a = 1 WHERE t2.nosuchcol = 1",
                "invalid identifier 'T2.NOSUCHCOL'");
            engine.execute("UPDATE T AS q SET a = q.b WHERE q.a = 1");
            assertRefused("DELETE FROM T q WHERE q.nosuchcol = 1",
                "invalid identifier 'Q.NOSUCHCOL'");
            assertRefused("UPDATE T SET a = 1 WHERE T.nosuchcol = 1",
                "invalid identifier 'T.NOSUCHCOL'");
            engine.execute("UPDATE T SET a = 1/0");
            engine.execute("UPDATE T SET a = CAST('x' AS INT)");
            engine.execute("DELETE FROM T WHERE a = 1/0");
            assertRefused("DELETE FROM T WHERE COUNT(*) > 1",
                "Invalid aggregate function in where clause [COUNT(*)]");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P443B_DB");
        }
    }
}
