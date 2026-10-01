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
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * GRANT, REVOKE and SHOW GRANTS ON spell a multi-word kind in words: FILE FORMAT, MASKING POLICY, ROW ACCESS POLICY,
 * RESOURCE MONITOR and SESSION POLICY. Written as one word, the kind is refused in live's sentence, or read by SHOW
 * GRANTS ON as a bare name, which live reads as a table or a view only. An object of any kind that does not exist is
 * refused in its kind's own words, by its name as written or in full (live-verified).
 */
public class GrantObjectKindTest extends BaseDatabaseTest {

    private static final String SYNTAX = "SQL compilation error:\nsyntax error line 1 at position ";

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE TABLE T1 (a INT)");
        engine.execute("CREATE VIEW V1 AS SELECT 1 AS x");
        engine.execute("CREATE FILE FORMAT FF TYPE = CSV");
        engine.execute("CREATE MASKING POLICY MP AS (v VARCHAR) RETURNS VARCHAR -> v");
        engine.execute("CREATE ROW ACCESS POLICY RAP AS (v VARCHAR) RETURNS BOOLEAN -> TRUE");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private String missing(final String kind, final String name) {
        return hinted("SQL compilation error:\n" + kind + " '" + name + "' does not exist or not authorized.");
    }

    private static String notAKind(final String kind) {
        return "SQL compilation error: Object type or Class '" + kind + "' does not exist or not authorized.";
    }

    /** Each grant SHOW GRANTS ON lists, as privilege, kind, name, grantee and grantor. */
    private List<String> grants(final String on) {
        final ResultSet shown = engine.executeQuery("SHOW GRANTS ON " + on);
        final List<String> grants = new ArrayList<>();
        for (final Row row : shown.getRows()) {
            grants.add(row.getValue(shown.getColumnIndex("privilege")) + " "
                + row.getValue(shown.getColumnIndex("granted_on")) + " " + row.getValue(shown.getColumnIndex("name"))
                + " " + row.getValue(shown.getColumnIndex("grantee_name")) + " "
                + row.getValue(shown.getColumnIndex("granted_by")));
        }
        return grants;
    }

    private String owner(final String show) {
        final ResultSet shown = engine.executeQuery(show);
        assertEquals(1, shown.getRowCount(), show);
        return String.valueOf(shown.getRows().get(0).getValue(shown.getColumnIndex("owner")));
    }

    @Test
    public void aMultiWordKindIsSpelledInWords() {
        assertEquals(List.of("OWNERSHIP FILE_FORMAT TEST_DB.TEST_SCHEMA.FF ACCOUNTADMIN ACCOUNTADMIN"),
            grants("FILE FORMAT FF"));
        assertEquals(List.of("OWNERSHIP FILE_FORMAT TEST_DB.TEST_SCHEMA.FF ACCOUNTADMIN ACCOUNTADMIN"),
            grants("FILE  FORMAT test_db.test_schema.FF"));
        assertEquals(List.of("OWNERSHIP MASKING_POLICY TEST_DB.TEST_SCHEMA.MP ACCOUNTADMIN ACCOUNTADMIN"),
            grants("MASKING POLICY MP"));
        assertEquals(List.of("OWNERSHIP ROW_ACCESS_POLICY TEST_DB.TEST_SCHEMA.RAP ACCOUNTADMIN ACCOUNTADMIN"),
            grants("ROW ACCESS POLICY RAP"));
        engine.execute("GRANT USAGE ON FILE FORMAT FF TO ROLE PUBLIC");
        assertEquals(List.of("OWNERSHIP FILE_FORMAT TEST_DB.TEST_SCHEMA.FF ACCOUNTADMIN ACCOUNTADMIN",
            "USAGE FILE_FORMAT TEST_DB.TEST_SCHEMA.FF PUBLIC ACCOUNTADMIN"), grants("FILE FORMAT FF"));
        engine.execute("REVOKE USAGE ON FILE FORMAT FF FROM ROLE PUBLIC");
        assertEquals(1, grants("FILE FORMAT FF").size());
        engine.execute("GRANT ALL ON FILE FORMAT FF TO ROLE PUBLIC");
        engine.execute("GRANT APPLY ON ROW ACCESS POLICY RAP TO ROLE PUBLIC");
        engine.execute("GRANT ALL ON MASKING POLICY MP TO ROLE PUBLIC");
        assertEquals(List.of("OWNERSHIP FILE_FORMAT TEST_DB.TEST_SCHEMA.FF ACCOUNTADMIN ACCOUNTADMIN",
            "USAGE FILE_FORMAT TEST_DB.TEST_SCHEMA.FF PUBLIC ACCOUNTADMIN"), grants("FILE FORMAT FF"));
        assertEquals(List.of("OWNERSHIP MASKING_POLICY TEST_DB.TEST_SCHEMA.MP ACCOUNTADMIN ACCOUNTADMIN",
            "APPLY MASKING_POLICY TEST_DB.TEST_SCHEMA.MP PUBLIC ACCOUNTADMIN"), grants("MASKING POLICY MP"));
        assertEquals(List.of("OWNERSHIP ROW_ACCESS_POLICY TEST_DB.TEST_SCHEMA.RAP ACCOUNTADMIN ACCOUNTADMIN",
            "APPLY ROW_ACCESS_POLICY TEST_DB.TEST_SCHEMA.RAP PUBLIC ACCOUNTADMIN"), grants("ROW ACCESS POLICY RAP"));
        assertEquals("SQL compilation error:\nInvalid object type 'FILE_FORMAT' for privilege 'SELECT'.",
            refusal("GRANT SELECT ON FILE FORMAT FF TO ROLE PUBLIC"));
        assertEquals("SQL compilation error:\nInvalid object type 'POLICY' for privilege 'SELECT'.",
            refusal("GRANT SELECT ON MASKING POLICY MP TO ROLE PUBLIC"));
    }

    @Test
    public void aMultiWordKindWrittenAsOneWordIsNoKind() {
        assertEquals(SYNTAX + "27 unexpected 'FF'.", refusal("SHOW GRANTS ON FILE_FORMAT FF"));
        assertEquals(SYNTAX + "30 unexpected 'MP'.", refusal("SHOW GRANTS ON MASKING_POLICY MP"));
        assertEquals(SYNTAX + "33 unexpected 'RAP'.", refusal("SHOW GRANTS ON ROW_ACCESS_POLICY RAP"));
        assertEquals(SYNTAX + "32 unexpected 'NOSUCH_RM'.", refusal("SHOW GRANTS ON RESOURCE_MONITOR NOSUCH_RM"));
        assertEquals(SYNTAX + "30 unexpected 'NOSUCH_SP'.", refusal("SHOW GRANTS ON SESSION_POLICY NOSUCH_SP"));
        // Ahead of the object and of the grantee.
        assertEquals(notAKind("FILE_FORMAT"), refusal("GRANT USAGE ON FILE_FORMAT FF TO ROLE PUBLIC"));
        assertEquals(notAKind("FILE_FORMAT"), refusal("GRANT USAGE ON FILE_FORMAT NOSUCH_FF TO ROLE PUBLIC"));
        assertEquals(notAKind("FILE_FORMAT"), refusal("GRANT USAGE ON FILE_FORMAT FF TO ROLE nosuchrole"));
        assertEquals(notAKind("FILE_FORMAT"), refusal("REVOKE USAGE ON FILE_FORMAT FF FROM ROLE PUBLIC"));
        assertEquals(notAKind("MASKING_POLICY"), refusal("GRANT APPLY ON MASKING_POLICY MP TO ROLE PUBLIC"));
        assertEquals(notAKind("MASKING_POLICY"), refusal("REVOKE APPLY ON MASKING_POLICY MP FROM ROLE PUBLIC"));
    }

    @Test
    public void anObjectThatDoesNotExistIsRefusedInItsKindsWords() {
        assertEquals(missing("File format", "NOSUCH_FF"), refusal("SHOW GRANTS ON FILE FORMAT NOSUCH_FF"));
        assertEquals(missing("File format", "TEST_DB.TEST_SCHEMA.NOSUCH_FF"),
            refusal("SHOW GRANTS ON FILE FORMAT test_db.test_schema.NOSUCH_FF"));
        assertEquals(missing("File format", "NOSUCH_FF"), refusal("GRANT USAGE ON FILE FORMAT NOSUCH_FF TO ROLE PUBLIC"));
        assertEquals(missing("File format", "NOSUCH_FF"),
            refusal("REVOKE USAGE ON FILE FORMAT NOSUCH_FF FROM ROLE PUBLIC"));
        assertEquals(missing("Integration", "NOSUCH_INT"), refusal("SHOW GRANTS ON INTEGRATION NOSUCH_INT"));
        assertEquals(missing("Integration", "NOSUCH_INT"),
            refusal("GRANT USAGE ON INTEGRATION NOSUCH_INT TO ROLE nosuchrole"));
        assertEquals(missing("Integration", "NOSUCH_INT"),
            refusal("REVOKE USAGE ON INTEGRATION NOSUCH_INT FROM ROLE PUBLIC"));
        assertEquals(missing("Resource monitor", "NOSUCH_RM"), refusal("SHOW GRANTS ON RESOURCE MONITOR NOSUCH_RM"));
        assertEquals(missing("Resource monitor", "\"nosuch_rm\""), refusal("SHOW GRANTS ON RESOURCE MONITOR \"nosuch_rm\""));
        assertEquals(missing("Resource monitor", "NOSUCH_RM"),
            refusal("GRANT MONITOR ON RESOURCE MONITOR NOSUCH_RM TO ROLE PUBLIC"));
        assertEquals(missing("Session policy", "TEST_DB.TEST_SCHEMA.NOSUCH_SP"),
            refusal("SHOW GRANTS ON SESSION POLICY NOSUCH_SP"));
        assertEquals(missing("Session policy", "TEST_DB.TEST_SCHEMA.NOSUCH_SP"),
            refusal("GRANT APPLY ON SESSION POLICY NOSUCH_SP TO ROLE PUBLIC"));
        assertEquals(missing("Masking policy", "TEST_DB.TEST_SCHEMA.NOSUCH_MP"),
            refusal("SHOW GRANTS ON MASKING POLICY NOSUCH_MP"));
        assertEquals(missing("Row access policy", "TEST_DB.TEST_SCHEMA.NOSUCH_RAP"),
            refusal("SHOW GRANTS ON ROW ACCESS POLICY NOSUCH_RAP"));
        assertEquals(missing("Tag", "NOSUCH_TAG"), refusal("SHOW GRANTS ON TAG NOSUCH_TAG"));
        assertEquals(missing("Tag", "TEST_DB.TEST_SCHEMA.NOSUCH_TAG"),
            refusal("SHOW GRANTS ON TAG test_db.test_schema.NOSUCH_TAG"));
        assertEquals(missing("Pipe", "TEST_DB.TEST_SCHEMA.NOSUCH_PIPE"), refusal("SHOW GRANTS ON PIPE nosuch_pipe"));
        assertEquals(missing("Task", "TEST_DB.TEST_SCHEMA.NOSUCH_TASK"), refusal("SHOW GRANTS ON TASK nosuch_task"));
        assertEquals(missing("Warehouse", "NOSUCH_WH"), refusal("SHOW GRANTS ON WAREHOUSE nosuch_wh"));
        assertEquals(missing("Stream", "NOSUCH_STREAM"), refusal("SHOW GRANTS ON STREAM NOSUCH_STREAM"));
        assertEquals(missing("Stream", "TEST_DB.TEST_SCHEMA.NOSUCH_STREAM"),
            refusal("SHOW GRANTS ON STREAM test_db.test_schema.NOSUCH_STREAM"));
    }

    @Test
    public void aBareNameIsATableOrAView() {
        assertEquals(List.of("OWNERSHIP TABLE TEST_DB.TEST_SCHEMA.T1 ACCOUNTADMIN ACCOUNTADMIN"), grants("T1"));
        assertEquals(List.of("OWNERSHIP TABLE TEST_DB.TEST_SCHEMA.T1 ACCOUNTADMIN ACCOUNTADMIN"), grants("\"T1\""));
        assertEquals(List.of("OWNERSHIP TABLE TEST_DB.TEST_SCHEMA.T1 ACCOUNTADMIN ACCOUNTADMIN"),
            grants("test_db.test_schema.T1"));
        assertEquals(List.of("OWNERSHIP VIEW TEST_DB.TEST_SCHEMA.V1 ACCOUNTADMIN ACCOUNTADMIN"), grants("V1"));
        assertEquals(missing("Object", "FF"), refusal("SHOW GRANTS ON FF"));
        assertEquals(missing("Object", "NOSUCH_OBJ"), refusal("SHOW GRANTS ON NOSUCH_OBJ"));
        assertEquals(missing("Object", "TEST_DB.TEST_SCHEMA.NOSUCH_OBJ"),
            refusal("SHOW GRANTS ON test_db.test_schema.NOSUCH_OBJ"));
        assertEquals(missing("Object", "INTEGRATION"), refusal("SHOW GRANTS ON INTEGRATION"));
        assertEquals(missing("Object", "PUBLIC"), refusal("SHOW GRANTS ON PUBLIC"));
        assertEquals(SYNTAX + "23 unexpected '<EOF>'.", refusal("SHOW GRANTS ON DATABASE"));
    }

    @Test
    public void aFileFormatOrAPolicyIsOwnedAndTransferredLikeAnyObject() {
        assertEquals("ACCOUNTADMIN", owner("SHOW FILE FORMATS LIKE 'FF'"));
        engine.execute("GRANT USAGE ON FILE FORMAT FF TO ROLE PUBLIC");
        assertEquals("SQL execution error: Dependent grant of privilege 'USAGE' on securable 'TEST_DB.TEST_SCHEMA.FF' to "
            + "role 'PUBLIC' exists.  It must be revoked first.  More than one dependent grant may exist: use 'SHOW "
            + "GRANTS' command to view them.  To revoke all dependent grants while transferring object ownership, use "
            + "convenience command 'GRANT OWNERSHIP ON <target_objects> TO <target_role> REVOKE CURRENT GRANTS'.",
            refusal("GRANT OWNERSHIP ON FILE FORMAT FF TO ROLE SYSADMIN"));
        engine.execute("GRANT OWNERSHIP ON FILE FORMAT FF TO ROLE SYSADMIN REVOKE CURRENT GRANTS");
        assertEquals("SYSADMIN", owner("SHOW FILE FORMATS LIKE 'FF'"));
        assertEquals(List.of("OWNERSHIP FILE_FORMAT TEST_DB.TEST_SCHEMA.FF SYSADMIN SYSADMIN"), grants("FILE FORMAT FF"));
        engine.execute("GRANT APPLY ON MASKING POLICY MP TO ROLE PUBLIC");
        engine.execute("GRANT OWNERSHIP ON MASKING POLICY MP TO ROLE SYSADMIN COPY CURRENT GRANTS");
        assertEquals(List.of("APPLY MASKING_POLICY TEST_DB.TEST_SCHEMA.MP PUBLIC SYSADMIN",
            "OWNERSHIP MASKING_POLICY TEST_DB.TEST_SCHEMA.MP SYSADMIN SYSADMIN"), grants("MASKING POLICY MP"));
        assertEquals("SYSADMIN", owner("SHOW MASKING POLICIES LIKE 'MP'"));
    }
}
