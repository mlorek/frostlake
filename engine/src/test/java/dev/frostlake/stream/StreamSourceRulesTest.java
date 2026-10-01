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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rules a stream's source and its creation clauses keep: a stage whose directory table ALTER STAGE turned
 * on takes a stream, INSERT_ONLY is refused in each source's own sentence, and a tag named twice in one clause
 * must carry one value. Every assertion holds on the embedded engine and on a real account alike.
 */
public class StreamSourceRulesTest extends BaseDatabaseTest {

    private String refusalOf(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return error.getMessage();
    }

    @Test
    public void aDirectoryTurnedOnByAlterStageTakesAStream() {
        engine.execute("CREATE STAGE st2");
        engine.execute("ALTER STAGE st2 SET DIRECTORY = (ENABLE = TRUE)");
        engine.execute("CREATE STREAM ss2 ON STAGE st2");
        final ResultSet streams = engine.executeQuery("SHOW STREAMS LIKE 'SS2'");
        assertEquals("Stage", cell(streams, soleRowWhere(streams, "name", "SS2"), "source_type"));
        engine.execute("ALTER STAGE st2 SET DIRECTORY = (ENABLE = FALSE)");
        assertEquals("DIRECTORY not enabled for the stage ST2", refusalOf("CREATE STREAM ss3 ON STAGE st2"));
    }

    @Test
    public void insertOnlyIsRefusedInEachSourcesSentence() {
        engine.execute("CREATE STAGE st DIRECTORY = (ENABLE = TRUE)");
        assertEquals("Streams on directories cannot have INSERT_ONLY set to true.",
            refusalOf("CREATE STREAM s1 ON STAGE st INSERT_ONLY = TRUE"));
        assertEquals("Streams on directories cannot have INSERT_ONLY set to true.",
            refusalOf("CREATE STREAM s2 ON STAGE st APPEND_ONLY = TRUE INSERT_ONLY = TRUE"));
        engine.execute("CREATE STREAM s3 ON STAGE st INSERT_ONLY = FALSE");
        engine.execute("CREATE TABLE t (id INT)");
        engine.execute("CREATE VIEW vw AS SELECT id FROM t");
        assertEquals("SQL compilation error: line 0 at position -1:\nChange tracking of type INSERT_ONLY is not "
            + "supported on queries with 'TABLE' inside views, saw 'T'. (inside view 'TEST_DB.TEST_SCHEMA.VW').",
            refusalOf("CREATE STREAM s4 ON VIEW vw INSERT_ONLY = TRUE"));
        assertEquals("Streams of type INSERT_ONLY can only be created on external tables or Iceberg tables with an "
            + "external catalog integration.", refusalOf("CREATE STREAM s5 ON TABLE t INSERT_ONLY = TRUE"));
    }

    /** The two-values refusal, whose values the account lists in no fixed order. */
    private static void assertTwoValues(final String refusal) {
        final String prefix = "Same tag TG with multiple values provided, values = ";
        assertTrue(refusal.startsWith(prefix), refusal);
        final String values = refusal.substring(prefix.length());
        assertTrue("a, b".equals(values) || "b, a".equals(values), refusal);
    }

    @Test
    public void aTagNamedTwiceCarriesOneValue() {
        engine.execute("CREATE TAG tg");
        assertTwoValues(refusalOf("CREATE TABLE tt1 (id INT) WITH TAG (tg = 'a', tg = 'b')"));
        engine.execute("CREATE TABLE tt2 (id INT) WITH TAG (tg = 'a', tg = 'a')");
        assertTwoValues(refusalOf("CREATE STREAM s1 WITH TAG (tg = 'a', TG = 'b') ON TABLE tt2"));
        engine.execute("CREATE STREAM s2 WITH TAG (tg = 'a', tg = 'a') ON TABLE tt2");
        assertTwoValues(refusalOf("CREATE VIEW vt WITH TAG (tg = 'a', tg = 'b') AS SELECT 1 x"));
    }
}
