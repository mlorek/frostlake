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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A RESULTSET that was declared and never given a statement. Reading it is {@code Stored procedure execution
 * error: uninitialized resultSet 'RS'} - 92212 / P0000 to a handler - a statement error placed at the name:
 * in {@code RETURN TABLE(rs)}, from a block or a procedure, and where a cursor is declared over it, in a LET
 * or in the DECLARE section, so that the cursor's OPEN is never reached. A FOR over it is the same refusal,
 * which the account places nowhere: {@code on line 0 at position -1}. Once a statement is assigned to it the
 * RESULTSET reads as any other. Every answer is live-verified.
 */
public class UnfilledResultSetTest extends MeasuredCellsTestSupport {

    @Test
    public void readBeforeAnythingWasGivenToIt() {
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : Stored procedure "
                + "execution error: uninitialized resultSet 'RS'",
            "EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; BEGIN RETURN TABLE(rs); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 46 : Stored procedure "
                + "execution error: uninitialized resultSet 'RS'",
            "EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; BEGIN LET c CURSOR FOR rs; RETURN 'declared'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 36 : Stored procedure "
                + "execution error: uninitialized resultSet 'RS'",
            "EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; c CURSOR FOR rs; BEGIN OPEN c; RETURN 'opened'; END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 0 at position -1 : Stored procedure "
                + "execution error: uninitialized resultSet 'RS'",
            "EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; BEGIN FOR x IN rs DO RETURN 1; END FOR; RETURN 'none'; "
                + "END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 38 : Stored procedure "
                + "execution error: uninitialized resultSet 'RS'",
            "EXECUTE IMMEDIATE $$ BEGIN LET rs RESULTSET; RETURN TABLE(rs); END; $$");
        assertAnswer(
            "Function XT successfully created.",
            "CREATE OR REPLACE PROCEDURE xt() RETURNS TABLE () LANGUAGE SQL AS $$ DECLARE rs RESULTSET; BEGIN "
                + "RETURN TABLE(rs); END; $$");
        assertAnswer(
            "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 42 : Stored procedure "
                + "execution error: uninitialized resultSet 'RS'",
            "CALL xt()");
        assertAnswer(
            "92212|Stored procedure execution error: uninitialized resultSet 'RS'|P0000|62",
            "EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; BEGIN RETURN TABLE(rs); EXCEPTION WHEN OTHER THEN RETURN "
                + "SQLCODE || '|' || SQLERRM || '|' || SQLSTATE || '|' || LENGTH(SQLERRM); END; $$");
        assertAnswer(
            "1",
            "EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; BEGIN rs := (SELECT 1 AS a); RETURN TABLE(rs); END; $$");
        answer(
            "EXECUTE IMMEDIATE $$ DECLARE rs RESULTSET; n NUMBER; BEGIN n := (SELECT COUNT(*) FROM TABLE(rs)); "
                + "RETURN n; END; $$");
    }
}
