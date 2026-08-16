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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INFORMATION_SCHEMA.POLICY_REFERENCES — the readback surface every policy attachment shares, and
 * the only one that shows a row access or aggregation policy at all (DESCRIBE's {@code policy name}
 * cell answers for masking policies alone).
 *
 * <p>The measured split: a policy attached to a COLUMN fills {@code REF_COLUMN_NAME} and leaves
 * {@code REF_ARG_COLUMN_NAMES} null, while one attached to the OBJECT does the reverse and renders
 * its argument columns as a spaced JSON array.
 */
public class PolicyReferencesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE MASKING POLICY pr_mp AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE ROW ACCESS POLICY pr_rap AS (a VARCHAR, b VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE PROJECTION POLICY pr_pp AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        engine.execute("CREATE AGGREGATION POLICY pr_ap AS () RETURNS AGGREGATION_CONSTRAINT"
            + " -> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 2)");
        engine.execute("CREATE TABLE pr_t (a VARCHAR, b VARCHAR, id NUMBER)");
        engine.execute("CREATE VIEW pr_v AS SELECT * FROM pr_t");
    }

    private ResultSet forTable() {
        return engine.executeQuery("SELECT POLICY_NAME, POLICY_KIND, REF_ENTITY_NAME,"
            + " REF_ENTITY_DOMAIN, REF_COLUMN_NAME, REF_ARG_COLUMN_NAMES, POLICY_STATUS"
            + " FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
            + "REF_ENTITY_NAME => 'PR_T', REF_ENTITY_DOMAIN => 'TABLE'))");
    }

    private String cellsOf(final ResultSet rs, final Row row) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            out.append(row.getValue(i)).append('|');
        }
        return out.toString();
    }

    /** A masking policy is keyed by its column. */
    @Test
    public void aColumnPolicyNamesItsColumn() {
        engine.execute("ALTER TABLE pr_t ALTER COLUMN a SET MASKING POLICY pr_mp");
        final ResultSet references = forTable();
        assertEquals(1, references.getRows().size());
        assertEquals("PR_MP|MASKING_POLICY|PR_T|TABLE|A|null|ACTIVE|",
            cellsOf(references, references.getRows().get(0)));
    }

    /** A projection policy is keyed the same way. */
    @Test
    public void aProjectionPolicyIsKeyedByColumnToo() {
        engine.execute("ALTER TABLE pr_t ALTER COLUMN b SET PROJECTION POLICY pr_pp");
        final ResultSet references = forTable();
        assertEquals("PR_PP|PROJECTION_POLICY|PR_T|TABLE|B|null|ACTIVE|",
            cellsOf(references, references.getRows().get(0)));
    }

    /** An object-level policy leaves the column null and lists its arguments instead. */
    @Test
    public void aRowAccessPolicyListsItsArgumentColumns() {
        engine.execute("ALTER TABLE pr_t ADD ROW ACCESS POLICY pr_rap ON (a, b)");
        final ResultSet references = forTable();
        assertEquals("PR_RAP|ROW_ACCESS_POLICY|PR_T|TABLE|null|[ \"A\", \"B\" ]|ACTIVE|",
            cellsOf(references, references.getRows().get(0)));
    }

    @Test
    public void anAggregationPolicyListsItsEntityKey() {
        engine.execute("ALTER TABLE pr_t SET AGGREGATION POLICY pr_ap ENTITY KEY (id)");
        final ResultSet references = forTable();
        assertEquals("PR_AP|AGGREGATION_POLICY|PR_T|TABLE|null|[ \"ID\" ]|ACTIVE|",
            cellsOf(references, references.getRows().get(0)));
    }

    /** Several attachments come back together, ordered by policy name. */
    @Test
    public void everyAttachmentOnTheObjectIsListed() {
        engine.execute("ALTER TABLE pr_t ALTER COLUMN a SET MASKING POLICY pr_mp");
        engine.execute("ALTER TABLE pr_t ALTER COLUMN b SET PROJECTION POLICY pr_pp");
        engine.execute("ALTER TABLE pr_t SET AGGREGATION POLICY pr_ap ENTITY KEY (id)");
        final ResultSet references = forTable();
        assertEquals(3, references.getRows().size());
        assertEquals("PR_AP", references.getRows().get(0).getValue(0).toString());
        assertEquals("PR_MP", references.getRows().get(1).getValue(0).toString());
        assertEquals("PR_PP", references.getRows().get(2).getValue(0).toString());
    }

    @Test
    public void aViewIsItsOwnDomain() {
        engine.execute("ALTER VIEW pr_v ADD ROW ACCESS POLICY pr_rap ON (a, b)");
        final ResultSet references = engine.executeQuery("SELECT POLICY_NAME, REF_ENTITY_NAME,"
            + " REF_ENTITY_DOMAIN FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
            + "REF_ENTITY_NAME => 'PR_V', REF_ENTITY_DOMAIN => 'VIEW'))");
        assertEquals(1, references.getRows().size());
        assertEquals("PR_RAP|PR_V|VIEW|", cellsOf(references, references.getRows().get(0)));
    }

    /** The domain is matched without regard to case. */
    @Test
    public void theDomainIsCaseInsensitive() {
        engine.execute("ALTER TABLE pr_t ALTER COLUMN a SET MASKING POLICY pr_mp");
        assertEquals(1, engine.executeQuery("SELECT POLICY_NAME FROM TABLE("
            + "INFORMATION_SCHEMA.POLICY_REFERENCES(REF_ENTITY_NAME => 'PR_T',"
            + " REF_ENTITY_DOMAIN => 'table'))").getRows().size());
    }

    /** The other call shape: one policy, every object it reaches. */
    @Test
    public void aPolicyListsEveryObjectItIsAttachedTo() {
        engine.execute("ALTER TABLE pr_t ADD ROW ACCESS POLICY pr_rap ON (a, b)");
        engine.execute("ALTER VIEW pr_v ADD ROW ACCESS POLICY pr_rap ON (a, b)");
        final ResultSet references = engine.executeQuery("SELECT REF_ENTITY_NAME, REF_ENTITY_DOMAIN"
            + " FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES(POLICY_NAME => 'PR_RAP'))");
        assertEquals(2, references.getRows().size());
    }

    @Test
    public void anObjectWithNoPoliciesAnswersNothing() {
        assertEquals(0, forTable().getRows().size());
    }

    /** One of the two argument shapes is required — half of the entity pair is not enough. */
    @Test
    public void theArgumentShapesAreSpelledOutWhenNeitherIsGiven() {
        final String expected = "SQL compilation error: function 'information_schema.policy_references'"
            + " expects argument (policy_name=>'policyName') or (ref_entity_name=>'name',"
            + " ref_entity_domain=>'domain').";
        final RuntimeException none = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES())");
            }
        });
        assertEquals(expected, none.getMessage());
        final RuntimeException half = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
                    + "REF_ENTITY_NAME => 'PR_T'))");
            }
        });
        assertEquals(expected, half.getMessage());
    }

    @Test
    public void anObjectThatDoesNotExistIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
                    + "REF_ENTITY_NAME => 'NO_SUCH_T', REF_ENTITY_DOMAIN => 'TABLE'))");
            }
        });
        assertEquals("SQL compilation error:\nTable 'NO_SUCH_T' does not exist or not authorized.",
            ex.getMessage());
    }

    @Test
    public void aPolicyThatDoesNotExistIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
                    + "POLICY_NAME => 'NO_SUCH_P'))");
            }
        });
        assertEquals("SQL compilation error:\nPolicy 'TEST_DB.TEST_SCHEMA.NO_SUCH_P'"
            + " does not exist or not authorized.", ex.getMessage());
    }

    @Test
    public void anUnknownDomainIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
                    + "REF_ENTITY_NAME => 'PR_T', REF_ENTITY_DOMAIN => 'NONSENSE'))");
            }
        });
        assertEquals("Unknown domain: NONSENSE.", ex.getMessage());
    }

    /** A positional call is read as (policy_name, policy_kind), and the kind is what it rejects. */
    @Test
    public void aPositionalCallIsRefusedAsAPolicyKind() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFORMATION_SCHEMA.POLICY_REFERENCES("
                    + "'PR_T', 'TABLE'))");
            }
        });
        assertTrue(ex.getMessage().contains("Unknown policy kind: TABLE."), ex.getMessage());
    }
}
