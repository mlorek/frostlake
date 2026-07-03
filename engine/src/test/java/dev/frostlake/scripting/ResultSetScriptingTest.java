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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RESULTSET handling in Snowflake Scripting. A RESULTSET value reaches a block two ways — {@code DECLARE rs
 * RESULTSET DEFAULT (query)} and {@code rs := (EXECUTE IMMEDIATE …)} — and both must be usable identically by
 * {@code RETURN TABLE(rs)} and by a {@code FOR rec IN rs} loop. Also covers {@code RETURN TABLE(SELECT …)}.
 */
public class ResultSetScriptingTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private long rowsOf(final String block) {
        return engine.executeQuery(block).getRowCount();
    }

    private int returnedInt(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        return ((Number) rs.getRows().get(0).getValue(0)).intValue();
    }

    @Test
    public void declareResultsetDefaultThenReturnTable() {
        assertEquals(2L, rowsOf("""
            DECLARE rs RESULTSET DEFAULT (SELECT 1 AS a UNION ALL SELECT 2 AS a);
            BEGIN
                RETURN TABLE(rs);
            END;
            """));
    }

    @Test
    public void executeImmediateResultsetThenReturnTable() {
        // The previously-only-working path — kept as a regression guard.
        assertEquals(2L, rowsOf("""
            DECLARE
                res RESULTSET;
                stmt VARCHAR;
            BEGIN
                stmt := 'SELECT 1 AS a UNION ALL SELECT 2 AS a';
                res := (EXECUTE IMMEDIATE stmt);
                RETURN TABLE(res);
            END;
            """));
    }

    @Test
    public void forLoopOverDefaultResultset() {
        assertEquals(30, returnedInt("""
            DECLARE rs RESULTSET DEFAULT (SELECT 10 AS v UNION ALL SELECT 20 AS v);
            BEGIN
                LET total INTEGER := 0;
                FOR rec IN rs DO
                    total := total + rec.v;
                END FOR;
                RETURN total;
            END;
            """));
    }

    @Test
    public void forLoopOverExecuteImmediateResultset() {
        assertEquals(30, returnedInt("""
            DECLARE
                res RESULTSET;
                stmt VARCHAR;
            BEGIN
                stmt := 'SELECT 10 AS v UNION ALL SELECT 20 AS v';
                res := (EXECUTE IMMEDIATE stmt);
                LET total INTEGER := 0;
                FOR rec IN res DO
                    total := total + rec.v;
                END FOR;
                RETURN total;
            END;
            """));
    }

    @Test
    public void returnTableOfDirectQuery() {
        assertEquals(2L, rowsOf("""
            BEGIN
                RETURN TABLE(SELECT 1 AS a UNION ALL SELECT 2 AS a);
            END;
            """));
    }

    @Test
    public void selectFromTableOfDefaultResultset() {
        // FROM TABLE(rs) treats a DEFAULT-init RESULTSET as a table source; the WHERE filters its rows.
        assertEquals(1L, rowsOf("""
            DECLARE rs RESULTSET DEFAULT (SELECT 1 AS a UNION ALL SELECT 2 AS a);
            BEGIN
                RETURN TABLE(SELECT a FROM TABLE(rs) WHERE a = 2);
            END;
            """));
    }

    @Test
    public void selectFromTableOfExecuteImmediateResultset() {
        assertEquals(2L, rowsOf("""
            DECLARE
                res RESULTSET;
                stmt VARCHAR;
            BEGIN
                stmt := 'SELECT 1 AS a UNION ALL SELECT 2 AS a';
                res := (EXECUTE IMMEDIATE stmt);
                RETURN TABLE(SELECT a FROM TABLE(res));
            END;
            """));
    }
}
