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
 * An OPEN runs its cursor's query as a child statement of the session's, listed in QUERY_HISTORY under the query as
 * the cursor declares it, before the statements the block runs after it (live-verified).
 */
public class CursorOpenHistoryTest extends MeasuredCellsTestSupport {

    @Test
    public void anOpenedCursorsQueryIsAChildStatement() {
        assertCells(new String[][] {
            {"BEGIN LET c CURSOR FOR SELECT 943011; OPEN c; CLOSE c; LET r RESULTSET := (SELECT 943012); RETURN 'z'; "
                + "END;", "z"},
            {"SELECT QUERY_TYPE, EXECUTION_STATUS, QUERY_TEXT FROM TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION("
                + "RESULT_LIMIT => 300)) WHERE CONTAINS(QUERY_TEXT, '9430' || '1') ORDER BY START_TIME, QUERY_TEXT",
                "CALL|SUCCESS|BEGIN LET c CURSOR FOR SELECT 943011; OPEN c; CLOSE c; LET r RESULTSET := (SELECT "
                + "943012); RETURN 'z'; END; ;; SELECT|SUCCESS|SELECT 943011 ;; SELECT|SUCCESS|SELECT 943012"},
        });
    }
}
