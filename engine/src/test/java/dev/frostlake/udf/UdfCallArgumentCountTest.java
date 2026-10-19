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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A call a user-defined function or a procedure does not take, refused while the statement compiles
 * (live-verified). More arguments than the widest overload declares is {@code too many arguments for function
 * [G(1, 2)] expected 1, got 2}; any other count no overload takes lists the argument types, {@code Invalid argument
 * types for function 'G': ()}. Named arguments that fit no signature are listed, parameters in declaration order
 * and then the names that are none. A function is refused at its call, a CALL nowhere, and an expression body at
 * CREATE in the body's own frame.
 */
public class UdfCallArgumentCountTest extends BaseDatabaseTest {

    private static final String AT_CALL = "SQL compilation error: error line 1 at position 7\n";
    private static final String UNPLACED = "SQL compilation error: error line 0 at position -1\n";

    @Override
    protected void setupTest() {
        engine.execute("CREATE FUNCTION ucc_g(x INT) RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE FUNCTION ucc_g(x VARCHAR) RETURNS INT AS $$ 2 $$");
        engine.execute("CREATE FUNCTION ucc_h(x INT, y INT DEFAULT 5) RETURNS INT AS $$ x + y $$");
        engine.execute("CREATE FUNCTION ucc_k(x INT) RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE FUNCTION ucc_k(x INT, y INT, z INT) RETURNS INT AS $$ 3 $$");
        engine.execute("CREATE FUNCTION ucc_n(a INT, b INT) RETURNS INT AS $$ a + b $$");
        engine.execute("CREATE FUNCTION ucc_f2() RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE FUNCTION ucc_g2(x INT, y VARCHAR) RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE FUNCTION \"uccMixed\"() RETURNS INT AS $$ 1 $$");
        engine.execute("CREATE TABLE ucc_empty (a INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String value(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aCountPastTheWidestOverloadIsTooMany() {
        assertEquals(AT_CALL + "too many arguments for function [UCC_G(1, 2)] expected 1, got 2",
            refusal("SELECT ucc_g(1, 2)"));
        assertEquals(AT_CALL + "too many arguments for function [UCC_H(1, 2, 3)] expected 2, got 3",
            refusal("SELECT ucc_h(1, 2, 3)"));
        assertEquals(AT_CALL + "too many arguments for function [UCC_K(1, 2, 3, 4)] expected 3, got 4",
            refusal("SELECT ucc_k(1, 2, 3, 4)"));
        assertEquals(AT_CALL + "too many arguments for function [UCC_G2('a', 1, TRUE)] expected 2, got 3",
            refusal("SELECT ucc_g2('a', 1, TRUE)"));
        assertEquals(AT_CALL + "too many arguments for function [uccMixed(1)] expected 0, got 1",
            refusal("SELECT \"uccMixed\"(1)"));
        assertEquals(AT_CALL + "too many arguments for function [UCC_G(1, 2)] expected 1, got 2",
            refusal("SELECT test_db.test_schema.ucc_g(1, 2)"));
        assertEquals(AT_CALL + "too many arguments for function [UCC_G(1, 2)] expected 1, got 2",
            refusal("SELECT ucc_g(x => 1, y => 2)"));
    }

    @Test
    public void anyOtherCountNoOverloadTakesListsTheArgumentTypes() {
        assertEquals(AT_CALL + "Invalid argument types for function 'UCC_G': ()", refusal("SELECT ucc_g()"));
        assertEquals(AT_CALL + "Invalid argument types for function 'UCC_H': ()", refusal("SELECT ucc_h()"));
        assertEquals("6", value("SELECT ucc_h(1)"));
        assertEquals(AT_CALL + "Invalid argument types for function 'UCC_K': (NUMBER(1,0), NUMBER(1,0))",
            refusal("SELECT ucc_k(1, 2)"));
        assertEquals(AT_CALL + "Invalid argument types for function 'UCC_G2': (NUMBER(1,0))",
            refusal("SELECT ucc_g2(1)"));
        assertEquals(AT_CALL + "Invalid argument types for function 'UCC_G2': (VARCHAR(1))",
            refusal("SELECT ucc_g2('a')"));
    }

    @Test
    public void theCountIsJudgedWhereverTheCallStandsAndOverNoRows() {
        assertEquals(AT_CALL + "too many arguments for function [UCC_G(UCC_EMPTY.A, UCC_EMPTY.A)] expected 1, got 2",
            refusal("SELECT ucc_g(a, a) FROM ucc_empty"));
        assertEquals(AT_CALL + "Invalid argument types for function 'UCC_G': ()",
            refusal("SELECT ucc_g() FROM ucc_empty"));
        assertEquals("SQL compilation error: error line 1 at position 15\n"
            + "too many arguments for function [UCC_G(1, 2)] expected 1, got 2", refusal("SELECT 1 WHERE ucc_g(1, 2) = 1"));
        assertEquals("SQL compilation error: error line 1 at position 11\n"
            + "too many arguments for function [UCC_G(1, 2)] expected 1, got 2", refusal("SELECT ABS(ucc_g(1, 2))"));
    }

    @Test
    public void namedArgumentsThatFitNoSignatureAreListed() {
        assertEquals("3", value("SELECT ucc_n(b => 1, a => 2)"));
        assertEquals(AT_CALL + "named arguments [Z] do not match any signature for function UCC_N",
            refusal("SELECT ucc_n(z => 1)"));
        assertEquals(AT_CALL + "named arguments [A, Z] do not match any signature for function UCC_N",
            refusal("SELECT ucc_n(a => 1, z => 2)"));
        assertEquals(AT_CALL + "named arguments [Z] do not match any signature for function UCC_N",
            refusal("SELECT ucc_n(1, z => 2)"));
        assertEquals(AT_CALL + "named arguments [A] do not match any signature for function UCC_N",
            refusal("SELECT ucc_n(a => 1)"));
        assertEquals(AT_CALL + "named arguments [Y, Z] do not match any signature for function UCC_N",
            refusal("SELECT ucc_n(y => 1, z => 2)"));
        assertEquals(AT_CALL + "named arguments [a, B] do not match any signature for function UCC_N",
            refusal("SELECT ucc_n(\"a\" => 1, b => 2)"));
        assertEquals(AT_CALL + "too many arguments for function [UCC_N(1, 2, 3)] expected 2, got 3",
            refusal("SELECT ucc_n(a => 1, b => 2, z => 3)"));
        assertEquals(AT_CALL + "named arguments [Z] do not match any signature for function UCC_G",
            refusal("SELECT ucc_g(z => 1)"));
        assertEquals("SQL compilation error: error line 1 at position 26\n"
            + "duplicate named argument A for function UCC_N", refusal("SELECT ucc_n(a => 1, a => 2)"));
    }

    @Test
    public void aCallStatementIsJudgedTheSameWayNowhereInItsText() {
        engine.execute("CREATE PROCEDURE \"uccP\"(x INT) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        engine.execute("CREATE PROCEDURE ucc_p3(x INT, y VARCHAR) RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$");
        engine.execute("CREATE PROCEDURE ucc_p4(x INT, y INT DEFAULT 5) RETURNS INT LANGUAGE SQL AS"
            + " $$ BEGIN RETURN 1; END; $$");
        assertEquals(UNPLACED + "Invalid argument types for function 'uccP': ()", refusal("CALL \"uccP\"()"));
        assertEquals(UNPLACED + "too many arguments for function [uccP(1, 2)] expected 1, got 2",
            refusal("CALL \"uccP\"(1, 2)"));
        assertEquals(UNPLACED + "Invalid argument types for function 'UCC_P3': (NUMBER(1,0))", refusal("CALL ucc_p3(1)"));
        assertEquals(UNPLACED + "Invalid argument types for function 'UCC_P3': ()", refusal("CALL ucc_p3()"));
        assertEquals(UNPLACED + "too many arguments for function [UCC_P3(1, 'a', 2)] expected 2, got 3",
            refusal("CALL ucc_p3(1, 'a', 2)"));
        assertEquals(UNPLACED + "Invalid argument types for function 'UCC_P4': ()", refusal("CALL ucc_p4()"));
        assertEquals(UNPLACED + "named arguments [Z] do not match any signature for function UCC_P3",
            refusal("CALL ucc_p3(z => 1)"));
        assertEquals(UNPLACED + "named arguments [X, Z] do not match any signature for function UCC_P3",
            refusal("CALL ucc_p3(x => 1, z => 'a')"));
        assertEquals(UNPLACED + "named arguments [X] do not match any signature for function UCC_P3",
            refusal("CALL ucc_p3(x => 1)"));
        assertEquals("1", value("CALL ucc_p3(y => 'a', x => 1)"));
    }

    @Test
    public void anExpressionBodyIsJudgedAtCreateInItsOwnFrame() {
        final String tooMany = "too many arguments for function [ABS(1, 2)] expected 1, got 2";
        assertEquals("SQL compilation error: error line 1 at position 1\n" + tooMany,
            refusal("CREATE FUNCTION ucc_b0() RETURNS INT AS 'ABS(1, 2)'"));
        assertEquals("SQL compilation error: error line 1 at position 5\n" + tooMany,
            refusal("CREATE FUNCTION ucc_b1() RETURNS INT AS '1 + ABS(1, 2)'"));
        assertEquals("SQL compilation error: error line 1 at position 2\n" + tooMany,
            refusal("CREATE FUNCTION ucc_b2() RETURNS INT AS ' ABS(1, 2)'"));
        assertEquals("SQL compilation error: error line 2 at position 2\n" + tooMany,
            refusal("CREATE FUNCTION ucc_b5() RETURNS INT AS $$\n  ABS(1, 2)\n$$"));
        assertEquals("SQL compilation error: error line 1 at position 8\n" + tooMany,
            refusal("CREATE FUNCTION ucc_b3() RETURNS INT AS 'SELECT ABS(1, 2)'"));
        assertEquals("SQL compilation error: error line 1 at position 1\n"
            + "too many arguments for function [UCC_F2(1)] expected 0, got 1",
            refusal("CREATE FUNCTION ucc_b10() RETURNS INT AS 'ucc_f2(1)'"));
        assertEquals("SQL compilation error: error line 1 at position 5\n"
            + "too many arguments for function [UCC_F2(1)] expected 0, got 1",
            refusal("CREATE FUNCTION ucc_b7() RETURNS INT AS 'ABS(ucc_f2(1), 2)'"));
        assertEquals("SQL compilation error: error line 1 at position 1\n"
            + "not enough arguments for function [LOG(10)], expected 2, got 1",
            refusal("CREATE FUNCTION ucc_b9() RETURNS FLOAT AS 'LOG(10)'"));
        assertEquals("SQL compilation error: error line 1 at position 1\n"
            + "Invalid argument types for function 'UCC_G2': ()",
            refusal("CREATE FUNCTION ucc_b11() RETURNS INT AS 'ucc_g2()'"));
        assertEquals("SQL compilation error: error line 1 at position 1\n"
            + "Invalid argument types for function 'UCC_G2': (DATE, NUMBER(1,0))",
            refusal("CREATE FUNCTION ucc_b12() RETURNS INT AS 'ucc_g2(CURRENT_DATE, 1)'"));
        assertEquals("SQL compilation error: error line 1 at position 1\n"
            + "Invalid argument types for function 'UCC_G2': (NUMBER(38,0))",
            refusal("CREATE FUNCTION ucc_b13(p INT) RETURNS INT AS 'ucc_g2(p)'"));
        assertEquals("SQL compilation error: error line 1 at position 1\n"
            + "named arguments [Z] do not match any signature for function UCC_G2",
            refusal("CREATE FUNCTION ucc_b14() RETURNS INT AS 'ucc_g2(z => 1)'"));
        assertEquals("SQL compilation error: error line 1 at position 1\n"
            + "Invalid argument types for function 'ABS': (DATE)",
            refusal("CREATE FUNCTION ucc_b19() RETURNS INT AS 'ABS(CURRENT_DATE)'"));
        assertEquals("SQL compilation error:\nUnknown function NOSUCH.",
            refusal("CREATE FUNCTION ucc_b17() RETURNS INT AS 'nosuch(1) + ABS(1, 2)'"));
    }
}
