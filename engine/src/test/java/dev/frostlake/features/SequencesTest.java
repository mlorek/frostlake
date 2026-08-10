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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for SEQUENCE object support
  *
 * <p>NOTE (live-verified): Snowflake sequences are NOT gap-free — NEXTVAL calls in separate
 * statements allocate per-statement ranges (START 10 INCREMENT 10 returned 10, then 1010), so the
 * exact values asserted here are Frostlake's deterministic gap-free behavior; on a real account
 * only ordering/uniqueness/increment-within-a-statement hold.
 */
public class SequencesTest extends BaseDatabaseTest {

    private static final Logger logger = LoggerFactory.getLogger(SequencesTest.class);

    /**
     * The exact value NEXTVAL handed out. Asserted on the embedded engine, which allocates one value
     * at a time; skipped against live, which allocates per statement out of ranges it chooses, so a
     * sequence read by three statements can answer 1, then 101, then 201. What live does guarantee is
     * measured in {@link #allocationPropertiesHoldOnBothBackends}. Running the rest of this class
     * against live is still worth it — every CREATE/ALTER SEQUENCE form here has to be accepted.
     */
    private void assertAllocated(final long expected, final Number actual) {
        assertNotNull(actual, "NEXTVAL returned no value");
        if (!isLiveSnowflake()) {
            assertEquals(expected, actual.longValue());
        }
    }

    @Test
    public void testCreateSequence() {
        logger.info("Testing CREATE SEQUENCE");

        engine.execute("CREATE SEQUENCE seq1");

        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertNotNull(sequences);
        assertEquals(1, sequences.getRowCount());
        assertEquals("SEQ1", sequences.getRows().get(0).getValue(sequences.getColumnIndex("name")));
        logger.info("Created sequence: seq1");
    }

    @Test
    public void testCreateSequenceWithOptions() {
        logger.info("Testing CREATE SEQUENCE with START and INCREMENT");

        engine.execute("CREATE SEQUENCE seq_custom START WITH 100 INCREMENT BY 5");

        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertNotNull(sequences);
        assertEquals(1, sequences.getRowCount());
        assertEquals("SEQ_CUSTOM", sequences.getRows().get(0).getValue(sequences.getColumnIndex("name")));
        // SHOW SEQUENCES names the two numbers next_value / interval, not start_value / increment —
        // live-verified on a real account; a fresh sequence's next_value is its START.
        assertEquals(100L, sequences.getRows().get(0).getValue(sequences.getColumnIndex("next_value")));
        assertEquals(5L, sequences.getRows().get(0).getValue(sequences.getColumnIndex("interval")));
        assertEquals("TEST_DB",
            sequences.getRows().get(0).getValue(sequences.getColumnIndex("database_name")));
        logger.info("Created sequence with START=100, INCREMENT=5");
    }

    @Test
    public void testCreateSequenceWithComment() {
        logger.info("Testing CREATE SEQUENCE with COMMENT");

        engine.execute("CREATE SEQUENCE seq_comment COMMENT = 'Test sequence'");

        final ResultSet describe = engine.executeQuery("DESCRIBE SEQUENCE seq_comment");
        assertNotNull(describe);
        // Live Snowflake DESC SEQUENCE returns one columnar row; the comment lands in its own column.
        assertEquals(1, describe.getRowCount());
        final Row row = describe.getRows().get(0);
        assertEquals("SEQ_COMMENT", row.getValue(describe.getColumnIndex("name")));
        assertEquals("Test sequence", row.getValue(describe.getColumnIndex("comment")));
        logger.info("Created sequence with comment");
    }

    @Test
    public void testCreateSequenceIfNotExists() {
        logger.info("Testing CREATE SEQUENCE IF NOT EXISTS");

        engine.execute("CREATE SEQUENCE seq_exists");
        engine.execute("CREATE SEQUENCE IF NOT EXISTS seq_exists");

        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertEquals(1, sequences.getRowCount());
        logger.info("IF NOT EXISTS works correctly");
    }

    @Test
    public void testCreateDuplicateSequenceFails() {
        logger.info("Testing duplicate sequence creation fails");

        engine.execute("CREATE SEQUENCE seq_dup");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("CREATE SEQUENCE seq_dup");
                
            }
        });
        logger.info("Duplicate sequence creation correctly fails");
    }

    @Test
    public void testDropSequence() {
        logger.info("Testing DROP SEQUENCE");

        engine.execute("CREATE SEQUENCE seq_drop");
        engine.execute("DROP SEQUENCE seq_drop");

        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertEquals(0, sequences.getRowCount());
        logger.info("Dropped sequence successfully");
    }

    @Test
    public void testDropSequenceIfExists() {
        logger.info("Testing DROP SEQUENCE IF EXISTS");

        engine.execute("CREATE SEQUENCE seq_drop_exists");
        engine.execute("DROP SEQUENCE IF EXISTS seq_drop_exists");
        engine.execute("DROP SEQUENCE IF EXISTS seq_drop_exists");

        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertEquals(0, sequences.getRowCount());
        logger.info("IF EXISTS works correctly for DROP");
    }

    @Test
    public void testDropNonExistentSequenceFails() {
        logger.info("Testing DROP of non-existent sequence fails");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("DROP SEQUENCE seq_nonexistent");
                
            }
        });
        logger.info("Drop non-existent sequence correctly fails");
    }

    @Test
    public void testNextValFunction() {
        logger.info("Testing NEXTVAL function");

        engine.execute("CREATE SEQUENCE seq_nextval START WITH 1 INCREMENT BY 1");

        final ResultSet result1 = engine.executeQuery("SELECT seq_nextval.NEXTVAL as val");
        assertAllocated(1L, (Number) result1.getRows().get(0).getValues().get(0));

        final ResultSet result2 = engine.executeQuery("SELECT seq_nextval.NEXTVAL as val");
        assertAllocated(2L, (Number) result2.getRows().get(0).getValues().get(0));

        final ResultSet result3 = engine.executeQuery("SELECT seq_nextval.NEXTVAL as val");
        assertAllocated(3L, (Number) result3.getRows().get(0).getValues().get(0));

        logger.info("NEXTVAL returns correct sequential values");
    }

    @Test
    public void testNextValWithCustomIncrement() {
        logger.info("Testing NEXTVAL with custom increment");

        engine.execute("CREATE SEQUENCE seq_inc START WITH 10 INCREMENT BY 10");

        final ResultSet result1 = engine.executeQuery("SELECT seq_inc.NEXTVAL as val");
        assertAllocated(10L, (Number) result1.getRows().get(0).getValues().get(0));

        final ResultSet result2 = engine.executeQuery("SELECT seq_inc.NEXTVAL as val");
        assertAllocated(20L, (Number) result2.getRows().get(0).getValues().get(0));

        final ResultSet result3 = engine.executeQuery("SELECT seq_inc.NEXTVAL as val");
        assertAllocated(30L, (Number) result3.getRows().get(0).getValues().get(0));

        logger.info("NEXTVAL respects custom INCREMENT");
    }

    @Test
    public void testNextValWithNegativeIncrement() {
        logger.info("Testing NEXTVAL with negative increment");

        engine.execute("CREATE SEQUENCE seq_neg START WITH 100 INCREMENT BY -5");

        final ResultSet result1 = engine.executeQuery("SELECT seq_neg.NEXTVAL as val");
        assertAllocated(100L, (Number) result1.getRows().get(0).getValues().get(0));

        final ResultSet result2 = engine.executeQuery("SELECT seq_neg.NEXTVAL as val");
        assertAllocated(95L, (Number) result2.getRows().get(0).getValues().get(0));

        final ResultSet result3 = engine.executeQuery("SELECT seq_neg.NEXTVAL as val");
        assertAllocated(90L, (Number) result3.getRows().get(0).getValues().get(0));

        logger.info("NEXTVAL works with negative increment");
    }

    @Test
    public void testCurrValFunctionIsRejected() {
        logger.info("Testing that the CURRVAL function form is rejected");

        engine.execute("CREATE SEQUENCE seq_currval START WITH 1 INCREMENT BY 1");
        engine.executeQuery("SELECT seq_currval.NEXTVAL as val");

        // Live-verified: Snowflake has no CURRVAL — the call form is a syntax error.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT CURRVAL('seq_currval') as val");
            }
        });

        logger.info("CURRVAL function form correctly rejected");
    }

    @Test
    public void testCurrValBeforeNextValFails() {
        logger.info("Testing CURRVAL before NEXTVAL fails");

        engine.execute("CREATE SEQUENCE seq_currval_fail START WITH 1");

        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.executeQuery("SELECT CURRVAL('seq_currval_fail') as val");
                
            }
        });

        logger.info("CURRVAL correctly fails when NEXTVAL not called yet");
    }

    @Test
    public void testSequenceInInsert() {
        logger.info("Testing sequence in INSERT statement");

        engine.execute("CREATE TABLE seq_table (id INTEGER, name VARCHAR)");
        engine.execute("CREATE SEQUENCE seq_insert START WITH 1 INCREMENT BY 1");

        engine.execute("INSERT INTO seq_table VALUES (seq_insert.NEXTVAL, 'Alice')");
        engine.execute("INSERT INTO seq_table VALUES (seq_insert.NEXTVAL, 'Bob')");
        engine.execute("INSERT INTO seq_table VALUES (seq_insert.NEXTVAL, 'Charlie')");

        final ResultSet result = engine.executeQuery("SELECT id, name FROM seq_table ORDER BY id");
        assertEquals(3, result.getRowCount());
        assertAllocated(1L, (Number) result.getRows().get(0).getValues().get(0));
        assertAllocated(2L, (Number) result.getRows().get(1).getValues().get(0));
        assertAllocated(3L, (Number) result.getRows().get(2).getValues().get(0));

        logger.info("Sequence works correctly in INSERT statements");
    }

    @Test
    public void testAlterSequenceRestartIsRejected() {
        logger.info("Testing that ALTER SEQUENCE ... RESTART WITH is rejected");

        engine.execute("CREATE SEQUENCE seq_restart START WITH 1 INCREMENT BY 1");

        final ResultSet val1 = engine.executeQuery("SELECT seq_restart.NEXTVAL as val");
        assertAllocated(1L, (Number) val1.getRows().get(0).getValues().get(0));

        // Live-verified: Snowflake ALTER SEQUENCE has no RESTART form.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER SEQUENCE seq_restart RESTART WITH 10");
            }
        });

        logger.info("ALTER SEQUENCE RESTART WITH correctly rejected");
    }

    @Test
    public void testAlterSequenceRestartWithoutValueIsRejected() {
        logger.info("Testing that ALTER SEQUENCE ... RESTART without a value is rejected");

        engine.execute("CREATE SEQUENCE seq_restart_orig START WITH 100 INCREMENT BY 1");

        // Live-verified: the bare RESTART form is a syntax error too.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER SEQUENCE seq_restart_orig RESTART");
            }
        });

        logger.info("ALTER SEQUENCE RESTART correctly rejected");
    }

    @Test
    public void testShowSequences() {
        logger.info("Testing SHOW SEQUENCES");

        engine.execute("CREATE SEQUENCE seq_show1 START WITH 1");
        engine.execute("CREATE SEQUENCE seq_show2 START WITH 100 INCREMENT BY 10");

        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertEquals(2, sequences.getRowCount());
        logger.info("SHOW SEQUENCES displays all sequences");
    }

    @Test
    public void testDescribeSequence() {
        logger.info("Testing DESCRIBE SEQUENCE");

        engine.execute("CREATE SEQUENCE seq_desc START WITH 50 INCREMENT BY 5");

        final ResultSet describe = engine.executeQuery("DESCRIBE SEQUENCE seq_desc");
        assertNotNull(describe);
        // Live Snowflake DESC SEQUENCE returns ONE row with columns
        // name | database_name | schema_name | next_value | interval | created_on | owner |
        // comment | owner_role_type | ordered — not property/value rows.
        assertEquals(1, describe.getRowCount());
        final Row row = describe.getRows().get(0);
        assertEquals("SEQ_DESC", row.getValue(describe.getColumnIndex("name")));
        assertEquals("TEST_DB", row.getValue(describe.getColumnIndex("database_name")));
        assertEquals("TEST_SCHEMA", row.getValue(describe.getColumnIndex("schema_name")));
        assertAllocated(50L, (Number) row.getValue(describe.getColumnIndex("next_value")));
        assertAllocated(5L, (Number) row.getValue(describe.getColumnIndex("interval")));
        assertEquals("N", row.getValue(describe.getColumnIndex("ordered")));

        logger.info("DESCRIBE SEQUENCE returns the one-row column shape");
    }

    @Test
    public void testMultipleSequencesIndependent() {
        logger.info("Testing multiple sequences are independent");

        engine.execute("CREATE SEQUENCE seq_a START WITH 1 INCREMENT BY 1");
        engine.execute("CREATE SEQUENCE seq_b START WITH 1000 INCREMENT BY 100");

        final ResultSet valA1 = engine.executeQuery("SELECT seq_a.NEXTVAL as val");
        final ResultSet valB1 = engine.executeQuery("SELECT seq_b.NEXTVAL as val");
        final ResultSet valA2 = engine.executeQuery("SELECT seq_a.NEXTVAL as val");
        final ResultSet valB2 = engine.executeQuery("SELECT seq_b.NEXTVAL as val");

        assertAllocated(1L, (Number) valA1.getRows().get(0).getValues().get(0));
        assertAllocated(1000L, (Number) valB1.getRows().get(0).getValues().get(0));
        assertAllocated(2L, (Number) valA2.getRows().get(0).getValues().get(0));
        assertAllocated(1100L, (Number) valB2.getRows().get(0).getValues().get(0));

        logger.info("Multiple sequences maintain independent values");
    }

    @Test
    public void testSequenceCaseInsensitivity() {
        logger.info("Testing sequence name case insensitivity");

        engine.execute("CREATE SEQUENCE SeQ_CaSe START WITH 1");

        final ResultSet val1 = engine.executeQuery("SELECT seq_case.NEXTVAL as val");
        final ResultSet val2 = engine.executeQuery("SELECT SEQ_CASE.NEXTVAL as val");
        final ResultSet val3 = engine.executeQuery("SELECT SeQ_CaSe.NEXTVAL as val");

        assertAllocated(1L, (Number) val1.getRows().get(0).getValues().get(0));
        assertAllocated(2L, (Number) val2.getRows().get(0).getValues().get(0));
        assertAllocated(3L, (Number) val3.getRows().get(0).getValues().get(0));

        logger.info("Sequence names are case insensitive");
    }

    @Test
    public void testSequenceInMultipleTables() {
        logger.info("Testing same sequence used across multiple tables");

        engine.execute("CREATE SEQUENCE shared_seq START WITH 1 INCREMENT BY 1");
        engine.execute("CREATE TABLE table1 (id INTEGER, data VARCHAR)");
        engine.execute("CREATE TABLE table2 (id INTEGER, info VARCHAR)");

        engine.execute("INSERT INTO table1 VALUES (shared_seq.NEXTVAL, 'data1')");
        engine.execute("INSERT INTO table2 VALUES (shared_seq.NEXTVAL, 'info1')");
        engine.execute("INSERT INTO table1 VALUES (shared_seq.NEXTVAL, 'data2')");

        final ResultSet result1 = engine.executeQuery("SELECT id FROM table1 ORDER BY id");
        final ResultSet result2 = engine.executeQuery("SELECT id FROM table2 ORDER BY id");

        assertAllocated(1L, (Number) result1.getRows().get(0).getValues().get(0));
        assertAllocated(3L, (Number) result1.getRows().get(1).getValues().get(0));
        assertAllocated(2L, (Number) result2.getRows().get(0).getValues().get(0));

        logger.info("Shared sequence maintains global counter across tables");
    }

    @Test
    public void testNextValInSelectWithoutFrom() {
        logger.info("Testing NEXTVAL in SELECT without FROM");

        engine.execute("CREATE SEQUENCE seq_select START WITH 42 INCREMENT BY 7");

        final ResultSet result = engine.executeQuery("SELECT seq_select.NEXTVAL as next_value");
        assertEquals(1, result.getRowCount());
        assertAllocated(42L, (Number) result.getRows().get(0).getValues().get(0));

        logger.info("NEXTVAL works in SELECT without FROM clause");
    }

    @Test
    public void testSequenceWithLargeNumbers() {
        logger.info("Testing sequence with large numbers");

        engine.execute("CREATE SEQUENCE seq_large START WITH 1000000 INCREMENT BY 1000000");

        final ResultSet val1 = engine.executeQuery("SELECT seq_large.NEXTVAL as val");
        final ResultSet val2 = engine.executeQuery("SELECT seq_large.NEXTVAL as val");

        assertAllocated(1000000L, (Number) val1.getRows().get(0).getValues().get(0));
        assertAllocated(2000000L, (Number) val2.getRows().get(0).getValues().get(0));

        logger.info("Sequence handles large numbers correctly");
    }

    @Test
    public void testCreateSequenceWithOrder() {
        logger.info("Testing CREATE SEQUENCE with ORDER");

        engine.execute("CREATE SEQUENCE seq_order START WITH 1 INCREMENT BY 1 ORDER");

        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertNotNull(sequences);
        assertTrue(sequences.getRowCount() >= 1);

        // Verify the sequence works
        final ResultSet val1 = engine.executeQuery("SELECT seq_order.NEXTVAL as val");
        assertAllocated(1L, (Number) val1.getRows().get(0).getValues().get(0));

        logger.info("Created sequence with ORDER option");
    }

    @Test
    public void testCreateSequenceWithNoOrder() {
        logger.info("Testing CREATE SEQUENCE with NOORDER");

        engine.execute("CREATE SEQUENCE seq_noorder START WITH 10 INCREMENT BY 2 NOORDER");

        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertNotNull(sequences);
        assertTrue(sequences.getRowCount() >= 1);

        // Verify the sequence works
        final ResultSet val1 = engine.executeQuery("SELECT seq_noorder.NEXTVAL as val");
        assertAllocated(10L, (Number) val1.getRows().get(0).getValues().get(0));

        final ResultSet val2 = engine.executeQuery("SELECT seq_noorder.NEXTVAL as val");
        assertAllocated(12L, (Number) val2.getRows().get(0).getValues().get(0));

        logger.info("Created sequence with NOORDER option");
    }

    @Test
    public void testCreateSequenceDefaultNoOrder() {
        logger.info("Testing CREATE SEQUENCE defaults to NOORDER");

        engine.execute("CREATE SEQUENCE seq_default START WITH 1");

        // Default should be NOORDER (order = false)
        final ResultSet val1 = engine.executeQuery("SELECT seq_default.NEXTVAL as val");
        assertAllocated(1L, (Number) val1.getRows().get(0).getValues().get(0));

        logger.info("Default sequence behavior verified");
    }

    @Test
    public void testCreateSequenceOrderWithAllOptions() {
        logger.info("Testing CREATE SEQUENCE with all options including ORDER");

        engine.execute("CREATE SEQUENCE seq_all_opts START WITH 100 INCREMENT BY 5 ORDER COMMENT = 'Full options'");

        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertNotNull(sequences);
        assertTrue(sequences.getRowCount() >= 1);

        final ResultSet val1 = engine.executeQuery("SELECT seq_all_opts.NEXTVAL as val");
        assertAllocated(100L, (Number) val1.getRows().get(0).getValues().get(0));

        final ResultSet val2 = engine.executeQuery("SELECT seq_all_opts.NEXTVAL as val");
        assertAllocated(105L, (Number) val2.getRows().get(0).getValues().get(0));

        logger.info("Sequence with all options including ORDER works correctly");
    }

    @Test
    public void testCreateSequenceWithoutWith() {
        logger.info("Testing CREATE SEQUENCE START without WITH");

        engine.execute("CREATE SEQUENCE seq_no_with START 200");

        final ResultSet sequences = engine.executeQuery("SHOW SEQUENCES");
        assertNotNull(sequences);
        assertTrue(sequences.getRowCount() >= 1);

        final ResultSet val1 = engine.executeQuery("SELECT seq_no_with.NEXTVAL as val");
        assertAllocated(200L, (Number) val1.getRows().get(0).getValues().get(0));

        logger.info("START without WITH works correctly");
    }

    @Test
    public void testCreateSequenceWithoutBy() {
        logger.info("Testing CREATE SEQUENCE INCREMENT without BY");

        engine.execute("CREATE SEQUENCE seq_no_by START 1 INCREMENT 10");

        final ResultSet val1 = engine.executeQuery("SELECT seq_no_by.NEXTVAL as val");
        assertAllocated(1L, (Number) val1.getRows().get(0).getValues().get(0));

        final ResultSet val2 = engine.executeQuery("SELECT seq_no_by.NEXTVAL as val");
        assertAllocated(11L, (Number) val2.getRows().get(0).getValues().get(0));

        logger.info("INCREMENT without BY works correctly");
    }

    @Test
    public void testCreateSequenceWithoutWithOrBy() {
        logger.info("Testing CREATE SEQUENCE without WITH or BY");

        engine.execute("CREATE SEQUENCE seq_no_keywords START 50 INCREMENT 5");

        final ResultSet val1 = engine.executeQuery("SELECT seq_no_keywords.NEXTVAL as val");
        assertAllocated(50L, (Number) val1.getRows().get(0).getValues().get(0));

        final ResultSet val2 = engine.executeQuery("SELECT seq_no_keywords.NEXTVAL as val");
        assertAllocated(55L, (Number) val2.getRows().get(0).getValues().get(0));

        final ResultSet val3 = engine.executeQuery("SELECT seq_no_keywords.NEXTVAL as val");
        assertAllocated(60L, (Number) val3.getRows().get(0).getValues().get(0));

        logger.info("START and INCREMENT without WITH/BY works correctly");
    }

    @Test
    public void testCreateSequenceWithEquals() {
        logger.info("Testing CREATE SEQUENCE with = syntax");

        engine.execute("CREATE SEQUENCE seq_equals START = 1000 INCREMENT = 100");

        final ResultSet val1 = engine.executeQuery("SELECT seq_equals.NEXTVAL as val");
        assertAllocated(1000L, (Number) val1.getRows().get(0).getValues().get(0));

        final ResultSet val2 = engine.executeQuery("SELECT seq_equals.NEXTVAL as val");
        assertAllocated(1100L, (Number) val2.getRows().get(0).getValues().get(0));

        logger.info("START = and INCREMENT = syntax works correctly");
    }

    @Test
    public void testCreateSequenceMixedSyntax() {
        logger.info("Testing CREATE SEQUENCE with mixed syntax");

        engine.execute("CREATE SEQUENCE seq_mixed START WITH 10 INCREMENT 3");

        final ResultSet val1 = engine.executeQuery("SELECT seq_mixed.NEXTVAL as val");
        assertAllocated(10L, (Number) val1.getRows().get(0).getValues().get(0));

        final ResultSet val2 = engine.executeQuery("SELECT seq_mixed.NEXTVAL as val");
        assertAllocated(13L, (Number) val2.getRows().get(0).getValues().get(0));

        logger.info("Mixed syntax (START WITH, INCREMENT without BY) works correctly");
    }

    @Test
    public void testAlterSequenceSetIncrement() {
        logger.info("Testing ALTER SEQUENCE SET INCREMENT changes the NEXTVAL step");
        engine.execute("CREATE SEQUENCE seq_inc START WITH 1 INCREMENT BY 1");

        final ResultSet v1 = engine.executeQuery("SELECT seq_inc.NEXTVAL as val");
        assertAllocated(1L, (Number) v1.getRows().get(0).getValues().get(0));

        engine.execute("ALTER SEQUENCE seq_inc SET INCREMENT = 10");

        // The next value steps by the new increment: 1 + 10 = 11.
        final ResultSet v2 = engine.executeQuery("SELECT seq_inc.NEXTVAL as val");
        assertAllocated(11L, (Number) v2.getRows().get(0).getValues().get(0));
    }

    @Test
    public void testNextValDotSyntax() {
        logger.info("Testing the seq.NEXTVAL pseudo-column syntax");
        engine.execute("CREATE SEQUENCE dseq START WITH 5 INCREMENT BY 5");
        assertAllocated(5L, (Number) engine.executeQuery("SELECT dseq.NEXTVAL AS v")
            .getRows().get(0).getValue(0));
        assertAllocated(10L, (Number) engine.executeQuery("SELECT dseq.NEXTVAL AS v")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void testCurrValDotSyntaxIsRejected() {
        logger.info("Testing that the seq.CURRVAL pseudo-column syntax is rejected");
        engine.execute("CREATE SEQUENCE cseq START WITH 1 INCREMENT BY 1");
        engine.executeQuery("SELECT cseq.NEXTVAL AS v");
        // Live-verified: Snowflake has no CURRVAL pseudo-column either.
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT cseq.CURRVAL AS v");
            }
        });
    }

    @Test
    public void testSequenceDefaultAppliedOnInsert() {
        logger.info("Testing a column DEFAULT of seq.NEXTVAL on plain INSERT");
        engine.execute("CREATE SEQUENCE iseq START WITH 100 INCREMENT BY 1");
        engine.execute("CREATE TABLE idef (id INTEGER DEFAULT iseq.NEXTVAL, name VARCHAR)");
        engine.execute("INSERT INTO idef (name) VALUES ('a')");
        engine.execute("INSERT INTO idef (name) VALUES ('b')");

        final ResultSet rs = engine.executeQuery("SELECT id FROM idef ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertAllocated(100L, (Number) rs.getRows().get(0).getValue(0));
        assertAllocated(101L, (Number) rs.getRows().get(1).getValue(0));
    }

    @Test
    public void testSequenceDefaultAppliedOnMerge() {
        // The reported scenario: MERGE ... WHEN NOT MATCHED THEN INSERT must apply the seq.NEXTVAL DEFAULT
        // for omitted columns instead of failing with "column not found <seq.nextval>".
        logger.info("Testing a sequence column DEFAULT applied by MERGE INSERT");
        engine.execute("CREATE SEQUENCE mseq START WITH 100 INCREMENT BY 1");
        engine.execute("CREATE TABLE mtgt (id INTEGER DEFAULT mseq.NEXTVAL, name VARCHAR)");
        engine.execute("CREATE TABLE msrc (name VARCHAR)");
        engine.execute("INSERT INTO msrc VALUES ('a'), ('b')");

        engine.execute(
            "MERGE INTO mtgt t USING msrc s ON t.name = s.name"
            + " WHEN NOT MATCHED THEN INSERT (name) VALUES (s.name)");

        final ResultSet rs = engine.executeQuery("SELECT id FROM mtgt ORDER BY id");
        assertEquals(2, rs.getRowCount());
        assertAllocated(100L, (Number) rs.getRows().get(0).getValue(0));
        assertAllocated(101L, (Number) rs.getRows().get(1).getValue(0));
    }

    /**
     * A schema-qualified {@code seq.NEXTVAL} inside a JOINED or ALIASED select must resolve through
     * the sequence machinery, not be mistaken for a FROM-clause qualified column — the alias-shadowing
     * rule ("an alias replaces the table name") must never swallow it. This is the integration-loader
     * shape that broke when that rule first landed unscoped.
     */
    @Test
    public void qualifiedNextvalResolvesInsideJoinedAndAliasedSelects() {
        engine.execute("CREATE SEQUENCE test_schema.seq_join_shape");
        engine.execute("CREATE TABLE sq_a (id INTEGER)");
        engine.execute("CREATE TABLE sq_b (id INTEGER)");
        engine.execute("INSERT INTO sq_a VALUES (1)");
        engine.execute("INSERT INTO sq_b VALUES (1)");

        final ResultSet joined = engine.executeQuery("SELECT test_schema.seq_join_shape.nextval"
            + " FROM sq_a a JOIN sq_b b ON a.id = b.id");
        final long first = ((Number) joined.getRows().get(0).getValue(0)).longValue();
        assertEquals(1L, first);

        // ACROSS statements a sequence guarantees only unique, increasing values — a real account
        // allocates in blocks, so the second statement may continue (2) or jump (e.g. 101). Within
        // one statement values ARE consecutive; that cell is held elsewhere in this class.
        final ResultSet aliased = engine.executeQuery(
            "SELECT test_schema.seq_join_shape.nextval FROM sq_a x");
        final long second = ((Number) aliased.getRows().get(0).getValue(0)).longValue();
        assertTrue(second > first, "sequence values must increase across statements, got "
            + first + " then " + second);
    }

    /**
     * Each SELECT item evaluates ONCE per row: {@code ORDER BY 1} over a NEXTVAL item sorts the
     * OUTPUT values and the rows number consecutively from START. Re-evaluating the item as a sort
     * key drew every value twice (1..n as keys, n+1..2n projected).
     */
    @Test
    public void orderByOverANextvalItemDrawsEachValueOnce() {
        engine.execute("CREATE SEQUENCE test_schema.seq_once");
        engine.execute("CREATE TABLE sq_three (id INTEGER)");
        engine.execute("INSERT INTO sq_three VALUES (1), (2), (3)");
        final ResultSet rs = engine.executeQuery(
            "SELECT test_schema.seq_once.nextval FROM sq_three ORDER BY 1");
        assertEquals(3, rs.getRowCount());
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 1L, ((Number) rs.getRows().get(i).getValue(0)).longValue());
        }
    }

    /** Two NEXTVAL references in ONE row draw DISTINCT values. */
    @Test
    public void twoNextvalReferencesInOneRowDrawDistinctValues() {
        engine.execute("CREATE SEQUENCE test_schema.seq_two_refs");
        engine.execute("CREATE TABLE sq_one (id INTEGER)");
        engine.execute("INSERT INTO sq_one VALUES (1)");
        final ResultSet rs = engine.executeQuery(
            "SELECT test_schema.seq_two_refs.nextval, test_schema.seq_two_refs.nextval FROM sq_one");
        assertAllocated(1L, (Number) rs.getRows().get(0).getValue(0));
        assertAllocated(2L, (Number) rs.getRows().get(0).getValue(1));
    }

    /** START / INCREMENT step consecutively within one statement. */
    @Test
    public void startAndIncrementStepWithinOneStatement() {
        engine.execute("CREATE SEQUENCE test_schema.seq_step START = 5 INCREMENT = 10");
        engine.execute("CREATE TABLE sq_step_rows (id INTEGER)");
        engine.execute("INSERT INTO sq_step_rows VALUES (1), (2), (3)");
        final ResultSet rs = engine.executeQuery(
            "SELECT test_schema.seq_step.nextval FROM sq_step_rows ORDER BY 1");
        assertAllocated(5L, (Number) rs.getRows().get(0).getValue(0));
        assertAllocated(15L, (Number) rs.getRows().get(1).getValue(0));
        assertAllocated(25L, (Number) rs.getRows().get(2).getValue(0));
    }

    /**
     * The allocation guarantees that hold on BOTH backends, each one measured against a live account:
     *
     * <pre>
     *   the first NEXTVAL of a fresh sequence is its START      START=1000 answered 1000
     *   within ONE statement the step is exactly INCREMENT      INCREMENT=5  answered 1, 6, 11, 16
     *   ... including a negative one                            INCREMENT=-5 answered 100, 95, 90
     *   across statements the values strictly increase          answered 1, 101, 201, 202, 203, 301
     * </pre>
     *
     * <p>The jumps in that last row are why the rest of this class asserts exact values only off-live.
     */
    @Test
    public void allocationPropertiesHoldOnBothBackends() {
        engine.execute("CREATE SEQUENCE seq_props START WITH 1000 INCREMENT BY 100");
        final ResultSet first = engine.executeQuery("SELECT seq_props.NEXTVAL AS v");
        assertEquals(1000L, ((Number) first.getRows().get(0).getValue(0)).longValue(),
            "the first value of a fresh sequence is its START on both backends");

        engine.execute("CREATE SEQUENCE seq_step START WITH 1 INCREMENT BY 5");
        assertStepWithinOneStatement("seq_step", 5L);

        engine.execute("CREATE SEQUENCE seq_back START WITH 100 INCREMENT BY -5");
        assertStepWithinOneStatement("seq_back", -5L);

        engine.execute("CREATE SEQUENCE seq_mono START WITH 1 INCREMENT BY 1");
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < 4; i++) {
            final long value = ((Number) engine.executeQuery("SELECT seq_mono.NEXTVAL AS v")
                .getRows().get(0).getValue(0)).longValue();
            assertTrue(value > previous,
                "NEXTVAL must strictly increase across statements, saw " + value + " after " + previous);
            previous = value;
        }
    }

    /** Four values drawn by one statement must sit exactly {@code increment} apart. */
    private void assertStepWithinOneStatement(final String sequence, final long increment) {
        final ResultSet rs = engine.executeQuery(
            "SELECT " + sequence + ".NEXTVAL AS v FROM TABLE(GENERATOR(ROWCOUNT => 4))");
        assertEquals(4, rs.getRowCount());
        final List<Row> rows = rs.getRows();
        for (int i = 1; i < rows.size(); i++) {
            final long before = ((Number) rows.get(i - 1).getValue(0)).longValue();
            final long after = ((Number) rows.get(i).getValue(0)).longValue();
            assertEquals(increment, after - before,
                "consecutive values inside one statement step by INCREMENT (" + before + " -> " + after + ")");
        }
    }
}
