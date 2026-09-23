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
import dev.frostlake.storage.ResultSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A privilege is named by a LEAD word and the words after it. The lead is a closed set — an unknown one
 * is a syntax error on that very word — while the tail is not: the account grants CREATE AGENT, CREATE
 * DBT PROJECT, CREATE ZEROCOPY CONNECTOR and sixty more whose kind words no listing enumerates. The
 * grammar used to spell out each name it accepted, so {@code GRANT ALL ON SCHEMA} expanded to
 * eighty-four privileges of which most could not then be granted, or revoked, one at a time.
 */
public class PrivilegeByNameTest extends BaseDatabaseTest {

    /** The multi-word names measured grantable on a schema. */
    private static final String[] ON_SCHEMA = {"CREATE AGENT", "CREATE DYNAMIC TABLE",
        "CREATE MATERIALIZED VIEW", "CREATE SECRET", "CREATE TEMPORARY TABLE",
        "ADD SEARCH OPTIMIZATION", "EXECUTE AUTO CLASSIFICATION", "CREATE FILE FORMAT",
        "CREATE ALERT", "CREATE NOTEBOOK", "MODIFY"};

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        engine.execute("CREATE OR REPLACE ROLE pna");
        engine.execute("CREATE OR REPLACE SCHEMA ps1");
        engine.execute("CREATE VIEW ps1.v1 AS SELECT 1 AS a");
    }

    /** The privileges a role holds, sorted so the listing's own order does not decide the assertion. */
    private List<String> heldPrivileges() {
        final ResultSet rs = engine.executeQuery("SHOW GRANTS TO ROLE pna");
        final List<String> held = new ArrayList<>();
        while (rs.next()) {
            held.add(String.valueOf(rs.getValue("privilege")));
        }
        Collections.sort(held);
        return held;
    }

    /** The refusal a statement raises, or "OK". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "OK";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace("\n", " ");
        }
    }

    /** Every multi-word name a schema's GRANT ALL expands to can be granted on its own. */
    @Test
    public void aMultiWordPrivilegeGrantsByName() {
        for (final String name : ON_SCHEMA) {
            assertEquals("OK", outcome("GRANT " + name + " ON SCHEMA ps1 TO ROLE pna"), name);
        }
        final List<String> held = heldPrivileges();
        for (final String name : ON_SCHEMA) {
            assertTrue(held.contains(name), name + " not in " + held);
        }
    }

    /** A database and a view have names of their own, and those grant by name too. */
    @Test
    public void theOtherKindsNamesGrantToo() {
        assertEquals("OK", outcome(
            "GRANT CREATE DATABASE ROLE ON DATABASE test_db TO ROLE pna"));
        assertEquals("OK", outcome(
            "GRANT VIEW EXPANDED QUERY PROFILE ON VIEW ps1.v1 TO ROLE pna"));
        final List<String> held = heldPrivileges();
        assertTrue(held.contains("CREATE DATABASE ROLE"), held.toString());
        assertTrue(held.contains("VIEW EXPANDED QUERY PROFILE"), held.toString());
    }

    /** Several of them in one statement, and one of them revoked again. */
    @Test
    public void aListOfThemGrantsAndRevokes() {
        assertEquals("OK", outcome(
            "GRANT CREATE AGENT, CREATE SECRET ON SCHEMA ps1 TO ROLE pna"));
        assertTrue(heldPrivileges().contains("CREATE AGENT"), heldPrivileges().toString());
        assertEquals("OK", outcome("REVOKE CREATE AGENT ON SCHEMA ps1 FROM ROLE pna"));
        assertTrue(!heldPrivileges().contains("CREATE AGENT"), heldPrivileges().toString());
        assertTrue(heldPrivileges().contains("CREATE SECRET"), heldPrivileges().toString());
    }

    /** An unknown LEAD word is a syntax error on that word, not a privilege the engine then refuses. */
    @Test
    public void anUnknownLeadWordIsASyntaxError() {
        final String answer = outcome("GRANT NO SUCH PRIVILEGE ON SCHEMA ps1 TO ROLE pna");
        assertTrue(answer.contains("syntax error line 1 at position 6 unexpected 'NO'"), answer);
    }

    /** A known privilege on a kind that has no such privilege is refused by NAME, not by syntax. */
    @Test
    public void aPrivilegeTheKindDoesNotHaveIsRefusedByName() {
        assertEquals("SQL compilation error: Invalid object type 'SCHEMA' for privilege 'SELECT'.",
            outcome("GRANT SELECT ON SCHEMA ps1 TO ROLE pna"));
    }
}
