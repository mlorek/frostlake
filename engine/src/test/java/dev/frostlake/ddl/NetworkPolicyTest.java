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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE, ALTER, DROP, SHOW and DESCRIBE NETWORK POLICY, and attaching a network policy to the account and to a
 * user, which is recorded and never enforced.
 */
public class NetworkPolicyTest extends BaseDatabaseTest {

    /** Runs a statement that must be refused and answers the refusal's message. */
    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    private String describedValue(final String policy, final String property) {
        final ResultSet described = engine.executeQuery("DESCRIBE NETWORK POLICY " + policy);
        final List<Row> rows = rowsWhere(described, "name", property);
        return rows.isEmpty() ? null : cell(described, rows.get(0), "value");
    }

    @Test
    public void aPolicyIsCreatedListedAndDescribed() {
        engine.execute("CREATE NETWORK RULE np_rule TYPE = IPV4 VALUE_LIST = ('10.0.0.0/8')");
        engine.execute("CREATE NETWORK POLICY np_a ALLOWED_NETWORK_RULE_LIST = ('np_rule') ALLOWED_IP_LIST = "
            + "('1.2.3.4', '5.6.7.8') BLOCKED_IP_LIST = ('9.9.9.9') COMMENT = 'pol'");
        final ResultSet listed = engine.executeQuery("SHOW NETWORK POLICIES");
        final Row row = soleRowWhere(listed, "name", "NP_A");
        assertEquals("pol", cell(listed, row, "comment"));
        assertEquals("2", cell(listed, row, "entries_in_allowed_ip_list"));
        assertEquals("1", cell(listed, row, "entries_in_blocked_ip_list"));
        assertEquals("1", cell(listed, row, "entries_in_allowed_network_rules"));
        assertEquals("0", cell(listed, row, "entries_in_blocked_network_rules"));
        assertEquals("1.2.3.4,5.6.7.8", describedValue("np_a", "ALLOWED_IP_LIST"));
        assertEquals("[{\"fullyQualifiedRuleName\":\"TEST_DB.TEST_SCHEMA.NP_RULE\"}]",
            describedValue("np_a", "ALLOWED_NETWORK_RULE_LIST"));
        assertNull(describedValue("np_a", "BLOCKED_NETWORK_RULE_LIST"), "an unset list is not described");
    }

    @Test
    public void aRuleListNamesRulesThatExist() {
        assertTrue(refusal("CREATE NETWORK POLICY np_b ALLOWED_NETWORK_RULE_LIST = ('no_such_rule')").contains(
            "Network rule 'TEST_DB.TEST_SCHEMA.NO_SUCH_RULE' does not exist or not authorized."));
        assertTrue(refusal("CREATE NETWORK POLICY np_b ALLOWED_IP_LIST = ('1.1.1.1') MODE = INGRESS").contains(
            "invalid property 'MODE' for 'NETWORK_POLICY'"));
    }

    @Test
    public void alterSetsAddsRemovesUnsetsAndRenames() {
        engine.execute("CREATE NETWORK RULE np_r1 TYPE = IPV4 VALUE_LIST = ('1.1.1.1')");
        engine.execute("CREATE NETWORK RULE np_r2 TYPE = IPV4 VALUE_LIST = ('2.2.2.2')");
        engine.execute("CREATE NETWORK POLICY np_c COMMENT = 'c'");
        assertTrue(refusal("ALTER NETWORK POLICY np_c ADD BLOCKED_NETWORK_RULE_LIST = 'np_r1'").contains(
            "invalid type of property ''np_r1'' for 'BLOCKED_NETWORK_RULE_LIST'"));
        engine.execute("ALTER NETWORK POLICY np_c ADD BLOCKED_NETWORK_RULE_LIST = ('np_r1')");
        engine.execute("ALTER NETWORK POLICY np_c ADD BLOCKED_NETWORK_RULE_LIST = ('test_db.test_schema.np_r2')");
        assertEquals("[{\"fullyQualifiedRuleName\":\"TEST_DB.TEST_SCHEMA.NP_R1\"},"
            + "{\"fullyQualifiedRuleName\":\"TEST_DB.TEST_SCHEMA.NP_R2\"}]",
            describedValue("np_c", "BLOCKED_NETWORK_RULE_LIST"));
        engine.execute("ALTER NETWORK POLICY np_c REMOVE BLOCKED_NETWORK_RULE_LIST = ('np_r1')");
        assertEquals("[{\"fullyQualifiedRuleName\":\"TEST_DB.TEST_SCHEMA.NP_R2\"}]",
            describedValue("np_c", "BLOCKED_NETWORK_RULE_LIST"));
        engine.execute("CREATE NETWORK RULE np_out TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('example.com')");
        assertTrue(refusal("ALTER NETWORK POLICY np_c ADD ALLOWED_NETWORK_RULE_LIST = ('np_out')").startsWith(
            "The egress network rule NP_OUT cannot be attached to the network policy NP_C."));
        engine.execute("ALTER NETWORK POLICY np_c SET ALLOWED_IP_LIST = ('3.3.3.3') COMMENT = 'd'");
        assertEquals("3.3.3.3", describedValue("np_c", "ALLOWED_IP_LIST"));
        engine.execute("ALTER NETWORK POLICY np_c UNSET COMMENT");
        engine.execute("ALTER NETWORK POLICY np_c UNSET ALLOWED_IP_LIST");
        assertNull(describedValue("np_c", "ALLOWED_IP_LIST"));
        engine.execute("ALTER NETWORK POLICY np_c RENAME TO np_c2");
        final ResultSet listed = engine.executeQuery("SHOW NETWORK POLICIES");
        assertEquals(0, rowsWhere(listed, "name", "NP_C").size());
        assertEquals("", cell(listed, soleRowWhere(listed, "name", "NP_C2"), "comment"));
        engine.execute("ALTER NETWORK POLICY IF EXISTS np_c SET COMMENT = 'gone'");
        assertTrue(refusal("ALTER NETWORK POLICY np_c SET COMMENT = 'gone'").contains(
            "Network policy 'NP_C' does not exist or not authorized."));
    }

    @Test
    public void createModesAndDrop() {
        engine.execute("CREATE NETWORK POLICY np_d COMMENT = 'first'");
        assertTrue(refusal("CREATE NETWORK POLICY np_d").contains("Object 'NP_D' already exists."));
        engine.execute("CREATE NETWORK POLICY IF NOT EXISTS np_d COMMENT = 'second'");
        assertEquals("first", cell(engine.executeQuery("SHOW NETWORK POLICIES"),
            soleRowWhere(engine.executeQuery("SHOW NETWORK POLICIES"), "name", "NP_D"), "comment"));
        engine.execute("CREATE OR REPLACE NETWORK POLICY np_d COMMENT = 'third'");
        engine.execute("CREATE OR ALTER NETWORK POLICY np_d ALLOWED_IP_LIST = ('1.1.1.1')");
        final ResultSet listed = engine.executeQuery("SHOW NETWORK POLICIES");
        assertEquals("third", cell(listed, soleRowWhere(listed, "name", "NP_D"), "comment"),
            "what CREATE OR ALTER leaves out is kept");
        assertEquals("1", cell(listed, soleRowWhere(listed, "name", "NP_D"), "entries_in_allowed_ip_list"));
        engine.execute("DROP NETWORK POLICY np_d");
        engine.execute("DROP NETWORK POLICY IF EXISTS np_d");
        assertTrue(refusal("DROP NETWORK POLICY np_d").contains("Network policy 'NP_D' does not exist"));
    }

    @Test
    public void attachingToTheAccountAndToAUserIsRecorded() {
        engine.execute("CREATE NETWORK POLICY np_e ALLOWED_IP_LIST = ('1.1.1.1')");
        engine.execute("CREATE USER np_user");
        engine.execute("ALTER ACCOUNT SET NETWORK_POLICY = np_e");
        engine.execute("ALTER USER np_user SET NETWORK_POLICY = 'np_e'");
        engine.execute("ALTER USER np_user UNSET NETWORK_POLICY");
        engine.execute("ALTER ACCOUNT UNSET NETWORK_POLICY");
        engine.execute("ALTER USER IF EXISTS np_nobody SET NETWORK_POLICY = np_e");
        assertEquals("Network policy NP_MISSING does not exist or not authorized.",
            refusal("ALTER ACCOUNT SET NETWORK_POLICY = np_missing"));
        engine.execute("ALTER USER np_user SET NETWORK_POLICY = np_e");
        assertTrue(refusal("DROP NETWORK POLICY np_e").contains("The policy is attached to USER with name NP_USER."));
        engine.execute("ALTER USER np_user UNSET NETWORK_POLICY");
        engine.execute("DROP NETWORK POLICY np_e");
        assertTrue(refusal("ALTER USER np_nobody SET NETWORK_POLICY = np_e").contains(
            "User 'NP_NOBODY' does not exist or not authorized."));
    }

    @Test
    public void tagsAreSetAndReadBack() {
        engine.execute("CREATE TAG np_tag");
        engine.execute("CREATE NETWORK POLICY np_f");
        engine.execute("ALTER NETWORK POLICY np_f SET TAG np_tag = 'v1'");
        ResultSet tags = engine.executeQuery(
            "SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('np_f', 'NETWORK POLICY'))");
        assertEquals(1, tags.getRows().size());
        assertEquals("v1", cell(tags, tags.getRows().get(0), "TAG_VALUE"));
        engine.execute("ALTER NETWORK POLICY np_f UNSET TAG np_tag");
        tags = engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.TAG_REFERENCES('np_f', 'NETWORK POLICY'))");
        assertFalse(tags.getRows().size() > 0);
    }
}
