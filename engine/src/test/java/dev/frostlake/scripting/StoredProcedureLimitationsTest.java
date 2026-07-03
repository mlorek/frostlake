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

import com.snowflake.snowpark_java.Session;
import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Java/Scala stored-procedure limitations enforced by the Snowpark {@link Session} stub (which
 * both {@code JavaProcedureExecutor} and {@code ScalaProcedureExecutor} inject into handlers): no cross-thread
 * query submission (concurrency), no named temporary objects under owner's rights, and the standing bans on
 * creating a new session / obtaining the JDBC connection. Mirrors
 * <a href="https://docs.snowflake.com/en/developer-guide/stored-procedure/java/procedure-java-limitations">the
 * documented limitations</a>.
 */
public class StoredProcedureLimitationsTest extends BaseDatabaseTest {

    @Test
    public void queryOnTheOwningThreadWorks() {
        final Session session = new Session(engine);
        assertNotNull(session.sql("SELECT 1"));
    }

    @Test
    public void queryFromAnotherThreadIsRejected() throws InterruptedException {
        final Session session = new Session(engine);
        final Throwable[] caught = new Throwable[1];
        final Thread worker = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    session.sql("SELECT 1");
                } catch (final Throwable t) {
                    caught[0] = t;
                }
            }
        });
        worker.start();
        worker.join();
        assertNotNull(caught[0], "a query submitted from another thread must be rejected");
        assertTrue(caught[0] instanceof UnsupportedOperationException, "unexpected: " + caught[0]);
        assertTrue(caught[0].getMessage().contains("Concurrency"), caught[0].getMessage());
    }

    @Test
    public void ownersRightsRejectsNamedTemporaryObject() {
        final Session owner = new Session(engine, true);
        final UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
            new Executable() {
                @Override
                public void execute() {
                    owner.sql("CREATE TEMPORARY TABLE tmp_owner (a INTEGER)");
                }
            });
        assertTrue(ex.getMessage().toLowerCase().contains("temporary"), ex.getMessage());
    }

    @Test
    public void callersRightsAllowsTemporaryObject() {
        // EXECUTE AS CALLER (ownersRights = false) is unrestricted: the temp table is actually created.
        final Session caller = new Session(engine, false);
        assertNotNull(caller.sql("CREATE TEMPORARY TABLE tmp_caller (a INTEGER)"));
    }

    @Test
    public void newSessionAndJdbcConnectionRemainRejected() {
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                Session.builder().create();
            }
        });
        final Session session = new Session(engine);
        assertThrows(UnsupportedOperationException.class, new Executable() {
            @Override
            public void execute() {
                session.jdbcConnection();
            }
        });
    }
}
