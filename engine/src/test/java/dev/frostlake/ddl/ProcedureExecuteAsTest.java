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
package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rights a procedure runs with (live-verified): EXECUTE AS takes OWNER, CALLER and RESTRICTED CALLER, at
 * CREATE and by ALTER PROCEDURE, and the description and the DDL tell the one in force. A function has no such
 * clause.
 */
public class ProcedureExecuteAsTest extends BaseDatabaseTest {

    private static final String BODY = " AS 'BEGIN RETURN ''x''; END'";

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private String executeAs() {
        final ResultSet described = engine.executeQuery("DESCRIBE PROCEDURE ea_p()");
        return cell(described, soleRowWhere(described, "property", "execute as"), "value");
    }

    private void create(final String clause) {
        engine.execute("CREATE OR REPLACE PROCEDURE ea_p() RETURNS VARCHAR LANGUAGE SQL " + clause + BODY);
    }

    @Test
    public void aProcedureRunsAsItsOwnerItsCallerOrARestrictedCaller() {
        create("");
        assertEquals("OWNER", executeAs(), "a procedure that names no rights runs with its owner's");
        create("EXECUTE AS CALLER");
        assertEquals("CALLER", executeAs());
        create("EXECUTE AS RESTRICTED CALLER");
        assertEquals("RESTRICTED CALLER", executeAs());
        assertEquals("x", String.valueOf(engine.executeQuery("CALL ea_p()").getRows().get(0).getValue(0)));
        final String ddl = String.valueOf(engine.executeQuery("SELECT GET_DDL('PROCEDURE', 'ea_p()')").getRows()
            .get(0).getValue(0));
        assertTrue(ddl.contains("LANGUAGE SQL\nEXECUTE AS RESTRICTED CALLER\nAS "), ddl);
    }

    @Test
    public void theRightsAreChangedInPlace() {
        create("");
        engine.execute("ALTER PROCEDURE ea_p() EXECUTE AS RESTRICTED CALLER");
        assertEquals("RESTRICTED CALLER", executeAs());
        engine.execute("ALTER PROCEDURE ea_p() EXECUTE AS CALLER");
        assertEquals("CALLER", executeAs());
        engine.execute("ALTER PROCEDURE ea_p() EXECUTE AS OWNER");
        assertEquals("OWNER", executeAs());
    }

    @Test
    public void onlyACallerIsRestrictedAndAFunctionHasNoRights() {
        final String sql = "CREATE OR REPLACE PROCEDURE ea_q() RETURNS VARCHAR LANGUAGE SQL EXECUTE AS RESTRICTED";
        assertTrue(refusal(sql + " OWNER" + BODY).contains("syntax error line 1 at position 86 unexpected 'OWNER'."));
        assertTrue(refusal(sql + BODY).contains("syntax error line 1 at position 86 unexpected 'AS'."));
        assertEquals("Unsupported invocation type for function.",
            refusal("CREATE OR REPLACE FUNCTION ea_f() RETURNS NUMBER EXECUTE AS CALLER AS '1'"));
    }
}
