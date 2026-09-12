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
public class JavaScriptRoutineTypeTest extends BaseDatabaseTest {

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
    public void javaScriptCarriesNeitherAFixedPointNumberNorATime() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P481_DB");
            assertRefused("CREATE OR REPLACE FUNCTION jr1() RETURNS NUMBER LANGUAGE JAVASCRIPT AS $$ return 1; $$",
                "Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.");
            assertRefused("CREATE OR REPLACE FUNCTION jr2() RETURNS NUMBER(10,2) LANGUAGE JAVASCRIPT AS $$ return 1; $$",
                "Language JAVASCRIPT does not support type 'NUMBER(10,2)' for argument or return type.");
            assertRefused("CREATE OR REPLACE FUNCTION jr3() RETURNS INT LANGUAGE JAVASCRIPT AS $$ return 1; $$",
                "Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.");
            assertRefused("CREATE OR REPLACE FUNCTION jr4() RETURNS DECIMAL(5,1) LANGUAGE JAVASCRIPT AS $$ return 1; $$",
                "Language JAVASCRIPT does not support type 'NUMBER(5,1)' for argument or return type.");
            engine.execute("CREATE OR REPLACE FUNCTION jr5() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            engine.execute("CREATE OR REPLACE FUNCTION jr6() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            engine.execute("CREATE OR REPLACE FUNCTION jr7() RETURNS BOOLEAN LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            engine.execute("CREATE OR REPLACE FUNCTION jr8() RETURNS DATE LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            engine.execute("CREATE OR REPLACE FUNCTION jr9() RETURNS TIMESTAMP_NTZ LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            engine.execute("CREATE OR REPLACE FUNCTION jr10() RETURNS TIMESTAMP_LTZ LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            engine.execute("CREATE OR REPLACE FUNCTION jr11() RETURNS TIMESTAMP_TZ LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            assertRefused("CREATE OR REPLACE FUNCTION jr12() RETURNS TIME LANGUAGE JAVASCRIPT AS $$ return 1; $$",
                "Language JAVASCRIPT does not support type 'TIME(9)' for argument or return type.");
            engine.execute("CREATE OR REPLACE FUNCTION jr13() RETURNS BINARY LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            engine.execute("CREATE OR REPLACE FUNCTION jr14() RETURNS VARIANT LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            engine.execute("CREATE OR REPLACE FUNCTION jr15() RETURNS OBJECT LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            engine.execute("CREATE OR REPLACE FUNCTION jr16() RETURNS ARRAY LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            engine.execute("CREATE OR REPLACE FUNCTION jr17() RETURNS GEOGRAPHY LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            assertRefused("CREATE OR REPLACE FUNCTION ja1(x NUMBER) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$",
                "Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.");
            assertRefused("CREATE OR REPLACE FUNCTION ja2(x INT) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$",
                "Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.");
            engine.execute("CREATE OR REPLACE FUNCTION ja3(x VARCHAR) RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            assertRefused("CREATE OR REPLACE PROCEDURE jp1() RETURNS NUMBER LANGUAGE JAVASCRIPT AS $$ return 1; $$",
                "Language JAVASCRIPT does not support type 'NUMBER(38,0)' for argument or return type.");
            engine.execute("CREATE OR REPLACE FUNCTION jt1() RETURNS TABLE(a NUMBER) LANGUAGE JAVASCRIPT AS $$ return 1; $$");
            assertRefused("CREATE OR REPLACE FUNCTION jm1() RETURNS NUMBER LANGUAGE JAVASCRIPT MEMOIZABLE AS $$ return 1; $$",
                "SQL compilation error: Memoizable function supports only SQL language.");
            engine.execute("CREATE OR REPLACE FUNCTION jv1() RETURNS FLOAT LANGUAGE JAVASCRIPT AS $$ return 1; $$");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P481_DB");
        }
    }
}
