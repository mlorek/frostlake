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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A missing table named where a statement READS it, a MERGE target or source or a CLONE or LIKE source, is a
 * missing Object named as written: bare as the writer spelled it, in full when any qualifier was given.
 * COPY INTO has words of its own, the name as written and no "or not authorized" tail. Every cell is
 * live-verified.
 */
public class MissingTableSentenceTest extends BaseDatabaseTest {

    private static final String ACCEPTED = "<accepted>";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
        engine.execute("CREATE TABLE u (a INT)");
    }

    private String outcome(final String sql) {
        try {
            engine.executeQuery(sql);
            return ACCEPTED;
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String missing(final String kind, final String name) {
        return "SQL compilation error:|" + kind + " '" + name + "' does not exist or not authorized.";
    }

    @Test
    public void aMergeNamesAMissingTableAsAnObject() {
        assertEquals(missing("Object", "NOSUCH"),
            outcome("MERGE INTO nosuch USING (SELECT 1 AS a) s ON nosuch.a = s.a WHEN MATCHED THEN DELETE"));
        assertEquals(missing("Object", "NOSUCH2"),
            outcome("MERGE INTO t USING nosuch2 s ON t.a = s.a WHEN MATCHED THEN DELETE"));
        assertEquals(missing("Object", "TEST_DB.TEST_SCHEMA.NOSUCH"),
            outcome("MERGE INTO test_schema.nosuch USING (SELECT 1 AS a) s ON nosuch.a = s.a WHEN MATCHED THEN DELETE"));
        assertEquals(ACCEPTED, outcome("MERGE INTO t USING u ON t.a = u.a WHEN MATCHED THEN DELETE"));
    }

    @Test
    public void aCloneOrLikeSourceIsAnObjectToo() {
        assertEquals(missing("Object", "NOSUCH"), outcome("CREATE TABLE x1 LIKE nosuch"));
        assertEquals(missing("Object", "NOSUCH"), outcome("CREATE TABLE x2 CLONE nosuch"));
        assertEquals(missing("Object", "TEST_DB.TEST_SCHEMA.NOSUCH"), outcome("CREATE TABLE x3 LIKE test_schema.nosuch"));
        assertEquals(missing("Object", "TEST_DB.TEST_SCHEMA.NOSUCH"), outcome("CREATE TABLE x4 CLONE test_schema.nosuch"));
        assertEquals(ACCEPTED, outcome("CREATE TABLE x5 LIKE t"));
        assertEquals(ACCEPTED, outcome("CREATE TABLE x6 CLONE t"));
    }

    @Test
    public void copyIntoNamesAMissingTableAsWritten() {
        assertEquals("SQL compilation error:|Table 'NOSUCH' does not exist", outcome("COPY INTO nosuch FROM @~"));
        assertEquals("SQL compilation error:|Table 'TEST_SCHEMA.NOSUCH' does not exist",
            outcome("COPY INTO test_schema.nosuch FROM @~"));
    }

    /** The neighbouring readers already named a miss this way. */
    @Test
    public void theOtherReadersAgree() {
        assertEquals(missing("Object", "NOSUCH2"), outcome("INSERT INTO t SELECT * FROM nosuch2"));
        assertEquals(missing("Table", "NOSUCH"), outcome("INSERT INTO nosuch SELECT 1"));
        assertEquals(missing("Object", "NOSUCH"), outcome("DELETE FROM nosuch"));
        assertEquals(missing("Object", "TEST_DB.TEST_SCHEMA.NOSUCH"), outcome("UPDATE test_schema.nosuch SET a = 1"));
        assertEquals(missing("Table", "TEST_DB.TEST_SCHEMA.NOSUCH"), outcome("DESC TABLE test_schema.nosuch"));
        assertEquals(missing("Table", "TEST_DB.TEST_SCHEMA.NOSUCH"), outcome("ALTER TABLE t SWAP WITH nosuch"));
        assertEquals("SQL compilation error:|Unknown user-defined function TEST_SCHEMA.NOSUCH.",
            outcome("CALL test_schema.nosuch()"));
        assertEquals("SQL compilation error:|Unknown function NOSUCH.", outcome("CALL nosuch()"));
    }
}
