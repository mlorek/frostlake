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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table, a view, a materialized view, a dynamic table and a stream share one name space in a schema: a
 * create over a name another of them holds is refused naming the holder's kind, {@code Object 'KT' already
 * exists as TABLE}, and neither OR REPLACE nor IF NOT EXISTS gets past it. A TEMPORARY table is the
 * exception — it may take a view's, a materialized view's or a dynamic table's name, though not a stream's,
 * and a TEMPORARY view may take a table's — and it then shadows the object it names until it is dropped.
 * A sequence keeps its own name space. Every cell is live-verified.
 */
public class SqlUdfBodyCompileTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private void assertRefused(final String sql, final String fragment) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(fragment), refused.getMessage());
    }

    @Test
    public void aSqlUdfBodyIsCompiledAtCreate() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P473_DB");
            engine.execute("CREATE TABLE t (a INT)");
            assertRefused("CREATE FUNCTION f5() RETURNS INT AS 'nosuchcol'",
                "SQL compilation error: error line 1 at position 1\ninvalid identifier 'NOSUCHCOL'");
            engine.execute("CREATE FUNCTION f7(x INT) RETURNS INT AS 'x + 1'");
            assertRefused("CREATE FUNCTION f8(x INT) RETURNS INT AS 'y + 1'",
                "SQL compilation error: error line 1 at position 1\ninvalid identifier 'Y'");
            engine.execute("CREATE FUNCTION f9() RETURNS TABLE(a INT) AS 'SELECT a FROM t'");
            assertRefused("CREATE FUNCTION f10() RETURNS TABLE(a INT) AS 'SELECT nosuch FROM t'",
                "SQL compilation error: error line 1 at position 8\ninvalid identifier 'NOSUCH'");
            assertRefused("CREATE FUNCTION f11() RETURNS INT AS 'SELECT max(a) FROM nosuchtbl'",
                "SQL compilation error:\nObject 'P473_DB.PUBLIC.NOSUCHTBL' does not exist or not authorized.");
            engine.execute("CREATE FUNCTION fx() RETURNS INT AS '1'");
            engine.execute("CREATE FUNCTION f14() RETURNS INT AS 'SELECT 1'");
            assertEquals("1",
                rows("SELECT f14()"));
            engine.execute("CREATE FUNCTION f15(x INT) RETURNS INT AS 'X + 1'");
            assertEquals("3",
                rows("SELECT f15(2)"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P473_DB");
        }
    }

    @Test
    public void aBodysNamesAreResolvedAtCreate() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P473B_DB");
            engine.execute("CREATE OR REPLACE TABLE t (a INT)");
            engine.execute("CREATE OR REPLACE FUNCTION f1() RETURNS DATE AS 'CURRENT_DATE'");
            assertRefused("CREATE OR REPLACE FUNCTION f2() RETURNS INT AS 'nosuchcol'",
                "SQL compilation error: error line 1 at position 1\ninvalid identifier 'NOSUCHCOL'");
            engine.execute("CREATE OR REPLACE SEQUENCE s1");
            engine.execute("CREATE OR REPLACE FUNCTION f3() RETURNS INT AS 's1.nextval'");
            engine.execute("CREATE OR REPLACE FUNCTION f4() RETURNS TIMESTAMP_LTZ AS 'CURRENT_TIMESTAMP'");
            assertRefused("CREATE OR REPLACE FUNCTION f5() RETURNS INT AS 'nosuchfunc(1)'",
                "SQL compilation error:\nUnknown function NOSUCHFUNC.");
            assertRefused("CREATE OR REPLACE FUNCTION f6() RETURNS INT AS 'x + 1'",
                "SQL compilation error: error line 1 at position 1\ninvalid identifier 'X'");
            assertRefused("CREATE OR REPLACE FUNCTION f7(x INT) RETURNS INT AS 'x + y'",
                "SQL compilation error: error line 1 at position 5\ninvalid identifier 'Y'");
            engine.execute("CREATE OR REPLACE FUNCTION f8() RETURNS INT AS 'BITSHIFTLEFT(1, 2)'");
            engine.execute("CREATE OR REPLACE FUNCTION f9() RETURNS INT AS 'SELECT a FROM t'");
            assertRefused("CREATE OR REPLACE FUNCTION f10() RETURNS INT AS 'SELECT a FROM nosuchtbl'",
                "SQL compilation error:\nObject 'P473B_DB.PUBLIC.NOSUCHTBL' does not exist or not authorized.");
            assertRefused("CREATE OR REPLACE FUNCTION f11() RETURNS INT AS 'SELECT nosuchcol FROM t'",
                "SQL compilation error: error line 1 at position 8\ninvalid identifier 'NOSUCHCOL'");
            assertRefused("CREATE OR REPLACE FUNCTION f12() RETURNS INT AS 'LOCALTIME'",
                "Declared return type 'NUMBER(38,0)' is incompatible with actual return type 'TIME(9)'");
            assertRefused("CREATE OR REPLACE FUNCTION f13() RETURNS INT AS 'a'",
                "SQL compilation error: error line 1 at position 1\ninvalid identifier 'A'");
            engine.execute("CREATE OR REPLACE VIEW v1 AS SELECT a FROM t");
            engine.execute("CREATE OR REPLACE FUNCTION f14() RETURNS INT AS 'SELECT a FROM v1'");
            assertRefused("CREATE OR REPLACE FUNCTION f15() RETURNS INT AS 'SELECT MAX(a) FROM t WHERE nosuchcol > 1'",
                "SQL compilation error: error line 1 at position 28\ninvalid identifier 'NOSUCHCOL'");
            assertRefused("CREATE OR REPLACE FUNCTION f16() RETURNS INT AS 'f2()'",
                "SQL compilation error:\nUnknown function F2.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P473B_DB");
        }
    }

    @Test
    public void aQueryBodyMayReadItsOwnParameters() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P473C_DB");
            engine.execute("CREATE OR REPLACE TABLE t (a INT)");
            engine.execute("INSERT INTO t VALUES (1)");
            engine.execute("CREATE OR REPLACE FUNCTION fq1(x INT) RETURNS INT AS 'SELECT x'");
            assertEquals("3",
                rows("SELECT fq1(3)"));
            engine.execute("CREATE OR REPLACE FUNCTION fq2(x INT) RETURNS INT AS 'SELECT a FROM t WHERE a = x'");
            assertEquals("1",
                rows("SELECT fq2(1)"));
            assertRefused("CREATE OR REPLACE FUNCTION fq3(x INT) RETURNS INT AS 'SELECT nosuch FROM t WHERE a = x'",
                "SQL compilation error: error line 1 at position 8\ninvalid identifier 'NOSUCH'");
            assertRefused("CREATE OR REPLACE FUNCTION fq4() RETURNS INT AS '1 +\nnosuchcol'",
                "SQL compilation error: error line 2 at position 0\ninvalid identifier 'NOSUCHCOL'");
            assertRefused("CREATE OR REPLACE FUNCTION fq6(x INT) RETURNS INT AS '-- c\nnosuchcol'",
                "SQL compilation error: error line 2 at position 0\ninvalid identifier 'NOSUCHCOL'");
            engine.execute("CREATE OR REPLACE FUNCTION fq7() RETURNS INT AS 'SELECT MAX(a) FROM t'");
            assertEquals("1",
                rows("SELECT fq7()"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P473C_DB");
        }
    }
}
