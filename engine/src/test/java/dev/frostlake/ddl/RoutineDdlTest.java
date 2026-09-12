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
public class RoutineDdlTest extends BaseDatabaseTest {

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
    public void aRoutinesDdlIsRenderedAsLiveRendersIt() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P479_DB");
            engine.execute("CREATE OR REPLACE FUNCTION plain() RETURNS VARCHAR AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE FUNCTION g7() RETURNS VARCHAR COMMENT='c' AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE SECURE FUNCTION g3() RETURNS VARCHAR STRICT IMMUTABLE MEMOIZABLE AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE FUNCTION g5(x NUMBER) RETURNS NUMBER MEMOIZABLE AS 'x + 1'");
            engine.execute("CREATE OR REPLACE FUNCTION g9(x NUMBER DEFAULT 1) RETURNS NUMBER AS 'x + 1'");
            engine.execute("CREATE OR REPLACE FUNCTION g10() RETURNS TABLE(a INT) AS 'SELECT 1'");
            engine.execute("CREATE OR REPLACE FUNCTION g11() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return \"a\"'");
            engine.execute("CREATE OR REPLACE FUNCTION g12() RETURNS VARCHAR CALLED ON NULL INPUT AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE FUNCTION g13() RETURNS VARCHAR NOT NULL AS ' SELECT ''A'' '");
            engine.execute("CREATE OR REPLACE FUNCTION g14() RETURNS VARCHAR AS $$ SELECT 'A' $$");
            engine.execute("CREATE OR REPLACE PROCEDURE g15() RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'A'; END $$");
            assertEquals("CREATE OR REPLACE FUNCTION \"PLAIN\"()~RETURNS VARCHAR~LANGUAGE SQL~AS ' SELECT ''A'' ';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'plain()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"G7\"()~RETURNS VARCHAR~LANGUAGE SQL~COMMENT='c'~AS ' SELECT ''A'' ';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g7()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE SECURE FUNCTION \"G3\"()~RETURNS VARCHAR~LANGUAGE SQL~STRICT~IMMUTABLE~MEMOIZABLE AS ' SELECT ''A'' ';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g3()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"G5\"(\"X\" NUMBER(38,0))~RETURNS NUMBER(38,0)~LANGUAGE SQL~MEMOIZABLE AS 'x + 1';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g5(NUMBER)'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"G9\"(\"X\" NUMBER(38,0) DEFAULT 1)~RETURNS NUMBER(38,0)~LANGUAGE SQL~AS 'x + 1';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g9(NUMBER)'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"G10\"()~RETURNS TABLE (\"A\" NUMBER(38,0))~LANGUAGE SQL~AS 'SELECT 1';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g10()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"G11\"()~RETURNS VARCHAR~LANGUAGE JAVASCRIPT~AS 'return \"a\"';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g11()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"G12\"()~RETURNS VARCHAR~LANGUAGE SQL~AS ' SELECT ''A'' ';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g12()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"G13\"()~RETURNS VARCHAR~LANGUAGE SQL~AS ' SELECT ''A'' ';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g13()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"G14\"()~RETURNS VARCHAR~LANGUAGE SQL~AS ' SELECT ''A'' ';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'g14()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE PROCEDURE \"G15\"()~RETURNS VARCHAR~LANGUAGE SQL~EXECUTE AS OWNER~AS ' BEGIN RETURN ''A''; END ';",
                rows("SELECT REPLACE(GET_DDL('PROCEDURE', 'g15()'), CHR(10), '~')"));
            assertRefused("SELECT REPLACE(GET_DDL('FUNCTION', 'nosuch()'), CHR(10), '~')",
                "SQL compilation error:\nObject 'nosuch()' does not exist or not authorized.");
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P479_DB");
        }
    }

    @Test
    public void aRoutinesTypesAreSpelledAsDeclared() {
        try {
            engine.execute("CREATE OR REPLACE DATABASE P479B_DB");
            engine.execute("CREATE OR REPLACE FUNCTION h1(x VARCHAR(10)) RETURNS VARCHAR(20) AS 'x'");
            engine.execute("CREATE OR REPLACE FUNCTION h3() RETURNS TIMESTAMP_NTZ(3) AS 'SYSDATE()::TIMESTAMP_NTZ(3)'");
            engine.execute("CREATE OR REPLACE FUNCTION h4() RETURNS BOOLEAN AS 'TRUE'");
            engine.execute("CREATE OR REPLACE FUNCTION h5(x NUMBER(10,2)) RETURNS NUMBER(10,2) AS 'x'");
            engine.execute("CREATE OR REPLACE FUNCTION h6() RETURNS VARIANT AS 'PARSE_JSON(''1'')'");
            engine.execute("CREATE OR REPLACE TEMPORARY FUNCTION h7() RETURNS INT AS '1'");
            assertEquals("CREATE OR REPLACE FUNCTION \"H1\"(\"X\" VARCHAR(10))~RETURNS VARCHAR(20)~LANGUAGE SQL~AS 'x';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'h1(VARCHAR)'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"H3\"()~RETURNS TIMESTAMP_NTZ(3)~LANGUAGE SQL~AS 'SYSDATE()::TIMESTAMP_NTZ(3)';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'h3()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"H4\"()~RETURNS BOOLEAN~LANGUAGE SQL~AS 'TRUE';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'h4()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"H5\"(\"X\" NUMBER(10,2))~RETURNS NUMBER(10,2)~LANGUAGE SQL~AS 'x';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'h5(NUMBER)'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"H6\"()~RETURNS VARIANT~LANGUAGE SQL~AS 'PARSE_JSON(''1'')';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'h6()'), CHR(10), '~')"));
            assertEquals("CREATE OR REPLACE FUNCTION \"H7\"()~RETURNS NUMBER(38,0)~LANGUAGE SQL~AS '1';",
                rows("SELECT REPLACE(GET_DDL('FUNCTION', 'h7()'), CHR(10), '~')"));
        } finally {
            engine.execute("DROP DATABASE IF EXISTS P479B_DB");
        }
    }
}
