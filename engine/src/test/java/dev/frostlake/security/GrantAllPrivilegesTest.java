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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * GRANT ALL grants what the account lists for the object's kind: eleven privileges on a table, those and VIEW EXPANDED
 * QUERY PROFILE on a view, eighty-four on a schema. SHOW GRANTS spells each in words and dates each row from its own
 * grant, where Frostlake granted six on a table, printed CREATE_TABLE and dated every row from its grantee's creation
 * (live-verified).
 */
public class GrantAllPrivilegesTest extends BaseDatabaseTest {

    private static final List<String> TABLE = List.of("APPLYBUDGET", "DELETE", "DELETE ERROR TABLE", "EVOLVE SCHEMA",
        "INSERT", "REBUILD", "REFERENCES", "SELECT", "SELECT ERROR TABLE", "TRUNCATE", "UPDATE");

    private static final List<String> SCHEMA_SAMPLE = List.of("ADD SEARCH OPTIMIZATION", "APPLYBUDGET",
        "CREATE DYNAMIC TABLE", "CREATE FILE FORMAT", "CREATE MATERIALIZED VIEW", "CREATE MULTI PARTY APPROVAL POLICY",
        "CREATE TABLE", "CREATE TEMPORARY TABLE", "CREATE VIEW", "EXECUTE AUTO CLASSIFICATION", "MODIFY", "MONITOR",
        "USAGE");

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE TABLE T (a INT)");
        engine.execute("CREATE VIEW V AS SELECT 1 AS x");
        engine.execute("CREATE STAGE ST");
        engine.execute("CREATE SEQUENCE SQ");
        engine.execute("CREATE FUNCTION F(x NUMBER) RETURNS NUMBER AS 'x'");
        engine.execute("CREATE MASKING POLICY MP AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE ROW ACCESS POLICY RAP AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE TAG TG");
    }

    /** The privileges SHOW GRANTS ON lists for PUBLIC, in its order. */
    private List<String> publicPrivileges(final String on) {
        final ResultSet shown = engine.executeQuery("SHOW GRANTS ON " + on);
        final List<String> privileges = new ArrayList<>();
        for (final Row row : shown.getRows()) {
            if ("PUBLIC".equals(String.valueOf(row.getValue(shown.getColumnIndex("grantee_name"))))) {
                privileges.add(String.valueOf(row.getValue(shown.getColumnIndex("privilege"))));
            }
        }
        return privileges;
    }

    @Test
    public void grantAllGrantsWhatTheAccountListsForTheKind() {
        engine.execute("GRANT ALL PRIVILEGES ON TABLE T TO ROLE PUBLIC");
        assertEquals(TABLE, publicPrivileges("TABLE T"));
        engine.execute("GRANT ALL ON VIEW V TO ROLE PUBLIC");
        final List<String> view = new ArrayList<>(TABLE);
        view.add("VIEW EXPANDED QUERY PROFILE");
        assertEquals(view, publicPrivileges("VIEW V"));
        engine.execute("GRANT ALL ON DATABASE test_db TO ROLE PUBLIC");
        assertEquals(List.of("APPLYBUDGET", "CREATE DATABASE ROLE", "CREATE SCHEMA", "EXECUTE AUTO CLASSIFICATION",
            "MODIFY", "MONITOR", "USAGE"), publicPrivileges("DATABASE test_db"));
        engine.execute("GRANT ALL ON SCHEMA test_db.test_schema TO ROLE PUBLIC");
        final List<String> schema = publicPrivileges("SCHEMA test_db.test_schema");
        assertTrue(schema.size() >= 84, String.valueOf(schema.size()));
        assertTrue(schema.containsAll(SCHEMA_SAMPLE), schema.toString());
        engine.execute("GRANT ALL ON STAGE ST TO ROLE PUBLIC");
        assertEquals(List.of("READ", "WRITE"), publicPrivileges("STAGE ST"));
        engine.execute("GRANT ALL ON SEQUENCE SQ TO ROLE PUBLIC");
        assertEquals(List.of("USAGE"), publicPrivileges("SEQUENCE SQ"));
        engine.execute("GRANT ALL ON FUNCTION F(NUMBER) TO ROLE PUBLIC");
        assertEquals(List.of("MONITOR", "USAGE"), publicPrivileges("FUNCTION F(NUMBER)"));
        engine.execute("GRANT ALL ON MASKING POLICY MP TO ROLE PUBLIC");
        assertEquals(List.of("APPLY"), publicPrivileges("MASKING POLICY MP"));
        engine.execute("GRANT ALL ON ROW ACCESS POLICY RAP TO ROLE PUBLIC");
        assertEquals(List.of("APPLY"), publicPrivileges("ROW ACCESS POLICY RAP"));
        engine.execute("GRANT ALL ON TAG TG TO ROLE PUBLIC");
        assertEquals(List.of("APPLY", "APPLYBUDGET", "MODIFY", "MONITOR", "READ"), publicPrivileges("TAG TG"));
        engine.execute("REVOKE ALL ON TABLE T FROM ROLE PUBLIC");
        assertEquals(List.of(), publicPrivileges("TABLE T"));
    }

    @Test
    public void eachPrivilegeIsGrantedAndSpelledInWords() {
        engine.execute("GRANT APPLYBUDGET ON TABLE T TO ROLE PUBLIC");
        engine.execute("GRANT EVOLVE SCHEMA ON TABLE T TO ROLE PUBLIC");
        engine.execute("GRANT REBUILD ON TABLE T TO ROLE PUBLIC");
        engine.execute("GRANT SELECT ERROR TABLE ON TABLE T TO ROLE PUBLIC");
        engine.execute("GRANT DELETE ERROR TABLE ON TABLE T TO ROLE PUBLIC");
        assertEquals(List.of("APPLYBUDGET", "DELETE ERROR TABLE", "EVOLVE SCHEMA", "REBUILD", "SELECT ERROR TABLE"),
            publicPrivileges("TABLE T"));
        engine.execute("REVOKE EVOLVE SCHEMA ON TABLE T FROM ROLE PUBLIC");
        assertEquals(List.of("APPLYBUDGET", "DELETE ERROR TABLE", "REBUILD", "SELECT ERROR TABLE"),
            publicPrivileges("TABLE T"));
        engine.execute("GRANT CREATE TABLE ON SCHEMA test_db.test_schema TO ROLE PUBLIC");
        assertEquals(List.of("CREATE TABLE"), publicPrivileges("SCHEMA test_db.test_schema"));
    }

    @Test
    public void aGrantRowDatesFromItsGrant() {
        engine.execute("GRANT SELECT ON TABLE T TO ROLE PUBLIC");
        assertEquals(1L, ((Number) engine.executeQuery("SHOW GRANTS ON TABLE T ->> SELECT COUNT(*) FROM $1 WHERE "
            + "\"privilege\" = 'SELECT' AND \"created_on\" >= (SELECT MAX(\"created_on\") FROM $1 WHERE \"privilege\" = "
            + "'OWNERSHIP')").getRows().get(0).getValue(0)).longValue());
    }
}
