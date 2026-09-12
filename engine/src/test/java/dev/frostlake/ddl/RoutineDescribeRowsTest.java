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
public class RoutineDescribeRowsTest extends BaseDatabaseTest {

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
    public void aRoutinesDescribeRowsAreTheOnesLiveGives() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P483_DB");
            engine.execute("CREATE OR REPLACE FUNCTION plain() RETURNS VARCHAR AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE FUNCTION memo() RETURNS VARCHAR MEMOIZABLE AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE FUNCTION imm() RETURNS VARCHAR IMMUTABLE AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE FUNCTION strictf() RETURNS VARCHAR STRICT AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE SECURE FUNCTION sec() RETURNS VARCHAR AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE FUNCTION jsf() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return \"a\"'");
            engine.execute("CREATE OR REPLACE FUNCTION fint() RETURNS INT AS '7'");
            engine.execute("CREATE OR REPLACE PROCEDURE prc() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
            assertEquals("signature, () | returns, VARCHAR | language, SQL | body,  SELECT 'A' ",
                rows("DESCRIBE FUNCTION plain()"));
            assertEquals("signature, () | returns, VARCHAR | language, SQL | body,  SELECT 'A' ",
                rows("DESCRIBE FUNCTION memo()"));
            assertEquals("signature, () | returns, VARCHAR | language, SQL | body,  SELECT 'A' ",
                rows("DESCRIBE FUNCTION imm()"));
            assertEquals("signature, () | returns, VARCHAR | language, SQL | body,  SELECT 'A' ",
                rows("DESCRIBE FUNCTION strictf()"));
            assertEquals("signature, () | returns, VARCHAR | language, SQL | body,  SELECT 'A' ",
                rows("DESCRIBE FUNCTION sec()"));
            assertEquals("signature, () | returns, VARCHAR | language, JAVASCRIPT | null handling, CALLED ON NULL INPUT | volatility, VOLATILE | body, return \"a\"",
                rows("DESCRIBE FUNCTION jsf()"));
            assertEquals("signature, () | returns, NUMBER(38,0) | language, SQL | body, 7",
                rows("DESCRIBE FUNCTION fint()"));
            assertEquals("signature, () | returns, VARCHAR | language, SQL | execute as, OWNER | body,  BEGIN RETURN 'A'; END ",
                rows("DESCRIBE PROCEDURE prc()"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P483_DB");
        }
    }

    @Test
    public void aNonSqlProcedureCarriesTheTwoExtraRows() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P483B_DB");
            engine.execute("CREATE OR REPLACE PROCEDURE pint() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 7; END $$");
            engine.execute("CREATE OR REPLACE PROCEDURE pjs() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return \"a\"; $$");
            assertEquals("signature, () | returns, NUMBER(38,0) | language, SQL | execute as, OWNER | body,  BEGIN RETURN 7; END ",
                rows("DESCRIBE PROCEDURE pint()"));
            assertEquals("signature, () | returns, VARCHAR | language, JAVASCRIPT | null handling, CALLED ON NULL INPUT | volatility, VOLATILE | execute as, OWNER | body,  return \"a\"; ",
                rows("DESCRIBE PROCEDURE pjs()"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P483B_DB");
        }
    }
}
