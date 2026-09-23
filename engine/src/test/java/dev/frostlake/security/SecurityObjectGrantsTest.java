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
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * Network policies, network rules, password policies and secrets are granted on like any securable: each takes its
 * own privileges, SHOW GRANTS ON lists who holds them, a grant blocks an ownership move, and dropping one needs
 * OWNERSHIP of it. A network policy attached to a user shows in SHOW PARAMETERS IN USER, and a HOST_PORT rule refuses
 * a value that cannot name a resolvable host.
 */
public class SecurityObjectGrantsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("USE ROLE ACCOUNTADMIN");
        // Privileges come from the primary role alone, and the refusals name only it.
        engine.execute("USE SECONDARY ROLES NONE");
        engine.execute("CREATE ROLE IF NOT EXISTS sg_r");
        engine.execute("CREATE ROLE IF NOT EXISTS sg_o");
        engine.execute("CREATE OR REPLACE NETWORK POLICY sg_np ALLOWED_IP_LIST = ('1.1.1.1')");
        engine.execute("CREATE NETWORK RULE sg_nr MODE = INGRESS TYPE = IPV4 VALUE_LIST = ('1.1.1.1')");
        engine.execute("CREATE PASSWORD POLICY sg_pp");
        engine.execute("CREATE SECRET sg_sec TYPE = PASSWORD USERNAME = 'u' PASSWORD = 'p'");
    }

    @Override
    protected void teardownTest() {
        quietly("USE ROLE ACCOUNTADMIN");
        quietly("GRANT ROLE sg_o TO ROLE ACCOUNTADMIN");
        quietly("ALTER USER IF EXISTS sg_u UNSET NETWORK_POLICY");
        quietly("DROP USER IF EXISTS sg_u");
        quietly("DROP NETWORK POLICY IF EXISTS sg_np");
        quietly("DROP NETWORK POLICY IF EXISTS sg_np2");
        quietly("DROP ROLE IF EXISTS sg_r");
        quietly("DROP ROLE IF EXISTS sg_o");
    }

    private void quietly(final String sql) {
        try {
            engine.execute(sql);
        } catch (final RuntimeException ignored) {
            // cleanup only
        }
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    /** Each grant SHOW GRANTS ON lists, as privilege, kind, name, grantee, grant option and grantor, by privilege. */
    private List<String> grants(final String on) {
        final ResultSet shown = engine.executeQuery("SHOW GRANTS ON " + on);
        final List<String> grants = new ArrayList<>();
        for (final Row row : shown.getRows()) {
            grants.add(row.getValue(shown.getColumnIndex("privilege")) + " "
                + row.getValue(shown.getColumnIndex("granted_on")) + " " + row.getValue(shown.getColumnIndex("name"))
                + " " + row.getValue(shown.getColumnIndex("grantee_name")) + " "
                + row.getValue(shown.getColumnIndex("grant_option")) + " "
                + row.getValue(shown.getColumnIndex("granted_by")));
        }
        Collections.sort(grants);
        return grants;
    }

    private static String invalid(final String kind, final String privilege) {
        return "SQL compilation error:\nInvalid object type '" + kind + "' for privilege '" + privilege + "'.";
    }

    @Test
    public void eachKindTakesItsOwnPrivileges() {
        engine.execute("GRANT USAGE ON NETWORK POLICY sg_np TO ROLE sg_r");
        engine.execute("GRANT USAGE ON NETWORK RULE sg_nr TO ROLE sg_r");
        engine.execute("GRANT APPLY ON PASSWORD POLICY sg_pp TO ROLE sg_r");
        engine.execute("GRANT USAGE, READ ON SECRET sg_sec TO ROLE sg_r");
        assertEquals(List.of("OWNERSHIP NETWORK_POLICY SG_NP ACCOUNTADMIN true ACCOUNTADMIN",
            "USAGE NETWORK_POLICY SG_NP SG_R false ACCOUNTADMIN"), grants("NETWORK POLICY sg_np"));
        assertEquals(List.of("OWNERSHIP NETWORK_RULE TEST_DB.TEST_SCHEMA.SG_NR ACCOUNTADMIN true ACCOUNTADMIN",
            "USAGE NETWORK_RULE TEST_DB.TEST_SCHEMA.SG_NR SG_R false ACCOUNTADMIN"), grants("NETWORK RULE sg_nr"));
        assertEquals(List.of("APPLY PASSWORD_POLICY TEST_DB.TEST_SCHEMA.SG_PP SG_R false ACCOUNTADMIN",
            "OWNERSHIP PASSWORD_POLICY TEST_DB.TEST_SCHEMA.SG_PP ACCOUNTADMIN true ACCOUNTADMIN"),
            grants("PASSWORD POLICY sg_pp"));
        assertEquals(List.of("OWNERSHIP SECRET TEST_DB.TEST_SCHEMA.SG_SEC ACCOUNTADMIN true ACCOUNTADMIN",
            "READ SECRET TEST_DB.TEST_SCHEMA.SG_SEC SG_R false ACCOUNTADMIN",
            "USAGE SECRET TEST_DB.TEST_SCHEMA.SG_SEC SG_R false ACCOUNTADMIN"), grants("SECRET sg_sec"));
        assertEquals(invalid("NETWORK_POLICY", "SELECT"), refusal("GRANT SELECT ON NETWORK POLICY sg_np TO ROLE sg_r"));
        assertEquals(invalid("NETWORK_POLICY", "MODIFY"), refusal("GRANT MODIFY ON NETWORK POLICY sg_np TO ROLE sg_r"));
        assertEquals(invalid("NETWORK_RULE", "SELECT"), refusal("GRANT SELECT ON NETWORK RULE sg_nr TO ROLE sg_r"));
        assertEquals(invalid("POLICY", "USAGE"), refusal("GRANT USAGE ON PASSWORD POLICY sg_pp TO ROLE sg_r"));
    }

    @Test
    public void aMissingObjectIsNamedByItsKind() {
        assertTrue(refusal("GRANT USAGE ON NETWORK POLICY sg_nosuch TO ROLE sg_r").startsWith(
            "SQL compilation error:\nNetwork policy 'SG_NOSUCH' does not exist or not authorized."));
        assertTrue(refusal("GRANT USAGE ON NETWORK RULE sg_nosuch TO ROLE sg_r").startsWith(
            "SQL compilation error:\nNetwork rule 'TEST_DB.TEST_SCHEMA.SG_NOSUCH' does not exist or not authorized."));
        assertTrue(refusal("GRANT APPLY ON PASSWORD POLICY sg_nosuch TO ROLE sg_r").startsWith(
            "SQL compilation error:\nPassword policy 'TEST_DB.TEST_SCHEMA.SG_NOSUCH' does not exist or not "
                + "authorized."));
        assertTrue(refusal("GRANT USAGE ON SECRET sg_nosuch TO ROLE sg_r").startsWith(
            "SQL compilation error:\nSecret 'TEST_DB.TEST_SCHEMA.SG_NOSUCH' does not exist or not authorized."));
    }

    @Test
    public void ownershipMovesOnceNothingDependsOnIt() {
        engine.execute("GRANT USAGE ON NETWORK POLICY sg_np TO ROLE sg_r");
        assertEquals("SQL execution error: Dependent grant of privilege 'USAGE' on securable 'SG_NP' to role 'SG_R' "
            + "exists.  It must be revoked first.  More than one dependent grant may exist: use 'SHOW GRANTS' command "
            + "to view them.  To revoke all dependent grants while transferring object ownership, use convenience "
            + "command 'GRANT OWNERSHIP ON <target_objects> TO <target_role> REVOKE CURRENT GRANTS'.",
            refusal("GRANT OWNERSHIP ON NETWORK POLICY sg_np TO ROLE sg_o"));
        engine.execute("REVOKE USAGE ON NETWORK POLICY sg_np FROM ROLE sg_r");
        engine.execute("GRANT OWNERSHIP ON NETWORK POLICY sg_np TO ROLE sg_o");
        assertEquals(List.of("OWNERSHIP NETWORK_POLICY SG_NP SG_O true SG_O"), grants("NETWORK POLICY sg_np"));
        engine.execute("GRANT OWNERSHIP ON NETWORK RULE sg_nr TO ROLE sg_o COPY CURRENT GRANTS");
        engine.execute("GRANT OWNERSHIP ON PASSWORD POLICY sg_pp TO ROLE sg_o COPY CURRENT GRANTS");
        engine.execute("GRANT OWNERSHIP ON SECRET sg_sec TO ROLE sg_o REVOKE CURRENT GRANTS");
        for (final String listing : new String[] {"SHOW NETWORK RULES LIKE 'SG_NR'",
            "SHOW PASSWORD POLICIES LIKE 'SG_PP'", "SHOW SECRETS LIKE 'SG_SEC'"}) {
            final ResultSet shown = engine.executeQuery(listing);
            assertEquals("SG_O", String.valueOf(shown.getRows().get(0).getValue(shown.getColumnIndex("owner"))),
                listing);
        }
        assertEquals("SQL access control error:\nInsufficient privileges to operate on network_policy 'SG_NP'. Your "
            + "primary role ACCOUNTADMIN must have OWNERSHIP granted on NETWORK POLICY "
            + "SG_NP.", refusal("DROP NETWORK POLICY sg_np"));
        assertEquals("SQL access control error:\nInsufficient privileges to operate on network_rule 'SG_NR'. Your "
            + "primary role ACCOUNTADMIN must have OWNERSHIP granted on NETWORK RULE "
            + "TEST_DB.TEST_SCHEMA.SG_NR.", refusal("DROP NETWORK RULE sg_nr"));
    }

    /** The one row SHOW PARAMETERS … IN USER answers for the pattern, as key, value, default, level and type. */
    private List<String> userParameters(final String like) {
        final ResultSet shown = engine.executeQuery("SHOW PARAMETERS LIKE '" + like + "' IN USER sg_u");
        final List<String> rows = new ArrayList<>();
        for (final Row row : shown.getRows()) {
            rows.add(row.getValue(shown.getColumnIndex("key")) + "|" + row.getValue(shown.getColumnIndex("value"))
                + "|" + row.getValue(shown.getColumnIndex("default")) + "|"
                + row.getValue(shown.getColumnIndex("level")) + "|" + row.getValue(shown.getColumnIndex("description"))
                + "|" + row.getValue(shown.getColumnIndex("type")));
        }
        return rows;
    }

    @Test
    public void aUsersNetworkPolicyShowsAmongItsParameters() {
        engine.execute("CREATE USER IF NOT EXISTS sg_u");
        assertEquals(List.of("NETWORK_POLICY||||Network policy assigned for the given target.|STRING"),
            userParameters("NETWORK_POLICY"));
        engine.execute("CREATE OR REPLACE NETWORK POLICY sg_np2 ALLOWED_IP_LIST = ('0.0.0.0/0')");
        engine.execute("ALTER USER sg_u SET NETWORK_POLICY = sg_np2");
        final List<String> attached = List.of("NETWORK_POLICY|SG_NP2||USER|Network policy assigned for the given "
            + "target.|STRING");
        assertEquals(attached, userParameters("NETWORK_POLICY"));
        // A LIKE pattern: a prefix names the parameters that start with it.
        assertEquals(attached, userParameters("network%"));
        engine.execute("ALTER USER sg_u UNSET NETWORK_POLICY");
        assertEquals(List.of("NETWORK_POLICY||||Network policy assigned for the given target.|STRING"),
            userParameters("NETWORK_POLICY"));
    }

    @Test
    public void showParametersTakesALikePattern() {
        assertEquals(0, engine.executeQuery("SHOW PARAMETERS LIKE 'TIMESTAMP'").getRowCount());
        final ResultSet week = engine.executeQuery("SHOW PARAMETERS LIKE 'week%'");
        final List<String> keys = new ArrayList<>();
        for (final Row row : week.getRows()) {
            keys.add(String.valueOf(row.getValue(week.getColumnIndex("key"))));
        }
        Collections.sort(keys);
        assertEquals(List.of("WEEK_OF_YEAR_POLICY", "WEEK_START"), keys);
    }

    @Test
    public void aHostPortRuleNamesResolvableHosts() {
        for (final String accepted : new String[] {"example.com", "example.com:0", "example.com:443",
            "example.com:65535", "*.example.com"}) {
            engine.execute("CREATE OR REPLACE NETWORK RULE sg_hp MODE = EGRESS TYPE = HOST_PORT VALUE_LIST = ('"
                + accepted + "')");
        }
        for (final String refused : new String[] {"example.com:99999", "example.com:65536", "example.com:notaport",
            "example.com:*", "no-such-host.invalid", "localhost", "bad_host!.com", "-bad.example.com", "10.0.0.1:80",
            "example"}) {
            assertEquals("SQL compilation error:\ninvalid value '[" + refused + "]' for property 'VALUE_LIST', Reason: "
                + "One or more values might be an unresolvable host name. Verify all hosts are resolvable, or use a "
                + "wildcard pattern.", refusal("CREATE OR REPLACE NETWORK RULE sg_hp MODE = EGRESS TYPE = HOST_PORT "
                + "VALUE_LIST = ('" + refused + "')"), refused);
        }
    }
}
