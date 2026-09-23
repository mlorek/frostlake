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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RESULT_SCAN reads the statement its FIRST argument names, and every argument after it is ignored —
 * a string, a NULL, or a third one. A second argument neither moves the statement it reads nor refuses
 * the call.
 */
public class ResultScanExtraArgumentsTest extends BaseDatabaseTest {

    /** The first column of the first row, as text. */
    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** The same statement is read whatever follows the first argument. */
    @Test
    public void everyArgumentAfterTheFirstIsIgnored() {
        engine.executeQuery("SELECT 'marker' AS c");
        final String byId = answer("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertEquals("marker", byId);
        assertEquals("marker", answer("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2), 'zzz'))"),
            "a string second argument reads the same statement");
        assertEquals("marker", answer("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-3), NULL))"),
            "a NULL second argument is ignored, not refused");
        assertEquals("marker", answer("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-4), 'a', 'b'))"),
            "and so is a third");
    }
}
