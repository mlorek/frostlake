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

package dev.frostlake.stream;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A stream read by DML inside a stored procedure must advance its offset when the transaction commits —
 * exactly as the same DML would at top level. The consuming-DML window used to be opened only by the
 * string entry point ({@code QueryExecutor.execute}), but a {@code BEGIN…END} body dispatches its
 * statements through the visitor directly, so a procedure's {@code INSERT … SELECT FROM stream} read the
 * stream without registering it: the offset never advanced, and each re-CALL of a loader procedure
 * re-processed (duplicated) everything it had already loaded. The window now opens at
 * {@code visitDmlStatement}, the dispatch point every path shares.
 */
public class StreamConsumptionInProcedureTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE TABLE src (k VARCHAR)");
        engine.execute("CREATE STREAM st ON TABLE src");
        engine.execute("CREATE TABLE tgt (k VARCHAR)");
        engine.execute("INSERT INTO src VALUES ('r1'), ('r2')");
    }

    private long count(final String sql) {
        return engine.executeQuery(sql).getRowCount();
    }

    @Test
    public void procedureDmlConsumesTheStreamOnItsCommit() {
        engine.execute("""
            CREATE PROCEDURE load_it() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              INSERT INTO tgt SELECT k FROM st;
              COMMIT;
              RETURN 'ok';
            END $$""");
        engine.execute("CALL load_it()");
        assertEquals(2, count("SELECT k FROM tgt"));
        assertEquals(0, count("SELECT k FROM st"));   // consumed at the body COMMIT
    }

    @Test
    public void procedureDmlConsumesAtTheCallsAutocommitToo() {
        engine.execute("""
            CREATE PROCEDURE load_it() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              INSERT INTO tgt SELECT k FROM st;
              RETURN 'ok';
            END $$""");
        engine.execute("CALL load_it()");
        assertEquals(2, count("SELECT k FROM tgt"));
        assertEquals(0, count("SELECT k FROM st"));   // consumed when the CALL's transaction commits
    }

    @Test
    public void nestedCallConsumesAndACallRepeatedLoadsOnlyTheDelta() {
        // The loader-fixture shape: an outer procedure inserts new base rows, then CALLs the loader;
        // later steps insert more and CALL the loader again — each pass must load only its delta.
        engine.execute("""
            CREATE PROCEDURE loader() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              INSERT INTO tgt SELECT k FROM st;
              COMMIT;
              RETURN 'ok';
            END $$""");
        engine.execute("""
            CREATE PROCEDURE step() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              INSERT INTO src VALUES ('r3');
              CALL loader();
              RETURN 'done';
            END $$""");
        engine.execute("CALL step()");
        assertEquals(3, count("SELECT k FROM tgt"));      // r1, r2 (seed) + r3 (same-txn insert)
        assertEquals(0, count("SELECT k FROM st"));
        engine.execute("CALL step()");                     // second pass: only r3's twin is new
        assertEquals(4, count("SELECT k FROM tgt"));      // +1, NOT re-loaded r1..r3
        assertEquals(0, count("SELECT k FROM st"));
    }

    @Test
    public void aSiblingStreamKeepsItsOwnOffset() {
        engine.execute("CREATE STREAM st_other ON TABLE src");
        engine.execute("INSERT INTO src VALUES ('r3')");   // visible to both streams
        engine.execute("""
            CREATE PROCEDURE loader() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              INSERT INTO tgt SELECT k FROM st;
              COMMIT;
              RETURN 'ok';
            END $$""");
        engine.execute("CALL loader()");
        assertEquals(0, count("SELECT k FROM st"));        // consumed
        assertEquals(1, count("SELECT k FROM st_other"));  // untouched — created after r1/r2, sees only r3
    }

    @Test
    public void aMidProcedureFlushDoesNotSwallowRowsInsertedAfterIt() {
        // The loader-fixture idiom: flush the stream into a scrap temp table, insert the actual
        // scenario rows, then run the loader — which must see ONLY the post-flush rows. Needs both
        // halves of the fix: DDL inside the body implicitly commits the open transaction (so the
        // flush consumes at its own point), and commit-time consumption is scoped to what each read
        // saw (so the flush cannot swallow later inserts re-emitted by the same commit).
        engine.execute("""
            CREATE PROCEDURE loader() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              INSERT INTO tgt SELECT k FROM st;
              COMMIT;
              RETURN 'ok';
            END $$""");
        engine.execute("""
            CREATE PROCEDURE step() RETURNS VARCHAR LANGUAGE SQL AS $$
            BEGIN
              INSERT INTO src VALUES ('pre');
              CREATE OR REPLACE TEMP TABLE tmp_flush AS SELECT * FROM st;
              DROP TABLE IF EXISTS tmp_flush;
              INSERT INTO src VALUES ('post-1');
              INSERT INTO src VALUES ('post-2');
              CALL loader();
              RETURN 'done';
            END $$""");
        engine.execute("CALL step()");
        assertEquals(2, count("SELECT k FROM tgt"));                       // post-1, post-2 only
        assertEquals(0, count("SELECT k FROM tgt WHERE k IN ('r1','r2','pre')"));
        assertEquals(0, count("SELECT k FROM st"));
    }

    @Test
    public void consumptionIsScopedToWhatTheReadSaw() {
        // Explicit transaction: a consuming read, then MORE inserts to the base table, then COMMIT.
        // Only what the read saw is consumed; the later inserts surface as new stream rows.
        engine.execute("BEGIN");
        engine.execute("INSERT INTO tgt SELECT k FROM st");   // sees r1, r2
        engine.execute("INSERT INTO src VALUES ('r3')");      // after the read
        engine.execute("COMMIT");
        assertEquals(2, count("SELECT k FROM tgt"));
        assertEquals(1, count("SELECT k FROM st WHERE k = 'r3'"));
    }

    @Test
    public void aPlainSelectInsideAProcedureDoesNotConsume() {
        engine.execute("""
            CREATE PROCEDURE peek() RETURNS NUMBER LANGUAGE SQL AS $$
            DECLARE n NUMBER;
            BEGIN
              SELECT COUNT(*) INTO :n FROM st;
              RETURN n;
            END $$""");
        engine.execute("CALL peek()");
        assertEquals(2, count("SELECT k FROM st"));        // still unconsumed
    }
}
