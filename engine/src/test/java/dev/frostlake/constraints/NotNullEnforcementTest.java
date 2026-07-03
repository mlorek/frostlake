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

package dev.frostlake.constraints;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * NOT NULL enforcement (Phase 4 — Consistency). Snowflake ALWAYS enforces NOT NULL (unlike PK/UK/FK, which
 * are informational), so a NULL in a NOT NULL column on INSERT/UPDATE/MERGE fails at statement time. Uses
 * the default engine. See docs/acid-snowflake-plan.md.
 */
public class NotNullEnforcementTest extends BaseDatabaseTest {

    private void expectNotNullError(final String sql) {
        try {
            engine.execute(sql);
            fail("expected a NOT NULL violation for: " + sql);
        } catch (final RuntimeException e) {
            final String m = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            assertTrue(m.contains("non-nullable") || m.contains("not null") || m.contains("null"),
                "expected a NOT NULL message, got: " + e.getMessage());
        }
    }

    @Test
    public void insertExplicitNullIntoNotNullColumnFails() {
        engine.execute("CREATE TABLE t (id INTEGER NOT NULL, v VARCHAR)");
        expectNotNullError("INSERT INTO t VALUES (NULL, 'a')");
    }

    @Test
    public void insertOmittingNotNullColumnFails() {
        engine.execute("CREATE TABLE t (id INTEGER NOT NULL, v VARCHAR)");
        expectNotNullError("INSERT INTO t (v) VALUES ('a')");   // id omitted → null → violation
    }

    @Test
    public void nullableColumnAcceptsNull() {
        engine.execute("CREATE TABLE t (id INTEGER NOT NULL, v VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        engine.execute("INSERT INTO t VALUES (2, NULL)");   // v is nullable → ok
        assertEquals(2, engine.executeQuery("SELECT * FROM t").getRowCount());
    }

    @Test
    public void notNullWithDefaultUsesDefault() {
        engine.execute("CREATE TABLE t (id INTEGER NOT NULL DEFAULT 7, v VARCHAR)");
        engine.execute("INSERT INTO t (v) VALUES ('a')");   // id omitted → default 7 → ok
        assertEquals(1, engine.executeQuery("SELECT * FROM t WHERE id = 7").getRowCount());
    }

    @Test
    public void updateSettingNotNullColumnToNullFails() {
        engine.execute("CREATE TABLE t (id INTEGER NOT NULL, v VARCHAR)");
        engine.execute("INSERT INTO t VALUES (1, 'a')");
        expectNotNullError("UPDATE t SET id = NULL WHERE v = 'a'");
    }
}
