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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A scripting expression is not a statement: LAST_QUERY_ID still names the statement before it. The engine
 * evaluates a function call in an assignment, a condition or a RETURN by running a rebuilt
 * {@code SELECT f(args)}, which used to take a query ID of its own — so after
 * {@code CALL p(); a := ARRAY_APPEND(a, 'x');} RESULT_SCAN(LAST_QUERY_ID()) read the one-column answer of the
 * ARRAY_APPEND and refused "invalid identifier 'ROW_ID'". Snowflake reads the CALL's table there.
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
    public void aFunctionCallAssignmentKeepsTheCallsResult() {
        assertEquals("2", scalar("""
            EXECUTE IMMEDIATE $$
            DECLARE cnt INT; a ARRAY DEFAULT ARRAY_CONSTRUCT(); n NUMBER;
            BEGIN
              CALL rows_p();
              a := ARRAY_APPEND(a, 'CALL rows_p()');
              n := twice(21);
              IF (ARRAY_SIZE(a) = 1 AND UPPER('x') = 'X') THEN
                a := ARRAY_APPEND(a, 'SELECT ...');
              END IF;
              SELECT COUNT(*) INTO :cnt FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE row_id IN (1, 2);
              RETURN cnt;
            END;
            $$"""));
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
