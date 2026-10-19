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
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INVOKER_ROLE() names the role that owns the object executing, as Snowflake's INVOKER_ROLE page lays out: a view's
 * owner inside the view, a function's owner inside the function, an owner's-rights procedure's owner inside the
 * procedure, and the current role outside them. A row access policy keyed on it therefore treats a read through a view
 * as the view owner's read. The live account allows no CREATE ROLE, CREATE USER or GRANT, so a reader other than the
 * owner is the embedded engine's alone.
 */
public class InvokerRoleOwnerTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.setCurrentRole("SYSADMIN");
        engine.execute("CREATE DATABASE db");
        engine.execute("USE DATABASE db");
        engine.execute("USE SCHEMA PUBLIC");
        engine.execute("CREATE TABLE t (id INT, region STRING)");
        engine.execute("INSERT INTO t VALUES (1, 'EU'), (2, 'US'), (3, 'EU')");
        engine.execute("CREATE ROW ACCESS POLICY p AS (r STRING) RETURNS BOOLEAN -> INVOKER_ROLE() = 'R' OR r = 'EU'");
        engine.execute("ALTER TABLE t ADD ROW ACCESS POLICY p ON (region)");
        engine.execute("CREATE ROLE r");
        engine.execute("CREATE USER u");
        engine.execute("GRANT ROLE r TO USER u");
        engine.execute("GRANT USAGE ON DATABASE db TO ROLE r");
        engine.execute("GRANT USAGE ON SCHEMA db.public TO ROLE r");
        engine.execute("GRANT SELECT ON TABLE t TO ROLE r");
        engine.execute("CREATE VIEW v AS SELECT * FROM t");
        engine.execute("CREATE VIEW vr AS SELECT INVOKER_ROLE() AS ir, CURRENT_ROLE() AS cr");
        engine.execute("""
            CREATE FUNCTION ufn() RETURNS TABLE (a STRING, b STRING) AS 'SELECT INVOKER_ROLE(), CURRENT_ROLE()'""");
        engine.execute("CREATE FUNCTION sfn() RETURNS STRING AS 'INVOKER_ROLE()'");
        engine.execute("""
            CREATE PROCEDURE po() RETURNS STRING LANGUAGE SQL EXECUTE AS OWNER
            AS 'BEGIN RETURN INVOKER_ROLE() || ''/'' || CURRENT_ROLE(); END'""");
        engine.execute("""
            CREATE PROCEDURE pc() RETURNS STRING LANGUAGE SQL EXECUTE AS CALLER
            AS 'BEGIN RETURN INVOKER_ROLE() || ''/'' || CURRENT_ROLE(); END'""");
        engine.execute("GRANT SELECT ON VIEW v TO ROLE r");
        engine.execute("GRANT SELECT ON VIEW vr TO ROLE r");
        engine.execute("GRANT USAGE ON ALL FUNCTIONS IN SCHEMA db.public TO ROLE r");
        engine.execute("GRANT USAGE ON ALL PROCEDURES IN SCHEMA db.public TO ROLE r");
        engine.getSessionContext().clearActiveRoles();
        engine.setCurrentUser("u");
        engine.setCurrentRole("r");
    }

    @AfterEach
    public void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private Object scalar(final String sql) {
        return engine.executeQuery(sql).getRows().get(0).getValue(0);
    }

    @Test
    public void outsideEveryObjectItIsTheCurrentRole() {
        assertEquals("R", scalar("SELECT INVOKER_ROLE()"));
        assertEquals("R/R", scalar("CALL pc()"));
    }

    @Test
    public void insideAViewOrAFunctionItIsTheOwner() {
        final ResultSet view = engine.executeQuery("SELECT ir, cr FROM vr");
        assertEquals("SYSADMIN", view.getRows().get(0).getValue(0));
        assertEquals("R", view.getRows().get(0).getValue(1));
        final ResultSet table = engine.executeQuery("SELECT a, b FROM TABLE(ufn())");
        assertEquals("SYSADMIN", table.getRows().get(0).getValue(0));
        assertEquals("R", table.getRows().get(0).getValue(1));
        assertEquals("SYSADMIN", scalar("SELECT sfn()"));
        assertEquals("SYSADMIN/SYSADMIN", scalar("CALL po()"));
    }

    @Test
    public void aPolicyUnderAViewSeesTheViewOwner() {
        assertEquals(3L, ((Number) scalar("SELECT COUNT(*) FROM t")).longValue());
        assertEquals(2L, ((Number) scalar("SELECT COUNT(*) FROM v")).longValue());
    }
}
