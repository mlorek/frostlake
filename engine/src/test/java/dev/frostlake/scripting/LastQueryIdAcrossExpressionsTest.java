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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A scripting expression of built-in calls and operators is no statement: LAST_QUERY_ID still names the statement
 * before it, so after {@code CALL p(); a := ARRAY_APPEND(a, 'x');} RESULT_SCAN(LAST_QUERY_ID()) reads the CALL's
 * table. One that calls a user-defined function or reads a query runs as a query of its own, which LAST_QUERY_ID
 * then names: RESULT_SCAN reads its one value and refuses "invalid identifier 'ROW_ID'" (live-verified).
 */
public class LastQueryIdAcrossExpressionsTest extends BaseDatabaseTest {

    @BeforeEach
    public void tableProcedure() {
        engine.execute("CREATE OR REPLACE PROCEDURE rows_p() RETURNS TABLE (row_id NUMBER) LANGUAGE SQL AS $$"
            + " DECLARE rs RESULTSET; BEGIN rs := (SELECT 1 AS row_id UNION ALL SELECT 2); RETURN TABLE(rs); END $$");
        engine.execute("CREATE OR REPLACE FUNCTION twice(x NUMBER) RETURNS NUMBER AS $$ x * 2 $$");
    }

    private String scalar(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    @Test
    public void aBuiltInCallKeepsTheCallsResult() {
        assertEquals("2", scalar("""
            EXECUTE IMMEDIATE $$
            DECLARE cnt INT; a ARRAY DEFAULT ARRAY_CONSTRUCT(); n NUMBER;
            BEGIN
              CALL rows_p();
              a := ARRAY_APPEND(a, 'CALL rows_p()');
              n := ABS(-2) + 1;
              IF (ARRAY_SIZE(a) = 1 AND UPPER('x') = 'X') THEN
                a := ARRAY_APPEND(a, 'SELECT ...');
              END IF;
              SELECT COUNT(*) INTO :cnt FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE row_id IN (1, 2);
              RETURN cnt;
            END;
            $$"""));
    }

    @Test
    public void aUserFunctionOrAQueryTakesAQueryIdOfItsOwn() {
        final String[][] cells = {
            {"n := twice(21);", "100"},
            {"LET z := twice(1);", "103"},
            {"n := (SELECT 5);", "101"},
            {"a := ARRAY_APPEND(a, 'x'); n := twice(21);", "127"},
            {"IF (twice(1) = 2) THEN n := 1; END IF;", "123"},
        };
        for (final String[] cell : cells) {
            assertEquals("Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position " + cell[1]
                + " : SQL compilation error: error line 1 at position 63\ninvalid identifier 'ROW_ID'",
                refusal(between(cell[0])), cell[0]);
        }
        for (final String kept : new String[] {"", "a := ARRAY_APPEND(a, 'x');",
            "IF (UPPER('x') = 'X') THEN n := 1; END IF;", "n := ABS(-2);", "n := 1 + 1;",
            "IF (ARRAY_SIZE(a) = 0) THEN n := 1; END IF;"}) {
            assertEquals("2", scalar(between(kept)), kept);
        }
        assertEquals("false", scalar("EXECUTE IMMEDIATE $$ DECLARE cnt INT; BEGIN CALL rows_p(); "
            + "RETURN (SELECT LAST_QUERY_ID() IS NULL); END; $$").toLowerCase(Locale.ROOT));
    }

    /** A block that CALLs rows_p(), runs the given steps, and counts what RESULT_SCAN(LAST_QUERY_ID()) reads. */
    private static String between(final String steps) {
        return "EXECUTE IMMEDIATE $$ DECLARE cnt INT; a ARRAY DEFAULT ARRAY_CONSTRUCT(); n NUMBER; BEGIN "
            + "CALL rows_p(); " + steps + " SELECT COUNT(*) INTO :cnt FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) "
            + "WHERE row_id IN (1, 2); RETURN cnt; END; $$";
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void aProcedureReadsTheCallItMadeBeforeAnAssignment() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE reads_call() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE cnt INT; steps ARRAY DEFAULT ARRAY_CONSTRUCT();
            BEGIN
              CALL rows_p();
              steps := ARRAY_APPEND(steps, 'SELECT COUNT(*) FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))');
              SELECT COUNT(*) INTO :cnt FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE row_id IS NOT NULL;
              RETURN cnt || ' rows after ' || ARRAY_SIZE(steps) || ' step';
            END;
            $$""");
        assertEquals("2 rows after 1 step", scalar("CALL reads_call()"));
    }

    @Test
    public void aStatementStillMovesIt() {
        assertEquals("b", scalar("""
            EXECUTE IMMEDIATE $$
            DECLARE v VARCHAR;
            BEGIN
              SELECT 'a' AS c;
              SELECT 'b' AS c;
              SELECT c INTO :v FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()));
              RETURN v;
            END;
            $$"""));
    }
}
