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
 * Parameter NAMES are validated against the account's own vocabulary (live-verified, all shapes):
 * an unknown session name refuses {@code invalid parameter '<NAME>'} for SET and UNSET alike —
 * unquoted names upper-fold, quoted names keep their quotes — while an unknown key in an object's
 * generic SET refuses {@code invalid property '<NAME>' for '<KIND>'}. Known names stay accepted
 * whether or not the engine acts on them, because the registry is the account's list, not the
 * engine's implemented subset.
 */
public class ParameterNameValidationTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertEquals(message, e.getMessage());
    }

    @Test
    public void unknownSessionParameterRefusesOnSetAndUnset() {
        assertRefused("ALTER SESSION SET NOT_A_REAL_PARAMETER = 1",
            "SQL compilation error:\ninvalid parameter 'NOT_A_REAL_PARAMETER'");
        assertRefused("ALTER SESSION UNSET NOT_A_REAL_PARAMETER",
            "SQL compilation error:\ninvalid parameter 'NOT_A_REAL_PARAMETER'");
    }

    @Test
    public void aQuotedUnknownNameKeepsItsQuotes() {
        assertRefused("ALTER SESSION SET \"not_real\" = 1",
            "SQL compilation error:\ninvalid parameter '\"not_real\"'");
    }

    @Test
    public void knownSessionParametersStayAccepted() {
        engine.execute("ALTER SESSION SET QUERY_TAG = 'validation-test'");
        engine.execute("ALTER SESSION UNSET QUERY_TAG");
        engine.execute("ALTER SESSION SET TIMEZONE = 'UTC'");
        engine.execute("ALTER SESSION UNSET TIMEZONE");
    }

    @Test
    public void unknownTablePropertyRefuses() {
        engine.execute("CREATE TABLE pnv_t (a INTEGER)");
        assertRefused("ALTER TABLE pnv_t SET BOGUS_OPTION = 1",
            "SQL compilation error:\ninvalid property 'BOGUS_OPTION' for 'TABLE'");
        engine.execute("ALTER TABLE pnv_t SET DATA_RETENTION_TIME_IN_DAYS = 1");
    }

    @Test
    public void unknownDatabasePropertyRefuses() {
        assertRefused("ALTER DATABASE test_db SET BOGUS_OPTION = 1",
            "SQL compilation error:\ninvalid property 'BOGUS_OPTION' for 'DATABASE'");
        engine.execute("ALTER DATABASE test_db SET DATA_RETENTION_TIME_IN_DAYS = 1");
    }
}
