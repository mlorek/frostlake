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

package dev.frostlake.procedures;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.DateTimeType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A procedure declared RETURNS TABLE answers a CALL with the table its RETURN TABLE hands back, under the
 * declared columns' names and types and with the returned values unconverted; RETURNS TABLE () declares no
 * columns and answers the table as returned. A returned table of another shape is refused when the CALL runs,
 * a body that ends without a RETURN is refused once it has run, and a RETURN of the other kind — a scalar in a
 * table procedure, a table in any other — is refused while the body compiles, at the RETURN. Every cell is
 * live-verified.
 */
public class ReturnsTableProcedureTest extends BaseDatabaseTest {

    private static final String MISMATCH =
        "Stored procedure execution error: data type of returned table does not match expected returned table type";
    private static final String MISSING_RETURN = "SQL compilation error: stored procedure is missing a return statement";

    /** The first row's first cell, "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** Every row, its cells joined by a space, the rows by " | ". */
    private String rows(final ResultSet result) {
        final StringBuilder text = new StringBuilder();
        for (final Row row : result.getRows()) {
            text.append(text.length() > 0 ? " | " : "");
            for (int i = 0; i < row.getValues().size(); i++) {
                text.append(i > 0 ? " " : "").append(row.getValue(i));
            }
        }
        return text.toString();
    }

    /** Each result column as NAME TYPE(parameters), joined by " | ". */
    private String columns(final ResultSet result) {
        final StringBuilder text = new StringBuilder();
        for (final ResultSetColumn column : result.getColumns()) {
            text.append(text.length() > 0 ? " | " : "").append(column.getName()).append(' ')
                .append(spelled(column.getDataType()));
        }
        return text.toString();
    }

    private static String spelled(final DataType type) {
        if (type instanceof NumericType) {
            return "NUMBER(" + ((NumericType) type).getPrecision() + "," + ((NumericType) type).getScale() + ")";
        }
        if (type instanceof StringType) {
            return "VARCHAR(" + ((StringType) type).getMaxLength() + ")";
        }
        if (type instanceof DateTimeType) {
            return type.getName() + "(" + ((DateTimeType) type).getPrecision() + ")";
        }
        return type.getName();
    }

    /** A procedure of that RETURNS TABLE clause whose body returns the table that query answers. */
    private void tableProcedure(final String name, final String returns, final String query) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS TABLE (" + returns + ") LANGUAGE SQL AS $$"
            + " DECLARE res RESULTSET DEFAULT (" + query + "); BEGIN RETURN TABLE(res); END; $$");
    }

    @Test
    public void aCallAnswersTheDeclaredColumnsWithTheValuesReturned() {
        tableProcedure("p1", "a NUMBER(5,2)", "SELECT 1.7777 AS a UNION ALL SELECT 2");
        final ResultSet declared = engine.executeQuery("CALL p1()");
        assertEquals("A NUMBER(5,2)", columns(declared));
        assertEquals("1.7777 | 2.0000", rows(declared));

        tableProcedure("p2", "\"b c\" INT, d INT", "SELECT 1 AS x, 2 AS y");
        final ResultSet named = engine.executeQuery("CALL p2()");
        assertEquals("b c NUMBER(38,0) | D NUMBER(38,0)", columns(named));
        assertEquals("1 2", rows(named));

        tableProcedure("p3", "a VARCHAR(3)", "SELECT 'abcdef' AS a");
        final ResultSet wider = engine.executeQuery("CALL p3()");
        assertEquals("A VARCHAR(3)", columns(wider));
        assertEquals("abcdef", rows(wider));

        // RETURNS TABLE () declares no columns: the table answers as returned.
        tableProcedure("p4", "", "SELECT 1 AS x, 'y' AS y");
        final ResultSet undeclared = engine.executeQuery("CALL p4()");
        assertEquals("X NUMBER(1,0) | Y VARCHAR(1)", columns(undeclared));
        assertEquals("1 y", rows(undeclared));
    }

    @Test
    public void aReturnedTableOfAnotherShapeIsRefused() {
        final String[][] refused = {
            {"a NUMBER(5,2)", "SELECT 'abc' AS a"},
            {"a INT", "SELECT 1 AS a, 2 AS b"},
            {"a FLOAT", "SELECT 7 AS a"},
            {"a NUMBER(5,2)", "SELECT 1.5::FLOAT AS a"},
            {"a OBJECT", "SELECT PARSE_JSON('{\"k\":1}') AS a"},
            {"a VARIANT", "SELECT OBJECT_CONSTRUCT('k', 1) AS a"},
            {"a TIMESTAMP_NTZ(9)", "SELECT '2024-01-02 03:04:05.123'::TIMESTAMP_NTZ(3) AS a"},
            {"a TIMESTAMP_LTZ", "SELECT '2024-01-02 03:04:05'::TIMESTAMP_NTZ AS a"},
            {"a VARCHAR", "SELECT 1 AS a WHERE 1 = 0"},
        };
        for (int i = 0; i < refused.length; i++) {
            tableProcedure("mismatch" + i, refused[i][0], refused[i][1]);
            assertEquals(MISMATCH, answer("CALL mismatch" + i + "()"), refused[i][0] + " <- " + refused[i][1]);
        }
        final String[][] taken = {
            {"a INT", "SELECT 7 AS a", "7"},
            {"a TIME", "SELECT '03:04:05'::TIME AS a", "03:04:05"},
            {"a OBJECT", "SELECT OBJECT_CONSTRUCT('k', 1) AS a", "{\"k\":1}"},
            {"a BOOLEAN", "SELECT TRUE AS a", "true"},
        };
        for (int i = 0; i < taken.length; i++) {
            tableProcedure("match" + i, taken[i][0], taken[i][1]);
            assertEquals(taken[i][2], answer("CALL match" + i + "()").replace("\n", "").replace(" ", ""),
                taken[i][0] + " <- " + taken[i][1]);
        }
    }

    @Test
    public void aBodyEndingWithoutAReturnIsRefusedOnceItHasRun() {
        engine.execute("CREATE TABLE side_effects (a INT)");
        engine.execute("CREATE PROCEDURE no_return() RETURNS TABLE (a INT) LANGUAGE SQL AS $$"
            + " BEGIN INSERT INTO side_effects VALUES (1); END; $$");
        assertEquals(MISSING_RETURN, answer("CALL no_return()"));
        assertEquals("1", answer("SELECT COUNT(*) FROM side_effects"));
        engine.execute("CREATE PROCEDURE maybe_return(flag BOOLEAN) RETURNS TABLE (a INT) LANGUAGE SQL AS $$"
            + " DECLARE res RESULTSET DEFAULT (SELECT 1 AS a); BEGIN IF (flag) THEN RETURN TABLE(res); END IF; END; $$");
        assertEquals("1", answer("CALL maybe_return(TRUE)"));
        assertEquals(MISSING_RETURN, answer("CALL maybe_return(FALSE)"));
        engine.execute("CREATE PROCEDURE no_columns() RETURNS TABLE () LANGUAGE SQL AS $$ BEGIN LET x := 1; END; $$");
        assertEquals(MISSING_RETURN, answer("CALL no_columns()"));
    }

    @Test
    public void aReturnOfTheOtherKindIsRefusedAtTheReturn() {
        final String tableForScalar = "SQL compilation error: error line 1 at position %d| Declared return type 'TABLE'"
            + " is incompatible with actual return type 'SCALAR'";
        final String scalarForTable = "SQL compilation error: error line 1 at position %d| Declared return type 'SCALAR'"
            + " is incompatible with actual return type 'TABLE'";
        final String[][] cells = {
            {"TABLE (a INT)", " BEGIN RETURN 5; END; ", String.format(tableForScalar, 7)},
            {"TABLE (a INT)", "  BEGIN LET x := 1;   RETURN x; END; ", String.format(tableForScalar, 22)},
            {"TABLE ()", " BEGIN RETURN 'x'; END; ", String.format(tableForScalar, 7)},
            {"TABLE (a INT)", " BEGIN RETURN NULL; END; ", String.format(tableForScalar, 7)},
            {"TABLE (a INT)", " BEGIN BEGIN RETURN 3; END; END; ", String.format(tableForScalar, 13)},
            {"TABLE (a INT)", " BEGIN RETURN nosuch_var; END; ", String.format(tableForScalar, 7)},
            {"INT", " DECLARE res RESULTSET DEFAULT (SELECT 1 AS a); BEGIN RETURN TABLE(res); END; ",
                String.format(scalarForTable, 54)},
            {"INT", " DECLARE res RESULTSET DEFAULT (SELECT 1 AS a); BEGIN IF (TRUE) THEN RETURN TABLE(res); END IF;"
                + " RETURN 1; END; ", String.format(scalarForTable, 69)},
            {"DATE", " DECLARE res RESULTSET DEFAULT (SELECT 1 AS a); BEGIN RETURN 1631711999; RETURN TABLE(res); END; ",
                "SQL compilation error: error line 1 at position 54| Declared return type 'DATE' is incompatible with"
                    + " actual return type 'NUMBER(10,0)'"},
            {"TABLE (a INT)", " DECLARE res RESULTSET DEFAULT (SELECT 1 AS a); BEGIN RETURN TABLE(nosuch_rs); END; ",
                "SQL compilation error: error line 1 at position 54|invalid identifier 'NOSUCH_RS'"},
            {"TABLE (a INT)", " BEGIN LET x := nosuch_var; RETURN 5; END; ",
                "SQL compilation error: error line 1 at position 7| variable 'X' cannot have its type inferred from initializer"},
        };
        for (int i = 0; i < cells.length; i++) {
            engine.execute("CREATE OR REPLACE PROCEDURE kind" + i + "() RETURNS " + cells[i][0] + " LANGUAGE SQL AS $$"
                + cells[i][1] + "$$");
            assertEquals(cells[i][2], answer("CALL kind" + i + "()"), cells[i][0] + ":" + cells[i][1]);
        }
    }

    @Test
    public void aSqlTableFunctionMustDeclareItsColumns() {
        final String mismatch = "Mismatch between declared return signature column count (0) and actual column count (%d)";
        assertEquals(String.format(mismatch, 2), answer("CREATE FUNCTION ft1() RETURNS TABLE () AS 'SELECT 1 AS a, 2 AS b'"));
        assertEquals(String.format(mismatch, 1), answer("CREATE FUNCTION ft2(x INT) RETURNS TABLE () AS 'SELECT x'"));
        assertEquals(String.format(mismatch, 1),
            answer("CREATE FUNCTION ft3() RETURNS TABLE ( ) AS 'SELECT 1 AS a WHERE 1 = 0'"));
    }
}
