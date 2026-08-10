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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Session-context functions that only shape-stable assertions can pin across backends:
 * CURRENT_SESSION, CURRENT_STATEMENT, CURRENT_SECONDARY_ROLES, LAST_TRANSACTION.
 */
public class SessionContextGapsTest extends BaseDatabaseTest {

    private String text(final String sql) {
        return String.valueOf(engine.executeQuery("SELECT " + sql).getRows().get(0).getValue(0));
    }

    @Test
    public void currentSessionIdentifiesTheSession() {
        final String session = text("CURRENT_SESSION()");
        assertNotNull(session);
        assertFalse(session.isEmpty());
        assertFalse("null".equals(session));
    }

    @Test
    public void currentStatementEchoesTheRunningSql() {
        final String statement = text("CURRENT_STATEMENT()");
        assertTrue(statement.toUpperCase().contains("CURRENT_STATEMENT"),
            "statement text was: " + statement);
    }

    @Test
    public void currentSecondaryRolesReportsTheJsonShape() {
        final String roles = text("CURRENT_SECONDARY_ROLES()");
        assertTrue(roles.contains("roles"), "was: " + roles);
    }

    @Test
    public void lastTransactionAppearsAfterAnExplicitCommit() {
        engine.execute("CREATE TABLE tx (v INTEGER)");
        engine.execute("BEGIN");
        engine.execute("INSERT INTO tx VALUES (1)");
        engine.execute("COMMIT");
        final String txId = text("LAST_TRANSACTION()");
        assertNotNull(txId);
        assertFalse("null".equals(txId), "no transaction id after an explicit commit");
    }
}
