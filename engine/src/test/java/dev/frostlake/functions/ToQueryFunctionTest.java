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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TO_QUERY table function — {@code SELECT * FROM TABLE(TO_QUERY('<sql>'))} compiles the text into a
 * query and returns its rows; named arguments bind into {@code :name} placeholders. The production
 * shape is a stored procedure holding its source query in a variable and reading
 * {@code FROM TABLE(TO_QUERY(:source_sql))}.
 */
public class ToQueryFunctionTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE tq (x INTEGER, s VARCHAR)");
        engine.execute("INSERT INTO tq VALUES (1, 'a'), (2, 'b'), (3, 'c')");
    }

    @Test
    public void literalSqlTextRunsAsAQuery() {
        final ResultSet rs = engine.executeQuery(
            "SELECT * FROM TABLE(TO_QUERY('SELECT x, s FROM tq ORDER BY x'))");
        assertEquals(3, rs.getRowCount());
        assertEquals("1", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals("c", String.valueOf(rs.getRows().get(2).getValue(1)));
    }

    @Test
    public void resultParticipatesInTheOuterQuery() {
        final ResultSet rs = engine.executeQuery(
            "SELECT COUNT(*) FROM TABLE(TO_QUERY('SELECT x FROM tq')) WHERE x > 1");
        assertEquals("2", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void namedArgumentBindsIntoAPlaceholder() {
        final ResultSet rs = engine.executeQuery(
            "SELECT s FROM TABLE(TO_QUERY('SELECT s FROM tq WHERE x = :v', v => 2))");
        assertEquals(1, rs.getRowCount());
        assertEquals("b", String.valueOf(rs.getRows().get(0).getValue(0)));
    }

    @Test
    public void procedureVariableHoldsTheSourceSql() {
        // The loader shape: the source query text lives in a scripting variable, and the INSERT's
        // SELECT reads FROM TABLE(TO_QUERY(:src)).
        engine.execute("CREATE TABLE tq_out (x INTEGER)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE copy_via_to_query() RETURNS VARCHAR LANGUAGE SQL AS $$
            DECLARE src VARCHAR DEFAULT 'SELECT x FROM tq WHERE x >= 2';
            BEGIN
              INSERT INTO tq_out (x) SELECT q.x FROM TABLE(TO_QUERY(:src)) q;
              RETURN 'done';
            END $$""");
        engine.execute("CALL copy_via_to_query()");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*), MIN(x) FROM tq_out");
        assertEquals("2", String.valueOf(rs.getRows().get(0).getValue(0)));
        assertEquals("2", String.valueOf(rs.getRows().get(0).getValue(1)));
    }
}
