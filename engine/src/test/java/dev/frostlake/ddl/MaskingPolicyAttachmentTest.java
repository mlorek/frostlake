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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an attached MASKING POLICY becomes visible. Both readback surfaces spell the policy in FULL —
 * DESCRIBE TABLE's {@code policy name} cell and the {@code WITH MASKING POLICY} clause GET_DDL renders
 * on the column — whichever statement attached it and however little of the name that statement wrote,
 * so a policy living in another schema is reported under that schema (all live-verified).
 *
 * <p>Because the attachment is judged by policy IDENTITY and not by spelling, re-attaching the same
 * policy is a no-op under every spelling that resolves to it.
 */
public class MaskingPolicyAttachmentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE MASKING POLICY mp1 AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("CREATE SCHEMA test_db.pol_s");
        // CREATE SCHEMA makes the new schema current on live, so the session is put back explicitly.
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE MASKING POLICY pol_s.mpx AS (v VARCHAR) RETURNS VARCHAR -> '###'");
        engine.execute("CREATE TABLE mask_t (a VARCHAR WITH MASKING POLICY mp1, b VARCHAR, n NUMBER)");
    }

    private String policyCell(final String column) {
        final ResultSet described = engine.executeQuery("DESCRIBE TABLE mask_t");
        return cell(described, soleRowWhere(described, "name", column), "policy name");
    }

    private String tableDdl(final String table) {
        return engine.executeQuery("SELECT GET_DDL('TABLE', '" + table + "')")
            .getRows().get(0).getValue(0).toString();
    }

    @Test
    public void aColumnMaskedAtCreateTimeReportsItsPolicyInFull() {
        assertEquals("TEST_DB.TEST_SCHEMA.MP1", policyCell("A"));
    }

    @Test
    public void anUnmaskedColumnReportsNothing() {
        assertEquals(null, policyCell("B"));
        assertEquals(null, policyCell("N"));
    }

    @Test
    public void theAlterFormFillsTheSameCell() {
        engine.execute("ALTER TABLE mask_t ALTER COLUMN b SET MASKING POLICY mp1");
        assertEquals("TEST_DB.TEST_SCHEMA.MP1", policyCell("B"));
    }

    @Test
    public void unsetClearsTheCell() {
        engine.execute("ALTER TABLE mask_t ALTER COLUMN a UNSET MASKING POLICY");
        assertEquals(null, policyCell("A"));
    }

    /** A policy in another schema is reported under ITS schema, not the table's. */
    @Test
    public void aPolicyFromAnotherSchemaKeepsItsOwnPath() {
        engine.execute("ALTER TABLE mask_t ALTER COLUMN b SET MASKING POLICY pol_s.mpx");
        assertEquals("TEST_DB.POL_S.MPX", policyCell("B"));
    }

    /** GET_DDL puts the clause after DEFAULT and before COMMENT — live refuses the reverse order. */
    @Test
    public void getDdlRendersThePolicyBetweenDefaultAndComment() {
        engine.execute("CREATE TABLE ddl_t (a VARCHAR NOT NULL DEFAULT 'x'"
            + " WITH MASKING POLICY pol_s.mpx COMMENT 'c')");
        final String ddl = tableDdl("ddl_t");
        assertTrue(ddl.contains("NOT NULL DEFAULT 'x' WITH MASKING POLICY TEST_DB.POL_S.MPX COMMENT 'c'"), ddl);
    }

    /** Declared without the WITH, rendered back with it. */
    @Test
    public void getDdlAlwaysSpellsTheClauseWithWith() {
        engine.execute("CREATE TABLE ddl_t2 (a VARCHAR MASKING POLICY mp1)");
        assertTrue(tableDdl("ddl_t2").contains("WITH MASKING POLICY TEST_DB.TEST_SCHEMA.MP1"), tableDdl("ddl_t2"));
    }

    /**
     * The type rule, worded quite unlike the row access policy's: no column is named, and the POLICY's
     * type is spelled in the internal vocabulary — TEXT, FIXED, REAL — while the column keeps its
     * canonical SQL spelling.
     */
    @Test
    public void aTypeMismatchNamesNoColumnAndUsesTheInternalTypeName() {
        engine.execute("CREATE TABLE typ_t (s VARCHAR, n NUMBER, m NUMBER(10,2), d DATE,"
            + " ts TIMESTAMP, f FLOAT, bi BINARY, t TIME)");
        final String[][] columns = {{"n", "NUMBER(38,0)"}, {"m", "NUMBER(10,2)"}, {"d", "DATE"},
            {"ts", "TIMESTAMP_NTZ(9)"}, {"f", "FLOAT"}, {"bi", "BINARY(8388608)"}, {"t", "TIME(9)"}};
        for (final String[] column : columns) {
            final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("ALTER TABLE typ_t ALTER COLUMN " + column[0]
                        + " SET MASKING POLICY mp1");
                }
            });
            assertEquals("SQL compilation error: COLUMN data type " + column[1]
                + " does not match with masking policy data type TEXT.", ex.getMessage());
        }
    }

    /** FIXED for the exact numerics, REAL for the approximate ones. */
    @Test
    public void thePolicyTypeUsesTheInternalNames() {
        engine.execute("CREATE TABLE typ_t2 (s VARCHAR)");
        engine.execute("CREATE MASKING POLICY mp_n AS (v NUMBER) RETURNS NUMBER -> NULL");
        engine.execute("CREATE MASKING POLICY mp_f AS (v FLOAT) RETURNS FLOAT -> NULL");
        final RuntimeException fixed = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE typ_t2 ALTER COLUMN s SET MASKING POLICY mp_n");
            }
        });
        assertEquals("SQL compilation error: COLUMN data type VARCHAR(16777216) does not match with"
            + " masking policy data type FIXED.", fixed.getMessage());
        final RuntimeException real = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE typ_t2 ALTER COLUMN s SET MASKING POLICY mp_f");
            }
        });
        assertEquals("SQL compilation error: COLUMN data type VARCHAR(16777216) does not match with"
            + " masking policy data type REAL.", real.getMessage());
    }

    /** Within a family the parameters are ignored, as for a row access policy. */
    @Test
    public void aSizedColumnMatchesAnUnsizedPolicy() {
        engine.execute("CREATE TABLE typ_t3 (s20 VARCHAR(20), m NUMBER(10,2))");
        engine.execute("CREATE MASKING POLICY mp_n AS (v NUMBER) RETURNS NUMBER -> NULL");
        engine.execute("ALTER TABLE typ_t3 ALTER COLUMN s20 SET MASKING POLICY mp1");
        engine.execute("ALTER TABLE typ_t3 ALTER COLUMN m SET MASKING POLICY mp_n");
    }

    /** The CREATE TABLE clause makes the same check. */
    @Test
    public void theColumnDefinitionMakesTheSameCheck() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE bad_t (n NUMBER WITH MASKING POLICY mp1)");
            }
        });
        assertEquals("SQL compilation error: COLUMN data type NUMBER(38,0) does not match with"
            + " masking policy data type TEXT.", ex.getMessage());
    }

    /** A policy hands back what it was given — a disagreeing signature is refused at CREATE. */
    @Test
    public void anArgumentAndReturnTypeMismatchIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE MASKING POLICY mp_bad AS (v VARCHAR) RETURNS NUMBER -> 1");
            }
        });
        assertEquals("SQL compilation error: Masking policy function argument and return"
            + " type mismatch.", ex.getMessage());
    }

    @Test
    public void attachingAPolicyThatDoesNotExistIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE mask_t ALTER COLUMN b SET MASKING POLICY no_such_mp");
            }
        });
        assertEquals(hinted("SQL compilation error:\nMasking policy 'TEST_DB.TEST_SCHEMA.NO_SUCH_MP'"
            + " does not exist or not authorized."), ex.getMessage());
    }

    /** A missing SCHEMA in the policy name answers the schema's own sentence, not the policy's. */
    @Test
    public void aPolicyNamedInAMissingSchemaIsRefusedAsTheSchema() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE mask_t ALTER COLUMN b SET MASKING POLICY no_such_s.mp1");
            }
        });
        assertEquals(hinted("SQL compilation error:\nSchema 'TEST_DB.NO_SUCH_S' does not exist or not authorized."),
            ex.getMessage());
    }

    @Test
    public void theCreateTableFormMakesTheSameCheck() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE bad_t (a VARCHAR WITH MASKING POLICY no_such_mp)");
            }
        });
        assertEquals(hinted("SQL compilation error:\nMasking policy 'TEST_DB.TEST_SCHEMA.NO_SUCH_MP'"
            + " does not exist or not authorized."), ex.getMessage());
    }

    @Test
    public void detachingFromAnUnmaskedColumnIsANoOp() {
        engine.execute("ALTER TABLE mask_t ALTER COLUMN n UNSET MASKING POLICY");
        assertEquals(null, policyCell("N"));
    }

    @Test
    public void anUnknownColumnIsRefusedAtItsOwnPosition() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE mask_t ALTER COLUMN no_such_c UNSET MASKING POLICY");
            }
        });
        assertEquals("SQL compilation error: error line 1 at position 32\ninvalid identifier 'NO_SUCH_C'",
            ex.getMessage());
    }

    /** Bare, schema-qualified or in full: the same policy re-attached changes nothing. */
    @Test
    public void reAttachingTheSamePolicyIsANoOpUnderEverySpelling() {
        engine.execute("ALTER TABLE mask_t ALTER COLUMN b SET MASKING POLICY pol_s.mpx");
        engine.execute("ALTER TABLE mask_t ALTER COLUMN b SET MASKING POLICY pol_s.mpx");
        engine.execute("ALTER TABLE mask_t ALTER COLUMN b SET MASKING POLICY test_db.pol_s.mpx");
        engine.execute("USE SCHEMA test_db.pol_s");
        engine.execute("ALTER TABLE test_schema.mask_t ALTER COLUMN b SET MASKING POLICY mpx");
        engine.execute("USE SCHEMA test_db.test_schema");
        assertEquals("TEST_DB.POL_S.MPX", policyCell("B"));
    }

    /** A different policy still needs FORCE, and the cell then follows the new one. */
    @Test
    public void aDifferentPolicyNeedsForce() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE mask_t ALTER COLUMN a SET MASKING POLICY pol_s.mpx");
            }
        });
        assertTrue(ex.getMessage().contains("already attached to another masking policy"), ex.getMessage());
        engine.execute("ALTER TABLE mask_t ALTER COLUMN a SET MASKING POLICY pol_s.mpx FORCE");
        assertEquals("TEST_DB.POL_S.MPX", policyCell("A"));
    }
}
