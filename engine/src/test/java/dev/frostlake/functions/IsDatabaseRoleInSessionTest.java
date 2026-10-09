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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * IS_DATABASE_ROLE_IN_SESSION (live-verified): TRUE for a database role of the CURRENT database the session acts
 * with — granted to its current role, directly or through other database roles. The name is one identifier: a name
 * the statement writes is read as an identifier is, one a row carries is matched as it is spelled, and a name
 * qualified by its database names nothing. It is typed BOOLEAN; the bare word NULL and a number are refused while
 * the statement compiles, and the arity is checked.
 */
public class IsDatabaseRoleInSessionTest extends BaseDatabaseTest {

    /** The one row's cells, as text. */
    private List<String> row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> cells = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            cells.add(String.valueOf(rs.getRows().get(0).getValue(i)));
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

    @Test
    public void aNameNoDatabaseRoleCarriesIsFalse() {
        assertEquals(List.of("false", "false", "false", "false", "BOOLEAN[SB1]"),
            row("""
                SELECT IS_DATABASE_ROLE_IN_SESSION('X'), IS_DATABASE_ROLE_IN_SESSION(''),
                    IS_DATABASE_ROLE_IN_SESSION('NOSUCHDB.R1'), IS_DATABASE_ROLE_IN_SESSION('ACCOUNTADMIN'),
                    SYSTEM$TYPEOF(IS_DATABASE_ROLE_IN_SESSION('X'))"""));
        engine.execute("CREATE TABLE r (a VARCHAR)");
        engine.execute("INSERT INTO r VALUES ('abcdef')");
        assertEquals(List.of("false"), row("SELECT IS_DATABASE_ROLE_IN_SESSION(a) FROM r"));
    }

    private void holdThroughTheCurrentRole() {
        engine.execute("CREATE DATABASE ROLE test_db.held_dr");
        engine.execute("CREATE DATABASE ROLE test_db.nested_dr");
        engine.execute("CREATE DATABASE ROLE test_db.other_dr");
        engine.execute("GRANT DATABASE ROLE test_db.nested_dr TO DATABASE ROLE test_db.held_dr");
        engine.execute("GRANT DATABASE ROLE test_db.held_dr TO ROLE " + row("SELECT CURRENT_ROLE()").get(0));
    }

    @Test
    public void aDatabaseRoleHeldThroughTheCurrentRoleIsTrue() {
        holdThroughTheCurrentRole();
        assertEquals(List.of("true", "true", "true", "true", "true", "false"), row("""
            SELECT IS_DATABASE_ROLE_IN_SESSION('held_dr'), IS_DATABASE_ROLE_IN_SESSION('HELD_DR'),
                IS_DATABASE_ROLE_IN_SESSION('Held_Dr'), IS_DATABASE_ROLE_IN_SESSION('"HELD_DR"'),
                IS_DATABASE_ROLE_IN_SESSION('nested_dr'), IS_DATABASE_ROLE_IN_SESSION('other_dr')"""));
        assertEquals(List.of("true", "true", "true"), row("""
            SELECT IS_DATABASE_ROLE_IN_SESSION(UPPER('held_dr')), IS_DATABASE_ROLE_IN_SESSION('HELD' || '_DR'),
                IS_DATABASE_ROLE_IN_SESSION(LOWER('HELD_DR'))"""), "a constant is a written name");
        engine.execute("REVOKE DATABASE ROLE test_db.held_dr FROM ROLE " + row("SELECT CURRENT_ROLE()").get(0));
        assertEquals(List.of("false", "false"),
            row("SELECT IS_DATABASE_ROLE_IN_SESSION('HELD_DR'), IS_DATABASE_ROLE_IN_SESSION('NESTED_DR')"));
        engine.execute("GRANT DATABASE ROLE test_db.nested_dr TO ROLE " + row("SELECT CURRENT_ROLE()").get(0));
        assertEquals(List.of("false", "true"),
            row("SELECT IS_DATABASE_ROLE_IN_SESSION('HELD_DR'), IS_DATABASE_ROLE_IN_SESSION('NESTED_DR')"),
            "the role that held it is not held with it");
    }

    @Test
    public void theNameIsOneIdentifierOfTheCurrentDatabase() {
        holdThroughTheCurrentRole();
        assertEquals(List.of("false", "false", "false", "false", "false"), row("""
            SELECT IS_DATABASE_ROLE_IN_SESSION('test_db.held_dr'), IS_DATABASE_ROLE_IN_SESSION('TEST_DB.NESTED_DR'),
                IS_DATABASE_ROLE_IN_SESSION(' HELD_DR'), IS_DATABASE_ROLE_IN_SESSION('HELD_DR '),
                IS_DATABASE_ROLE_IN_SESSION('"held_dr"')"""));
        engine.execute("CREATE OR REPLACE DATABASE idris_other");
        try {
            engine.execute("USE DATABASE idris_other");
            assertEquals(List.of("false", "false"), row(
                "SELECT IS_DATABASE_ROLE_IN_SESSION('HELD_DR'), IS_DATABASE_ROLE_IN_SESSION('TEST_DB.HELD_DR')"),
                "a role of another database is not reached");
        } finally {
            engine.execute("USE SCHEMA test_db.test_schema");
            engine.execute("DROP DATABASE IF EXISTS idris_other");
        }
    }

    @Test
    public void aNameARowCarriesIsMatchedAsItIsSpelled() {
        holdThroughTheCurrentRole();
        engine.execute("CREATE TABLE names (a VARCHAR)");
        engine.execute("INSERT INTO names VALUES ('HELD_DR'), ('held_dr'), ('\"HELD_DR\"'), ('NESTED_DR'), ('OTHER_DR')");
        final ResultSet rs = engine.executeQuery("SELECT a, IS_DATABASE_ROLE_IN_SESSION(a) FROM names ORDER BY a");
        final List<String> cells = new ArrayList<>();
        while (rs.next()) {
            cells.add(rs.getValue(0) + "=" + rs.getValue(1));
        }
        assertEquals(List.of("\"HELD_DR\"=false", "HELD_DR=true", "NESTED_DR=true", "OTHER_DR=false", "held_dr=false"),
            cells);
        assertTrue(refusal("SELECT IS_DATABASE_ROLE_IN_SESSION(UPPER(a)) FROM names").contains("invalid identifier 'A'"),
            "a column inside an expression is no name");
    }

    @Test
    public void aNullOrANumberIsNoRoleName() {
        assertEquals("SQL compilation error: error line Invalid database role name at position 0\n"
            + "invalid argument for function [IS_DATABASE_ROLE_IN_SESSION] unexpected argument [NULL] at position 0,",
            refusal("SELECT IS_DATABASE_ROLE_IN_SESSION(NULL)"));
        assertEquals("SQL compilation error: error line Invalid database role name at position 0\n"
            + "invalid argument for function [IS_DATABASE_ROLE_IN_SESSION] unexpected argument [1] at position 0,",
            refusal("SELECT IS_DATABASE_ROLE_IN_SESSION(1)"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "not enough arguments for function [IS_DATABASE_ROLE_IN_SESSION()], expected 1, got 0",
            refusal("SELECT IS_DATABASE_ROLE_IN_SESSION()"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "too many arguments for function [IS_DATABASE_ROLE_IN_SESSION('X', 'Y')] expected 1, got 2",
            refusal("SELECT IS_DATABASE_ROLE_IN_SESSION('X', 'Y')"));
    }
}
