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
public class RoutineAlterPropertyTest extends BaseDatabaseTest {

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
    public void aRoutinesPropertiesAreSetAndUnsetAsLiveTakesThem() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P480_DB");
            engine.execute("CREATE OR REPLACE FUNCTION g1() RETURNS VARCHAR COMMENT='c' AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE FUNCTION memo_arg(x NUMBER) RETURNS NUMBER MEMOIZABLE AS 'x + 1'");
            engine.execute("CREATE OR REPLACE PROCEDURE p1() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
            engine.execute("CREATE OR REPLACE TAG t1");
            engine.execute("ALTER FUNCTION g1() SET SECURE");
            assertEquals("CREATE OR REPLACE SECURE FUNCTION \"G1\"()~RETURNS VARCHAR~LANGUAGE SQL~COMMENT='c'~AS ' SELECT ''A'' ';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g1()'), CHR(10), '~')"));
            engine.execute("ALTER FUNCTION g1() UNSET SECURE");
            assertEquals("CREATE OR REPLACE FUNCTION \"G1\"()~RETURNS VARCHAR~LANGUAGE SQL~COMMENT='c'~AS ' SELECT ''A'' ';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g1()'), CHR(10), '~')"));
            engine.execute("ALTER FUNCTION g1() SET COMMENT = 'changed'");
            engine.execute("ALTER FUNCTION g1() UNSET COMMENT");
            engine.execute("ALTER FUNCTION g1() SET LOG_LEVEL = 'DEBUG'");
            engine.execute("ALTER FUNCTION g1() SET TRACE_LEVEL = 'ALWAYS'");
            assertRefused("ALTER FUNCTION g1() SET BOGUS = 1",
                "SQL compilation error:\ninvalid property 'BOGUS' for 'FUNCTION'");
            assertRefused("ALTER FUNCTION g1() SET BOGUS",
                "SQL compilation error:\nsyntax error line 1 at position 29 unexpected '<EOF>'.");
            assertRefused("ALTER FUNCTION g1() UNSET BOGUS",
                "SQL compilation error:\ninvalid property 'BOGUS' for 'FUNCTION'");
            assertRefused("ALTER FUNCTION memo_arg(NUMBER) UNSET MEMOIZABLE",
                "SQL compilation error:\ninvalid property 'MEMOIZABLE' for 'FUNCTION'");
            assertRefused("ALTER FUNCTION memo_arg(NUMBER) SET MEMOIZABLE",
                "SQL compilation error:\nsyntax error line 1 at position 46 unexpected '<EOF>'.");
            engine.execute("ALTER FUNCTION g1() SET TAG t1 = 'v'");
            assertRefused("ALTER PROCEDURE p1() SET SECURE",
                "SQL compilation error:\nsyntax error line 1 at position 31 unexpected '<EOF>'.");
            assertRefused("ALTER FUNCTION nosuch() SET SECURE",
                "SQL compilation error:\nFunction 'P480_DB.PUBLIC.NOSUCH' does not exist or not authorized.");
            engine.execute("ALTER FUNCTION g1() SET COMMENT = 'x' COMMENT = 'y'");
            assertEquals("CREATE OR REPLACE FUNCTION \"G1\"()~RETURNS VARCHAR~LANGUAGE SQL~COMMENT='y'~AS ' SELECT ''A'' ';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g1()'), CHR(10), '~')"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P480_DB");
        }
    }
}
