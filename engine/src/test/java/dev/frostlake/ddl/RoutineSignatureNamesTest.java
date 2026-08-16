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
 * A table, a view, a materialized view, a dynamic table and a stream share one name space in a schema: a
 * create over a name another of them holds is refused naming the holder's kind, {@code Object 'KT' already
 * exists as TABLE}, and neither OR REPLACE nor IF NOT EXISTS gets past it. A TEMPORARY table is the
 * exception — it may take a view's, a materialized view's or a dynamic table's name, though not a stream's,
 * and a TEMPORARY view may take a table's — and it then shadows the object it names until it is dropped.
 * A sequence keeps its own name space. Every cell is live-verified.
 */
public class RoutineSignatureNamesTest extends BaseDatabaseTest {

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
    public void aRepeatedArgumentNameIsRefusedFirst() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P485_DB");
            assertRefused("CREATE OR REPLACE PROCEDURE q10(x INT, x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$",
                "Argument 'X' repeats in the function signature.");
            assertRefused("CREATE OR REPLACE FUNCTION f1(x INT, x INT) RETURNS INT AS 'x'",
                "Argument 'X' repeats in the function signature.");
            assertRefused("CREATE OR REPLACE FUNCTION f2(x INT, X INT) RETURNS INT AS '1'",
                "Argument 'X' repeats in the function signature.");
            engine.execute("CREATE OR REPLACE FUNCTION f3(\"x\" INT, x INT) RETURNS INT AS '1'");
            engine.execute("CREATE OR REPLACE FUNCTION f4(\"x\" INT, \"X\" INT) RETURNS INT AS '1'");
            assertRefused("CREATE OR REPLACE FUNCTION f5(a INT, b INT, a INT) RETURNS INT AS '1'",
                "Argument 'A' repeats in the function signature.");
            assertRefused("CREATE OR REPLACE FUNCTION f6(x INT, x INT) RETURNS INT LANGUAGE JAVASCRIPT AS 'return 1'",
                "Argument 'X' repeats in the function signature.");
            assertRefused("CREATE OR REPLACE FUNCTION f7(x INT, x INT) RETURNS INT AS 'this is not sql'",
                "Argument 'X' repeats in the function signature.");
            assertRefused("CREATE OR REPLACE PROCEDURE q11(x INT, x INT) RETURNS INT LANGUAGE SQL AS $$ DECLARE v INT; v INT; BEGIN RETURN 1; END; $$",
                "Argument 'X' repeats in the function signature.");
            assertRefused("CREATE OR REPLACE FUNCTION f8() RETURNS TABLE(a INT, a INT) AS 'SELECT 1, 2'",
                "Return signature contains a duplicate column name 'A'.");
            assertRefused("CREATE OR REPLACE FUNCTION f9(x INT, x FLOAT) RETURNS INT AS '1'",
                "Argument 'X' repeats in the function signature.");
            engine.execute("CREATE OR REPLACE FUNCTION f10(x INT) RETURNS INT AS '1'");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P485_DB");
        }
    }
}
