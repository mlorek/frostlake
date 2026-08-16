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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Session VARIABLES and session PARAMETERS are two separate stores that merely share a name space of
 * spellings. A SET of a parameter's name makes a variable and leaves the parameter alone; an ALTER
 * SESSION SET of a variable's name makes a parameter that no {@code $name} can read; and SHOW VARIABLES
 * lists only what SET defined. Live-verified.
 */
public class SessionVariableStorageTest extends BaseDatabaseTest {

    /** SET of a parameter's name defines a VARIABLE — the session parameter keeps its own value. */
    @Test
    public void setDoesNotWriteTheSessionParameter() {
        engine.execute("SET timezone = 'abc'");
        try {
            assertEquals("abc", scalar("SELECT $timezone"));
            final ResultSet parameters = engine.executeQuery("SHOW PARAMETERS LIKE 'TIMEZONE' IN SESSION");
            assertEquals(1, parameters.getRowCount());
            final Object value = parameters.getRows().get(0).getValue(parameters.getColumnIndex("value"));
            assertNotEquals("abc", String.valueOf(value), "SET must not write the TIMEZONE parameter");
        } finally {
            engine.execute("UNSET timezone");
        }
        assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT $timezone");
            }
        });
    }

    /** An ALTER SESSION parameter is not a variable: {@code $name} does not see it, SHOW VARIABLES omits it. */
    @Test
    public void alterSessionParameterIsNotAVariable() {
        engine.execute("ALTER SESSION SET QUERY_TAG = 'x'");
        try {
            final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT $QUERY_TAG");
                }
            });
            assertTrue(error.getMessage().contains("Session variable '$QUERY_TAG' does not exist"),
                "unexpected refusal: " + error.getMessage());
            assertNull(variableValue("QUERY_TAG"), "an ALTER SESSION parameter must not be listed as a variable");
        } finally {
            engine.execute("ALTER SESSION UNSET QUERY_TAG");
        }
    }

    /** SHOW VARIABLES lists what SET defined and nothing else — no session parameter appears in it. */
    @Test
    public void showVariablesListsOnlyWhatSetDefined() {
        engine.execute("SET sv_kept = 1");
        try {
            assertEquals("1", variableValue("SV_KEPT"));
            assertNull(variableValue("MULTI_STATEMENT_COUNT"), "a session parameter must not be listed as a variable");
            assertNull(variableValue("TIMEZONE"), "a session parameter must not be listed as a variable");
        } finally {
            engine.execute("UNSET sv_kept");
        }
        assertNull(variableValue("SV_KEPT"), "UNSET must drop the variable from SHOW VARIABLES");
    }

    /** UNSET of a name no SET defined is accepted silently. */
    @Test
    public void unsetOfAnUndefinedNameIsAccepted() {
        engine.execute("UNSET sv_never_defined");
    }

    /** The value SHOW VARIABLES lists for a name, or null when it lists no such variable. */
    private String variableValue(final String name) {
        final ResultSet variables = engine.executeQuery("SHOW VARIABLES");
        for (int i = 0; i < variables.getRowCount(); i++) {
            final Object listed = variables.getRows().get(i).getValue(variables.getColumnIndex("name"));
            if (listed != null && name.equalsIgnoreCase(listed.toString())) {
                final Object value = variables.getRows().get(i).getValue(variables.getColumnIndex("value"));
                return value == null ? null : value.toString();
            }
        }
        return null;
    }

    /** The single cell of a single-row, single-column query, as text. */
    private String scalar(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRowCount());
        final Object value = rs.getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }
}
