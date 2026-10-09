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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A privilege is granted on one object: the one its name reaches exactly, and for a routine the one overload its
 * argument types name. The engine kept grants under the upper-cased name, so a grant on {@code "t"} showed on
 * {@code T} as well, a REVOKE on either took it from both, and SHOW GRANTS spelled the name without its quotes.
 *
 * <p>Live-verified: SHOW GRANTS spells a name with the quotes it needs, {@code DB.S."t"}, and a routine with its
 * signature, a defaulted parameter marked, {@code DB.S.F(NUMBER, DEFAULT VARCHAR)}; a routine named without its
 * argument types is refused wherever a privilege on it is granted, revoked or listed, a procedure in the words of a
 * function; ALL FUNCTIONS and ALL PROCEDURES reach each overload and count it; a grant follows a rename and is not
 * listed once its object is dropped; an object named without its kind is a table; and a grantee named without ROLE
 * is a role, where GRANT ROLE itself requires the keyword.
 */
public class GrantsByExactNameTest extends BaseDatabaseTest {

    private static final String ROLE = "FL_GRANT_NAMES_R";

    @Override
    protected void setupTest() {
        engine.execute("DROP ROLE IF EXISTS " + ROLE);
        engine.execute("CREATE ROLE " + ROLE);
    }

    @Override
    protected void teardownTest() {
        engine.execute("DROP ROLE IF EXISTS " + ROLE);
    }

    /** A query's rows, each one's cells joined by a bar. */
    private List<String> rowsOf(final String sql) {
        final List<String> rows = new ArrayList<>();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            final List<String> cells = new ArrayList<>();
            for (final Object value : row.getValues()) {
                cells.add(String.valueOf(value));
            }
            rows.add(String.join("|", cells));
        }
        return rows;
    }

    /** What the role holds, as SHOW GRANTS TO lists it: {@code PRIVILEGE|kind|name}, in the listing's own order. */
    private String held() {
        engine.executeQuery("SHOW GRANTS TO ROLE " + ROLE);
        return String.join(" ; ", rowsOf("SELECT \"privilege\", \"granted_on\", \"name\""
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2, 3, 1"));
    }

    /** What SHOW GRANTS ON lists: {@code PRIVILEGE|name|whether the test's role holds it}, by privilege. */
    private String grantsOn(final String target) {
        engine.executeQuery("SHOW GRANTS ON " + target);
        return String.join(" ; ", rowsOf("SELECT \"privilege\", \"name\", \"grantee_name\" = '" + ROLE + "'"
            + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1"));
    }

    private String status(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    /** A refusal's own sentence: the message up to the privilege hint, which names the session's role. */
    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            final String message = String.valueOf(refused.getMessage()).replace('\n', ' ');
            final int hint = message.indexOf(" Your primary role");
            return hint < 0 ? message : message.substring(0, hint);
        }
    }

    @Test
    public void aGrantIsHeldOnTheObjectItsNameReachesExactly() {
        engine.execute("CREATE TABLE \"x775\" (a INT)");
        engine.execute("CREATE TABLE X775 (a INT)");
        engine.execute("CREATE VIEW \"v775\" AS SELECT 1 AS a");
        engine.execute("CREATE VIEW V775 AS SELECT 2 AS a");
        engine.execute("CREATE SEQUENCE \"q775\"");
        engine.execute("CREATE SEQUENCE Q775");
        engine.execute("CREATE STAGE \"g775\"");
        engine.execute("CREATE STAGE G775");
        engine.execute("CREATE FUNCTION \"f775\"() RETURNS INT AS '1'");
        engine.execute("CREATE FUNCTION F775() RETURNS INT AS '2'");
        engine.execute("GRANT SELECT ON TABLE \"x775\" TO ROLE " + ROLE);
        engine.execute("GRANT SELECT ON VIEW \"v775\" TO ROLE " + ROLE);
        engine.execute("GRANT USAGE ON SEQUENCE \"q775\" TO ROLE " + ROLE);
        engine.execute("GRANT READ ON STAGE \"g775\" TO ROLE " + ROLE);
        engine.execute("GRANT USAGE ON FUNCTION \"f775\"() TO ROLE " + ROLE);

        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.X775|false", grantsOn("TABLE X775"));
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.\"x775\"|false ; SELECT|TEST_DB.TEST_SCHEMA.\"x775\"|true",
            grantsOn("TABLE \"x775\""));
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.V775|false", grantsOn("VIEW V775"));
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.\"v775\"|false ; SELECT|TEST_DB.TEST_SCHEMA.\"v775\"|true",
            grantsOn("VIEW \"v775\""));
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.Q775|false", grantsOn("SEQUENCE Q775"));
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.\"q775\"|false ; USAGE|TEST_DB.TEST_SCHEMA.\"q775\"|true",
            grantsOn("SEQUENCE \"q775\""));
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.G775|false", grantsOn("STAGE G775"));
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.\"g775\"|false ; READ|TEST_DB.TEST_SCHEMA.\"g775\"|true",
            grantsOn("STAGE \"g775\""));
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.F775()|false", grantsOn("FUNCTION F775()"));
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.\"f775\"()|false ; USAGE|TEST_DB.TEST_SCHEMA.\"f775\"()|true",
            grantsOn("FUNCTION \"f775\"()"));
        // SHOW GRANTS TO orders by kind, then by the name as it is spelled, then by privilege.
        assertEquals("USAGE|FUNCTION|TEST_DB.TEST_SCHEMA.\"f775\"() ; USAGE|SEQUENCE|TEST_DB.TEST_SCHEMA.\"q775\""
            + " ; READ|STAGE|TEST_DB.TEST_SCHEMA.\"g775\" ; SELECT|TABLE|TEST_DB.TEST_SCHEMA.\"x775\""
            + " ; SELECT|VIEW|TEST_DB.TEST_SCHEMA.\"v775\"", held());

        assertEquals("Statement executed successfully. 0 objects affected.",
            status("REVOKE SELECT ON TABLE X775 FROM ROLE " + ROLE));
        assertEquals("Statement executed successfully. 0 objects affected.",
            status("REVOKE SELECT ON VIEW V775 FROM ROLE " + ROLE));
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE SELECT ON TABLE \"x775\" FROM ROLE " + ROLE));
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE SELECT ON VIEW \"v775\" FROM ROLE " + ROLE));
    }

    @Test
    public void aNameInAnotherCaseReachesNothing() {
        engine.execute("CREATE TABLE ONLYUP (a INT)");
        engine.execute("CREATE SEQUENCE ONLYUPQ");
        assertEquals("SQL compilation error: Table '\"onlyup\"' does not exist or not authorized.",
            refusal("GRANT SELECT ON TABLE \"onlyup\" TO ROLE " + ROLE));
        assertEquals("SQL compilation error: Table '\"onlyup\"' does not exist or not authorized.",
            refusal("SHOW GRANTS ON TABLE \"onlyup\""));
        assertEquals("SQL compilation error: Sequence 'TEST_DB.TEST_SCHEMA.\"onlyupq\"' does not exist or not"
            + " authorized.", refusal("GRANT USAGE ON SEQUENCE \"onlyupq\" TO ROLE " + ROLE));
        assertEquals("", held());
    }

    @Test
    public void aGrantFollowsARenameAndIsNotListedOnceItsObjectIsDropped() {
        engine.execute("CREATE TABLE \"x775\" (a INT)");
        engine.execute("CREATE TABLE X775 (a INT)");
        engine.execute("CREATE TABLE X775B (a INT)");
        engine.execute("GRANT UPDATE ON TABLE X775 TO ROLE " + ROLE);
        engine.execute("GRANT DELETE ON TABLE \"x775\" TO ROLE " + ROLE);
        engine.execute("GRANT SELECT ON TABLE X775B TO ROLE " + ROLE);
        engine.execute("ALTER TABLE \"x775\" RENAME TO \"x775r\"");
        assertEquals("DELETE|TABLE|TEST_DB.TEST_SCHEMA.\"x775r\" ; UPDATE|TABLE|TEST_DB.TEST_SCHEMA.X775"
            + " ; SELECT|TABLE|TEST_DB.TEST_SCHEMA.X775B", held());
        engine.execute("DROP TABLE X775");
        assertEquals("DELETE|TABLE|TEST_DB.TEST_SCHEMA.\"x775r\" ; SELECT|TABLE|TEST_DB.TEST_SCHEMA.X775B", held());
        engine.execute("CREATE TABLE X775 (a INT)");
        assertEquals("DELETE|TABLE|TEST_DB.TEST_SCHEMA.\"x775r\" ; SELECT|TABLE|TEST_DB.TEST_SCHEMA.X775B", held(),
            "a new table under the old name is another object");
    }

    @Test
    public void aRoutineIsNamedWithItsArgumentTypes() {
        engine.execute("CREATE PROCEDURE p775(x INT) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE PROCEDURE p775(x VARCHAR) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 2; END'");
        engine.execute("CREATE PROCEDURE \"p775\"() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 3; END'");
        engine.execute("CREATE FUNCTION g775(x INT) RETURNS INT AS '1'");
        final String needed = "Argument types of function 'P775' must be specified.";
        assertEquals(needed, refusal("GRANT USAGE ON PROCEDURE p775 TO ROLE " + ROLE));
        assertEquals(needed, refusal("REVOKE USAGE ON PROCEDURE p775 FROM ROLE " + ROLE));
        assertEquals(needed, refusal("SHOW GRANTS ON PROCEDURE p775"));
        assertEquals("Argument types of function 'p775' must be specified.",
            refusal("GRANT USAGE ON PROCEDURE \"p775\" TO ROLE " + ROLE));
        assertEquals("Argument types of function 'G775' must be specified.",
            refusal("SHOW GRANTS ON FUNCTION test_db.test_schema.g775"));
        assertEquals("Argument types of function 'NOSUCH775' must be specified.",
            refusal("GRANT USAGE ON FUNCTION nosuch775 TO ROLE " + ROLE));
        assertEquals("SQL compilation error: Procedure 'TEST_DB.TEST_SCHEMA.P775' does not exist or not authorized.",
            refusal("GRANT USAGE ON PROCEDURE p775(DATE) TO ROLE " + ROLE));

        engine.execute("GRANT USAGE ON PROCEDURE \"p775\"() TO ROLE " + ROLE);
        engine.execute("GRANT ALL ON PROCEDURE p775(VARCHAR) TO ROLE " + ROLE);
        assertEquals("USAGE|PROCEDURE|TEST_DB.TEST_SCHEMA.\"p775\"() ; MONITOR|PROCEDURE|TEST_DB.TEST_SCHEMA.P775(VARCHAR)"
            + " ; USAGE|PROCEDURE|TEST_DB.TEST_SCHEMA.P775(VARCHAR)", held());
        assertEquals("Statement executed successfully. 3 objects affected.",
            status("GRANT USAGE ON ALL PROCEDURES IN SCHEMA test_db.test_schema TO ROLE " + ROLE));
        assertEquals("USAGE|PROCEDURE|TEST_DB.TEST_SCHEMA.\"p775\"() ; USAGE|PROCEDURE|TEST_DB.TEST_SCHEMA.P775(NUMBER)"
            + " ; MONITOR|PROCEDURE|TEST_DB.TEST_SCHEMA.P775(VARCHAR) ; USAGE|PROCEDURE|TEST_DB.TEST_SCHEMA.P775(VARCHAR)",
            held());
        assertEquals("Statement executed successfully. 3 objects affected.",
            status("REVOKE USAGE ON ALL PROCEDURES IN SCHEMA test_db.test_schema FROM ROLE " + ROLE));
        assertEquals("MONITOR|PROCEDURE|TEST_DB.TEST_SCHEMA.P775(VARCHAR)", held());
        engine.execute("ALTER PROCEDURE p775(VARCHAR) RENAME TO p775b");
        assertEquals("MONITOR|PROCEDURE|TEST_DB.TEST_SCHEMA.P775B(VARCHAR)", held());
        engine.execute("DROP PROCEDURE p775b(VARCHAR)");
        assertEquals("", held());
    }

    @Test
    public void aReplacedRoutineKeepsItsGrantsOnlyWhenToldTo() {
        engine.execute("CREATE FUNCTION g775(x INT) RETURNS INT AS '1'");
        engine.execute("CREATE FUNCTION g775(x VARCHAR) RETURNS INT AS '2'");
        engine.execute("CREATE FUNCTION d775(x INT, y VARCHAR DEFAULT 'a') RETURNS INT AS '1'");
        engine.execute("GRANT USAGE ON FUNCTION g775(VARCHAR) TO ROLE " + ROLE);
        assertEquals("OWNERSHIP|TEST_DB.TEST_SCHEMA.G775(NUMBER)|false", grantsOn("FUNCTION g775(NUMBER)"));
        engine.execute("CREATE OR REPLACE FUNCTION g775(x VARCHAR) RETURNS INT AS '3'");
        assertEquals("", held());
        engine.execute("GRANT USAGE ON FUNCTION g775(VARCHAR) TO ROLE " + ROLE);
        engine.execute("CREATE OR REPLACE FUNCTION g775(x VARCHAR) COPY GRANTS RETURNS INT AS '4'");
        assertEquals("USAGE|FUNCTION|TEST_DB.TEST_SCHEMA.G775(VARCHAR)", held());
        engine.execute("CREATE PROCEDURE cp775(x VARCHAR) RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("GRANT USAGE ON PROCEDURE cp775(VARCHAR) TO ROLE " + ROLE);
        engine.execute("CREATE OR REPLACE PROCEDURE cp775(x VARCHAR) COPY GRANTS RETURNS INT LANGUAGE SQL AS"
            + " 'BEGIN RETURN 2; END'");
        assertEquals("USAGE|FUNCTION|TEST_DB.TEST_SCHEMA.G775(VARCHAR) ; USAGE|PROCEDURE|TEST_DB.TEST_SCHEMA.CP775(VARCHAR)",
            held());
        assertEquals("SQL compilation error: Invalid operation COPY GRANTS without specifying source object. Please use"
            + " OR REPLACE", refusal("CREATE FUNCTION cgn775(x VARCHAR) COPY GRANTS RETURNS INT AS '1'"));
        assertEquals("SQL compilation error: syntax error line 1 at position 60 unexpected 'GRANTS'.",
            refusal("CREATE OR REPLACE FUNCTION g775(x VARCHAR) RETURNS INT COPY GRANTS AS '3'"));
        engine.execute("DROP PROCEDURE cp775(VARCHAR)");
        engine.execute("DROP FUNCTION g775(VARCHAR)");
        engine.execute("CREATE FUNCTION g775(x VARCHAR) RETURNS INT AS '5'");
        assertEquals("", held());
        engine.execute("GRANT USAGE ON FUNCTION d775(INT, VARCHAR) TO ROLE " + ROLE);
        assertEquals("USAGE|FUNCTION|TEST_DB.TEST_SCHEMA.D775(NUMBER, DEFAULT VARCHAR)", held());
        assertEquals("SQL compilation error: Function 'TEST_DB.TEST_SCHEMA.D775' does not exist or not authorized.",
            refusal("GRANT USAGE ON FUNCTION d775(INT) TO ROLE " + ROLE));
    }

    @Test
    public void aGranteeWithoutAKeywordIsARole() {
        engine.execute("CREATE TABLE t893 (a INT)");
        engine.execute("CREATE TABLE t893b (a INT)");
        engine.execute("GRANT SELECT ON TABLE t893 TO " + ROLE);
        assertEquals("SELECT|TABLE|TEST_DB.TEST_SCHEMA.T893", held());
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE SELECT ON TABLE t893 FROM " + ROLE));
        assertEquals("", held());
        engine.execute("GRANT SELECT ON TABLE t893 TO PUBLIC");
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE SELECT ON TABLE t893 FROM PUBLIC"));
        assertEquals("SQL compilation error: Role 'NOSUCHROLE893' does not exist or not authorized.",
            refusal("GRANT SELECT ON TABLE t893 TO nosuchrole893"));
        assertEquals("SQL compilation error: Role 'NOSUCHROLE893' does not exist or not authorized.",
            refusal("REVOKE SELECT ON TABLE t893 FROM nosuchrole893"));
        engine.execute("GRANT SELECT ON TABLE t893 TO \"" + ROLE + "\"");
        engine.execute("GRANT USAGE ON SCHEMA test_db.test_schema TO " + ROLE);
        engine.execute("GRANT USAGE ON DATABASE test_db TO " + ROLE);
        engine.execute("GRANT INSERT ON TABLE t893 TO " + ROLE + " WITH GRANT OPTION");
        engine.execute("GRANT UPDATE, DELETE ON TABLE t893 TO " + ROLE);
        assertEquals("Statement executed successfully. 2 objects affected.",
            status("GRANT REFERENCES ON ALL TABLES IN SCHEMA test_db.test_schema TO " + ROLE));
        engine.execute("GRANT SELECT ON FUTURE TABLES IN SCHEMA test_db.test_schema TO " + ROLE);
        engine.execute("GRANT CREATE DATABASE ON ACCOUNT TO " + ROLE);
        assertEquals("CREATE DATABASE|ACCOUNT ; USAGE|DATABASE ; USAGE|SCHEMA ; DELETE|TABLE ; INSERT|TABLE"
            + " ; REFERENCES|TABLE ; SELECT|TABLE ; UPDATE|TABLE ; REFERENCES|TABLE", String.join(" ; ", heldKinds()));
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE GRANT OPTION FOR INSERT ON TABLE t893 FROM " + ROLE));
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE UPDATE ON TABLE t893 FROM " + ROLE + " CASCADE"));
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE CREATE DATABASE ON ACCOUNT FROM " + ROLE));
        assertEquals("Statement executed successfully. 2 objects affected.",
            status("REVOKE REFERENCES ON ALL TABLES IN SCHEMA test_db.test_schema FROM " + ROLE));
        engine.execute("REVOKE SELECT ON FUTURE TABLES IN SCHEMA test_db.test_schema FROM " + ROLE);
    }

    /** {@code PRIVILEGE|kind} of what the role holds, in the listing's own order. */
    private List<String> heldKinds() {
        engine.executeQuery("SHOW GRANTS TO ROLE " + ROLE);
        return rowsOf("SELECT \"privilege\", \"granted_on\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2, \"name\", 1");
    }

    @Test
    public void grantRoleItselfNeedsItsKeyword() {
        engine.execute("DROP ROLE IF EXISTS " + ROLE + "_B");
        engine.execute("CREATE ROLE " + ROLE + "_B");
        try {
            assertEquals("SQL compilation error: syntax error line 1 at position 33 unexpected '" + ROLE + "'.",
                refusal("GRANT ROLE " + ROLE + "_B TO " + ROLE));
            assertEquals("SQL compilation error: syntax error line 1 at position 36 unexpected '" + ROLE + "'.",
                refusal("REVOKE ROLE " + ROLE + "_B FROM " + ROLE));
            engine.execute("GRANT ROLE " + ROLE + "_B TO ROLE " + ROLE);
            engine.execute("REVOKE ROLE " + ROLE + "_B FROM ROLE " + ROLE);
        } finally {
            engine.execute("DROP ROLE IF EXISTS " + ROLE + "_B");
        }
    }

    @Test
    public void anObjectWithoutItsKindIsATableAndAnySpellingReachesIt() {
        engine.execute("CREATE TABLE t1052 (a INT)");
        engine.execute("GRANT INSERT ON t1052 TO ROLE " + ROLE);
        assertEquals("INSERT|TABLE|TEST_DB.TEST_SCHEMA.T1052", held());
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE INSERT ON t1052 FROM ROLE " + ROLE));
        assertEquals("SQL compilation error: Table 'NOSUCH1052' does not exist or not authorized.",
            refusal("GRANT SELECT ON nosuch1052 TO ROLE " + ROLE));
        // One object, one set of grants, whichever way a statement spells its name.
        engine.execute("GRANT REFERENCES ON TABLE t1052 TO ROLE " + ROLE);
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE REFERENCES ON TABLE test_db.test_schema.t1052 FROM ROLE " + ROLE));
        engine.execute("GRANT TRUNCATE ON TABLE test_db.test_schema.t1052 TO ROLE " + ROLE);
        assertEquals("TRUNCATE|TABLE|TEST_DB.TEST_SCHEMA.T1052", held());
        assertEquals("Statement executed successfully. 1 objects affected.",
            status("REVOKE TRUNCATE ON TABLE test_schema.t1052 FROM ROLE " + ROLE));
        assertEquals("", held());
    }
}
