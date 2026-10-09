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
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * IS_ROLE_IN_SESSION (live-verified): TRUE for the session's current role, for every role granted to that one
 * directly or through another, and for PUBLIC. A name the statement writes is read as an identifier is — bare it
 * folds to upper case, in double quotes it is kept — where one a row carries is matched as it is spelled; a text
 * that is no single identifier names no role. The bare word NULL and a number are refused while the statement
 * compiles.
 */
public class IsRoleInSessionTest extends BaseDatabaseTest {

    @Override
    protected void teardownTest() {
        for (final String role : new String[] {"irs_held", "irs_inner", "irs_free"}) {
            engine.execute("DROP ROLE IF EXISTS " + role);
        }
    }

    /** Every cell of every row, as text, in row order. */
    private List<String> cells(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> cells = new ArrayList<>();
        while (rs.next()) {
            for (int i = 0; i < rs.getColumnCount(); i++) {
                cells.add(String.valueOf(rs.getValue(i)));
            }
        }
        return cells;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private String currentRole() {
        return cells("SELECT CURRENT_ROLE()").get(0);
    }

    @Test
    public void theCurrentRoleItsGrantedRolesAndPublicAreInSession() {
        final String role = currentRole();
        engine.execute("CREATE ROLE irs_held");
        engine.execute("CREATE ROLE irs_inner");
        engine.execute("CREATE ROLE irs_free");
        engine.execute("GRANT ROLE irs_inner TO ROLE irs_held");
        engine.execute("GRANT ROLE irs_held TO ROLE " + role);
        assertEquals(List.of("true", "true", "true", "true", "false", "false"), cells("SELECT IS_ROLE_IN_SESSION('"
            + role + "'), IS_ROLE_IN_SESSION('PUBLIC'), IS_ROLE_IN_SESSION('IRS_HELD'),"
            + " IS_ROLE_IN_SESSION('IRS_INNER'), IS_ROLE_IN_SESSION('IRS_FREE'), IS_ROLE_IN_SESSION('NOSUCH_ROLE_QZX')"));
        assertEquals(List.of("BOOLEAN[SB1]"), cells("SELECT SYSTEM$TYPEOF(IS_ROLE_IN_SESSION('PUBLIC'))"));
    }

    @Test
    public void aWrittenNameIsReadAsAnIdentifier() {
        final String role = currentRole();
        assertEquals(List.of("true", "true", "true", "true"), cells("SELECT IS_ROLE_IN_SESSION('"
            + role.toLowerCase() + "'), IS_ROLE_IN_SESSION('\"" + role + "\"'), IS_ROLE_IN_SESSION('public'),"
            + " IS_ROLE_IN_SESSION('\"PUBLIC\"')"));
        assertEquals(List.of("false", "false", "false"), cells(
            "SELECT IS_ROLE_IN_SESSION(' PUBLIC'), IS_ROLE_IN_SESSION(''), IS_ROLE_IN_SESSION('TEST_DB.PUBLIC')"),
            "a text that is no single identifier names no role");
    }

    @Test
    public void aNameARowCarriesIsMatchedAsItIsSpelled() {
        engine.execute("CREATE TABLE rn (a VARCHAR)");
        engine.execute("INSERT INTO rn VALUES ('PUBLIC'), ('public'), ('x'), (NULL)");
        assertEquals(List.of("PUBLIC", "true", "public", "false", "x", "false", "null", "null"),
            cells("SELECT a, IS_ROLE_IN_SESSION(a) FROM rn ORDER BY a"));
        assertEquals(List.of("true", "true", "false", "null"),
            cells("SELECT IS_ROLE_IN_SESSION(UPPER(a)) FROM rn ORDER BY a"), "an expression over the column too");
    }

    @Test
    public void aNullOrANumberIsNoRoleName() {
        assertEquals("SQL compilation error: error line Check Arg at position 0\n"
            + "invalid argument for function [IS_ROLE_IN_SESSION] unexpected argument [NULL] at position 0,",
            refusal("SELECT IS_ROLE_IN_SESSION(NULL)"));
        assertEquals("SQL compilation error: error line Check Arg at position 0\n"
            + "invalid argument for function [IS_ROLE_IN_SESSION] unexpected argument [TO_CHAR(1)] at position 0,",
            refusal("SELECT IS_ROLE_IN_SESSION(1)"));
    }
}
