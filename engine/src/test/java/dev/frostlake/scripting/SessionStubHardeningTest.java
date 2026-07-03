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
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the Snowpark {@code Session} stub reproduces Snowflake's stored-procedure restrictions:
 * a Java procedure handler may not create a new session ({@code Session.builder()...create()}) nor
 * obtain the underlying JDBC connection ({@code session.jdbcConnection()}). Both calls compile (the
 * stub mirrors the real Snowpark surface) but are rejected at runtime, and the rejection message
 * propagates out of the CALL rather than being swallowed as a null-message wrapper.
 */
public class SessionStubHardeningTest {

    private static final Logger logger = LoggerFactory.getLogger(SessionStubHardeningTest.class);
    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE test_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE SCHEMA io");
        engine.execute("USE SCHEMA io");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    public void creatingANewSessionIsRejected() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE make_session()
            RETURNS VARCHAR
            LANGUAGE JAVA
            HANDLER='MakeSession.run'
            AS
            $$
            import com.snowflake.snowpark_java.Session;
            import java.util.HashMap;
            import java.util.Map;

            public class MakeSession {
              public String run(Session session) {
                Map<String, String> cfg = new HashMap<>();
                Session other = Session.builder().configs(cfg).create();
                return "should not reach here";
              }
            }
            $$
            """);

        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("CALL make_session()");
            }
        });
        final String chain = messageChain(ex);
        logger.info("session-creation rejection: {}", chain);
        assertTrue(chain.contains("session"),
            "Expected a session-creation rejection mentioning 'session', got: " + chain);
    }

    @Test
    public void jdbcConnectionAccessIsRejected() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE grab_conn()
            RETURNS VARCHAR
            LANGUAGE JAVA
            HANDLER='GrabConn.run'
            AS
            $$
            import com.snowflake.snowpark_java.Session;
            import java.sql.Connection;

            public class GrabConn {
              public String run(Session session) {
                Connection c = session.jdbcConnection();
                return "should not reach here";
              }
            }
            $$
            """);

        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("CALL grab_conn()");
            }
        });
        final String chain = messageChain(ex);
        logger.info("jdbcConnection rejection: {}", chain);
        assertTrue(chain.contains("jdbcConnection"),
            "Expected a jdbcConnection rejection mentioning 'jdbcConnection', got: " + chain);
    }

    @Test
    public void ordinarySessionSqlStillWorks() {
        engine.execute("USE SCHEMA public");
        engine.execute("CREATE TABLE nums (n INTEGER)");
        engine.execute("INSERT INTO nums VALUES (1), (2), (3)");
        engine.execute("USE SCHEMA io");

        engine.execute("""
            CREATE OR REPLACE PROCEDURE count_nums()
            RETURNS VARCHAR
            LANGUAGE JAVA
            HANDLER='CountNums.run'
            AS
            $$
            import com.snowflake.snowpark_java.Session;
            import com.snowflake.snowpark_java.DataFrame;

            public class CountNums {
              public String run(Session session) {
                DataFrame df = session.sql("SELECT COUNT(*) AS CNT FROM public.nums");
                Object[] rows = df.collect();
                return "count=" + ((dev.frostlake.storage.Row) rows[0]).getValue(0);
              }
            }
            $$
            """);

        final ResultSet result = engine.executeQuery("CALL count_nums()");
        final String value = result.getRows().get(0).getValue(0).toString();
        logger.info("Count result: {}", value);
        assertTrue(value.startsWith("count="), "Expected 'count=' prefix, got: " + value);
    }

    private static String messageChain(final Throwable t) {
        final StringBuilder sb = new StringBuilder();
        Throwable cur = t;
        while (cur != null) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(cur.getClass().getSimpleName()).append(": ").append(cur.getMessage());
            cur = cur.getCause();
        }
        return sb.toString();
    }
}
