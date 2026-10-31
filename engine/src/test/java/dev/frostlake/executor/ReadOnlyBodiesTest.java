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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A request whose own text only reads may still reach a write through what its names stand for: a SQL function
 * whose body moves a sequence or runs TO_QUERY, a view over one, a procedure called in a FROM clause, a policy on a
 * table it reads. Such a request runs under the engine's exclusive lock (see {@link ReadOnlyBodies}).
 */
public class ReadOnlyBodiesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE rbt (a INT)");
        engine.execute("INSERT INTO rbt VALUES (1), (2)");
        engine.execute("CREATE OR REPLACE SEQUENCE rbs");
        engine.execute("CREATE OR REPLACE FUNCTION rb_count() RETURNS NUMBER AS 'SELECT COUNT(*) FROM rbt'");
        engine.execute("CREATE OR REPLACE FUNCTION rb_next() RETURNS NUMBER AS 'SELECT rbs.NEXTVAL'");
        engine.execute("CREATE OR REPLACE FUNCTION rb_outer() RETURNS NUMBER AS 'SELECT rb_next() + 1'");
    }

    private boolean reaches(final String sql) {
        return ReadOnlyBodies.reachWrite(engine.getCatalog(), sql, null);
    }

    @Test
    public void aRequestReachingNoWritingBodyReads() {
        assertFalse(reaches("SELECT a FROM rbt"));
        assertFalse(reaches("SELECT rb_count()"));
        assertFalse(reaches("SELECT 1"));
    }

    @Test
    public void aFunctionBodyIsFollowedThroughEveryNameItHolds() {
        assertTrue(reaches("SELECT rb_next()"));
        assertTrue(reaches("SELECT rb_outer()"));
        assertTrue(reaches("SELECT \"RB_OUTER\"()"));
        assertFalse(reaches("SELECT \"rb_outer\"()"));
    }

    @Test
    public void aViewOverAWritingFunctionIsFollowed() {
        engine.execute("CREATE OR REPLACE VIEW rbv AS SELECT rb_outer() AS x");
        assertTrue(reaches("SELECT x FROM rbv"));
        assertTrue(reaches("SELECT x FROM IDENTIFIER('rbv')"));
    }

    @Test
    public void aSessionVariableNamingAWritingViewIsFollowed() {
        final ConcurrentDatabaseEngine concurrent = new ConcurrentDatabaseEngine();
        try {
            final SessionContext session = concurrent.createSession();
            concurrent.execute("CREATE OR REPLACE DATABASE rb_db", session);
            concurrent.execute("CREATE OR REPLACE SEQUENCE rbs", session);
            concurrent.execute("CREATE OR REPLACE FUNCTION rb_next() RETURNS NUMBER AS 'SELECT rbs.NEXTVAL'", session);
            concurrent.execute("CREATE OR REPLACE VIEW rbv AS SELECT rb_next() AS x", session);
            concurrent.execute("CREATE OR REPLACE TABLE rbt (a INT)", session);
            final String read = "SELECT x FROM IDENTIFIER($v)";
            assertFalse(ReadOnlyBodies.reachWrite(concurrent.getEngine().getCatalog(), read, session.getSettings()));
            concurrent.execute("SET v = 'rbv'", session);
            assertTrue(ReadOnlyBodies.reachWrite(concurrent.getEngine().getCatalog(), read, session.getSettings()));
            assertFalse(ReadOnlyBodies.reachWrite(concurrent.getEngine().getCatalog(), read, null));
            concurrent.execute("SET v = 'rbt'", session);
            assertFalse(ReadOnlyBodies.reachWrite(concurrent.getEngine().getCatalog(), read, session.getSettings()));
        } finally {
            concurrent.shutdown();
        }
    }

    @Test
    public void aPolicyOnATableTheRequestReadsIsFollowed() {
        engine.execute("CREATE OR REPLACE MASKING POLICY rbm AS (v NUMBER) RETURNS NUMBER -> "
            + "CASE WHEN rb_next() > 0 THEN v ELSE 0 END");
        engine.execute("ALTER TABLE rbt MODIFY COLUMN a SET MASKING POLICY rbm");
        assertTrue(reaches("SELECT a FROM rbt"));
        assertFalse(reaches("SELECT 1"));
    }

    @Test
    public void aProcedureCalledInFromIsFollowed() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE rb_proc() RETURNS TABLE (n NUMBER) LANGUAGE SQL AS
            $$
            DECLARE r RESULTSET DEFAULT (SELECT rbs.NEXTVAL AS n);
            BEGIN
              RETURN TABLE(r);
            END
            $$""");
        assertTrue(reaches("SELECT * FROM TABLE(rb_proc())"));
    }

    @Test
    public void theServerRunsSuchARequestAloneAndKeepsServing() {
        final ConcurrentDatabaseEngine concurrent = new ConcurrentDatabaseEngine();
        try {
            final SessionContext session = concurrent.createSession();
            concurrent.execute("CREATE OR REPLACE DATABASE rb_db", session);
            concurrent.execute("CREATE OR REPLACE SEQUENCE rbs", session);
            concurrent.execute("CREATE OR REPLACE FUNCTION rb_next() RETURNS NUMBER AS 'SELECT rbs.NEXTVAL'", session);
            assertTimeoutPreemptively(Duration.ofSeconds(60), new Executable() {
                @Override
                public void execute() {
                    final ResultSet first = concurrent.executeQuery("SELECT rb_next()", session);
                    final ResultSet second = concurrent.executeQuery("SELECT rb_next()", session);
                    assertEquals(1, first.getRowCount());
                    assertTrue(((Number) second.getRows().get(0).getValue(0)).longValue()
                        > ((Number) first.getRows().get(0).getValue(0)).longValue());
                    concurrent.execute("CREATE OR REPLACE TABLE rb_after (a INT)", session);
                }
            });
        } finally {
            concurrent.shutdown();
        }
    }
}
