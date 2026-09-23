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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A routine's options come in a fixed order — LANGUAGE, the null-handling clause, the volatility, MEMOIZABLE —
 * and then the properties in any order, each of which may repeat with the last one winning. An option out of
 * place is a syntax error where it stops reading as a property: its first word is taken as a property's name.
 * A procedure takes the same options and its EXECUTE AS last. Every cell is live-verified.
 */
public class RoutineOptionOrderTest extends BaseDatabaseTest {

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private void assertRefused(final String sql, final String expected) {
        final String refusal = refusalOf(sql);
        assertTrue(refusal.contains(expected), sql + " -> " + refusal);
    }

    private String description(final String kind, final String name) {
        for (final Row row : engine.executeQuery("SHOW USER " + kind + " LIKE '" + name + "'").getRows()) {
            return String.valueOf(row.getValue(9));
        }
        return null;
    }

    @Test
    public void anOptionAfterAPropertyIsRefusedWhereItStopsReadingAsOne() {
        assertRefused("CREATE FUNCTION ocS() RETURNS INT COMMENT='c' STRICT AS '1'",
            "syntax error line 1 at position 53 unexpected 'AS'.");
        assertRefused("CREATE FUNCTION ocL() RETURNS INT COMMENT='c' LANGUAGE SQL AS '1'",
            "syntax error line 1 at position 55 unexpected 'SQL'.");
        assertRefused("CREATE FUNCTION ocV() RETURNS INT COMMENT='c' VOLATILE AS '1'",
            "syntax error line 1 at position 55 unexpected 'AS'.");
        assertRefused("CREATE FUNCTION ocM() RETURNS INT COMMENT='c' MEMOIZABLE AS '1'",
            "syntax error line 1 at position 57 unexpected 'AS'.");
        assertRefused("CREATE FUNCTION x4() RETURNS INT COMMENT='c' STRICT COMMENT='d' AS '1'",
            "syntax error line 1 at position 52 unexpected 'COMMENT'.");
        assertRefused("CREATE FUNCTION x19() RETURNS INT COMMENT='c' STRICT",
            "syntax error line 1 at position 52 unexpected '<EOF>'.");
        assertRefused("CREATE FUNCTION f6() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.11' STRICT HANDLER='h' "
            + "AS $$\ndef h():\n    return 1\n$$", "syntax error line 1 at position 79 unexpected 'HANDLER'.");
    }

    @Test
    public void aNullHandlingPhraseOutOfPlaceStacksASecondLine() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 53 unexpected 'ON'.\n"
            + "syntax error line 1 at position 67 unexpected 'AS'.",
            refusalOf("CREATE FUNCTION ocC() RETURNS INT COMMENT='c' CALLED ON NULL INPUT AS '1'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 54 unexpected 'NULL'.\n"
            + "syntax error line 1 at position 59 unexpected 'ON'.",
            refusalOf("CREATE FUNCTION ocR() RETURNS INT COMMENT='c' RETURNS NULL ON NULL INPUT AS '1'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 51 unexpected 'NULL'.\n"
            + "syntax error line 1 at position 56 unexpected 'ON'.",
            refusalOf("CREATE FUNCTION f9() RETURNS INT IMMUTABLE RETURNS NULL ON NULL INPUT AS '1'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 66 unexpected 'ON'.\n"
            + "syntax error line 1 at position 80 unexpected 'IMMUTABLE'.\n"
            + "syntax error line 1 at position 97 unexpected 'AS'.",
            refusalOf("CREATE FUNCTION g31() RETURNS INT LANGUAGE SQL COMMENT='c' CALLED ON NULL INPUT IMMUTABLE "
                + "STRICT AS '1'"));
    }

    @Test
    public void immutableRightAfterAPropertyIsRefusedAtItself() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 46 unexpected 'IMMUTABLE'.",
            refusalOf("CREATE FUNCTION ocI() RETURNS INT COMMENT='c' IMMUTABLE AS '1'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 59 unexpected 'IMMUTABLE'.",
            refusalOf("CREATE FUNCTION sv7() RETURNS INT LANGUAGE SQL COMMENT='c' IMMUTABLE STRICT AS '1'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 66 unexpected 'IMMUTABLE'.\n"
            + "syntax error line 1 at position 83 unexpected 'AS'.",
            refusalOf("CREATE FUNCTION sv8() RETURNS INT LANGUAGE SQL COMMENT='c' STRICT IMMUTABLE STRICT AS '1'"));
        assertRefused("CREATE FUNCTION f2() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.11' HANDLER='h' IMMUTABLE "
            + "AS $$\ndef h():\n    return 1\n$$", "syntax error line 1 at position 84 unexpected 'IMMUTABLE'.");
    }

    @Test
    public void anOptionBeforeItsPlaceOrTwiceIsRefusedAfterItsFirstWord() {
        assertRefused("CREATE FUNCTION osL() RETURNS INT STRICT LANGUAGE SQL AS '1'",
            "syntax error line 1 at position 50 unexpected 'SQL'.");
        assertRefused("CREATE FUNCTION oiS() RETURNS INT IMMUTABLE STRICT AS '1'",
            "syntax error line 1 at position 51 unexpected 'AS'.");
        assertRefused("CREATE FUNCTION oiI() RETURNS INT IMMUTABLE IMMUTABLE AS '1'",
            "syntax error line 1 at position 54 unexpected 'AS'.");
        assertRefused("CREATE FUNCTION omI() RETURNS INT MEMOIZABLE IMMUTABLE AS '1'",
            "syntax error line 1 at position 55 unexpected 'AS'.");
        assertRefused("CREATE FUNCTION olL() RETURNS INT LANGUAGE SQL LANGUAGE SQL AS '1'",
            "syntax error line 1 at position 56 unexpected 'SQL'.");
        assertRefused("CREATE FUNCTION f8() RETURNS INT RETURNS NULL ON NULL INPUT LANGUAGE PYTHON "
            + "RUNTIME_VERSION='3.11' HANDLER='h' AS $$\ndef h():\n    return 1\n$$",
            "syntax error line 1 at position 69 unexpected 'PYTHON'.");
    }

    @Test
    public void theOptionsInTheirOrderAreCreatedAndARepeatedPropertyWinsLast() {
        engine.execute("CREATE FUNCTION ok1() RETURNS INT LANGUAGE SQL RETURNS NULL ON NULL INPUT IMMUTABLE MEMOIZABLE "
            + "COMMENT='a' AS '1'");
        engine.execute("CREATE FUNCTION ok2() RETURNS INT STRICT COMMENT='a' COMMENT='b' AS '2'");
        engine.execute("CREATE FUNCTION ok3() RETURNS INT NULL AS '3'");
        engine.execute("CREATE FUNCTION ok4() RETURNS INT COMMENT=$$dollar$$ AS '4'");
        assertEquals("b", description("FUNCTIONS", "OK2"));
        assertEquals("dollar", description("FUNCTIONS", "OK4"));
        assertEquals("1", String.valueOf(engine.executeQuery("SELECT ok1()").getRows().get(0).getValue(0)));
        assertEquals("3", String.valueOf(engine.executeQuery("SELECT ok3()").getRows().get(0).getValue(0)));
    }

    @Test
    public void aLaterImportsReplacesAnEarlierOne() {
        engine.execute("CREATE FUNCTION im1() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.11' "
            + "IMPORTS=('@nostage/a.zip') IMPORTS=() HANDLER='h' AS $$\ndef h():\n    return 1\n$$");
        assertRefused("CREATE FUNCTION im2() RETURNS INT LANGUAGE PYTHON RUNTIME_VERSION='3.11' IMPORTS=() "
            + "IMPORTS=('@nostage/a.zip') HANDLER='h' AS $$\ndef h():\n    return 1\n$$",
            "Stage 'TEST_DB.TEST_SCHEMA.NOSTAGE' does not exist or not authorized.");
    }

    @Test
    public void aProcedureTakesTheFunctionOptionsInTheSameOrder() {
        engine.execute("CREATE PROCEDURE p1() RETURNS INT LANGUAGE SQL STRICT AS $$ BEGIN RETURN 1; END $$");
        engine.execute("CREATE PROCEDURE p3() RETURNS INT LANGUAGE SQL CALLED ON NULL INPUT VOLATILE "
            + "AS $$ BEGIN RETURN 3; END $$");
        engine.execute("CREATE PROCEDURE p14() RETURNS INT NULL LANGUAGE SQL COMMENT='c' COMMENT='d' EXECUTE AS CALLER "
            + "AS $$ BEGIN RETURN 14; END $$");
        engine.execute("CREATE PROCEDURE p7() RETURNS INT LANGUAGE PYTHON PACKAGES=('snowflake-snowpark-python') "
            + "RUNTIME_VERSION='3.11' HANDLER='run' AS $$\ndef run(session):\n    return 1\n$$");
        assertEquals("1", String.valueOf(engine.executeQuery("CALL p1()").getRows().get(0).getValue(0)));
        assertEquals("14", String.valueOf(engine.executeQuery("CALL p14()").getRows().get(0).getValue(0)));
        assertEquals("d", description("PROCEDURES", "P14"));
        assertRefused("CREATE PROCEDURE p9() RETURNS INT STRICT LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$",
            "syntax error line 1 at position 50 unexpected 'SQL'.");
        assertRefused("CREATE PROCEDURE p22() RETURNS INT LANGUAGE SQL COMMENT='c' IMMUTABLE "
            + "AS $$ BEGIN RETURN 1; END $$", "syntax error line 1 at position 60 unexpected 'IMMUTABLE'.");
    }

    @Test
    public void nothingButTheBodyMayFollowAProceduresExecuteAs() {
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 65 unexpected 'COMMENT'.",
            refusalOf("CREATE PROCEDURE p5() RETURNS INT LANGUAGE SQL EXECUTE AS CALLER COMMENT='c' "
                + "AS $$ BEGIN RETURN 1; END $$"));
        assertRefused("CREATE PROCEDURE p12() RETURNS INT LANGUAGE SQL EXECUTE AS OWNER EXECUTE AS CALLER "
            + "AS $$ BEGIN RETURN 1; END $$", "syntax error line 1 at position 65 unexpected 'EXECUTE'.");
        assertRefused("CREATE PROCEDURE p20() RETURNS INT EXECUTE AS CALLER LANGUAGE SQL AS $$ BEGIN RETURN 1; END $$",
            "syntax error line 1 at position 53 unexpected 'LANGUAGE'.");
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 67 unexpected 'EXECUTE'.\n"
            + "syntax error line 1 at position 78 unexpected 'CALLER'.",
            refusalOf("CREATE PROCEDURE sv5() RETURNS INT LANGUAGE SQL COMMENT='c' STRICT EXECUTE AS CALLER "
                + "AS $$ BEGIN RETURN 1; END $$"));
    }

    @Test
    public void aFunctionHasNoInvocationType() {
        assertEquals("Unsupported invocation type for function.",
            refusalOf("CREATE FUNCTION e6() RETURNS INT EXECUTE AS CALLER AS '1'"));
        assertEquals("Unsupported invocation type for function.",
            refusalOf("CREATE FUNCTION x2(a INT, a INT) RETURNS INT EXECUTE AS OWNER AS '1'"));
        assertEquals("Unsupported invocation type for function.",
            refusalOf("CREATE FUNCTION x8() RETURNS INT EXECUTE AS CALLER AS 'not sql'"));
        assertEquals("SQL compilation error:\nUnknown function language: COBOL.",
            refusalOf("CREATE FUNCTION x1() RETURNS INT LANGUAGE COBOL EXECUTE AS CALLER AS '1'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 51 unexpected 'COMMENT'.",
            refusalOf("CREATE FUNCTION x3() RETURNS INT EXECUTE AS CALLER COMMENT='c' AS '1'"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 53 unexpected 'EXECUTE'.\n"
            + "syntax error line 1 at position 64 unexpected 'CALLER'.",
            refusalOf("CREATE FUNCTION sv4() RETURNS INT COMMENT='c' STRICT EXECUTE AS CALLER AS '1'"));
    }

    @Test
    public void aProcedureRefusesTheServiceFunctionProperties() {
        assertRefused("CREATE PROCEDURE sv1() RETURNS INT LANGUAGE SQL SERVICE = s ENDPOINT = e "
            + "AS $$ BEGIN RETURN 1; END $$", "invalid property 'SERVICE' for 'PROCEDURE'");
        assertRefused("CREATE PROCEDURE sv2() RETURNS INT LANGUAGE SQL MAX_BATCH_ROWS = 5 "
            + "AS $$ BEGIN RETURN 1; END $$", "invalid property 'MAX_BATCH_ROWS' for 'FUNCTION'");
        assertRefused("CREATE PROCEDURE e8() RETURNS INT LANGUAGE SQL ENDPOINT = e AS $$ BEGIN RETURN 1; END $$",
            "invalid property 'SERVICE_ENDPOINT' for 'FUNCTION'");
    }
}
