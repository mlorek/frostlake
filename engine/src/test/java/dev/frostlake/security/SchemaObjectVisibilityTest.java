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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stages, file formats, functions, procedures, tags and policies are listed by SHOW only to a role that owns them or
 * holds a privilege on them, or holds MANAGE GRANTS (documented on every SHOW page), and DESCRIBE and GET_DDL answer
 * one the role does not see as one that does not exist. A function or a procedure is seen through its own signature's
 * privileges. The live account allows no CREATE ROLE, CREATE USER or GRANT, so the rule is held by the embedded engine.
 */
public class SchemaObjectVisibilityTest {

    private DatabaseEngine engine;
    private String adminUser;
    private String adminRole;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        adminUser = engine.getSessionContext().getCurrentUser();
        adminRole = engine.getSessionContext().getCurrentRole();
        engine.execute("CREATE DATABASE p");
        engine.execute("USE SCHEMA p.PUBLIC");
        engine.execute("CREATE ROLE reader_role");
        engine.execute("CREATE USER reader");
        engine.execute("GRANT ROLE reader_role TO USER reader");
        engine.execute("GRANT USAGE ON DATABASE p TO ROLE reader_role");
        engine.execute("GRANT USAGE ON SCHEMA p.PUBLIC TO ROLE reader_role");
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE FILE FORMAT ff TYPE = CSV");
        engine.execute("CREATE FUNCTION f(x NUMBER) RETURNS NUMBER AS 'x + 1'");
        engine.execute("CREATE FUNCTION f(x VARCHAR) RETURNS VARCHAR AS 'x'");
        engine.execute("CREATE PROCEDURE pr() RETURNS INT LANGUAGE SQL AS 'BEGIN RETURN 1; END'");
        engine.execute("CREATE TAG tg");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> v");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (v STRING) RETURNS BOOLEAN -> TRUE");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** The reader's answer - rows as "a, b | c, d", or the refusal - then back to the administrator. */
    private String asReader(final String role, final String sql) {
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("reader");
        engine.setCurrentRole(role);
        try {
            final StringBuilder rows = new StringBuilder();
            for (final Row row : engine.executeQuery(sql).getRows()) {
                final StringBuilder cells = new StringBuilder();
                for (final Object value : row.getValues()) {
                    cells.append(cells.length() > 0 ? ", " : "").append(value);
                }
                rows.append(rows.length() > 0 ? " | " : "").append(cells);
            }
            return rows.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        } finally {
            engine.getSessionContext().clearActiveRoles();
            engine.setCurrentUser(adminUser);
            engine.setCurrentRole(adminRole);
        }
    }

    /** The names a SHOW lists, in its order. */
    private String names(final String role, final String show) {
        return asReader(role, show + " ->> SELECT \"name\" FROM $1");
    }

    /** The signatures SHOW USER FUNCTIONS or PROCEDURES lists, in its order. */
    private String signatures(final String show) {
        return asReader("reader_role", show + " ->> SELECT \"arguments\" FROM $1");
    }

    @Test
    public void showListsOnlyWhatTheRoleHoldsAPrivilegeOn() {
        assertEquals("", names("reader_role", "SHOW STAGES"));
        assertEquals("", names("reader_role", "SHOW FILE FORMATS"));
        assertEquals("", names("reader_role", "SHOW USER FUNCTIONS"));
        assertEquals("", names("reader_role", "SHOW USER PROCEDURES"));
        assertEquals("", names("reader_role", "SHOW TAGS"));
        assertEquals("", names("reader_role", "SHOW MASKING POLICIES"));
        assertEquals("", names("reader_role", "SHOW ROW ACCESS POLICIES"));
        engine.execute("GRANT READ ON STAGE st TO ROLE reader_role");
        engine.execute("GRANT USAGE ON FILE FORMAT ff TO ROLE reader_role");
        engine.execute("GRANT USAGE ON FUNCTION f(NUMBER) TO ROLE reader_role");
        engine.execute("GRANT USAGE ON PROCEDURE pr() TO ROLE reader_role");
        engine.execute("GRANT APPLY ON TAG tg TO ROLE reader_role");
        engine.execute("GRANT APPLY ON MASKING POLICY mp TO ROLE reader_role");
        engine.execute("GRANT APPLY ON ROW ACCESS POLICY rap TO ROLE reader_role");
        assertEquals("ST", names("reader_role", "SHOW STAGES"));
        assertEquals("FF", names("reader_role", "SHOW FILE FORMATS"));
        assertEquals("F(NUMBER) RETURN NUMBER", signatures("SHOW USER FUNCTIONS"));
        assertEquals("PR", names("reader_role", "SHOW USER PROCEDURES"));
        assertEquals("TG", names("reader_role", "SHOW TAGS"));
        assertEquals("MP", names("reader_role", "SHOW MASKING POLICIES"));
        assertEquals("RAP", names("reader_role", "SHOW ROW ACCESS POLICIES"));
        assertEquals("F | F", names(adminRole, "SHOW USER FUNCTIONS"));
    }

    @Test
    public void describeAndGetDdlAnswerAnUnseenObjectAsMissing() {
        assertTrue(asReader("reader_role", "DESCRIBE STAGE st").contains("Stage 'P.PUBLIC.ST' does not exist"),
            asReader("reader_role", "DESCRIBE STAGE st"));
        assertTrue(asReader("reader_role", "DESCRIBE FILE FORMAT ff").contains("does not exist or not authorized"));
        assertTrue(asReader("reader_role", "DESCRIBE FUNCTION f(NUMBER)").contains("does not exist or not authorized"));
        assertTrue(asReader("reader_role", "DESCRIBE PROCEDURE pr()").contains("does not exist or not authorized"));
        assertTrue(asReader("reader_role", "DESCRIBE MASKING POLICY mp").contains("does not exist or not authorized"));
        assertTrue(asReader("reader_role", "DESCRIBE ROW ACCESS POLICY rap")
            .contains("does not exist or not authorized"));
        for (final String ddl : new String[] {"'FILE_FORMAT', 'ff'", "'FUNCTION', 'f(NUMBER)'", "'PROCEDURE', 'pr()'",
            "'TAG', 'tg'", "'POLICY', 'mp'", "'POLICY', 'rap'"}) {
            assertTrue(asReader("reader_role", "SELECT GET_DDL(" + ddl + ")").contains("does not exist"), ddl);
        }
        engine.execute("GRANT USAGE ON FUNCTION f(NUMBER) TO ROLE reader_role");
        engine.execute("GRANT USAGE ON FILE FORMAT ff TO ROLE reader_role");
        assertTrue(asReader("reader_role", "DESCRIBE FUNCTION f(NUMBER)").startsWith("signature, (X NUMBER)"),
            asReader("reader_role", "DESCRIBE FUNCTION f(NUMBER)"));
        assertTrue(asReader("reader_role", "DESCRIBE FUNCTION f(VARCHAR)").contains("does not exist"));
        assertTrue(asReader("reader_role", "SELECT GET_DDL('FUNCTION', 'f(NUMBER)')").contains("x + 1"));
        assertTrue(asReader("reader_role", "SELECT GET_DDL('FILE_FORMAT', 'ff')").contains("FILE FORMAT"));
    }

    @Test
    public void manageGrantsSeesEveryObject() {
        engine.execute("GRANT ROLE SECURITYADMIN TO USER reader");
        assertEquals("ST", names("SECURITYADMIN", "SHOW STAGES"));
        assertEquals("F | F", names("SECURITYADMIN", "SHOW USER FUNCTIONS"));
        assertEquals("MP", names("SECURITYADMIN", "SHOW MASKING POLICIES"));
    }
}
