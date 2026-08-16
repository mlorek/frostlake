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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The type a block's or a CALL's result column declares when the RETURN that ran keeps its value's own
 * type: the static type of the expression it names, as SQL types it over the names in scope — a FOR
 * counter among them — whatever RETURNS type the procedure declares, with a text or a binary at full
 * width. A sibling RETURN that did not run plays no part. A CALL whose body ran no RETURN answers a NULL
 * of its declared RETURNS type. Every cell is live-verified, read back the way a client can: DESCRIBE
 * over a table made from the result.
 */
public class ReturnExpressionResultTypeTest extends BaseDatabaseTest {

    /** The declared type of the previous statement's one result column, as DESCRIBE reads it back. */
    private String previousResultType() {
        engine.execute("CREATE OR REPLACE TABLE ret_type AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        return String.valueOf(engine.executeQuery("DESC TABLE ret_type").getRows().get(0).getValue(1));
    }

    /** The result column's type of a block run as the text of an EXECUTE IMMEDIATE. */
    private String blockType(final String block) {
        engine.executeQuery("EXECUTE IMMEDIATE $$ " + block + " $$");
        return previousResultType();
    }

    private void procedure(final String name, final String returns, final String body) {
        engine.execute("CREATE OR REPLACE PROCEDURE " + name + "() RETURNS " + returns + " LANGUAGE SQL AS $$ "
            + body + " $$");
    }

    /** The CALL result column's type of a procedure without parameters. */
    private String callType(final String name, final String returns, final String body) {
        procedure(name, returns, body);
        engine.executeQuery("CALL " + name + "()");
        return previousResultType();
    }

    private static void assertNumber(final String expected, final Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(new BigDecimal(String.valueOf(actual))),
            expected + " vs " + actual);
    }

    @Test
    public void aNumericExpressionTypesTheBlocksColumn() {
        assertEquals("NUMBER(5,4)", blockType("BEGIN RETURN (SELECT 1.7777); END;"));
        assertEquals("NUMBER(7,4)", blockType("BEGIN RETURN 1.7777 + 1; END;"));
        assertEquals("NUMBER(7,4)", blockType("BEGIN RETURN 1.7777 - 1; END;"));
        assertEquals("NUMBER(2,0)", blockType("BEGIN RETURN 1 + 1; END;"));
        assertEquals("NUMBER(7,6)", blockType("BEGIN RETURN 1 / 3; END;"));
        assertEquals("NUMBER(6,4)", blockType("BEGIN RETURN ABS(1.7777); END;"));
        assertEquals("NUMBER(6,2)", blockType("BEGIN RETURN ROUND(1.7777, 2); END;"));
        assertEquals("NUMBER(2,1)", blockType("BEGIN RETURN CASE WHEN TRUE THEN 1 ELSE 2.5 END; END;"));
        assertEquals("NUMBER(5,4)", blockType("BEGIN RETURN IFF(TRUE, 1.7777, 0); END;"));
        assertEquals("NUMBER(10,4)", blockType("BEGIN RETURN TO_NUMBER('1.7777', 10, 4); END;"));
        assertEquals("NUMBER(10,4)", blockType("BEGIN RETURN 1.7777::NUMBER(10,4); END;"));
        assertEquals("NUMBER(5,2)", blockType("BEGIN RETURN NULL::NUMBER(5,2); END;"));
        assertEquals("NUMBER(19,0)", blockType("BEGIN RETURN NULL + 1; END;"));
        assertEquals("FLOAT", blockType("BEGIN RETURN SQRT(4); END;"));
        // Over the typed names in scope, a bind among them.
        assertEquals("NUMBER(6,2)", blockType("DECLARE x NUMBER(5,2) := 1.25; BEGIN RETURN x + 1; END;"));
        assertEquals("NUMBER(6,2)", blockType("DECLARE x NUMBER(5,2) := 1.25; BEGIN RETURN :x + 1; END;"));
        assertEquals("NUMBER(10,4)", blockType("DECLARE x NUMBER(5,2) := 1.25; BEGIN RETURN x * x; END;"));
        assertEquals("NUMBER(10,4)", blockType("DECLARE x NUMBER(10,4) := 1.7777; BEGIN RETURN -x; END;"));
        assertEquals("NUMBER(5,2)", blockType("DECLARE x NUMBER(5,2) := 1.25; BEGIN RETURN NVL(x, 0); END;"));
        assertEquals("NUMBER(38,0)", blockType("BEGIN LET x := 1; RETURN x * 2; END;"));
        assertEquals("FLOAT", blockType("DECLARE x FLOAT := 1.5; BEGIN RETURN x + 1; END;"));
        // Over the tables the subquery reads.
        engine.execute("CREATE OR REPLACE TABLE rt (c NUMBER(5,2))");
        engine.execute("INSERT INTO rt VALUES (1.25), (2.5)");
        assertEquals("NUMBER(18,0)", blockType("BEGIN RETURN (SELECT COUNT(*) FROM rt); END;"));
        assertEquals("NUMBER(5,2)", blockType("BEGIN RETURN (SELECT MAX(c) FROM rt); END;"));
        // A block run directly answers the same.
        final ResultSet direct = engine.executeQuery("BEGIN RETURN 1.7777 + 1; END;");
        assertNumber("2.7777", direct.getRows().get(0).getValue(0));
        assertEquals("NUMBER(7,4)", previousResultType());
    }

    @Test
    public void everyOtherFamilyKeepsItsOwnType() {
        assertEquals("DATE", blockType("BEGIN RETURN CURRENT_DATE(); END;"));
        assertEquals("DATE", blockType("BEGIN RETURN DATEADD(day, 1, CURRENT_DATE()); END;"));
        assertEquals("DATE", blockType("DECLARE d DATE := '2020-01-15'; BEGIN RETURN d + 1; END;"));
        assertEquals("TIMESTAMP_LTZ(9)", blockType("BEGIN RETURN CURRENT_TIMESTAMP(); END;"));
        assertEquals("TIMESTAMP_NTZ(9)", blockType("BEGIN RETURN TO_TIMESTAMP_NTZ('2020-01-15 10:00:00'); END;"));
        assertEquals("TIME(9)", blockType("BEGIN RETURN TIME_FROM_PARTS(1, 2, 3); END;"));
        assertEquals("BOOLEAN", blockType("BEGIN RETURN 1 = 1; END;"));
        assertEquals("OBJECT", blockType("BEGIN RETURN OBJECT_CONSTRUCT('a', 1); END;"));
        assertEquals("OBJECT", blockType("BEGIN RETURN {'a': 1}; END;"));
        assertEquals("ARRAY", blockType("BEGIN RETURN ARRAY_CONSTRUCT(1, 2); END;"));
        assertEquals("ARRAY", blockType("BEGIN RETURN [1, 2]; END;"));
        assertEquals("VARIANT", blockType("BEGIN RETURN PARSE_JSON('{\"a\":1}'); END;"));
        assertEquals("VARIANT", blockType("DECLARE v VARIANT := PARSE_JSON('{\"a\":1}'); BEGIN RETURN v:a; END;"));
        // A text and a binary read at full width, whatever width they carry.
        assertEquals("VARCHAR(16777216)", blockType("BEGIN RETURN UPPER('ab'); END;"));
        assertEquals("VARCHAR(16777216)", blockType("BEGIN RETURN (SELECT 'abc'); END;"));
        assertEquals("VARCHAR(16777216)", blockType("BEGIN RETURN 'abc'::VARCHAR(5); END;"));
        assertEquals("VARCHAR(16777216)", blockType("BEGIN RETURN (SELECT NULL); END;"));
        assertEquals("BINARY(8388608)", blockType("BEGIN RETURN TO_BINARY('ab', 'HEX'); END;"));
        assertEquals("BINARY(8388608)",
            blockType("DECLARE x BINARY(10) := TO_BINARY('ab', 'HEX'); BEGIN RETURN x; END;"));
    }

    @Test
    public void aForCounterTypesItsExpressionsWhileTheBareNameStaysText() {
        assertEquals("NUMBER(10,0)", blockType("BEGIN FOR i IN 1 TO 1 DO RETURN i + 1; END FOR; END;"));
        assertEquals("NUMBER(11,1)",
            blockType("BEGIN FOR i IN 1 TO 3 DO IF (i = 2) THEN RETURN i * 1.5; END IF; END FOR; RETURN 0; END;"));
        // Bound, the counter is its NUMBER(9,0); written bare, even in parentheses, it is text.
        final ResultSet bound =
            engine.executeQuery("EXECUTE IMMEDIATE $$ BEGIN FOR i IN 1 TO 1 DO RETURN :i; END FOR; END; $$");
        assertNumber("1", bound.getRows().get(0).getValue(0));
        assertEquals("NUMBER(9,0)", previousResultType());
        assertEquals("VARCHAR(16777216)", blockType("BEGIN FOR i IN 1 TO 1 DO RETURN i; END FOR; END;"));
        assertEquals("VARCHAR(16777216)", blockType("BEGIN FOR i IN 1 TO 1 DO RETURN (i); END FOR; END;"));
    }

    @Test
    public void theReturnThatRanDecides() {
        assertEquals("NUMBER(7,4)",
            blockType("BEGIN IF (TRUE) THEN RETURN 1.7777 + 1; ELSE RETURN CURRENT_DATE(); END IF; END;"));
        assertEquals("DATE",
            blockType("BEGIN IF (FALSE) THEN RETURN 1.7777 + 1; ELSE RETURN CURRENT_DATE(); END IF; END;"));
        assertEquals("NUMBER(7,4)",
            blockType("BEGIN IF (TRUE) THEN RETURN 1.7777 + 1; END IF; RETURN 123456789 + 1; END;"));
        assertEquals("NUMBER(10,0)",
            blockType("BEGIN IF (FALSE) THEN RETURN 1.7777 + 1; END IF; RETURN 123456789 + 1; END;"));
        assertEquals("NUMBER(2,0)", blockType("BEGIN IF (TRUE) THEN RETURN 1 + 1; END IF; RETURN 'abc' || 'd'; END;"));
        assertEquals("VARCHAR(16777216)",
            blockType("BEGIN IF (FALSE) THEN RETURN 1 + 1; END IF; RETURN 'abc' || 'd'; END;"));
        assertEquals("NUMBER(7,4)",
            blockType("BEGIN SELECT 1/0; RETURN 1 + 1; EXCEPTION WHEN OTHER THEN RETURN 1.7777 + 1; END;"));
        assertEquals("NUMBER(7,4)", blockType("BEGIN BEGIN RETURN 1.7777 + 1; END; END;"));
        assertEquals("NUMBER(7,4)", blockType("BEGIN CASE WHEN TRUE THEN RETURN 1.7777 + 1; END CASE; END;"));
        assertEquals("DATE", blockType("BEGIN WHILE (TRUE) DO RETURN CURRENT_DATE(); END WHILE; END;"));
    }

    @Test
    public void aCallsColumnFollowsTheReturnedExpressionNotItsReturnsClause() {
        procedure("p_subq", "NUMBER(5,2)", "BEGIN RETURN (SELECT 1.7777); END;");
        final ResultSet subquery = engine.executeQuery("CALL p_subq()");
        assertEquals("P_SUBQ", subquery.getColumns().get(0).getName());
        assertNumber("1.7777", subquery.getRows().get(0).getValue(0));
        assertEquals("NUMBER(5,4)", previousResultType());
        assertEquals("NUMBER(7,4)", callType("p_text", "VARCHAR", "BEGIN RETURN 1.7777 + 1; END;"));
        assertEquals("NUMBER(7,4)", callType("p_float", "FLOAT", "BEGIN RETURN 1.7777 + 1; END;"));
        assertEquals("NUMBER(7,4)", callType("p_whole", "NUMBER(38,0)", "BEGIN RETURN 1.7777 + 1; END;"));
        assertEquals("DATE", callType("p_date", "NUMBER", "BEGIN RETURN CURRENT_DATE(); END;"));
        assertEquals("DATE", callType("p_text_date", "VARCHAR", "BEGIN RETURN CURRENT_DATE(); END;"));
        assertEquals("OBJECT", callType("p_object", "VARIANT", "BEGIN RETURN OBJECT_CONSTRUCT('a', 1); END;"));
        assertEquals("NUMBER(2,0)", callType("p_sum", "VARIANT", "BEGIN RETURN 1 + 1; END;"));
        assertEquals("NUMBER(6,4)", callType("p_abs", "NUMBER(5,2)", "BEGIN RETURN ABS(1.7777); END;"));
        assertEquals("NUMBER(11,4)",
            callType("p_cast_sum", "NUMBER(5,2)", "BEGIN RETURN 1.7777::NUMBER(10,4) + 0; END;"));
        assertEquals("NUMBER(11,4)",
            callType("p_var_sum", "NUMBER(5,2)", "DECLARE x NUMBER(10,4) := 1.7777; BEGIN RETURN x + 1; END;"));
        assertEquals("NUMBER(7,4)",
            callType("p_nested", "NUMBER(5,2)", "BEGIN IF (TRUE) THEN RETURN 1.7777 + 1; END IF; END;"));
        assertEquals("NUMBER(10,0)",
            callType("p_counter", "VARCHAR", "BEGIN FOR i IN 1 TO 1 DO RETURN i + 1; END FOR; END;"));
        assertEquals("NUMBER(9,0)",
            callType("p_bound", "VARCHAR", "BEGIN FOR i IN 1 TO 1 DO RETURN :i; END FOR; END;"));
        assertEquals("VARCHAR(16777216)", callType("p_width", "VARCHAR(10)", "BEGIN RETURN (SELECT 'abc'); END;"));
        // A parameter types the expression over it as a declared name does.
        engine.execute("CREATE OR REPLACE PROCEDURE p_param(a NUMBER(5,2)) RETURNS NUMBER LANGUAGE SQL AS $$"
            + " BEGIN RETURN a + 1; END; $$");
        final ResultSet param = engine.executeQuery("CALL p_param(1.7777)");
        assertNumber("2.78", param.getRows().get(0).getValue(0));
        assertEquals("NUMBER(6,2)", previousResultType());
        // A RETURN the declared type converts still answers in it.
        assertEquals("NUMBER(5,2)", callType("p_literal", "NUMBER(5,2)", "BEGIN RETURN 1.7777; END;"));
        assertEquals("BINARY(8388608)", callType("p_binary", "BINARY(10)",
            "DECLARE x BINARY(10) := TO_BINARY('ab', 'HEX'); BEGIN RETURN x; END;"));
    }

    @Test
    public void aCallThatRanNoReturnAnswersANullOfItsDeclaredType() {
        assertEquals("NUMBER(5,2)", callType("n_number", "NUMBER(5,2)", "BEGIN LET x := 1; END;"));
        assertEquals("NUMBER(38,0)", callType("n_whole", "NUMBER", "BEGIN LET x := 1; END;"));
        assertEquals("FLOAT", callType("n_float", "FLOAT", "BEGIN LET x := 1; END;"));
        assertEquals("DATE", callType("n_date", "DATE", "BEGIN LET x := 1; END;"));
        assertEquals("TIMESTAMP_NTZ(9)", callType("n_ts", "TIMESTAMP_NTZ", "BEGIN LET x := 1; END;"));
        assertEquals("VARIANT", callType("n_variant", "VARIANT", "BEGIN LET x := 1; END;"));
        assertEquals("OBJECT", callType("n_object", "OBJECT", "BEGIN LET x := 1; END;"));
        assertEquals("ARRAY", callType("n_array", "ARRAY", "BEGIN LET x := 1; END;"));
        assertEquals("VARCHAR(16777216)", callType("n_text", "VARCHAR(10)", "BEGIN LET x := 1; END;"));
        assertEquals("BINARY(8388608)", callType("n_binary", "BINARY", "BEGIN LET x := 1; END;"));
        assertEquals("BINARY(8388608)", callType("n_binary_10", "BINARY(10)", "BEGIN LET x := 1; END;"));
        assertEquals("NUMBER(5,2)", callType("n_select", "NUMBER(5,2)", "BEGIN SELECT 1; END;"));
        assertEquals("NUMBER(5,2)",
            callType("n_handler", "NUMBER(5,2)", "BEGIN SELECT 1/0; EXCEPTION WHEN OTHER THEN LET y := 2; END;"));
        // An earlier RETURN elsewhere types nothing here.
        procedure("n_after", "NUMBER(5,2)", "BEGIN LET x := 1; END;");
        engine.executeQuery("EXECUTE IMMEDIATE $$ BEGIN RETURN 1.7777; END; $$");
        engine.executeQuery("CALL n_after()");
        assertEquals("NUMBER(5,2)", previousResultType());
        procedure("n_skipped", "VARCHAR", "BEGIN IF (FALSE) THEN RETURN 1; END IF; END;");
        engine.executeQuery("EXECUTE IMMEDIATE $$ BEGIN RETURN CURRENT_DATE(); END; $$");
        engine.executeQuery("CALL n_skipped()");
        assertEquals("VARCHAR(16777216)", previousResultType());
        // A bare RETURN is a RETURN: its NULL stays the nominal VARCHAR.
        assertEquals("VARCHAR(16777216)", callType("n_bare", "NUMBER(5,2)", "BEGIN RETURN; END;"));
        assertEquals("VARCHAR(16777216)",
            callType("n_bare_nested", "NUMBER(5,2)", "BEGIN IF (TRUE) THEN RETURN; END IF; END;"));
    }
}
