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
package dev.frostlake.executor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which requests only read: the judgement the server's shared lock and a read-only request both go by. */
public class ReadOnlyRequestTest {

    @Test
    public void theFourReadingStatementsRead() {
        assertTrue(ReadOnlyRequest.reads("SELECT 1"));
        assertTrue(ReadOnlyRequest.reads("WITH c AS (SELECT 1 AS x) SELECT x FROM c; SELECT 2"));
        assertTrue(ReadOnlyRequest.reads("SHOW TABLES"));
        assertTrue(ReadOnlyRequest.reads("DESCRIBE TABLE t"));
        assertTrue(ReadOnlyRequest.reads("DESC TABLE t"));
        assertTrue(ReadOnlyRequest.reads("EXPLAIN SELECT 1"));
        assertTrue(ReadOnlyRequest.reads("SELECT SYSTEM$TYPEOF(1), SYSTEM$STREAM_HAS_DATA('s')"));
        assertTrue(ReadOnlyRequest.reads("SELECT * FROM TABLE(some_procedure())"));
        assertTrue(ReadOnlyRequest.reads("SELECT nextval_of_x, to_query_text FROM t"));
    }

    @Test
    public void anythingElseWrites() {
        assertFalse(ReadOnlyRequest.reads("DELETE FROM t"));
        assertFalse(ReadOnlyRequest.reads("SELECT 1; DROP TABLE t"));
        assertFalse(ReadOnlyRequest.reads("CALL p()"));
        assertFalse(ReadOnlyRequest.reads("SET v = 1"));
        assertFalse(ReadOnlyRequest.reads("USE SCHEMA s"));
        assertFalse(ReadOnlyRequest.reads("BEGIN RETURN 1; END;"));
        assertFalse(ReadOnlyRequest.reads("SELEC 1"));
        assertFalse(ReadOnlyRequest.reads(""));
    }

    @Test
    public void aQueryThatReachesAWriteWrites() {
        assertFalse(ReadOnlyRequest.reads("SELECT s.NEXTVAL"));
        assertFalse(ReadOnlyRequest.reads("SELECT s.nextval"));
        assertFalse(ReadOnlyRequest.reads("SELECT s.\"NEXTVAL\""));
        assertFalse(ReadOnlyRequest.reads("SELECT * FROM TABLE(TO_QUERY('SELECT 1'))"));
        assertFalse(ReadOnlyRequest.reads("SELECT * FROM TABLE(\"TO_QUERY\"('CALL p()'))"));
        assertFalse(ReadOnlyRequest.reads("SELECT SYSTEM$ABORT_SESSION(1)"));
        assertFalse(ReadOnlyRequest.reads("SELECT SYSTEM$CANCEL_QUERY('q')"));
        assertFalse(ReadOnlyRequest.reads("SELECT SYSTEM$TASK_DEPENDENTS_ENABLE('t')"));
        assertFalse(ReadOnlyRequest.reads("SELECT SYSTEM$NOT_A_FUNCTION()"));
    }

    @Test
    public void theRefusalSaysWhy() {
        assertNull(ReadOnlyRequest.refusal("SELECT 1"));
        assertNull(ReadOnlyRequest.refusal("SELEC 1"));
        assertEquals("Read-only request: a statement that begins with DROP may write. Only a query, SHOW, DESCRIBE and"
            + " EXPLAIN run.", ReadOnlyRequest.refusal("SELECT 1; DROP TABLE t"));
        assertEquals("Read-only request: NEXTVAL moves a sequence.", ReadOnlyRequest.refusal("SELECT s.NEXTVAL"));
        assertEquals("Read-only request: TO_QUERY may run a procedure.",
            ReadOnlyRequest.refusal("SELECT * FROM TABLE(TO_QUERY('SELECT 1'))"));
        assertEquals("Read-only request: SYSTEM$CANCEL_QUERY may act on the server.",
            ReadOnlyRequest.refusal("SELECT SYSTEM$CANCEL_QUERY('q')"));
    }
}
