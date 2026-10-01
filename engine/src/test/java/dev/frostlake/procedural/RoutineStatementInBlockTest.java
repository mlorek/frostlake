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

package dev.frostlake.procedural;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A CREATE FUNCTION or CREATE PROCEDURE inside a Snowflake Scripting block is judged while the block compiles,
 * reachable or not: options out of order, an unknown language and a function's EXECUTE AS refuse the whole block
 * with the plain compilation error, and nothing before them runs. The pass that judges the language also judges the
 * declared types and the names a DECLARE section introduces twice, in the order the block is written, before a
 * parameter a DECLARE item repeats, an unnamed bind, a LET naming a variable twice and every name. A procedure's
 * body is judged the same way at its CREATE. Every cell is live-verified.
 */
public class RoutineStatementInBlockTest extends BaseDatabaseTest {

    private static final String COBOL = "SQL compilation error:\nUnknown function language: COBOL.";
    private static final String INVOCATION = "Unsupported invocation type for function.";

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    /** An anonymous block whose text starts on the line after {@code $$}, as written by hand. */
    private static String block(final String... lines) {
        return "EXECUTE IMMEDIATE $$\n" + String.join("\n", lines) + "\n$$";
    }

    private static String badWidth(final int line, final int position) {
        return "SQL compilation error: error line " + line + " at position " + position
            + "\nInvalid character length: 0. Must be between 1 and 134,217,728.";
    }

    @Test
    public void optionsOutOfOrderRefuseTheWholeBlockBeforeAnyOfItRuns() {
        assertEquals("SQL compilation error:\nsyntax error line 4 at position 57 unexpected 'AS'.",
            refusalOf(block("BEGIN", "  CREATE TABLE rv_side1 (a INT);",
                "  CREATE FUNCTION rv_n3() RETURNS INT COMMENT='c' STRICT AS '1';", "  RETURN 1;", "END;")));
        assertEquals(hinted("SQL compilation error:\nObject 'RV_SIDE1' does not exist or not authorized."),
            refusalOf("SELECT COUNT(*) FROM rv_side1"));
        assertEquals("SQL compilation error:\nsyntax error line 4 at position 59 unexpected 'AS'.",
            refusalOf(block("BEGIN", "  IF (1 = 0) THEN",
                "    CREATE FUNCTION rv_n4() RETURNS INT COMMENT='c' STRICT AS '1';", "  END IF;", "  RETURN 7;",
                "END;")));
        assertEquals("SQL compilation error:\nsyntax error line 4 at position 57 unexpected 'ON'.\n"
            + "syntax error line 4 at position 71 unexpected 'AS'.",
            refusalOf(block("BEGIN", "  IF (1 = 0) THEN",
                "    CREATE FUNCTION a7f() RETURNS INT COMMENT='c' CALLED ON NULL INPUT AS '1';", "  END IF;",
                "  RETURN 7;", "END;")));
    }

    @Test
    public void anUnknownLanguageOrAFunctionsInvocationTypeRefusesTheBlockOnAnyBranch() {
        assertEquals(COBOL, refusalOf(block("BEGIN", "  IF (1 = 0) THEN",
            "    CREATE FUNCTION rv_n5() RETURNS INT LANGUAGE COBOL AS 'x';", "  END IF;", "  RETURN 8;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  CREATE FUNCTION rv_n5b() RETURNS INT LANGUAGE COBOL AS 'x';",
            "  RETURN 9;", "END;")));
        assertEquals(INVOCATION, refusalOf(block("BEGIN",
            "  CREATE FUNCTION a12f() RETURNS INT EXECUTE AS CALLER AS '1';",
            "  CREATE FUNCTION a12g() RETURNS INT LANGUAGE COBOL AS 'x';", "  RETURN 12;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  CREATE FUNCTION a13f() RETURNS INT LANGUAGE COBOL AS 'x';",
            "  CREATE FUNCTION a13g() RETURNS INT EXECUTE AS CALLER AS '1';", "  RETURN 13;", "END;")));
    }

    @Test
    public void theyComeBeforeNamesUnnamedBindsAndARepeatedLet() {
        assertEquals(COBOL, refusalOf(block("BEGIN", "  LET x := nosuchvar;",
            "  CREATE FUNCTION a9f() RETURNS INT LANGUAGE COBOL AS 'x';", "  RETURN 9;", "END;")));
        assertEquals(INVOCATION, refusalOf(block("BEGIN", "  LET x := nosuchvar;",
            "  CREATE FUNCTION a14f() RETURNS INT EXECUTE AS CALLER AS '1';", "  RETURN 14;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  CREATE FUNCTION bo11() RETURNS INT LANGUAGE COBOL AS 'x';",
            "  LET y := :nosuchbind;", "  RETURN 1;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  LET y := ?;",
            "  CREATE FUNCTION u1() RETURNS INT LANGUAGE COBOL AS 'x';", "  RETURN 1;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  LET x INT := 1;", "  LET x INT := 2;",
            "  CREATE FUNCTION bo10() RETURNS INT LANGUAGE COBOL AS 'x';", "  RETURN 1;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  LET x INT := 1;",
            "  CREATE FUNCTION bp11() RETURNS INT LANGUAGE COBOL AS 'x';", "  LET x INT := 2;", "  RETURN 1;",
            "END;")));
    }

    @Test
    public void aNameTheDeclareSectionIntroducesTwiceComesFirst() {
        assertEquals("SQL compilation error: error line 4 at position 2\n Variable with name 'X' declared twice.",
            refusalOf(block("DECLARE", "  x INT;", "  x INT;", "BEGIN",
                "  CREATE FUNCTION a11f() RETURNS INT LANGUAGE COBOL AS 'x';", "  RETURN 11;", "END;")));
        assertEquals("SQL compilation error: error line 4 at position 2\n Variable with name 'X' declared twice.",
            refusalOf(block("DECLARE", "  x INT;", "  x INT;", "BEGIN",
                "  CREATE FUNCTION bp6() RETURNS INT EXECUTE AS CALLER AS '1';", "  RETURN 1;", "END;")));
        assertEquals("SQL compilation error: error line 7 at position 4\n Variable with name 'Y' declared twice.",
            refusalOf(block("DECLARE", "  x INT;", "BEGIN", "  DECLARE", "    y INT;", "    y INT;", "  BEGIN",
                "    RETURN 1;", "  END;", "  CREATE FUNCTION u8() RETURNS INT LANGUAGE COBOL AS 'x';", "END;")));
    }

    @Test
    public void aDeclaredWidthAndARoutineAreJudgedInTheOrderWritten() {
        assertEquals(badWidth(3, 12), refusalOf(block("DECLARE", "  v VARCHAR(0);", "BEGIN",
            "  CREATE FUNCTION a15f() RETURNS INT LANGUAGE COBOL AS 'x';", "  RETURN 15;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  CREATE FUNCTION bo3() RETURNS INT LANGUAGE COBOL AS 'x';",
            "  LET v VARCHAR(0) := 'a';", "  RETURN 1;", "END;")));
        assertEquals(badWidth(3, 16), refusalOf(block("BEGIN", "  LET v VARCHAR(0) := 'a';",
            "  CREATE FUNCTION bo4() RETURNS INT EXECUTE AS CALLER AS '1';", "  RETURN 1;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  IF (1 = 0) THEN",
            "    CREATE FUNCTION bo5() RETURNS INT LANGUAGE COBOL AS 'x';", "  END IF;", "  LET v VARCHAR(0) := 'a';",
            "  RETURN 1;", "END;")));
        assertEquals(badWidth(3, 32), refusalOf(block("BEGIN",
            "  CREATE FUNCTION bp7(a VARCHAR(0)) RETURNS INT LANGUAGE COBOL AS 'x';", "  RETURN 1;", "END;")));
        assertEquals(badWidth(3, 32), refusalOf(block("BEGIN",
            "  CREATE FUNCTION bp8(a VARCHAR(0)) RETURNS INT EXECUTE AS CALLER AS '1';", "  RETURN 1;", "END;")));
        assertEquals(badWidth(3, 44), refusalOf(block("BEGIN", "  LET c CURSOR FOR SELECT CAST(1 AS VARCHAR(0));",
            "  CREATE FUNCTION bp12() RETURNS INT LANGUAGE COBOL AS 'x';", "  RETURN 1;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  CREATE FUNCTION bp13() RETURNS INT LANGUAGE COBOL AS 'x';",
            "  LET c CURSOR FOR SELECT CAST(1 AS VARCHAR(0));", "  RETURN 1;", "END;")));
        assertEquals(badWidth(3, 27), refusalOf(block("BEGIN", "  SELECT CAST(1 AS VARCHAR(0));",
            "  CREATE FUNCTION bp14() RETURNS INT LANGUAGE COBOL AS 'x';", "  RETURN 1;", "END;")));
    }

    private static String declaredTwice(final int line, final int position, final String name) {
        return "SQL compilation error: error line " + line + " at position " + position + "\n Variable with name '"
            + name + "' declared twice.";
    }

    @Test
    public void aDeclareSectionsNamesAreJudgedInTheOrderWrittenAfterEachItemsOwnTypes() {
        assertEquals(badWidth(3, 12), refusalOf(block("DECLARE", "  v VARCHAR(0);", "  x INT;", "  x INT;", "BEGIN",
            "  RETURN 1;", "END;")));
        assertEquals(declaredTwice(4, 2, "X"), refusalOf(block("DECLARE", "  x INT;", "  x INT;", "  v VARCHAR(0);",
            "BEGIN", "  RETURN 1;", "END;")));
        assertEquals(badWidth(4, 12), refusalOf(block("DECLARE", "  x INT;", "  x VARCHAR(0);", "BEGIN", "  RETURN 1;",
            "END;")));
        assertEquals(badWidth(4, 34), refusalOf(block("DECLARE", "  x INT;", "  x INT DEFAULT CAST(1 AS VARCHAR(0));",
            "BEGIN", "  RETURN 1;", "END;")));
        assertEquals(badWidth(4, 25), refusalOf(block("DECLARE", "  x INT;", "  x := CAST(1 AS VARCHAR(0));", "BEGIN",
            "  RETURN 1;", "END;")));
        assertEquals(badWidth(4, 40), refusalOf(block("DECLARE", "  c INT;",
            "  c CURSOR FOR SELECT CAST(1 AS VARCHAR(0));", "BEGIN", "  RETURN 1;", "END;")));
        assertEquals(declaredTwice(4, 2, "E"), refusalOf(block("DECLARE", "  e EXCEPTION;", "  e EXCEPTION;",
            "  v VARCHAR(0);", "BEGIN", "  RETURN 1;", "END;")));
        assertEquals(badWidth(3, 12), refusalOf(block("DECLARE", "  v VARCHAR(0);", "  e EXCEPTION;", "  e EXCEPTION;",
            "BEGIN", "  RETURN 1;", "END;")));
    }

    @Test
    public void aNestedDeclareSectionTakesItsPlaceInTheText() {
        assertEquals(declaredTwice(5, 4, "Y"), refusalOf(block("BEGIN", "  DECLARE", "    y INT;", "    y INT;",
            "  BEGIN", "    RETURN 1;", "  END;", "  LET v VARCHAR(0) := 'a';", "END;")));
        assertEquals(badWidth(3, 16), refusalOf(block("BEGIN", "  LET v VARCHAR(0) := 'a';", "  DECLARE", "    y INT;",
            "    y INT;", "  BEGIN", "    RETURN 1;", "  END;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  CREATE FUNCTION u9() RETURNS INT LANGUAGE COBOL AS 'x';",
            "  DECLARE", "    y INT;", "    y INT;", "  BEGIN", "    RETURN 1;", "  END;", "END;")));
        assertEquals(INVOCATION, refusalOf(block("BEGIN",
            "  CREATE FUNCTION dq3() RETURNS INT EXECUTE AS CALLER AS '1';", "  DECLARE", "    y INT;", "    y INT;",
            "  BEGIN", "    RETURN 1;", "  END;", "END;")));
        assertEquals(COBOL, refusalOf(block("BEGIN", "  CREATE FUNCTION dq5() RETURNS INT LANGUAGE COBOL AS 'x';",
            "EXCEPTION", "  WHEN OTHER THEN", "    DECLARE", "      y INT;", "      y INT;", "    BEGIN",
            "      RETURN 1;", "    END;", "END;")));
        assertEquals("SQL compilation error:\nsyntax error line 6 at position 55 unexpected 'AS'.",
            refusalOf(block("DECLARE", "  x INT;", "  x INT;", "BEGIN",
                "  CREATE FUNCTION dq4() RETURNS INT COMMENT='c' STRICT AS '1';", "END;")));
    }

    @Test
    public void aParameterADeclareItemRepeatsIsRefusedAfterThePass() {
        assertEquals(declaredTwice(3, 2, "X"), refusalOf("CREATE PROCEDURE pc1(x INT) RETURNS INT LANGUAGE SQL AS "
            + "$$\nDECLARE\n  x INT;\nBEGIN\n  RETURN 1;\nEND\n$$"));
        assertEquals(COBOL, refusalOf("CREATE PROCEDURE pc2(x INT) RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n  x INT;\n"
            + "BEGIN\n  CREATE FUNCTION pc2f() RETURNS INT LANGUAGE COBOL AS 'x';\n  RETURN 1;\nEND\n$$"));
        assertEquals(badWidth(5, 16), refusalOf("CREATE PROCEDURE pc3(x INT) RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n"
            + "  x INT;\nBEGIN\n  LET v VARCHAR(0) := 'a';\n  RETURN 1;\nEND\n$$"));
        assertEquals(declaredTwice(5, 2, "Y"), refusalOf("CREATE PROCEDURE pc4(x INT) RETURNS INT LANGUAGE SQL AS "
            + "$$\nDECLARE\n  x INT;\n  y INT;\n  y INT;\nBEGIN\n  RETURN 1;\nEND\n$$"));
        assertEquals(INVOCATION, refusalOf("CREATE PROCEDURE pc6(x INT) RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n"
            + "  x INT;\nBEGIN\n  CREATE FUNCTION pc6f() RETURNS INT EXECUTE AS CALLER AS '1';\n  RETURN 1;\nEND\n$$"));
        assertEquals(declaredTwice(7, 4, "Z"), refusalOf("CREATE PROCEDURE pc7(x INT) RETURNS INT LANGUAGE SQL AS "
            + "$$\nDECLARE\n  x INT;\nBEGIN\n  DECLARE\n    z INT;\n    z INT;\n  BEGIN\n    RETURN 1;\n  END;\nEND\n$$"));
        assertEquals(badWidth(4, 12), refusalOf("CREATE PROCEDURE dq1(x INT) RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n"
            + "  x INT;\n  v VARCHAR(0);\nBEGIN\n  RETURN 1;\nEND\n$$"));
        assertEquals(badWidth(3, 12), refusalOf("CREATE PROCEDURE u7() RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n"
            + "  v VARCHAR(0);\n  x INT;\n  x INT;\nBEGIN\n  RETURN 1;\nEND\n$$"));
    }

    @Test
    public void aDeclaredWidthComesBeforeNamesAndUnnamedBinds() {
        assertEquals(badWidth(3, 12), refusalOf(block("DECLARE", "  v VARCHAR(0);", "BEGIN", "  LET x := nosuchvar;",
            "  RETURN 1;", "END;")));
        assertEquals(badWidth(4, 16), refusalOf(block("BEGIN", "  LET x := nosuchvar;", "  LET v VARCHAR(0) := 'a';",
            "  RETURN 1;", "END;")));
        assertEquals(badWidth(4, 16), refusalOf(block("BEGIN", "  LET y := ?;", "  LET v VARCHAR(0) := 'a';",
            "  RETURN 1;", "END;")));
        assertEquals(badWidth(3, 16), refusalOf(block("BEGIN", "  LET v VARCHAR(0) := 'a';", "  LET x INT := 1;",
            "  LET x INT := 2;", "  RETURN 1;", "END;")));
        assertEquals(badWidth(5, 16), refusalOf(block("BEGIN", "  LET x INT := 1;", "  LET x INT := 2;",
            "  LET v VARCHAR(0) := 'a';", "  RETURN 1;", "END;")));
    }

    @Test
    public void aProceduresBodyIsJudgedTheSameWayAtCreate() {
        assertEquals(COBOL, refusalOf("CREATE PROCEDURE bo6() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n"
            + "  CREATE FUNCTION bo6f() RETURNS INT LANGUAGE COBOL AS 'x';\n  LET v VARCHAR(0) := 'a';\n  RETURN 1;\n"
            + "END\n$$"));
        assertEquals(badWidth(3, 12), refusalOf("CREATE PROCEDURE bo7() RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n"
            + "  v VARCHAR(0);\nBEGIN\n  CREATE FUNCTION bo7f() RETURNS INT LANGUAGE COBOL AS 'x';\n  RETURN 1;\n"
            + "END\n$$"));
        assertEquals(COBOL, refusalOf("CREATE PROCEDURE bo8() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n"
            + "  CREATE FUNCTION bo8f() RETURNS INT LANGUAGE COBOL AS 'x';\n  RETURN nosuchvar;\nEND\n$$"));
        assertEquals("SQL compilation error:\nsyntax error line 4 at position 58 unexpected 'AS'.",
            refusalOf("CREATE PROCEDURE bo9() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n  IF (1 = 0) THEN\n"
                + "    CREATE FUNCTION bo9f() RETURNS INT COMMENT='c' STRICT AS '1';\n  END IF;\n  RETURN 9;\nEND\n$$"));
        assertEquals("SQL compilation error: error line 4 at position 2\n Variable with name 'X' declared twice.",
            refusalOf("CREATE PROCEDURE u5() RETURNS INT LANGUAGE SQL AS $$\nDECLARE\n  x INT;\n  x INT;\nBEGIN\n"
                + "  CREATE FUNCTION u5f() RETURNS INT LANGUAGE COBOL AS 'x';\n  RETURN 1;\nEND\n$$"));
        assertEquals(COBOL, refusalOf("CREATE PROCEDURE u6() RETURNS INT LANGUAGE SQL AS $$\nBEGIN\n"
            + "  LET x INT := 1;\n  LET x INT := 2;\n  CREATE FUNCTION u6f() RETURNS INT LANGUAGE COBOL AS 'x';\n"
            + "  RETURN 1;\nEND\n$$"));
    }
}
