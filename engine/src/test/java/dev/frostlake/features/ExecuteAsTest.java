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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExecuteAsTest extends BaseDatabaseTest {

    /**
     * The recorded EXECUTE AS of a procedure, read where a real account reports it.
     *
     * <p>SHOW PROCEDURES has no {@code execute_as} column — live-verified on a real account
     *, all six procedure listings return the same 16 columns and none of them is
     * {@code execute_as} or {@code language}. Both surface under DESCRIBE PROCEDURE instead, as the
     * property rows {@code language | SQL} and {@code execute as | OWNER}, and the argument-type list is
     * mandatory there.
     */
    private String executeAsOf(final String signature) {
        final ResultSet described = engine.executeQuery("DESCRIBE PROCEDURE " + signature);
        assertNotNull(described);
        for (int i = 0; i < described.getRowCount(); i++) {
            if ("execute as".equals(described.getRows().get(i).getValue(0))) {
                return String.valueOf(described.getRows().get(i).getValue(1));
            }
        }
        return null;
    }

    /** Whether SHOW PROCEDURES lists a procedure of this name. */
    private boolean listedByShowProcedures(final String name) {
        final ResultSet rs = engine.executeQuery("SHOW PROCEDURES");
        assertNotNull(rs);
        for (int i = 0; i < rs.getRowCount(); i++) {
            if (name.equalsIgnoreCase(rs.getRows().get(i).getValue(1).toString())) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void testExecuteAsOwner() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE my_proc() " +
            "RETURNS VARCHAR " +
            "LANGUAGE SQL " +
            "COMMENT = 'owner proc' " +
            "EXECUTE AS OWNER " +
            "AS $$ BEGIN RETURN 'ok'; END $$"
        );
        assertTrue(listedByShowProcedures("MY_PROC"),
            "Procedure MY_PROC should be visible in SHOW PROCEDURES");
        assertEquals("OWNER", executeAsOf("my_proc()"));
    }

    @Test
    public void testExecuteAsCaller() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE caller_proc(n INTEGER) " +
            "RETURNS INTEGER " +
            "LANGUAGE SQL " +
            "COMMENT = 'caller proc' " +
            "EXECUTE AS CALLER " +
            "AS $$ BEGIN RETURN n; END $$"
        );
        assertTrue(listedByShowProcedures("CALLER_PROC"),
            "Procedure CALLER_PROC should be visible in SHOW PROCEDURES");
        assertEquals("CALLER", executeAsOf("caller_proc(INTEGER)"));
    }

    @Test
    public void testDefaultExecuteAsIsOwner() {
        engine.execute(
            "CREATE OR REPLACE PROCEDURE default_proc() " +
            "RETURNS VARCHAR " +
            "LANGUAGE SQL " +
            "AS $$ BEGIN RETURN 'default'; END $$"
        );
        // Live-verified: DESC PROCEDURE default_proc() on a procedure created without an
        // EXECUTE AS clause answers "execute as | OWNER".
        assertEquals("OWNER", executeAsOf("default_proc()"), "Default execute_as should be OWNER");
    }

    @Test
    public void testCommentBeforeExecuteAs() {
        // COMMENT clause before EXECUTE AS clause
        engine.execute(
            "CREATE OR REPLACE PROCEDURE commented_proc() " +
            "RETURNS VARCHAR " +
            "LANGUAGE SQL " +
            "COMMENT = 'my comment' " +
            "EXECUTE AS OWNER " +
            "AS $$ BEGIN RETURN 'hello'; END $$"
        );
        final ResultSet rs = engine.executeQuery("SHOW PROCEDURES");
        final int commentIdx = rs.getColumnIndex("description");
        boolean found = false;
        for (int i = 0; i < rs.getRowCount(); i++) {
            if ("COMMENTED_PROC".equalsIgnoreCase(rs.getRows().get(i).getValue(1).toString())) {
                found = true;
                // The COMMENT does land in SHOW PROCEDURES' description column (live-verified
                // a procedure created with COMMENT = 'owner proc' lists
                // description = owner proc).
                assertEquals("my comment", rs.getRows().get(i).getValue(commentIdx));
            }
        }
        assertTrue(found, "Procedure COMMENTED_PROC should be visible in SHOW PROCEDURES");
        assertEquals("OWNER", executeAsOf("commented_proc()"));
    }
}
