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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** CREATE, ALTER, DROP, SHOW and DESCRIBE NETWORK RULE. */
public class NetworkRuleTest extends BaseDatabaseTest {

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

    @Test
    public void aRuleIsCreatedListedAndDescribed() {
        engine.execute("CREATE NETWORK RULE nr_a TYPE = IPV4 VALUE_LIST = ('10.0.0.0/8', '1.2.3.4') COMMENT = 'c'");
        final ResultSet listed = engine.executeQuery("SHOW NETWORK RULES LIKE 'nr_a'");
        final Row row = soleRowWhere(listed, "name", "NR_A");
        assertEquals("IPV4", cell(listed, row, "type"));
        assertEquals("INGRESS", cell(listed, row, "mode"), "MODE defaults to INGRESS");
        assertEquals("2", cell(listed, row, "entries_in_valuelist"));
        assertEquals("c", cell(listed, row, "comment"));
        assertEquals("TEST_DB", cell(listed, row, "database_name"));
        assertEquals("TEST_SCHEMA", cell(listed, row, "schema_name"));
        assertEquals("ROLE", cell(listed, row, "owner_role_type"));

        final ResultSet described = engine.executeQuery("DESC NETWORK RULE nr_a");
        assertEquals(1, described.getRows().size());
        assertEquals("10.0.0.0/8,1.2.3.4", cell(described, described.getRows().get(0), "value_list"));
    }

    @Test
    public void setAndUnsetChangeTheValueListAndTheComment() {
        engine.execute("CREATE NETWORK RULE nr_b TYPE = HOST_PORT MODE = EGRESS VALUE_LIST = ('example.com')");
        engine.execute("ALTER NETWORK RULE nr_b SET VALUE_LIST = ('a.com', 'b.com', 'c.com') COMMENT = 'x'");
        ResultSet listed = engine.executeQuery("SHOW NETWORK RULES LIKE 'NR_B'");
        assertEquals("3", cell(listed, listed.getRows().get(0), "entries_in_valuelist"));
        assertEquals("x", cell(listed, listed.getRows().get(0), "comment"));
        engine.execute("ALTER NETWORK RULE nr_b UNSET VALUE_LIST");
        engine.execute("ALTER NETWORK RULE IF EXISTS nr_b UNSET COMMENT");
        listed = engine.executeQuery("SHOW NETWORK RULES LIKE 'NR_B'");
        assertEquals("0", cell(listed, listed.getRows().get(0), "entries_in_valuelist"));
        assertEquals("", cell(listed, listed.getRows().get(0), "comment"));
        assertEquals("EGRESS", cell(listed, listed.getRows().get(0), "mode"));
        engine.execute("ALTER NETWORK RULE IF EXISTS nr_missing SET COMMENT = 'x'");
    }

    @Test
    public void createOrAlterKeepsTheRuleAndRefusesANewType() {
        engine.execute("CREATE NETWORK RULE nr_c TYPE = IPV4 VALUE_LIST = ('1.1.1.1') COMMENT = 'old'");
        engine.execute("CREATE OR ALTER NETWORK RULE nr_c TYPE = IPV4 VALUE_LIST = ('2.2.2.2', '3.3.3.3')");
        final ResultSet listed = engine.executeQuery("SHOW NETWORK RULES LIKE 'NR_C'");
        assertEquals("2", cell(listed, listed.getRows().get(0), "entries_in_valuelist"));
        assertEquals("old", cell(listed, listed.getRows().get(0), "comment"), "what the statement leaves out is kept");
        assertTrue(refusal("CREATE OR ALTER NETWORK RULE nr_c TYPE = IPV6 VALUE_LIST = ('::1')")
            .contains("cannot be changed"));
        engine.execute("CREATE OR ALTER NETWORK RULE nr_c2 TYPE = IPV6 VALUE_LIST = ('::1')");
        assertEquals(1, engine.executeQuery("SHOW NETWORK RULES LIKE 'NR_C2'").getRows().size());
    }

    @Test
    public void createAndDropRefusals() {
        engine.execute("CREATE NETWORK RULE nr_d TYPE = IPV4 VALUE_LIST = ('1.1.1.1')");
        assertTrue(refusal("CREATE NETWORK RULE nr_d TYPE = IPV4").contains("Object 'NR_D' already exists."));
        engine.execute("CREATE OR REPLACE NETWORK RULE nr_d TYPE = AWSVPCEID VALUE_LIST = ('vpce-1')");
        assertTrue(refusal("CREATE NETWORK RULE nr_e VALUE_LIST = ('1.1.1.1')").contains("Missing option(s): [TYPE]"));
        assertEquals("The network rule type must be one of the snowflake supported network rule type.",
            refusal("CREATE NETWORK RULE nr_e TYPE = NOSUCH"));
        assertTrue(refusal("CREATE NETWORK RULE nr_e TYPE = IPV4 MODE = EGRESS").contains(
            "The network rule mode EGRESS is not supported by the network rule type IPv4."));
        assertEquals("Unsupported feature 'renaming NETWORK_RULE'.", refusal("ALTER NETWORK RULE nr_d RENAME TO nr_x"));
        engine.execute("CREATE NETWORK RULE IF NOT EXISTS nr_d TYPE = IPV4");
        assertTrue(refusal("CREATE NETWORK RULE nr_e TYPE = IPV4 COLOR = 'red'").contains(
            "invalid property 'COLOR' for 'NETWORK_RULE'"));
        assertTrue(refusal("CREATE NETWORK RULE nr_e TYPE = IPV4 VALUE_LIST = '1.1.1.1'").contains(
            "invalid type of property ''1.1.1.1'' for 'VALUE_LIST'"));
        assertTrue(refusal("ALTER NETWORK RULE nr_d SET TYPE = IPV4").contains(
            "invalid property 'TYPE' for 'NETWORK_RULE'"));
        assertTrue(refusal("DROP NETWORK RULE nr_missing").contains(
            "Network rule 'TEST_DB.TEST_SCHEMA.NR_MISSING' does not exist or not authorized."));
        engine.execute("DROP NETWORK RULE IF EXISTS nr_missing");
        engine.execute("DROP NETWORK RULE nr_d");
        assertEquals(0, engine.executeQuery("SHOW NETWORK RULES LIKE 'NR_D'").getRows().size());
    }

    @Test
    public void listingsTakeTheirScopeAndPaging() {
        engine.execute("CREATE SCHEMA nr_other");
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE NETWORK RULE nr_p1 TYPE = IPV4 VALUE_LIST = ('1.1.1.1')");
        engine.execute("CREATE NETWORK RULE nr_p2 TYPE = IPV4 VALUE_LIST = ('1.1.1.1')");
        engine.execute("CREATE NETWORK RULE nr_other.nr_p3 TYPE = IPV4 VALUE_LIST = ('1.1.1.1')");
        assertEquals(2, engine.executeQuery("SHOW NETWORK RULES LIKE 'NR_P%' IN SCHEMA test_db.test_schema")
            .getRows().size());
        assertEquals(3, engine.executeQuery("SHOW NETWORK RULES LIKE 'NR_P%' IN DATABASE test_db").getRows().size());
        assertEquals(3, engine.executeQuery("SHOW NETWORK RULES LIKE 'NR_P%' IN ACCOUNT").getRows().size());
        assertEquals(3, engine.executeQuery("SHOW NETWORK RULES LIKE 'NR_P%'").getRows().size(),
            "with no scope the current database is listed");
        final ResultSet paged = engine.executeQuery("SHOW NETWORK RULES IN SCHEMA STARTS WITH 'NR_P' LIMIT 1 FROM 'NR_P1'");
        assertEquals(1, paged.getRows().size());
        assertEquals("NR_P2", cell(paged, paged.getRows().get(0), "name"));
        assertTrue(refusal("SHOW NETWORK RULES LIMIT 0").contains("must be greater than 0"));
    }

    @Test
    public void theNewKeywordsStillNameColumnsAndTables() {
        engine.execute("CREATE TABLE rules (rule INT, secret INT, secrets INT, network_policy INT)");
        engine.execute("INSERT INTO rules VALUES (1, 2, 3, 4)");
        final ResultSet selected = engine.executeQuery("SELECT rule, secret, secrets, network_policy FROM rules");
        assertEquals(4, selected.getColumns().size());
        assertEquals(1, selected.getRows().size());
    }
}
