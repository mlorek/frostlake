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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Which properties each user TYPE may carry, measured against a real account for all three types:
 *
 * <pre>
 *                             PERSON   SERVICE   LEGACY_SERVICE
 * FIRST_NAME / MIDDLE_NAME /    ok     refused      refused
 *   LAST_NAME
 * PASSWORD                      ok     refused        ok
 * MUST_CHANGE_PASSWORD          ok     refused        ok
 * EMAIL / DISPLAY_NAME /        ok       ok           ok
 *   DEFAULT_WAREHOUSE / COMMENT
 * </pre>
 *
 * <p>The two service types are NOT one set. A legacy service user keeping its password is the point of
 * the type, so refusing it there would reject a statement Snowflake accepts — while Frostlake's earlier
 * rule went the other way, checking SERVICE only and letting LEGACY_SERVICE carry a first name.
 *
 * <p>The refusal is the same sentence at CREATE and at ALTER, naming the type it applies to.
 */
public class ServiceUserPropertyTest extends BaseDatabaseTest {

    private String refusalOf(final String sql) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return error.getMessage();
    }

    // ── the names: refused by BOTH service types ──────────────────────────────────

    @Test
    public void aServiceUserMayNotCarryTheNames() {
        engine.execute("CREATE USER svc TYPE = SERVICE");
        assertEquals("SQL execution error: Cannot set FIRST_NAME on users with TYPE=SERVICE.",
            refusalOf("ALTER USER svc SET FIRST_NAME = 'F'"));
        assertEquals("SQL execution error: Cannot set MIDDLE_NAME on users with TYPE=SERVICE.",
            refusalOf("ALTER USER svc SET MIDDLE_NAME = 'M'"));
        assertEquals("SQL execution error: Cannot set LAST_NAME on users with TYPE=SERVICE.",
            refusalOf("ALTER USER svc SET LAST_NAME = 'L'"));
    }

    @Test
    public void aLegacyServiceUserMayNotCarryTheNamesEither() {
        engine.execute("CREATE USER legacy TYPE = LEGACY_SERVICE");
        assertEquals("SQL execution error: Cannot set FIRST_NAME on users with TYPE=LEGACY_SERVICE.",
            refusalOf("ALTER USER legacy SET FIRST_NAME = 'F'"));
        assertEquals("SQL execution error: Cannot set MIDDLE_NAME on users with TYPE=LEGACY_SERVICE.",
            refusalOf("ALTER USER legacy SET MIDDLE_NAME = 'M'"));
        assertEquals("SQL execution error: Cannot set LAST_NAME on users with TYPE=LEGACY_SERVICE.",
            refusalOf("ALTER USER legacy SET LAST_NAME = 'L'"));
    }

    // ── the credentials: where the two types DIVERGE ──────────────────────────────

    @Test
    public void aServiceUserMayNotCarryCredentials() {
        engine.execute("CREATE USER svc TYPE = SERVICE");
        assertEquals("SQL execution error: Cannot set PASSWORD on users with TYPE=SERVICE.",
            refusalOf("ALTER USER svc SET PASSWORD = 'Abcd1234!xyz'"));
        assertEquals(
            "SQL execution error: Cannot set MUST_CHANGE_PASSWORD on users with TYPE=SERVICE.",
            refusalOf("ALTER USER svc SET MUST_CHANGE_PASSWORD = TRUE"));
    }

    /** The divergence: a legacy service user KEEPS its password. */
    @Test
    public void aLegacyServiceUserMayCarryCredentials() {
        engine.execute("CREATE USER legacy TYPE = LEGACY_SERVICE");
        engine.execute("ALTER USER legacy SET PASSWORD = 'Abcd1234!xyz'");
        engine.execute("ALTER USER legacy SET MUST_CHANGE_PASSWORD = TRUE");
    }

    // ── what every type carries ───────────────────────────────────────────────────

    @Test
    public void theSharedPropertiesAreCarriedByEveryType() {
        for (final String type : new String[] {"PERSON", "SERVICE", "LEGACY_SERVICE"}) {
            engine.execute("CREATE USER shared_" + type + " TYPE = " + type);
            engine.execute("ALTER USER shared_" + type + " SET EMAIL = 'e@x.com'");
            engine.execute("ALTER USER shared_" + type + " SET DISPLAY_NAME = 'D'");
            engine.execute("ALTER USER shared_" + type + " SET COMMENT = 'c'");
        }
    }

    @Test
    public void aPersonCarriesEverything() {
        engine.execute("CREATE USER person TYPE = PERSON");
        engine.execute("ALTER USER person SET FIRST_NAME = 'F' MIDDLE_NAME = 'M' LAST_NAME = 'L'");
        engine.execute("ALTER USER person SET PASSWORD = 'Abcd1234!xyz'");
        engine.execute("ALTER USER person SET MUST_CHANGE_PASSWORD = TRUE");
    }

    // ── CREATE refuses the same combinations ALTER does ───────────────────────────

    @Test
    public void createRefusesTheSameCombinations() {
        assertEquals("SQL execution error: Cannot set FIRST_NAME on users with TYPE=LEGACY_SERVICE.",
            refusalOf("CREATE USER c1 TYPE = LEGACY_SERVICE FIRST_NAME = 'F'"));
        assertEquals("SQL execution error: Cannot set FIRST_NAME on users with TYPE=SERVICE.",
            refusalOf("CREATE USER c2 TYPE = SERVICE FIRST_NAME = 'F'"));
        assertEquals("SQL execution error: Cannot set PASSWORD on users with TYPE=SERVICE.",
            refusalOf("CREATE USER c3 TYPE = SERVICE PASSWORD = 'Abcd1234!xyz'"));
    }

    /** …and accepts at CREATE what the type may carry. */
    @Test
    public void createAcceptsWhatTheTypeMayCarry() {
        engine.execute("CREATE USER c4 TYPE = LEGACY_SERVICE PASSWORD = 'Abcd1234!xyz'"
            + " MUST_CHANGE_PASSWORD = TRUE EMAIL = 'e@x.com'");
        engine.execute("CREATE USER c5 TYPE = SERVICE EMAIL = 'e@x.com' COMMENT = 'c'");
    }
}
