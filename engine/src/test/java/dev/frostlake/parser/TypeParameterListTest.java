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

package dev.frostlake.parser;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A type's parameter list is judged by what the type takes: the character types and the FLOAT spellings take
 * empty parentheses or one parameter, NUMBER one or two, the binary and time types exactly one, and every other
 * type none at all. A list the type does not take is a syntax error, and the token it names depends on where the
 * type stands: after {@code ::} the token that cannot continue the list, in a CAST or a column the opening
 * parenthesis when the list cannot begin (live-verified).
 */
public class TypeParameterListTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void theCharacterAndFloatTypesTakeEmptyParentheses() {
        for (final String type : new String[] {"VARCHAR", "CHAR", "CHARACTER", "STRING", "TEXT", "NCHAR", "NVARCHAR",
            "NVARCHAR2", "CHAR VARYING", "FLOAT", "FLOAT4", "FLOAT8", "REAL"}) {
            engine.execute("SELECT NULL::" + type + "()");
            engine.execute("SELECT CAST(NULL AS " + type + "())");
        }
        engine.execute("CREATE OR REPLACE TABLE tp_v (a VARCHAR(), b CHAR(), c FLOAT())");
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE tp_v");
        assertEquals("VARCHAR(16777216)", String.valueOf(described.getRows().get(0).getValue(1)));
        assertEquals("VARCHAR(1)", String.valueOf(described.getRows().get(1).getValue(1)));
        assertEquals("FLOAT", String.valueOf(described.getRows().get(2).getValue(1)));
    }

    @Test
    public void theListsEachTypeTakesStillRead() {
        engine.execute("SELECT 1::NUMBER(5), 1::NUMBER(5,2), 1::DECIMAL(5, 2), '1'::VARCHAR(5), 1::FLOAT(5),"
            + " '10:00'::TIME(3), TO_BINARY('01')::BINARY(5), '2024-01-01'::TIMESTAMP_NTZ(3)");
        engine.execute("CREATE OR REPLACE TABLE tp_ok (a NUMBER(5,2), b VARCHAR(5), c TIME(3), d BINARY(5),"
            + " e TIMESTAMP_LTZ(3), f FLOAT(5))");
    }

    @Test
    public void anEmptyListAfterColonsIsRefusedAtItsClosingParenthesis() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 20 unexpected ')'.",
            refusal("SELECT NULL::NUMBER()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected ')'.",
            refusal("SELECT NULL::DECIMAL()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 17 unexpected ')'.",
            refusal("SELECT NULL::INT()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 20 unexpected ')'.",
            refusal("SELECT NULL::DOUBLE()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 30 unexpected ')'.",
            refusal("SELECT NULL::DOUBLE PRECISION()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 20 unexpected ')'.",
            refusal("SELECT NULL::BINARY()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected ')'.",
            refusal("SELECT NULL::BOOLEAN()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 18 unexpected ')'.",
            refusal("SELECT NULL::DATE()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 18 unexpected ')'.",
            refusal("SELECT NULL::TIME()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 23 unexpected ')'.",
            refusal("SELECT NULL::TIMESTAMP()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 27 unexpected ')'.",
            refusal("SELECT NULL::TIMESTAMP_NTZ()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected ')'.",
            refusal("SELECT NULL::VARIANT()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 23 unexpected ')'.",
            refusal("SELECT NULL::GEOGRAPHY()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected ')'.",
            refusal("SELECT NULL::NUMBER ()"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected ')'.",
            refusal("SELECT 1 WHERE 1::INT() = 1"));
    }

    @Test
    public void anEmptyListElsewhereIsRefusedAtItsOpeningParenthesis() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 26 unexpected '('.",
            refusal("SELECT CAST(NULL AS NUMBER())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 23 unexpected '('.",
            refusal("SELECT CAST(NULL AS INT())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 26 unexpected '('.",
            refusal("SELECT CAST(NULL AS DOUBLE())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 33 unexpected '('.",
            refusal("SELECT CAST(NULL AS TIMESTAMP_LTZ())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 24 unexpected '('.",
            refusal("SELECT CAST(NULL AS DATE())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 29 unexpected '('.",
            refusal("SELECT TRY_CAST('1' AS NUMBER())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 29 unexpected '('.",
            refusal("SELECT TRY_CAST('1' AS BINARY())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 38 unexpected '('.",
            refusal("CREATE OR REPLACE TABLE tp_t (a NUMBER())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 45 unexpected '('.",
            refusal("CREATE OR REPLACE TABLE tp_t (a TIMESTAMP_LTZ())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 39 unexpected '('.",
            refusal("CREATE OR REPLACE TABLE tp_t (a BOOLEAN())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 39 unexpected '('.",
            refusal("CREATE OR REPLACE TABLE tp_t (a VARIANT())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 40 unexpected '('.",
            refusal("CREATE OR REPLACE FUNCTION tp_f(x NUMBER()) RETURNS INT AS $$ 1 $$"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 48 unexpected '('.",
            refusal("CREATE OR REPLACE FUNCTION tp_g() RETURNS NUMBER() AS $$ NULL $$"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 40 unexpected '('.",
            refusal("DROP FUNCTION IF EXISTS tp_nosuch(NUMBER())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 19 unexpected '('.",
            refusal("EXECUTE IMMEDIATE $$ BEGIN LET x NUMBER() := NULL; RETURN x; END; $$"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 17 unexpected '('.",
            refusal("EXECUTE IMMEDIATE $$ DECLARE x NUMBER(); BEGIN RETURN 1; END; $$"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 38 unexpected '('.",
            refusal("CREATE OR REPLACE TABLE tp_a (a NUMBER(), b DATE())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 25 unexpected '('.",
            refusal("SELECT NULL::ARRAY(NUMBER())"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 28 unexpected '('.",
            refusal("SELECT NULL::OBJECT(a NUMBER())"));
    }

    @Test
    public void afterColonsAListIsRefusedWhereItGoesWrong() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 17 unexpected '5'.",
            refusal("SELECT NULL::INT(5)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 17 unexpected '5'.\nsyntax error line 1 at position 20 unexpected ')'.",
            refusal("SELECT NULL::INT(5,2)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 20 unexpected '5'.",
            refusal("SELECT NULL::DOUBLE(5)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected '5'.",
            refusal("SELECT NULL::BOOLEAN(5)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 18 unexpected '5'.\nsyntax error line 1 at position 21 unexpected ')'.",
            refusal("SELECT NULL::DATE(5,2)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected '-'.\nsyntax error line 1 at position 23 unexpected ')'.",
            refusal("SELECT NULL::VARIANT(-1)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected ','.\nsyntax error line 1 at position 24 unexpected ')'.",
            refusal("SELECT NULL::VARCHAR(5,2)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected ','.",
            refusal("SELECT NULL::VARCHAR(5,)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected ','.\nsyntax error line 1 at position 23 unexpected ')'.",
            refusal("SELECT NULL::VARCHAR(,2)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 27 unexpected ','.\nsyntax error line 1 at position 29 unexpected ')'.",
            refusal("SELECT NULL::CHAR VARYING(5,2)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 20 unexpected ','.\nsyntax error line 1 at position 22 unexpected ')'.",
            refusal("SELECT NULL::FLOAT(5,2)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 22 unexpected ')'.",
            refusal("SELECT NULL::NUMBER(5,)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 20 unexpected ','.\nsyntax error line 1 at position 22 unexpected ')'.",
            refusal("SELECT NULL::NUMBER(,2)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 23 unexpected ','.\nsyntax error line 1 at position 25 unexpected ')'.",
            refusal("SELECT NULL::NUMBER(5,2,1)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected ','.\nsyntax error line 1 at position 23 unexpected ')'.",
            refusal("SELECT NULL::BINARY(5,2)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 19 unexpected ','.\nsyntax error line 1 at position 21 unexpected ')'.",
            refusal("SELECT NULL::TIME(5,2)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 28 unexpected ','.",
            refusal("SELECT NULL::TIMESTAMP_NTZ(5,)"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 28 unexpected ','.\nsyntax error line 1 at position 32 unexpected ')'.",
            refusal("SELECT NULL::TIMESTAMP_NTZ(5,2,1)"));
    }

    @Test
    public void inACastAListThatCannotBeginIsRefusedAtItsParenthesis() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 23 unexpected '('.\nsyntax error line 1 at position 24 unexpected '5'.",
            refusal("SELECT CAST(NULL AS INT(5))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 23 unexpected '('.\nsyntax error line 1 at position 24 unexpected '5'.\nsyntax error line 1 at position 27 unexpected ')'.",
            refusal("SELECT CAST(NULL AS INT(5,2))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 23 unexpected '('.\nsyntax error line 1 at position 24 unexpected '5'.\nsyntax error line 1 at position 26 unexpected ')'.",
            refusal("SELECT CAST(NULL AS INT(5,))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 24 unexpected '('.\nsyntax error line 1 at position 25 unexpected '5'.\nsyntax error line 1 at position 30 unexpected ')'.",
            refusal("SELECT CAST(NULL AS DATE(5,2,1))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 27 unexpected '('.\nsyntax error line 1 at position 28 unexpected ','.\nsyntax error line 1 at position 30 unexpected ')'.",
            refusal("SELECT CAST(NULL AS VARCHAR(,2))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 26 unexpected '('.\nsyntax error line 1 at position 27 unexpected ','.\nsyntax error line 1 at position 29 unexpected ')'.",
            refusal("SELECT CAST(NULL AS NUMBER(,2))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 29 unexpected ','.\nsyntax error line 1 at position 31 unexpected ')'.",
            refusal("SELECT CAST(NULL AS VARCHAR(5,2))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 29 unexpected ','.",
            refusal("SELECT CAST(NULL AS VARCHAR(5,))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 29 unexpected ')'.",
            refusal("SELECT CAST(NULL AS NUMBER(5,))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 30 unexpected ','.\nsyntax error line 1 at position 32 unexpected ')'.",
            refusal("SELECT CAST(NULL AS NUMBER(5,2,1))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 26 unexpected ','.\nsyntax error line 1 at position 28 unexpected ')'.",
            refusal("SELECT CAST(NULL AS TIME(5,2))"));
    }

    @Test
    public void inAColumnAListThatCannotBeginIsRefusedAtItsParenthesisAlone() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 35 unexpected '('.",
            refusal("CREATE OR REPLACE TABLE tp_a (a INT(5))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 38 unexpected '('.",
            refusal("CREATE OR REPLACE TABLE tp_a (a DOUBLE(5,2))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 36 unexpected '('.",
            refusal("CREATE OR REPLACE TABLE tp_a (a DATE(5,))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 39 unexpected '('.",
            refusal("CREATE OR REPLACE TABLE tp_a (a VARCHAR(,2))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 38 unexpected '('.",
            refusal("CREATE OR REPLACE TABLE tp_a (a NUMBER(,2))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 41 unexpected ','.",
            refusal("CREATE OR REPLACE TABLE tp_a (a VARCHAR(5,))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 41 unexpected ')'.",
            refusal("CREATE OR REPLACE TABLE tp_a (a NUMBER(5,))"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 38 unexpected ','.",
            refusal("CREATE OR REPLACE TABLE tp_a (a TIME(5,))"));
    }

    @Test
    public void anEmptyListAfterColonsLeavesTheRestToBeReported() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 23 unexpected ')'.\n"
                + "syntax error line 1 at position 34 unexpected '('.",
            refusal("SELECT CAST(NULL::DATE() AS NUMBER())"));
    }

    @Test
    public void aFunctionBodyIsJudgedTheSameWay() {
        assertEquals("Compilation of SQL UDF failed: SQL compilation error:\n"
                + "syntax error line 1 at position 11 unexpected ')'.",
            refusal("CREATE OR REPLACE FUNCTION tp_u(x INT) RETURNS INT AS $$x::NUMBER()$$"));
    }

    @Test
    public void geometryReadsItsParameterAndRefusesIt() {
        assertEquals("SQL compilation error:\nUnsupported data type 'GEOMETRY with arguments'.",
            refusal("SELECT NULL::GEOMETRY(5)"));
    }
}
