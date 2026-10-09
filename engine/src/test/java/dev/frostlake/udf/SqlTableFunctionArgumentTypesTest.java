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
package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A SQL table function's arguments are matched against its parameters' declared types while the statement
 * compiles, by family and exactly: a NUMBER parameter takes a NUMBER of its own scale, a FLOAT no NUMBER, a
 * VARCHAR text of any width, every other family itself alone, and an untyped NULL matches nothing. A call no
 * overload takes is refused where the function is named, listing the arguments' types (all live-verified).
 */
public class SqlTableFunctionArgumentTypesTest extends BaseDatabaseTest {

    /** Function, argument, and either {@code ok} or the argument types the refusal lists. */
    private static final String CELLS = """
        ptf | 's' | VARCHAR(1)
        ptf | 1 | ok
        ptf | 1.5 | NUMBER(2,1)
        ptf | TRUE | BOOLEAN
        ptf | NULL | NULL
        ptf | PARSE_JSON('1') | VARIANT
        ptf | 1::FLOAT | FLOAT
        ptf | CURRENT_DATE() | DATE
        ptf | NULL::NUMBER | ok
        ptf | NULL::VARCHAR | VARCHAR(134217728)
        ptf | 1::NUMBER(5,2) | NUMBER(5,2)
        ptf | 'abc'::VARCHAR(20) | VARCHAR(20)
        ptf | 12345678901234567890 | ok
        ptf | ARRAY_CONSTRUCT(1) | ARRAY
        ptf | OBJECT_CONSTRUCT('a', 1) | OBJECT
        ptf | CURRENT_TIMESTAMP() | TIMESTAMP_LTZ(9)
        ptf | '2024-01-01 00:00:00'::TIMESTAMP_NTZ | TIMESTAMP_NTZ(9)
        ptf | '2024-01-01 00:00:00'::TIMESTAMP_TZ | TIMESTAMP_TZ(9)
        ptf | CURRENT_TIME() | TIME(9)
        ptf | TO_BINARY('00') | BINARY(67108864)
        ptf | 1 + 1 | ok
        ptf | 1 / 3 | NUMBER(7,6)
        ptf | tt.d | NUMBER(10,2)
        ptf | tt.n | ok
        ptf | tt.s | VARCHAR(5)
        ptf | tt.f | FLOAT
        ptv | 's' | ok
        ptv | 1 | NUMBER(1,0)
        ptv | 1.5 | NUMBER(2,1)
        ptv | TRUE | BOOLEAN
        ptv | NULL | NULL
        ptv | PARSE_JSON('1') | VARIANT
        ptv | 1::FLOAT | FLOAT
        ptv | CURRENT_DATE() | DATE
        ptv | NULL::NUMBER | NUMBER(38,0)
        ptv | NULL::VARCHAR | ok
        ptv | 1::NUMBER(5,2) | NUMBER(5,2)
        ptv | 'abc'::VARCHAR(20) | ok
        ptv | 12345678901234567890 | NUMBER(20,0)
        ptv | ARRAY_CONSTRUCT(1) | ARRAY
        ptv | OBJECT_CONSTRUCT('a', 1) | OBJECT
        ptv | CURRENT_TIMESTAMP() | TIMESTAMP_LTZ(9)
        ptv | '2024-01-01 00:00:00'::TIMESTAMP_NTZ | TIMESTAMP_NTZ(9)
        ptv | '2024-01-01 00:00:00'::TIMESTAMP_TZ | TIMESTAMP_TZ(9)
        ptv | CURRENT_TIME() | TIME(9)
        ptv | TO_BINARY('00') | BINARY(67108864)
        ptv | 1 + 1 | NUMBER(2,0)
        ptv | 1 / 3 | NUMBER(7,6)
        ptv | tt.d | NUMBER(10,2)
        ptv | tt.n | NUMBER(38,0)
        ptv | tt.s | ok
        ptv | tt.f | FLOAT
        ptd | 's' | VARCHAR(1)
        ptd | 1 | NUMBER(1,0)
        ptd | 1.5 | NUMBER(2,1)
        ptd | TRUE | BOOLEAN
        ptd | NULL | NULL
        ptd | PARSE_JSON('1') | VARIANT
        ptd | 1::FLOAT | FLOAT
        ptd | CURRENT_DATE() | DATE
        ptd | NULL::NUMBER | NUMBER(38,0)
        ptd | NULL::VARCHAR | VARCHAR(134217728)
        ptd | 1::NUMBER(5,2) | ok
        ptd | 'abc'::VARCHAR(20) | VARCHAR(20)
        ptd | 12345678901234567890 | NUMBER(20,0)
        ptd | ARRAY_CONSTRUCT(1) | ARRAY
        ptd | OBJECT_CONSTRUCT('a', 1) | OBJECT
        ptd | CURRENT_TIMESTAMP() | TIMESTAMP_LTZ(9)
        ptd | '2024-01-01 00:00:00'::TIMESTAMP_NTZ | TIMESTAMP_NTZ(9)
        ptd | '2024-01-01 00:00:00'::TIMESTAMP_TZ | TIMESTAMP_TZ(9)
        ptd | CURRENT_TIME() | TIME(9)
        ptd | TO_BINARY('00') | BINARY(67108864)
        ptd | 1 + 1 | NUMBER(2,0)
        ptd | 1 / 3 | NUMBER(7,6)
        ptd | tt.d | ok
        ptd | tt.n | NUMBER(38,0)
        ptd | tt.s | VARCHAR(5)
        ptd | tt.f | FLOAT
        ptr | 's' | VARCHAR(1)
        ptr | 1 | NUMBER(1,0)
        ptr | 1.5 | NUMBER(2,1)
        ptr | TRUE | BOOLEAN
        ptr | NULL | NULL
        ptr | PARSE_JSON('1') | VARIANT
        ptr | 1::FLOAT | ok
        ptr | CURRENT_DATE() | DATE
        ptr | NULL::NUMBER | NUMBER(38,0)
        ptr | NULL::VARCHAR | VARCHAR(134217728)
        ptr | 1::NUMBER(5,2) | NUMBER(5,2)
        ptr | 'abc'::VARCHAR(20) | VARCHAR(20)
        ptr | 12345678901234567890 | NUMBER(20,0)
        ptr | ARRAY_CONSTRUCT(1) | ARRAY
        ptr | OBJECT_CONSTRUCT('a', 1) | OBJECT
        ptr | CURRENT_TIMESTAMP() | TIMESTAMP_LTZ(9)
        ptr | '2024-01-01 00:00:00'::TIMESTAMP_NTZ | TIMESTAMP_NTZ(9)
        ptr | '2024-01-01 00:00:00'::TIMESTAMP_TZ | TIMESTAMP_TZ(9)
        ptr | CURRENT_TIME() | TIME(9)
        ptr | TO_BINARY('00') | BINARY(67108864)
        ptr | 1 + 1 | NUMBER(2,0)
        ptr | 1 / 3 | NUMBER(7,6)
        ptr | tt.d | NUMBER(10,2)
        ptr | tt.n | NUMBER(38,0)
        ptr | tt.s | VARCHAR(5)
        ptr | tt.f | ok
        ptb | 's' | VARCHAR(1)
        ptb | 1 | NUMBER(1,0)
        ptb | 1.5 | NUMBER(2,1)
        ptb | TRUE | ok
        ptb | NULL | NULL
        ptb | PARSE_JSON('1') | VARIANT
        ptb | 1::FLOAT | FLOAT
        ptb | CURRENT_DATE() | DATE
        ptb | NULL::NUMBER | NUMBER(38,0)
        ptb | NULL::VARCHAR | VARCHAR(134217728)
        ptb | 1::NUMBER(5,2) | NUMBER(5,2)
        ptb | 'abc'::VARCHAR(20) | VARCHAR(20)
        ptb | 12345678901234567890 | NUMBER(20,0)
        ptb | ARRAY_CONSTRUCT(1) | ARRAY
        ptb | OBJECT_CONSTRUCT('a', 1) | OBJECT
        ptb | CURRENT_TIMESTAMP() | TIMESTAMP_LTZ(9)
        ptb | '2024-01-01 00:00:00'::TIMESTAMP_NTZ | TIMESTAMP_NTZ(9)
        ptb | '2024-01-01 00:00:00'::TIMESTAMP_TZ | TIMESTAMP_TZ(9)
        ptb | CURRENT_TIME() | TIME(9)
        ptb | TO_BINARY('00') | BINARY(67108864)
        ptb | 1 + 1 | NUMBER(2,0)
        ptb | 1 / 3 | NUMBER(7,6)
        ptb | tt.d | NUMBER(10,2)
        ptb | tt.n | NUMBER(38,0)
        ptb | tt.s | VARCHAR(5)
        ptb | tt.f | FLOAT
        ptdt | 's' | VARCHAR(1)
        ptdt | 1 | NUMBER(1,0)
        ptdt | 1.5 | NUMBER(2,1)
        ptdt | TRUE | BOOLEAN
        ptdt | NULL | NULL
        ptdt | PARSE_JSON('1') | VARIANT
        ptdt | 1::FLOAT | FLOAT
        ptdt | CURRENT_DATE() | ok
        ptdt | NULL::NUMBER | NUMBER(38,0)
        ptdt | NULL::VARCHAR | VARCHAR(134217728)
        ptdt | 1::NUMBER(5,2) | NUMBER(5,2)
        ptdt | 'abc'::VARCHAR(20) | VARCHAR(20)
        ptdt | 12345678901234567890 | NUMBER(20,0)
        ptdt | ARRAY_CONSTRUCT(1) | ARRAY
        ptdt | OBJECT_CONSTRUCT('a', 1) | OBJECT
        ptdt | CURRENT_TIMESTAMP() | TIMESTAMP_LTZ(9)
        ptdt | '2024-01-01 00:00:00'::TIMESTAMP_NTZ | TIMESTAMP_NTZ(9)
        ptdt | '2024-01-01 00:00:00'::TIMESTAMP_TZ | TIMESTAMP_TZ(9)
        ptdt | CURRENT_TIME() | TIME(9)
        ptdt | TO_BINARY('00') | BINARY(67108864)
        ptdt | 1 + 1 | NUMBER(2,0)
        ptdt | 1 / 3 | NUMBER(7,6)
        ptdt | tt.d | NUMBER(10,2)
        ptdt | tt.n | NUMBER(38,0)
        ptdt | tt.s | VARCHAR(5)
        ptdt | tt.f | FLOAT
        ptts | 's' | VARCHAR(1)
        ptts | 1 | NUMBER(1,0)
        ptts | 1.5 | NUMBER(2,1)
        ptts | TRUE | BOOLEAN
        ptts | NULL | NULL
        ptts | PARSE_JSON('1') | VARIANT
        ptts | 1::FLOAT | FLOAT
        ptts | CURRENT_DATE() | DATE
        ptts | NULL::NUMBER | NUMBER(38,0)
        ptts | NULL::VARCHAR | VARCHAR(134217728)
        ptts | 1::NUMBER(5,2) | NUMBER(5,2)
        ptts | 'abc'::VARCHAR(20) | VARCHAR(20)
        ptts | 12345678901234567890 | NUMBER(20,0)
        ptts | ARRAY_CONSTRUCT(1) | ARRAY
        ptts | OBJECT_CONSTRUCT('a', 1) | OBJECT
        ptts | CURRENT_TIMESTAMP() | TIMESTAMP_LTZ(9)
        ptts | '2024-01-01 00:00:00'::TIMESTAMP_NTZ | ok
        ptts | '2024-01-01 00:00:00'::TIMESTAMP_TZ | TIMESTAMP_TZ(9)
        ptts | CURRENT_TIME() | TIME(9)
        ptts | TO_BINARY('00') | BINARY(67108864)
        ptts | 1 + 1 | NUMBER(2,0)
        ptts | 1 / 3 | NUMBER(7,6)
        ptts | tt.d | NUMBER(10,2)
        ptts | tt.n | NUMBER(38,0)
        ptts | tt.s | VARCHAR(5)
        ptts | tt.f | FLOAT
        ptvar | 's' | VARCHAR(1)
        ptvar | 1 | NUMBER(1,0)
        ptvar | 1.5 | NUMBER(2,1)
        ptvar | TRUE | BOOLEAN
        ptvar | NULL | NULL
        ptvar | PARSE_JSON('1') | ok
        ptvar | 1::FLOAT | FLOAT
        ptvar | CURRENT_DATE() | DATE
        ptvar | NULL::NUMBER | NUMBER(38,0)
        ptvar | NULL::VARCHAR | VARCHAR(134217728)
        ptvar | 1::NUMBER(5,2) | NUMBER(5,2)
        ptvar | 'abc'::VARCHAR(20) | VARCHAR(20)
        ptvar | 12345678901234567890 | NUMBER(20,0)
        ptvar | ARRAY_CONSTRUCT(1) | ARRAY
        ptvar | OBJECT_CONSTRUCT('a', 1) | OBJECT
        ptvar | CURRENT_TIMESTAMP() | TIMESTAMP_LTZ(9)
        ptvar | '2024-01-01 00:00:00'::TIMESTAMP_NTZ | TIMESTAMP_NTZ(9)
        ptvar | '2024-01-01 00:00:00'::TIMESTAMP_TZ | TIMESTAMP_TZ(9)
        ptvar | CURRENT_TIME() | TIME(9)
        ptvar | TO_BINARY('00') | BINARY(67108864)
        ptvar | 1 + 1 | NUMBER(2,0)
        ptvar | 1 / 3 | NUMBER(7,6)
        ptvar | tt.d | NUMBER(10,2)
        ptvar | tt.n | NUMBER(38,0)
        ptvar | tt.s | VARCHAR(5)
        ptvar | tt.f | FLOAT
        ptarr | 's' | VARCHAR(1)
        ptarr | 1 | NUMBER(1,0)
        ptarr | 1.5 | NUMBER(2,1)
        ptarr | TRUE | BOOLEAN
        ptarr | NULL | NULL
        ptarr | PARSE_JSON('1') | VARIANT
        ptarr | 1::FLOAT | FLOAT
        ptarr | CURRENT_DATE() | DATE
        ptarr | NULL::NUMBER | NUMBER(38,0)
        ptarr | NULL::VARCHAR | VARCHAR(134217728)
        ptarr | 1::NUMBER(5,2) | NUMBER(5,2)
        ptarr | 'abc'::VARCHAR(20) | VARCHAR(20)
        ptarr | 12345678901234567890 | NUMBER(20,0)
        ptarr | ARRAY_CONSTRUCT(1) | ok
        ptarr | OBJECT_CONSTRUCT('a', 1) | OBJECT
        ptarr | CURRENT_TIMESTAMP() | TIMESTAMP_LTZ(9)
        ptarr | '2024-01-01 00:00:00'::TIMESTAMP_NTZ | TIMESTAMP_NTZ(9)
        ptarr | '2024-01-01 00:00:00'::TIMESTAMP_TZ | TIMESTAMP_TZ(9)
        ptarr | CURRENT_TIME() | TIME(9)
        ptarr | TO_BINARY('00') | BINARY(67108864)
        ptarr | 1 + 1 | NUMBER(2,0)
        ptarr | 1 / 3 | NUMBER(7,6)
        ptarr | tt.d | NUMBER(10,2)
        ptarr | tt.n | NUMBER(38,0)
        ptarr | tt.s | VARCHAR(5)
        ptarr | tt.f | FLOAT
        """;

    private static final String[][] FUNCTIONS = {
        {"ptf", "NUMBER"},
        {"ptv", "VARCHAR"},
        {"ptd", "NUMBER(10,2)"},
        {"ptr", "FLOAT"},
        {"ptb", "BOOLEAN"},
        {"ptdt", "DATE"},
        {"ptts", "TIMESTAMP_NTZ"},
        {"ptvar", "VARIANT"},
        {"ptarr", "ARRAY"},
    };

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tt (d NUMBER(10,2), n NUMBER(38,0), s VARCHAR(5), f FLOAT)");
        engine.execute("INSERT INTO tt SELECT 1.5, 2, 'x', 1.5");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private Object first(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertTrue(rs.next(), sql);
        return rs.getValue(0);
    }

    private static String refused(final int position, final String function, final String types) {
        return "SQL compilation error: error line 1 at position " + position + "\nInvalid argument types for function '"
            + function + "': (" + types + ")";
    }

    @Test
    public void anArgumentMatchesItsParameterByFamilyAndExactly() {
        for (final String[] function : FUNCTIONS) {
            engine.execute("CREATE OR REPLACE FUNCTION " + function[0] + "(p " + function[1]
                + ") RETURNS TABLE (x VARCHAR) AS 'SELECT ''r'''");
        }
        for (final String line : CELLS.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            final String[] cell = line.trim().split(" \\| ", 3);
            final boolean column = cell[1].startsWith("tt.");
            final String sql = column ? "SELECT x FROM tt, TABLE(" + cell[0] + "(" + cell[1] + "))"
                : "SELECT x FROM TABLE(" + cell[0] + "(" + cell[1] + "))";
            if ("ok".equals(cell[2])) {
                assertEquals("r", first(sql), sql);
            } else {
                assertEquals(refused(column ? 24 : 20, cell[0].toUpperCase(), cell[2]), refusal(sql), sql);
            }
        }
    }

    @Test
    public void theRefusalStandsWhereTheFunctionIsNamed() {
        engine.execute("CREATE OR REPLACE FUNCTION ptf(p NUMBER) RETURNS TABLE (x VARCHAR) AS 'SELECT ''n'''");
        assertEquals(refused(20, "PTF", "VARCHAR(1)"), refusal("SELECT * FROM TABLE(ptf('s'))"));
        assertEquals("SQL compilation error: error line 2 at position 15\nInvalid argument types for function 'PTF':"
            + " (VARCHAR(1))", refusal("SELECT *\n  FROM TABLE(  ptf( 's' ) )"));
        assertEquals(refused(27, "PTF", "VARCHAR(1)"), refusal("SELECT COUNT(*) FROM TABLE(ptf('1'))"));
        assertEquals(refused(53, "PTF", "VARCHAR(1)"),
            refusal("CREATE OR REPLACE VIEW ptview AS SELECT * FROM TABLE(ptf('1'))"));
        assertEquals(refused(24, "PTF", "NUMBER(10,2)"), refusal("SELECT x FROM tt, TABLE(ptf(tt.d)) WHERE 1 = 0"));
        assertEquals(refused(20, "PTF", "VARCHAR(1)"), refusal("SELECT * FROM TABLE(ptf((SELECT '1')))"));
        assertEquals(refused(20, "PTF", "NUMBER(4,1)"), refusal("SELECT * FROM TABLE(ptf(1 + 1.5))"));
        assertEquals(refused(20, "PTF", "NUMBER(10,2)"), refusal("SELECT * FROM TABLE(ptf(TO_NUMBER('1', 10, 2)))"));
        assertEquals("n", first("SELECT * FROM TABLE(ptf((SELECT 1)))"));
        assertEquals("n", first("SELECT * FROM TABLE(ptf('1'::NUMBER))"));
        assertEquals("n", first("SELECT * FROM TABLE(ptf(TRY_TO_NUMBER('1')))"));
        assertEquals("n", first("SELECT x FROM tt, TABLE(ptf(tt.n))"));
    }

    @Test
    public void aCallIsMatchedByCountNameAndDefault() {
        engine.execute("CREATE OR REPLACE FUNCTION ptf(p NUMBER) RETURNS TABLE (x VARCHAR) AS 'SELECT ''n'''");
        engine.execute("CREATE OR REPLACE FUNCTION pt2(a NUMBER, b VARCHAR) RETURNS TABLE (x VARCHAR) AS 'SELECT ''r'''");
        engine.execute("CREATE OR REPLACE FUNCTION ptdef(p NUMBER DEFAULT 1) RETURNS TABLE (x VARCHAR) AS 'SELECT ''r'''");
        assertEquals("r", first("SELECT * FROM TABLE(pt2(1, 'b'))"));
        assertEquals(refused(20, "PT2", "VARCHAR(1), NUMBER(1,0)"), refusal("SELECT * FROM TABLE(pt2('a', 1))"));
        assertEquals(refused(20, "PT2", "VARCHAR(1), VARCHAR(1)"), refusal("SELECT * FROM TABLE(pt2('a', 'b'))"));
        assertEquals(refused(20, "PT2", "NUMBER(1,0), NUMBER(1,0)"), refusal("SELECT * FROM TABLE(pt2(1, 2))"));
        assertEquals(refused(20, "PT2", "NUMBER(1,0)"), refusal("SELECT * FROM TABLE(pt2(1))"));
        assertEquals(refused(20, "PT2", "NUMBER(1,0), VARCHAR(1), NUMBER(1,0)"),
            refusal("SELECT * FROM TABLE(pt2(1, 'b', 3))"));
        assertEquals("r", first("SELECT * FROM TABLE(ptdef())"));
        assertEquals(refused(20, "PTDEF", "VARCHAR(1)"), refusal("SELECT * FROM TABLE(ptdef('1'))"));
        assertEquals("n", first("SELECT * FROM TABLE(ptf(p => 1))"));
        assertEquals("SQL compilation error: error line 1 at position 20\nnamed arguments [P] do not match any signature"
            + " for function PTF", refusal("SELECT * FROM TABLE(ptf(p => '1'))"));
    }

    @Test
    public void overloadsAreToldApartByTheSameRule() {
        engine.execute("CREATE OR REPLACE FUNCTION ptov(p NUMBER) RETURNS TABLE (x VARCHAR) AS 'SELECT ''number'''");
        engine.execute("CREATE OR REPLACE FUNCTION ptov(p VARCHAR) RETURNS TABLE (x VARCHAR) AS 'SELECT ''varchar'''");
        engine.execute("CREATE OR REPLACE FUNCTION ptov(p NUMBER, q NUMBER) RETURNS TABLE (x VARCHAR) AS 'SELECT ''two'''");
        assertEquals("number", first("SELECT * FROM TABLE(ptov(1))"));
        assertEquals("varchar", first("SELECT * FROM TABLE(ptov('1'))"));
        assertEquals("two", first("SELECT * FROM TABLE(ptov(1, 2))"));
        assertEquals(refused(20, "PTOV", "BOOLEAN"), refusal("SELECT * FROM TABLE(ptov(TRUE))"));
        assertEquals(refused(20, "PTOV", "NULL"), refusal("SELECT * FROM TABLE(ptov(NULL))"));
        assertEquals(refused(20, "PTOV", "FLOAT"), refusal("SELECT * FROM TABLE(ptov(1.5::FLOAT))"));
        assertEquals(refused(20, "PTOV", "NUMBER(1,0), VARCHAR(1)"), refusal("SELECT * FROM TABLE(ptov(1, 'b'))"));
    }

    @Test
    public void aScalarSqlFunctionStillConvertsWhereItReads() {
        engine.execute("CREATE OR REPLACE FUNCTION pts(p NUMBER) RETURNS VARCHAR AS $$ 'scalar' $$");
        assertEquals("scalar", first("SELECT pts('1')"));
    }
}
