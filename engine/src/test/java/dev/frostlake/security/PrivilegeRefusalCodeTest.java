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
package dev.frostlake.security;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.jdbc.DirectConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A privilege refusal - an access-control error, {@code Insufficient privileges to operate on …} - carries the code
 * 3001 and the SQLSTATE 42501 (documented): a block's handler reads them as SQLCODE and SQLSTATE, and a client's
 * driver reads them off the failed statement, a block that left the refusal uncaught included. Engine only: the
 * live account allows no CREATE ROLE, CREATE USER or GRANT.
 */
public class PrivilegeRefusalCodeTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE prc");
        engine.execute("USE SCHEMA prc.public");
        engine.execute("CREATE TABLE owned (a INT)");
        engine.execute("CREATE ROLE prc_reader");
        engine.execute("CREATE USER prc_user");
        engine.execute("GRANT ROLE prc_reader TO USER prc_user");
        engine.execute("GRANT USAGE ON DATABASE prc TO ROLE prc_reader");
        engine.execute("GRANT USAGE ON SCHEMA prc.public TO ROLE prc_reader");
        // The role may enter the schema, but neither create in it nor drop what another role owns.
        engine.setCurrentUser("PRC_USER");
        engine.setCurrentRole("PRC_READER");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /** What a block's handler reads for the statement's refusal, as {@code "code state"}. */
    private String handled(final String statement) {
        return String.valueOf(engine.executeQuery("BEGIN " + statement + "; RETURN 'none'; EXCEPTION WHEN "
            + "STATEMENT_ERROR THEN RETURN SQLCODE || ' ' || SQLSTATE; END").getRows().get(0).getValue(0));
    }

    /** What a client's driver reads for the statement's refusal, as {@code "code state"}. */
    private String refusedToTheClient(final String sql) throws SQLException {
        try (final Connection connection = new DirectConnection(engine, false);
             final Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (final SQLException refused) {
            return refused.getErrorCode() + " " + refused.getSQLState();
        }
        return fail("no failure: " + sql);
    }

    @Test
    public void aBlocksHandlerReadsThePrivilegeRefusalsCodeAndState() {
        assertEquals("3001 42501", handled("CREATE TABLE made (a INT)"));
        assertEquals("3001 42501", handled("DROP TABLE owned"));
    }

    @Test
    public void aClientReadsTheSamePair() throws SQLException {
        assertEquals("3001 42501", refusedToTheClient("CREATE TABLE made (a INT)"));
        assertEquals("3001 42501", refusedToTheClient("DROP TABLE owned"));
        assertEquals("3001 42501", refusedToTheClient("BEGIN CREATE TABLE made (a INT); END"));
        assertEquals("3001 42501", refusedToTheClient("DROP VIEW prc.information_schema.tables"));
    }
}
