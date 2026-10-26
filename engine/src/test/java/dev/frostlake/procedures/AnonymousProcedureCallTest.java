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
package dev.frostlake.procedures;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A CALL may define the procedure it runs, {@code WITH name AS PROCEDURE (…) RETURNS … AS '…' CALL name(…)}
 * (Snowflake's CALL with an anonymous procedure): the definition is judged as CREATE PROCEDURE judges one, the CALL
 * binds its arguments by position or by name, a procedure that returns a table answers its rows, and a block may call
 * one INTO a variable. No schema keeps it: once its CALL has run it is gone.
 */
public class AnonymousProcedureCallTest extends MeasuredCellsTestSupport {

    private static final String ADD = "WITH ap AS PROCEDURE (x INT, y INT) RETURNS INT LANGUAGE SQL AS "
        + "$$ BEGIN RETURN x * 10 + y; END; $$ ";

    @Test
    public void aCallRunsTheProcedureItDefines() {
        assertCells(new String[][] {
            {"WITH ap AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'anon'; END; $$ CALL ap()",
                "anon"},
            {ADD + "CALL ap(2, 3)", "23"},
            {ADD + "CALL ap(y => 3, x => 2)", "23"},
            {"WITH ap AS PROCEDURE () RETURNS TABLE (a INT) LANGUAGE SQL AS $$ DECLARE r RESULTSET DEFAULT "
                + "(SELECT 1 AS a UNION ALL SELECT 2 ORDER BY a); BEGIN RETURN TABLE(r); END; $$ CALL ap()", "1 ;; 2"},
        });
    }

    @Test
    public void aBlockCallsOneIntoAVariable() {
        assertCells(new String[][] {
            {"EXECUTE IMMEDIATE $$ DECLARE v VARCHAR; BEGIN WITH ap AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS "
                + "'BEGIN RETURN ''inner''; END;' CALL ap() INTO :v; RETURN v; END; $$", "inner"},
        });
    }

    @Test
    public void noSchemaKeepsIt() {
        assertCells(new String[][] {
            {"WITH ap AS PROCEDURE () RETURNS VARCHAR LANGUAGE SQL AS $$ BEGIN RETURN 'anon'; END; $$ CALL ap()",
                "anon"},
            {"SHOW PROCEDURES LIKE 'AP' IN SCHEMA", ""},
            {"CALL ap()", "REFUSED SQL compilation error: | Unknown function AP."},
        });
    }
}
