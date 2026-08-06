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

import com.snowflake.snowpark_java.DataFrame;
import com.snowflake.snowpark_java.Session;
import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Snowpark DataFrame is a PLAN. Measured against a live account, with an identical Java handler:
 *
 * <pre>
 *   session.sql("INSERT …")                   nothing runs; the handler returns normally
 *   session.sql("INSERT …").collect()         runs once
 *   df.collect(); df.collect()                runs TWICE — a plan is not memoised
 *   df.count(); df.collect()                  also twice; count() is an action too
 *   session.table("missing")                  no error without an action
 *   OWNER + CREATE TEMPORARY TABLE, no action returns normally
 *   ... with .collect()                       "Unsupported statement type 'temporary TABLE'"
 * </pre>
 *
 * <p>Executing inside {@code sql()} — which this stub used to do — is the one divergence shape that
 * HIDES: a handler that builds a statement and never collects does real work here and nothing on
 * Snowflake, so the test passes locally and the procedure is a no-op in production.
 */
public class SnowparkLazyPlanTest extends BaseDatabaseTest {

    private long rowsIn(final String table) {
        return ((Number) engine.executeQuery("SELECT COUNT(*) FROM " + table)
            .getRows().get(0).getValue(0)).longValue();
    }

    /** Building a plan submits nothing. */
    @Test
    public void aPlanWithoutAnActionRunsNothing() {
        engine.execute("CREATE TABLE lazy_sink (n INTEGER)");
        final Session session = new Session(engine);
        session.sql("INSERT INTO lazy_sink VALUES (1)");
        assertEquals(0, rowsIn("lazy_sink"), "session.sql alone must submit nothing");
    }

    /** An action runs it — and a second action runs it again, as Snowpark does. */
    @Test
    public void everyActionRunsThePlanAgain() {
        engine.execute("CREATE TABLE lazy_twice (n INTEGER)");
        final Session session = new Session(engine);
        final DataFrame df = session.sql("INSERT INTO lazy_twice VALUES (1)");
        df.collect();
        assertEquals(1, rowsIn("lazy_twice"));
        df.collect();
        assertEquals(2, rowsIn("lazy_twice"), "a DataFrame is a plan, not a cached result");
    }

    /** count() is an action alongside collect(). */
    @Test
    public void countIsAnActionToo() {
        engine.execute("CREATE TABLE lazy_count (n INTEGER)");
        final Session session = new Session(engine);
        final DataFrame df = session.sql("INSERT INTO lazy_count VALUES (9)");
        df.count();
        df.collect();
        assertEquals(2, rowsIn("lazy_count"));
    }

    /** session.table() is lazy as well: naming a table that does not exist is not itself an error. */
    @Test
    public void tableIsLazyToo() {
        final Session session = new Session(engine);
        assertNotNull(session.table("no_such_table_at_all"),
            "table() hands back a plan; the name is not resolved until an action");
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                session.table("no_such_table_at_all").collect();
            }
        }, "and the action is where the missing table is reported");
    }

    /**
     * The owner's-rights refusal fires at the ACTION, because that is where live raises it — the same
     * handler returns normally when it never collects.
     */
    @Test
    public void theOwnersRightsRefusalFiresAtTheAction() {
        final Session owner = new Session(engine, true);
        owner.sql("CREATE TEMPORARY TABLE lazy_tmp (a INTEGER)");

        final UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
            new Executable() {
                @Override
                public void execute() {
                    owner.sql("CREATE TEMPORARY TABLE lazy_tmp (a INTEGER)").collect();
                }
            });
        assertTrue(ex.getMessage().contains("Unsupported statement type 'temporary TABLE'"), ex.getMessage());
    }

    /** A frame built over rows that already exist keeps answering from them. */
    @Test
    public void aMaterialisedFrameNeedsNoSession() {
        engine.execute("CREATE TABLE lazy_rows (n INTEGER)");
        engine.execute("INSERT INTO lazy_rows VALUES (1), (2)");
        final DataFrame df = new DataFrame(engine.executeQuery("SELECT * FROM lazy_rows"));
        assertEquals(2, df.count());
        assertEquals(2, df.collect().length);
        assertEquals(2, df.toMapList().size());
    }
}
