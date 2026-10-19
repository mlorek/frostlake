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
 * Inside a Snowflake Scripting block an EXECUTE IMMEDIATE text is held to the session's MULTI_STATEMENT_COUNT
 * (live-verified): under 0 a text of several statements runs, its INSERTs inserting, and once the count is unset
 * the text is refused against 1 again.
 */
public class ExecuteImmediateSessionCountTest extends MeasuredCellsTestSupport {

    @Test
    public void aBlockHoldsTheTextToTheSessionsCount() {
        engine.execute("CREATE OR REPLACE TABLE mi_t (a INT)");
        try {
            assertCells(new String[][] {
                {"EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'",
                    "REFUSED Actual statement count 2 did not match the desired statement count 1."},
                {"ALTER SESSION SET MULTI_STATEMENT_COUNT = 0", "Statement executed successfully."},
                {"BEGIN EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'; RETURN 'ran'; END;", "ran"},
                {"BEGIN LET r RESULTSET := (EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'); RETURN 'ran'; END;", "ran"},
                {"BEGIN EXECUTE IMMEDIATE 'SELECT 1;; SELECT 2'; RETURN 'ran'; END;", "ran"},
                {"BEGIN EXECUTE IMMEDIATE 'INSERT INTO mi_t VALUES (1); INSERT INTO mi_t VALUES (2)'; RETURN 'ran'; "
                    + "END;", "ran"},
                {"SELECT COUNT(*) FROM mi_t", "2"},
                {"ALTER SESSION SET MULTI_STATEMENT_COUNT = 2", "Statement executed successfully."},
                {"ALTER SESSION UNSET MULTI_STATEMENT_COUNT", "Statement executed successfully."},
                {"BEGIN EXECUTE IMMEDIATE 'SELECT 1; SELECT 2'; RETURN 'ran'; END;",
                    "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 6 : Actual statement "
                    + "count 2 did not match the desired statement count 1."},
            });
        } finally {
            // The session's count goes back to its default whatever the cells answered: a live session is shared.
            answer("ALTER SESSION UNSET MULTI_STATEMENT_COUNT");
        }
    }
}
