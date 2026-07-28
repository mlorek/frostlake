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
 * A stored procedure declared {@code RETURNS TABLE(…)} can be a FROM source — {@code TABLE(proc(args))}
 * — with the returned table as the row source and its columns named by the DECLARED signature, as in
 * Snowflake. The procedure runs as part of the enclosing statement: nested inside another procedure, in
 * an INSERT…SELECT, and inside an explicit transaction, without committing the enclosing statement's
 * transaction. Also covers the RESULTSET-assignment idiom with a leading comment inside the parentheses
 * ({@code res := ( -- note … SELECT …)}), common in real bodies.
 */
public class TableProcSourceTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE readings (grp VARCHAR, amount NUMBER)");
        engine.execute("INSERT INTO readings VALUES ('g1', 5), ('g1', 7), ('g2', 11)");
        engine.execute(
            """
            CREATE PROCEDURE grouped_totals(src_name VARCHAR)
            RETURNS TABLE (BUCKET VARCHAR, TOTAL NUMBER(32,0))
            LANGUAGE SQL
            AS
            $$
            DECLARE
              res RESULTSET;
            BEGIN
              res := (
                  -- leading comment must not hide the query from the RESULTSET path
                  SELECT grp, SUM(amount) AS total FROM IDENTIFIER(:src_name) GROUP BY grp);
              RETURN TABLE(res);
            END;
            $$
            """);
    }

    @Test
    public void procAsFromSourceUsesDeclaredColumnNames() {
        final ResultSet result = engine.executeQuery(
            "SELECT f.bucket, f.total FROM TABLE(grouped_totals('readings')) f ORDER BY f.bucket");
        assertEquals(2, result.getRows().size());
        assertEquals("g1", result.getRows().get(0).getValue(0));
        assertEquals(12L, ((Number) result.getRows().get(0).getValue(1)).longValue());
        assertEquals("g2", result.getRows().get(1).getValue(0));
        assertEquals(11L, ((Number) result.getRows().get(1).getValue(1)).longValue());
    }

    @Test
    public void worksWithWhereAndAggregationOverTheSource() {
        final ResultSet result = engine.executeQuery(
            "SELECT COUNT(*) AS n FROM TABLE(grouped_totals('readings')) f WHERE f.total > 11");
        assertEquals(1L, ((Number) result.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void emptyResultKeepsDeclaredColumnsQueryable() {
        engine.execute("CREATE TABLE empty_readings (grp VARCHAR, amount NUMBER)");
        final ResultSet result = engine.executeQuery(
            "SELECT f.bucket FROM TABLE(grouped_totals('empty_readings')) f");
        assertEquals(0, result.getRows().size());
    }

    @Test
    public void insertSelectFromProcSourceAtTopLevel() {
        engine.execute("CREATE TABLE totals_out (bucket VARCHAR, total NUMBER)");
        engine.execute(
            "INSERT INTO totals_out (bucket, total) SELECT f.bucket, f.total FROM TABLE(grouped_totals('readings')) f");
        final ResultSet result = engine.executeQuery("SELECT COUNT(*) AS n FROM totals_out");
        assertEquals(2L, ((Number) result.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void insertSelectFromProcSourceNestedInsideAnotherProcedure() {
        engine.execute("CREATE TABLE totals_out2 (bucket VARCHAR, total NUMBER)");
        engine.execute(
            """
            CREATE PROCEDURE load_totals()
            RETURNS VARCHAR LANGUAGE SQL AS
            $$
            BEGIN
              INSERT INTO totals_out2 (bucket, total)
              SELECT f.bucket, f.total FROM TABLE(grouped_totals('readings')) f;
              RETURN 'ok';
            END;
            $$
            """);
        engine.execute("CALL load_totals()");
        final ResultSet result = engine.executeQuery(
            "SELECT bucket, total FROM totals_out2 ORDER BY bucket");
        assertEquals(2, result.getRows().size());
        assertEquals(12L, ((Number) result.getRows().get(0).getValue(1)).longValue());
    }

    @Test
    public void enclosingExplicitTransactionSurvivesTheProcCall() {
        engine.execute("CREATE TABLE txn_out (bucket VARCHAR, total NUMBER)");
        engine.execute(
            """
            CREATE PROCEDURE load_in_txn()
            RETURNS VARCHAR LANGUAGE SQL AS
            $$
            BEGIN
              BEGIN TRANSACTION;
              INSERT INTO txn_out (bucket, total)
              SELECT f.bucket, f.total FROM TABLE(grouped_totals('readings')) f;
              UPDATE txn_out t SET t.total = t.total + 100 WHERE t.bucket = 'g1';
              COMMIT;
              RETURN 'ok';
            END;
            $$
            """);
        engine.execute("CALL load_in_txn()");
        final ResultSet result = engine.executeQuery(
            "SELECT total FROM txn_out WHERE bucket = 'g1'");
        assertEquals(112L, ((Number) result.getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void nestedCallOfTableProcedureReturnsTheFullTable() {
        engine.execute(
            """
            CREATE PROCEDURE relay_totals()
            RETURNS TABLE (BUCKET VARCHAR, TOTAL NUMBER(32,0)) LANGUAGE SQL AS
            $$
            DECLARE
              res RESULTSET;
            BEGIN
              res := (SELECT f.bucket, f.total FROM TABLE(grouped_totals('readings')) f);
              RETURN TABLE(res);
            END;
            $$
            """);
        final ResultSet result = engine.executeQuery(
            "SELECT COUNT(*) AS n FROM TABLE(relay_totals()) r");
        assertEquals(2L, ((Number) result.getRows().get(0).getValue(0)).longValue());
    }
}
