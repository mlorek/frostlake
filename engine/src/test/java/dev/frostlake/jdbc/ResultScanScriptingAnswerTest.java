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
package dev.frostlake.jdbc;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A plain star over RESULT_SCAN of a Snowflake Scripting answer - a SQL procedure's CALL or an anonymous block -
 * hands the answer out laid out across lines as the CALL did, aliased or not, and a plain star over the scan of
 * that star does again; every other statement over the scan reads the value compact: a named column, a star beside
 * another item, a LIMIT, a WHERE, a subquery's star, an alias's star (all live-verified).
 */
public class ResultScanScriptingAnswerTest extends BaseJdbcTest {

    private static final String LAID_OUT = "{\n\"a\": 1,\n\"b\": [\n1,\n2\n]\n}";
    private static final String COMPACT = "{\"a\":1,\"b\":[1,2]}";

    @Override
    protected void setupTest() throws SQLException {
        statement.execute("CREATE OR REPLACE PROCEDURE pv() RETURNS VARIANT LANGUAGE SQL AS"
            + " $$ BEGIN RETURN PARSE_JSON('{\"a\":1,\"b\":[1,2]}'); END $$");
    }

    private String first(final String sql) throws SQLException {
        try (final ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getString(1);
        }
    }

    @Test
    public void aPlainStarHandsTheAnswerOutAgain() throws SQLException {
        assertEquals(LAID_OUT, first("CALL pv()"));
        assertEquals(LAID_OUT, first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        assertEquals(LAID_OUT, first("BEGIN RETURN PARSE_JSON('{\"a\":1,\"b\":[1,2]}'); END"));
        assertEquals(LAID_OUT, first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))"));
        assertEquals(LAID_OUT, first("CALL pv()"));
        assertEquals(LAID_OUT, first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) AS r"));
        assertEquals(LAID_OUT, first("CALL pv()"));
        assertEquals(LAID_OUT, first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-1)))"));
        // The CALL, two statements back.
        assertEquals(LAID_OUT, first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))"));
        // The plain star over the CALL's scan, two statements back.
        assertEquals(LAID_OUT, first("SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID(-2)))"));
    }

    @Test
    public void everyOtherStatementOverTheScanReadsItCompact() throws SQLException {
        final String[] reads = {
            "SELECT \"PV\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
            "SELECT *, 1 AS one FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))",
            "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) LIMIT 1",
            "SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE TRUE",
            "SELECT * FROM (SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())))",
            "SELECT r.* FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) AS r",
        };
        for (final String read : reads) {
            assertEquals(LAID_OUT, first("CALL pv()"));
            assertEquals(COMPACT, first(read), read);
        }
    }
}
