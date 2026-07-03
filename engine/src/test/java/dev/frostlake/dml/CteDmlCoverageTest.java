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

package dev.frostlake.dml;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CTE (WITH clause) coverage across DML/DDL combinations the existing CTE suite did not exercise:
 * CREATE TABLE AS SELECT, CREATE VIEW, UPDATE … FROM, DELETE … USING, an empty CTE, and a recursive CTE
 * feeding a CTAS. (CTE + SELECT / INSERT / MERGE and the recursive-CTE basics are covered elsewhere.)
 */
public class CteDmlCoverageTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA public");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private long scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return ((Number) rs.getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void ctasFromCte() {
        engine.execute("CREATE TABLE src (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO src VALUES (1,10),(2,20)");
        engine.execute("CREATE TABLE dst AS WITH c AS (SELECT id, v * 2 AS v2 FROM src) SELECT id, v2 FROM c");
        assertEquals(2L, scalar("SELECT COUNT(*) FROM dst"));
        assertEquals(20L, scalar("SELECT v2 FROM dst WHERE id = 1"));
    }

    @Test
    public void ctasFromRecursiveCte() {
        engine.execute("""
            CREATE TABLE nums AS
            WITH RECURSIVE r(n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM r WHERE n < 5)
            SELECT n FROM r
            """);
        assertEquals(5L, scalar("SELECT COUNT(*) FROM nums"));
        assertEquals(5L, scalar("SELECT MAX(n) FROM nums"));
    }

    @Test
    public void createViewFromCte() {
        engine.execute("CREATE TABLE src (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO src VALUES (1,10),(2,20)");
        engine.execute("CREATE VIEW vw AS WITH c AS (SELECT id, v FROM src WHERE v > 15) SELECT * FROM c");
        assertEquals(1L, scalar("SELECT COUNT(*) FROM vw"));
        assertEquals(2L, scalar("SELECT id FROM vw"));
    }

    @Test
    public void updateFromCte() {
        engine.execute("CREATE TABLE t (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO t VALUES (1,0),(2,0)");
        engine.execute("CREATE TABLE s (id INTEGER, nv INTEGER)");
        engine.execute("INSERT INTO s VALUES (1,100),(2,200)");
        engine.execute("WITH c AS (SELECT id, nv FROM s) UPDATE t SET v = c.nv FROM c WHERE t.id = c.id");
        assertEquals(100L, scalar("SELECT v FROM t WHERE id = 1"));
        assertEquals(200L, scalar("SELECT v FROM t WHERE id = 2"));
    }

    @Test
    public void deleteUsingCte() {
        engine.execute("CREATE TABLE t (id INTEGER)");
        engine.execute("INSERT INTO t VALUES (1),(2),(3)");
        engine.execute("CREATE TABLE removals (id INTEGER)");
        engine.execute("INSERT INTO removals VALUES (2)");
        engine.execute("WITH c AS (SELECT id FROM removals) DELETE FROM t USING c WHERE t.id = c.id");
        assertEquals(2L, scalar("SELECT COUNT(*) FROM t"));
        assertEquals(0L, scalar("SELECT COUNT(*) FROM t WHERE id = 2"));
    }

    @Test
    public void emptyCte() {
        engine.execute("CREATE TABLE e (id INTEGER)");
        assertEquals(0L, scalar("WITH c AS (SELECT * FROM e) SELECT COUNT(*) FROM c"));
    }

    @Test
    public void updateSetFromCteSubquery() {
        // A subquery in the UPDATE SET clause can reference a WITH-clause CTE.
        engine.execute("CREATE TABLE t (id INTEGER, v INTEGER)");
        engine.execute("INSERT INTO t VALUES (1, 0)");
        engine.execute("WITH c AS (SELECT 99 AS x) UPDATE t SET v = (SELECT x FROM c) WHERE id = 1");
        assertEquals(99L, scalar("SELECT v FROM t WHERE id = 1"));
    }
}
