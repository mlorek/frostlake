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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A CREATE or RENAME that meets an object of the same kind under the same name is refused with one sentence
 * for every kind: {@code Object 'S1' already exists.} Sequences, file formats, stages, functions and
 * procedures of the same signature, streams, tasks, pipes, tags, schemas and databases all say it, and a
 * function or procedure with another signature is an overload that is created. Every cell is
 * live-verified.
 */
public class SameNameCollisionTest extends BaseDatabaseTest {

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
    public void aSecondObjectOfOneNameIsRefusedInTheAccountsWords() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P471_DB");
            engine.execute("CREATE SEQUENCE s1");
            assertRefused("CREATE SEQUENCE s1",
                "SQL compilation error:\nObject 'S1' already exists.");
            engine.execute("CREATE FILE FORMAT ff1 TYPE = CSV");
            assertRefused("CREATE FILE FORMAT ff1 TYPE = CSV",
                "SQL compilation error:\nObject 'FF1' already exists.");
            engine.execute("CREATE STAGE st1");
            assertRefused("CREATE STAGE st1",
                "SQL compilation error:\nObject 'ST1' already exists.");
            engine.execute("CREATE FUNCTION f1() RETURNS INT AS '1'");
            assertRefused("CREATE FUNCTION f1() RETURNS INT AS '1'",
                "SQL compilation error:\nObject 'F1' already exists.");
            engine.execute("CREATE FUNCTION f1(x INT) RETURNS INT AS 'x'");
            engine.execute("CREATE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            assertRefused("CREATE PROCEDURE p1() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'",
                "SQL compilation error:\nObject 'P1' already exists.");
            engine.execute("CREATE PROCEDURE p1(x INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
            engine.execute("CREATE TABLE tt (a INT)");
            engine.execute("CREATE STREAM str1 ON TABLE tt");
            assertRefused("CREATE STREAM str1 ON TABLE tt",
                "SQL compilation error:\nObject 'STR1' already exists.");
            engine.execute("CREATE TASK tk1 WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE' AS SELECT 1");
            assertRefused("CREATE TASK tk1 WAREHOUSE = COMPUTE_WH SCHEDULE = '60 MINUTE' AS SELECT 1",
                "SQL compilation error:\nObject 'TK1' already exists.");
            engine.execute("CREATE PIPE pp1 AS COPY INTO tt FROM @st1");
            assertRefused("CREATE PIPE pp1 AS COPY INTO tt FROM @st1",
                "SQL compilation error:\nObject 'PP1' already exists.");
            engine.execute("CREATE TAG tg1");
            assertRefused("CREATE TAG tg1",
                "SQL compilation error:\nObject 'TG1' already exists.");
            engine.execute("CREATE SEQUENCE s2");
            engine.execute("CREATE STAGE st2");
            assertRefused("ALTER STAGE st1 RENAME TO st2",
                "SQL compilation error:\nObject 'ST2' already exists.");
            engine.execute("CREATE FILE FORMAT ff2 TYPE = CSV");
            assertRefused("ALTER FILE FORMAT ff1 RENAME TO ff2",
                "SQL compilation error:\nObject 'FF2' already exists.");
            engine.execute("CREATE FUNCTION f2() RETURNS INT AS '2'");
            engine.execute("CREATE TABLE tt2 (a INT)");
            assertRefused("ALTER TABLE tt RENAME TO tt2",
                "SQL compilation error:\nObject 'TT2' already exists.");
            assertRefused("CREATE SCHEMA PUBLIC",
                "SQL compilation error:\nObject 'PUBLIC' already exists.");
            assertRefused("CREATE DATABASE P471_DB",
                "SQL compilation error:\nObject 'P471_DB' already exists.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P471_DB");
        }
    }
}
