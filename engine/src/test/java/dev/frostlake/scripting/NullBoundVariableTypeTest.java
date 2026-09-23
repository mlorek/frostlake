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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scripting variable holding NULL, bound into SQL. Bound into the block's own expression — the scalar subquery on
 * an assignment's right, a LET's, a RETURN's — a NULL text has no width, whatever the declaration says, and what is
 * computed from it follows; a statement the block runs keeps the declared width, which a table built over it stores,
 * and so does a RESULTSET's query, whose column declares the declared type — except where live binds the NULL as no
 * type at all: a whole item of a VALUES row, an argument of a date or time function, and a text wherever a number
 * may meet it. A NULL is no value at all, so a number holding one takes the smallest tag, and so does its length.
 * Every cell is live-verified.
 *
 * <p>Live reuses a statement's compiled plan for the same text, bound NULLs and all, so each statement-form cell
 * below carries a text of its own.
 */
public class NullBoundVariableTypeTest extends BaseDatabaseTest {

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** A block that declares {@code s} as written, then answers what SYSTEM$TYPEOF says of {@code expr} in a query. */
    private static String typed(final String declaration, final String expr) {
        return "DECLARE " + declaration + " t VARCHAR; BEGIN t := (SELECT SYSTEM$TYPEOF(" + expr + ")); RETURN t; END;";
    }

    @Test
    public void aNullTextInTheBlocksOwnExpressionHasNoWidth() {
        assertCells(new String[][] {
            {typed("s VARCHAR(10);", ":s"), "VARCHAR[LOB]"},
            {typed("s VARCHAR(10) DEFAULT NULL;", ":s"), "VARCHAR[LOB]"},
            {typed("s CHAR(5);", ":s"), "VARCHAR[LOB]"},
            {typed("s STRING;", ":s"), "VARCHAR[LOB]"},
            {typed("s VARCHAR(10) DEFAULT 'abc';", ":s"), "VARCHAR(3)[LOB]"},
            {"DECLARE s VARCHAR(10) DEFAULT 'abc'; t VARCHAR; BEGIN s := NULL; t := (SELECT SYSTEM$TYPEOF(:s));"
                + " RETURN t; END;", "VARCHAR[LOB]"},
            {"DECLARE s VARCHAR(10); BEGIN LET t VARCHAR := (SELECT SYSTEM$TYPEOF(:s)); RETURN t; END;",
                "VARCHAR[LOB]"},
            {"DECLARE s VARCHAR(10); BEGIN RETURN (SELECT SYSTEM$TYPEOF(:s)); END;", "VARCHAR[LOB]"},
        });
    }

    @Test
    public void whatIsComputedFromTheNullTextFollows() {
        assertCells(new String[][] {
            {typed("s VARCHAR(10);", ":s || 'x'"), "VARCHAR(134217728)[LOB]"},
            {typed("s VARCHAR(10);", "IFF(TRUE, :s, NULL)"), "VARCHAR[LOB]"},
            {typed("s VARCHAR(10);", "COALESCE(:s, 'ab')"), "VARCHAR[LOB]"},
            {typed("s VARCHAR(10);", "UPPER(:s)"), "VARCHAR[LOB]"},
            {typed("s VARCHAR(10);", ":s::VARCHAR(5)"), "VARCHAR(5)[LOB]"},
        });
    }

    @Test
    public void aProcedureParameterHoldingNullBindsTheSameWay() {
        engine.execute("CREATE OR REPLACE PROCEDURE pn(s VARCHAR(10)) RETURNS VARCHAR LANGUAGE SQL"
            + " AS 'DECLARE t VARCHAR; BEGIN t := (SELECT SYSTEM$TYPEOF(:s)); RETURN t; END;'");
        assertEquals("VARCHAR[LOB]", answer("CALL pn(NULL)"));
        assertEquals("VARCHAR(3)[LOB]", answer("CALL pn('abc')"));
    }

    @Test
    public void aTableBuiltOverTheNullTextKeepsTheDeclaredWidth() {
        engine.execute("DECLARE s VARCHAR(10); BEGIN CREATE OR REPLACE TABLE cn AS SELECT :s AS c,"
            + " UPPER(:s) AS u, :s || 'x' AS x, COALESCE(:s, 'ab') AS e; RETURN 'ok'; END;");
        assertEquals("VARCHAR(10)[LOB] VARCHAR(30)[LOB] VARCHAR(11)[LOB] VARCHAR(10)[LOB]",
            answer("SELECT SYSTEM$TYPEOF(c) || ' ' || SYSTEM$TYPEOF(u) || ' ' || SYSTEM$TYPEOF(x) || ' '"
                + " || SYSTEM$TYPEOF(e) FROM cn"));
    }

    @Test
    public void aNullNumberTakesTheSmallestTag() {
        assertCells(new String[][] {
            {typed("s NUMBER(10,2);", ":s"), "NUMBER(10,2)[SB1]"},
            {typed("s INT;", ":s"), "NUMBER(38,0)[SB1]"},
            {typed("s NUMBER(38,5);", ":s"), "NUMBER(38,5)[SB1]"},
            {typed("s NUMBER(10,2);", ":s + 1"), "NUMBER(11,2)[SB1]"},
            {"DECLARE s NUMBER(10,2); t VARCHAR; BEGIN SELECT SYSTEM$TYPEOF(:s + 1) INTO :t; RETURN t; END;",
                "NUMBER(11,2)[SB1]"},
            {typed("s NUMBER(10,2) DEFAULT 1.5;", ":s"), "NUMBER(10,2)[SB2]"},
            {typed("s DATE;", ":s"), "DATE[SB4]"},
            {typed("s TIMESTAMP_NTZ(3);", ":s"), "TIMESTAMP_NTZ(3)[SB8]"},
            {typed("s FLOAT;", ":s"), "FLOAT[DOUBLE]"},
        });
    }

    @Test
    public void theLengthOfANullIsTheSmallestTagToo() {
        assertCells(new String[][] {
            {typed("s VARCHAR(10);", "LENGTH(:s)"), "NUMBER(18,0)[SB1]"},
            {"SELECT SYSTEM$TYPEOF(LENGTH(NULL::VARCHAR(10)))", "NUMBER(18,0)[SB1]"},
        });
    }

    /** A column alias of its own for each declaration and expression, so no two cells share a statement text. */
    private static String alias(final String declaration, final String expr) {
        return "c" + Integer.toHexString((declaration + expr).hashCode() & 0x7fffffff);
    }

    /** A block that declares {@code s} as written, then returns a RESULTSET over SYSTEM$TYPEOF of {@code expr}. */
    private static String resultSetTyped(final String declaration, final String expr) {
        return "DECLARE " + declaration + " res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(" + expr + ") AS "
            + alias(declaration, expr) + "); RETURN TABLE(res); END;";
    }

    /** The declared type of the one column a RESULTSET over {@code :s} declares, spelled with its parameters. */
    private String resultSetColumnType(final String declaration) {
        final DataType type = engine.executeQuery("DECLARE " + declaration + " res RESULTSET; BEGIN res := (SELECT :s AS "
            + alias(declaration, ":s column") + "); RETURN TABLE(res); END;").getColumns().get(0).getDataType();
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            return numeric.getName() + "(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        return type.getName();
    }

    @Test
    public void aNullBoundIntoAResultSetsQueryKeepsTheDeclaredType() {
        assertCells(new String[][] {
            {resultSetTyped("s VARCHAR(10);", ":s"), "VARCHAR(10)[LOB]"},
            {resultSetTyped("s VARCHAR(3);", ":s"), "VARCHAR(3)[LOB]"},
            {resultSetTyped("s CHAR(5);", ":s"), "VARCHAR(5)[LOB]"},
            {resultSetTyped("s NUMBER(10,2);", ":s"), "NUMBER(10,2)[SB1]"},
            {resultSetTyped("s INT;", ":s"), "NUMBER(38,0)[SB1]"},
            {resultSetTyped("s FLOAT;", ":s"), "FLOAT[DOUBLE]"},
            {resultSetTyped("s DATE;", ":s"), "DATE[SB4]"},
            {resultSetTyped("s TIME;", ":s"), "TIME(9)[SB8]"},
            {resultSetTyped("s TIMESTAMP_LTZ;", ":s"), "TIMESTAMP_LTZ(9)[SB16]"},
            {resultSetTyped("s BOOLEAN;", ":s"), "BOOLEAN[SB1]"},
            {resultSetTyped("s VARIANT;", ":s"), "VARIANT[LOB]"},
            {resultSetTyped("s ARRAY;", ":s"), "ARRAY[LOB]"},
            {resultSetTyped("s BINARY;", ":s"), "BINARY(67108864)[LOB]"},
            {resultSetTyped("s NUMBER(10,2);", ":s + 1"), "NUMBER(11,2)[SB1]"},
            {resultSetTyped("s VARCHAR(10);", "UPPER(:s)"), "VARCHAR(30)[LOB]"},
            {resultSetTyped("s VARCHAR(10);", "COALESCE(:s, 'ab')"), "VARCHAR(10)[LOB]"},
            {"DECLARE s NUMBER(10,2); res RESULTSET DEFAULT (SELECT SYSTEM$TYPEOF(:s) AS cdefault);"
                + " BEGIN RETURN TABLE(res); END;", "NUMBER(10,2)[SB1]"},
            {"DECLARE s NUMBER(10,2); BEGIN LET res RESULTSET := (SELECT SYSTEM$TYPEOF(:s) AS clet);"
                + " RETURN TABLE(res); END;", "NUMBER(10,2)[SB1]"},
        });
    }

    @Test
    public void aResultSetsColumnOverTheNullDeclaresTheDeclaredType() {
        assertEquals("NUMBER(10,2)", resultSetColumnType("s NUMBER(10,2);"));
        assertEquals("VARCHAR(10)", resultSetColumnType("s VARCHAR(10);"));
        assertEquals("DATE", resultSetColumnType("s DATE;"));
        assertEquals("BOOLEAN", resultSetColumnType("s BOOLEAN;"));
    }

    @Test
    public void aProcedureParameterHoldingNullKeepsItsTypeInAResultSet() {
        engine.execute("CREATE OR REPLACE PROCEDURE prs(s NUMBER(10,2)) RETURNS TABLE() LANGUAGE SQL"
            + " AS 'DECLARE res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(:s) AS cproc); RETURN TABLE(res); END;'");
        assertEquals("NUMBER(10,2)[SB1]", answer("CALL prs(NULL)"));
    }

    /** A block returning a RESULTSET over SYSTEM$TYPEOF of the first column of a VALUES list with these rows. */
    private static String resultSetValues(final String declaration, final String rows) {
        return "DECLARE " + declaration + " res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF($1) AS "
            + alias(declaration, "values " + rows) + " FROM VALUES " + rows + "); RETURN TABLE(res); END;";
    }

    /** The refusal a block answers, or the empty text when it runs. */
    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    @Test
    public void aNullInAValuesRowOrADateFunctionHasNoType() {
        assertCells(new String[][] {
            {resultSetValues("s NUMBER(10,2);", "(:s), (1)"), "NUMBER(1,0)[SB1]"},
            {resultSetValues("s VARCHAR(10);", "(:s), ('ab')"), "VARCHAR(2)[LOB]"},
            {resultSetValues("s DATE;", "(:s), ('2024-01-01')"), "VARCHAR(10)[LOB]"},
            {resultSetTyped("s DATE;", "DATEADD(day, 1, :s)"), "NULL[LOB]"},
            {resultSetTyped("s DATE;", "YEAR(:s)"), "NULL[LOB]"},
            {resultSetTyped("s DATE;", "EXTRACT(year FROM :s)"), "NULL[LOB]"},
            {resultSetTyped("s DATE;", "DATEDIFF(day, :s, '2024-01-01'::DATE)"), "NULL[LOB]"},
            {resultSetTyped("s TIME;", "TIMEADD(minute, 1, :s)"), "NULL[LOB]"},
            {resultSetTyped("s TIMESTAMP_NTZ(3);", "DATE_TRUNC('day', :s)"), "NULL[LOB]"},
            {resultSetTyped("s NUMBER(10,2);", "DATEADD(day, :s, '2024-01-01'::DATE)"), "NULL[LOB]"},
            {resultSetTyped("s NUMBER(10,2);", "TRUNC(:s)"), "NULL[LOB]"},
            {resultSetTyped("s DATE;", "COALESCE(:s, NULL)"), "DATE[SB4]"},
            {resultSetTyped("s DATE;", "MAX(:s)"), "DATE[SB4]"},
        });
    }

    @Test
    public void arithmeticLiveRefusesKeepsTheTemporalType() {
        final String time = refusal(resultSetTyped("s TIME;", ":s + 1"));
        assertTrue(time.contains("Invalid argument types for function '+': (TIME(9), NUMBER(1,0))"), time);
        final String timestamp = refusal(resultSetTyped("s TIMESTAMP_NTZ(3);", ":s + 1"));
        assertTrue(timestamp.contains("Invalid argument types for function '+': (TIMESTAMP_NTZ(3), NUMBER(1,0))"),
            timestamp);
    }

    @Test
    public void aNullTextKeepsItsTypeOnlyWhereItIsReadAsText() {
        assertCells(new String[][] {
            {resultSetTyped("s VARCHAR(10);", ":s + 1"), "NUMBER(19,0)[SB1]"},
            {resultSetTyped("s VARCHAR(10);", ":s / 2"), "NUMBER(24,6)[SB1]"},
            {resultSetTyped("s VARCHAR(10);", "COALESCE(:s, 5)"), "NUMBER(1,0)[SB1]"},
            {resultSetTyped("s VARCHAR(10);", "LEAST(:s, 5)"), "NUMBER(1,0)[SB1]"},
            {resultSetTyped("s VARCHAR(10);", "ROUND(:s, 1)"), "NUMBER(18,0)[SB1]"},
            {resultSetTyped("s VARCHAR(10);", ":s || 'a'"), "VARCHAR(11)[LOB]"},
            {resultSetTyped("s VARCHAR(10);", "MAX(:s)"), "VARCHAR(10)[LOB]"},
            {resultSetTyped("s VARCHAR(10);", "IFNULL(:s, 'ab')"), "VARCHAR(10)[LOB]"},
            {resultSetTyped("s VARCHAR(10);", "ABS(:s)"), "FLOAT[DOUBLE]"},
            {resultSetTyped("s VARCHAR(10);", "-:s"), "FLOAT[DOUBLE]"},
            {"DECLARE s VARCHAR(10); res RESULTSET; BEGIN res := (SELECT SYSTEM$TYPEOF(a) AS cunion FROM (SELECT :s AS a"
                + " UNION ALL SELECT 5)); RETURN TABLE(res); END;", "NUMBER(1,0)[SB1]"},
        });
    }

    @Test
    public void aStatementABlockRunsAsTextKeepsTheTypeWhereLiveDoes() {
        engine.execute("CREATE OR REPLACE TABLE tvn (v VARIANT, a ARRAY, o OBJECT, n NUMBER(10,2), d DATE)");
        assertEquals("ok", answer("DECLARE s VARCHAR(10); BEGIN IF (TRUE) THEN INSERT INTO tvn (v, a, o, n, d)"
            + " VALUES (:s, :s, :s, :s, :s); END IF; RETURN 'ok'; END;"));
        assertEquals("ok", answer("DECLARE s NUMBER(10,2); BEGIN IF (TRUE) THEN INSERT INTO tvn (v, a, o, n, d)"
            + " VALUES (:s, :s, :s, :s, :s); END IF; RETURN 'ok'; END;"));
        final String refused = refusal("DECLARE s DATE; BEGIN IF (TRUE) THEN INSERT INTO tvn (n) SELECT :s; END IF;"
            + " RETURN 'ok'; END;");
        assertTrue(refused.contains("Expression type does not match column data type, expecting NUMBER(10,2) but got"
            + " DATE for column N"), refused);
        engine.execute("DECLARE s VARCHAR(10); BEGIN IF (TRUE) THEN CREATE OR REPLACE TABLE ctn AS SELECT :s AS c;"
            + " END IF; RETURN 'ok'; END;");
        assertEquals("VARCHAR(10)[LOB]", answer("SELECT SYSTEM$TYPEOF(c) FROM ctn"));
    }

    @Test
    public void aNullInALimitStillReadsAsNoLimit() {
        assertEquals("1", answer("DECLARE s NUMBER(10,2); res RESULTSET; BEGIN res := (SELECT * FROM (SELECT 1 AS a)"
            + " LIMIT :s); RETURN TABLE(res); END;"));
    }
}
