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
package dev.frostlake.session;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The session's query history holds the statements the session submitted and nothing else: the query a view, a
 * table, a materialized view, a dynamic table or a table function is created from is part of its CREATE, and a
 * read of the view is one statement.
 */
public class CreatedBodiesAreNoHistoryRowsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE WAREHOUSE IF NOT EXISTS COMPUTE_WH");
        engine.execute("CREATE OR REPLACE TABLE qht (a INT, b VARCHAR)");
        engine.execute("INSERT INTO qht VALUES (1, 'x'), (2, 'y')");
    }

    @Test
    public void aCreateIsOneStatementWhateverItIsMadeFrom() {
        final List<String> submitted = Arrays.asList(
            "CREATE OR REPLACE VIEW qh_marker_v AS SELECT a, b FROM qht WHERE a > 0",
            "SELECT b FROM qh_marker_v WHERE a = 2",
            "CREATE OR REPLACE TABLE qh_marker_t AS SELECT a AS qh_marker_c FROM qht",
            "INSERT INTO qh_marker_t SELECT a AS qh_marker_i FROM qht",
            "CREATE OR REPLACE MATERIALIZED VIEW qh_marker_mv AS SELECT a AS qh_marker_m FROM qht",
            "CREATE OR REPLACE DYNAMIC TABLE qh_marker_dt TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH"
                + " AS SELECT a AS qh_marker_d FROM qht",
            "CREATE OR REPLACE SECURE VIEW qh_marker_sv (z) AS SELECT a FROM qht",
            "WITH c AS (SELECT a AS qh_marker_w FROM qht) SELECT COUNT(*) FROM c",
            "CREATE OR REPLACE FUNCTION qh_marker_f() RETURNS TABLE (a INT) AS 'SELECT a FROM qht'",
            "SELECT COUNT(*) FROM TABLE(qh_marker_f())",
            "DESCRIBE VIEW qh_marker_v");
        for (final String statement : submitted) {
            engine.executeQuery(statement);
        }
        final ResultSet rs = engine.executeQuery("SELECT QUERY_TEXT FROM"
            + " TABLE(INFORMATION_SCHEMA.QUERY_HISTORY_BY_SESSION(RESULT_LIMIT => 1000))"
            + " WHERE (QUERY_TEXT ILIKE '%qh_marker%' OR QUERY_TEXT ILIKE '%FROM qht%')"
            + " AND QUERY_TEXT NOT ILIKE '%QUERY_HISTORY%' ORDER BY START_TIME, QUERY_TEXT");
        final List<String> recorded = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            recorded.add(String.valueOf(row.getValue(0)));
        }
        final List<String> expected = new ArrayList<>(submitted);
        Collections.sort(expected);
        Collections.sort(recorded);
        assertEquals(expected, recorded);
    }
}
