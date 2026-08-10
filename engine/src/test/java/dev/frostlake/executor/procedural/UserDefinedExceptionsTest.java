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

package dev.frostlake.executor.procedural;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * User-defined exceptions in Snowflake Scripting blocks: DECLARE ... EXCEPTION, RAISE, and the
 * live-verified wording an uncaught exception escapes with — {@code Uncaught exception of type
 * 'NAME' on line L at position P : message}, where the {@code  : message} tail is absent for a
 * message-less declaration and a declaration without arguments defaults to error code -20000.
 * An explicit error code must be a signed integer literal lying strictly between -20,999 and
 * -20,000 — the named bounds themselves are refused.
 */
public class UserDefinedExceptionsTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(UserDefinedExceptionsTest.class);

    /** Runs the script and returns the exception an uncaught RAISE escapes with. */
    private RuntimeException raiseUncaught(final String script) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(script);
            }
        });
    }

    /** Embedded raises the typed ProceduralException; over JDBC only the message survives. */
    private void assertErrorCode(final int expected, final RuntimeException exception) {
        if (exception instanceof ProceduralException) {
            assertEquals(expected, ((ProceduralException) exception).getErrorCode());
        }
    }

    @Test
    public void testDeclareAndRaiseException() {
        logger.info("Testing DECLARE and RAISE exception");

        final String script = """
            DECLARE
                my_exception EXCEPTION (-20002, 'my exception');
            BEGIN
                IF (EXISTS(SELECT 1 AS c)) THEN
                    RAISE my_exception;
                END IF;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains(
                "Uncaught exception of type 'MY_EXCEPTION' on line 5 at position 8"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains(" : my exception"), exception.getMessage());
        assertErrorCode(-20002, exception);

        logger.info("User-defined exception raised correctly");
    }

    @Test
    public void testDefaultCodeExceptionWithoutMessage() {
        logger.info("Testing exception declared without arguments");

        final String script = """
            DECLARE
                plain_exception EXCEPTION;
            BEGIN
                RAISE plain_exception;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains(
                "Uncaught exception of type 'PLAIN_EXCEPTION' on line 4 at position 4"),
            exception.getMessage());
        assertFalse(exception.getMessage().contains(" : "), exception.getMessage());
        assertErrorCode(-20000, exception);

        logger.info("Argument-less exception defaulted to -20000 with no message tail");
    }

    @Test
    public void testDeclareExceptionButDontRaise() {
        logger.info("Testing DECLARE exception without raising it");

        final String script = """
            DECLARE
                my_exception EXCEPTION (-20001, 'error message');
            BEGIN
                IF (1 = 0) THEN
                    RAISE my_exception;
                END IF;
                RETURN 42;
            END;
            """;

        final ResultSet result = engine.executeQuery(script);
        assertEquals(1, result.getRowCount());
        assertEquals(42L, result.getRows().get(0).getValue(0));

        logger.info("Exception declared but not raised - worked correctly");
    }

    @Test
    public void testMultipleExceptionDeclarations() {
        logger.info("Testing multiple exception declarations");

        final String script = """
            DECLARE
                exception1 EXCEPTION (-20001, 'first exception');
                exception2 EXCEPTION (-20002, 'second exception');
            BEGIN
                RAISE exception2;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains("Uncaught exception of type 'EXCEPTION2'"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains(" : second exception"), exception.getMessage());
        assertErrorCode(-20002, exception);

        logger.info("Multiple exceptions declared and correct one raised");
    }

    @Test
    public void testRaiseExceptionInElseBranch() {
        logger.info("Testing RAISE exception in ELSE branch");

        final String script = """
            DECLARE
                validation_error EXCEPTION (-20100, 'validation failed');
            BEGIN
                IF (EXISTS(SELECT * FROM VALUES(1) WHERE $1 > 10)) THEN
                    RETURN 1;
                ELSE
                    RAISE validation_error;
                END IF;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains("Uncaught exception of type 'VALIDATION_ERROR'"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains(" : validation failed"), exception.getMessage());
        assertErrorCode(-20100, exception);

        logger.info("Exception raised in ELSE branch correctly");
    }

    @Test
    public void testRaiseUndefinedException() {
        logger.info("Testing RAISE with undefined exception");

        final String script = """
            BEGIN
                RAISE undefined_exception;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        // The unresolved identifier fails the block's compilation rather than reading as an
        // undefined-exception error: `RAISE nosuch_exc` reports invalid identifier 'NOSUCH_EXC'.
        assertTrue(exception.getMessage().contains("invalid identifier"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains("UNDEFINED_EXCEPTION"),
            exception.getMessage());

        logger.info("Undefined exception correctly rejected");
    }

    @Test
    public void testExceptionInNestedIf() {
        logger.info("Testing exception in nested IF");

        final String script = """
            DECLARE
                nested_error EXCEPTION (-20200, 'nested error');
            BEGIN
                IF (1 = 1) THEN
                    IF (EXISTS(SELECT 1)) THEN
                        RAISE nested_error;
                    END IF;
                END IF;
                RETURN 0;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains("Uncaught exception of type 'NESTED_ERROR'"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains(" : nested error"), exception.getMessage());
        assertErrorCode(-20200, exception);

        logger.info("Exception in nested IF raised correctly");
    }

    @Test
    public void testErrorCodeOutsideRangeIsRefused() {
        logger.info("Testing exception with out-of-range error code");

        final String script = """
            DECLARE
                my_error EXCEPTION (-30000, 'custom error');
            BEGIN
                RAISE my_error;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains(
                "SQL compilation error: error line 2 at position 4"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains(
                " Invalid error code '-30,000'. Must be between -20,999 and -20,000"),
            exception.getMessage());

        logger.info("Out-of-range error code refused at compilation");
    }

    @Test
    public void testErrorCodeBoundariesAreRefused() {
        logger.info("Testing that the named range boundaries themselves are refused");

        // The bounds the refusal names are themselves invalid: the accepted codes lie strictly
        // between -20,999 and -20,000.
        final RuntimeException low = raiseUncaught("""
            DECLARE
                my_error EXCEPTION (-20999, 'edge low');
            BEGIN
                RAISE my_error;
            END;
            """);
        assertTrue(low.getMessage().contains(
                " Invalid error code '-20,999'. Must be between -20,999 and -20,000"),
            low.getMessage());

        final RuntimeException high = raiseUncaught("""
            DECLARE
                my_error EXCEPTION (-20000, 'edge high');
            BEGIN
                RAISE my_error;
            END;
            """);
        assertTrue(high.getMessage().contains(
                " Invalid error code '-20,000'. Must be between -20,999 and -20,000"),
            high.getMessage());

        logger.info("Both boundary error codes refused");
    }

    @Test
    public void testPositiveErrorCodeIsRefused() {
        logger.info("Testing exception with positive error code");

        final String script = """
            DECLARE
                my_error EXCEPTION (100, 'positive');
            BEGIN
                RAISE my_error;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains(
                " Invalid error code '100'. Must be between -20,999 and -20,000"),
            exception.getMessage());

        logger.info("Positive error code refused");
    }

    @Test
    public void testNonLiteralErrorCodeIsSyntaxError() {
        logger.info("Testing exception with a computed error code");

        final String script = """
            DECLARE
                my_error EXCEPTION (-20000 - 1, 'computed');
            BEGIN
                RAISE my_error;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        // Refused at the second '-': the code must be a signed integer literal, not an
        // expression.
        assertTrue(exception.getMessage().contains(
                "syntax error line 2 at position 31 unexpected '-'."),
            exception.getMessage());

        logger.info("Computed error code refused as a syntax error");
    }

    @Test
    public void testConditionalExceptionRaise() {
        logger.info("Testing conditional exception raise based on table data");

        engine.execute("CREATE TABLE test_table (id INTEGER, status VARCHAR)");
        engine.execute("INSERT INTO test_table VALUES (1, 'invalid')");

        final String script = """
            DECLARE
                invalid_status EXCEPTION (-20300, 'Invalid status found');
            BEGIN
                IF (EXISTS(SELECT * FROM test_table WHERE status = 'invalid')) THEN
                    RAISE invalid_status;
                END IF;
                RETURN 1;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains("Uncaught exception of type 'INVALID_STATUS'"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains("Invalid status found"), exception.getMessage());
        assertErrorCode(-20300, exception);

        logger.info("Conditional exception based on table data works correctly");
    }

    @Test
    public void testExceptionWithSpecialCharactersInMessage() {
        logger.info("Testing exception with special characters in message");

        final String script = """
            DECLARE
                special_error EXCEPTION (-20400, 'Error: "quoted", escaped\\\\, and new\\nline');
            BEGIN
                RAISE special_error;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains("Uncaught exception of type 'SPECIAL_ERROR'"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains("Error: \"quoted\", escaped\\, and new"),
            exception.getMessage());
        assertErrorCode(-20400, exception);

        logger.info("Exception with special characters raised correctly");
    }

    @Test
    public void testRaiseExceptionAfterSuccessfulQuery() {
        logger.info("Testing RAISE exception after successful query execution");

        engine.execute("CREATE TABLE test_table (id INTEGER)");
        engine.execute("INSERT INTO test_table VALUES (1), (2), (3)");

        final String script = """
            DECLARE
                post_query_error EXCEPTION (-20500, 'error after query');
            BEGIN
                IF (EXISTS(SELECT * FROM test_table WHERE id > 0)) THEN
                    RAISE post_query_error;
                END IF;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains("Uncaught exception of type 'POST_QUERY_ERROR'"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains(" : error after query"), exception.getMessage());
        assertErrorCode(-20500, exception);

        logger.info("Exception after query raised correctly");
    }

    @Test
    public void testMultipleIfBranchesWithDifferentExceptions() {
        logger.info("Testing multiple IF branches with different exceptions");

        final String script = """
            DECLARE
                error1 EXCEPTION (-20001, 'first error');
                error2 EXCEPTION (-20002, 'second error');
            BEGIN
                IF (1 = 0) THEN
                    RAISE error1;
                ELSEIF (1 = 1) THEN
                    RAISE error2;
                END IF;
                RETURN 0;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains("Uncaught exception of type 'ERROR2'"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains(" : second error"), exception.getMessage());
        assertErrorCode(-20002, exception);

        logger.info("Correct exception raised from multiple branches");
    }

    @Test
    public void testExceptionNameCaseInsensitive() {
        logger.info("Testing exception name is case insensitive");

        final String script = """
            DECLARE
                My_Exception EXCEPTION (-20600, 'case test');
            BEGIN
                RAISE my_exception;
            END;
            """;

        final RuntimeException exception = raiseUncaught(script);

        assertTrue(exception.getMessage().contains("Uncaught exception of type 'MY_EXCEPTION'"),
            exception.getMessage());
        assertTrue(exception.getMessage().contains(" : case test"), exception.getMessage());
        assertErrorCode(-20600, exception);

        logger.info("Case insensitive exception name works correctly");
    }
}
