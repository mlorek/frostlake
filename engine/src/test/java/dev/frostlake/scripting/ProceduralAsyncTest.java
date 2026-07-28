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

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake Scripting {@code ASYNC (<statement>)} / {@code AWAIT ALL}. The engine is single-threaded, so
 * ASYNC runs its wrapped DML/CALL synchronously and AWAIT is a no-op — the observable result is identical,
 * only the concurrency is lost.
 */
public class ProceduralAsyncTest extends BaseDatabaseTest {

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void asyncDmlStatementsRunAndAwaitAll() {
        engine.execute("CREATE TABLE a1 (x INT)");
        engine.execute("CREATE TABLE a2 (x INT)");
        for (int i = 1; i <= 10; i++) {
            engine.execute("INSERT INTO a1 VALUES (" + i + ")");
            engine.execute("INSERT INTO a2 VALUES (" + i + ")");
        }
        engine.execute("""
            CREATE OR REPLACE PROCEDURE archive() RETURNS STRING LANGUAGE SQL AS
            $$ DECLARE thr INT DEFAULT 5;
               BEGIN
                 ASYNC (DELETE FROM a1 WHERE x <= :thr);
                 ASYNC (DELETE FROM a2 WHERE x <= :thr);
                 AWAIT ALL;
                 RETURN 'done';
               END $$
            """);
        assertEquals("done", scalar("CALL archive()"));
        // Both ASYNC deletes ran (the :thr bind variable resolved), leaving rows 6..10.
        assertEquals(5L, ((Number) scalar("SELECT COUNT(*) FROM a1")).longValue());
        assertEquals(5L, ((Number) scalar("SELECT COUNT(*) FROM a2")).longValue());
    }

    @Test
    public void asyncCallStatementRuns() {
        engine.execute("CREATE TABLE alog (v INT)");
        engine.execute("""
            CREATE OR REPLACE PROCEDURE ins(v INT) RETURNS INT LANGUAGE SQL AS
            $$ BEGIN INSERT INTO alog VALUES (:v); RETURN 1; END $$
            """);
        engine.execute("""
            CREATE OR REPLACE PROCEDURE caller() RETURNS STRING LANGUAGE SQL AS
            $$ BEGIN ASYNC (CALL ins(42)); AWAIT ALL; RETURN 'ok'; END $$
            """);
        assertEquals("ok", scalar("CALL caller()"));
        assertEquals(42, ((Number) scalar("SELECT v FROM alog")).intValue());
    }
}
