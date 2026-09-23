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
 * PROJECTION POLICY — the policy kind that refuses to let a column be SELECTED rather than changing
 * what it returns. Everything here is live-verified, and the interesting part is where the line falls:
 * the column may not appear in the statement's own select list (aliased, wrapped in an expression or
 * expanded from a star), yet stays usable in a WHERE, an ORDER BY, an aggregate, and in an inner
 * select whose result the outer one does not project.
 *
 * <p>The policy takes no arguments, its body answers a {@code PROJECTION_CONSTRAINT(ALLOW => …)} —
 * which is an ordinary callable function, unlike AGGREGATION_CONSTRAINT — and the argument must be
 * named.
 */
public class ProjectionPolicyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE PROJECTION POLICY pp_allow AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        engine.execute("CREATE PROJECTION POLICY pp_deny AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => FALSE)");
        engine.execute("CREATE TABLE pj_t (a VARCHAR, b NUMBER)");
        engine.execute("INSERT INTO pj_t VALUES ('x', 1), ('y', 2)");
    }

    private void assertRestricted(final String sql, final String item) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertEquals("SQL compilation error: The following columns are restricted by a Projection"
            + " Policy. Please remove them from the list of projected columns:\n\n " + item,
            ex.getMessage());
    }

    /** Live upper-cases the offending items however they were written. */
    private void assertRestrictedItems(final String sql, final String items) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertEquals("SQL compilation error: The following columns are restricted by a Projection"
            + " Policy. Please remove them from the list of projected columns:\n" + items,
            ex.getMessage());
    }

    @Test
    public void theBodyFunctionIsCallableOnItsOwn() {
        assertEquals("{\"allow\":true,\"enforcement\":\"FAIL\"}",
            engine.executeQuery("SELECT PROJECTION_CONSTRAINT(ALLOW => TRUE)")
                .getRows().get(0).getValue(0).toString());
    }

    @Test
    public void theArgumentMustBeNamed() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT PROJECTION_CONSTRAINT(TRUE)");
            }
        });
        // Live prefixes this with a source position, which Frostlake does not carry on ANY
        // function-call refusal in a select list (SELECT LOG(10) shows the same gap), so the
        // sentence itself is what both sides are held to here.
        assertTrue(ex.getMessage().endsWith(" Invalid argument for function PROJECTION_CONSTRAINT."
            + " Please specify allow=>true or allow=>false as the input."), ex.getMessage());
    }

    @Test
    public void aPolicyWithArgumentsIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PROJECTION POLICY pp_bad AS (v VARCHAR)"
                    + " RETURNS PROJECTION_CONSTRAINT -> PROJECTION_CONSTRAINT(ALLOW => TRUE)");
            }
        });
        assertEquals("Projection policy must have exactly zero arguments, got 1 arguments.",
            ex.getMessage());
    }

    @Test
    public void anotherReturnTypeIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PROJECTION POLICY pp_b AS () RETURNS BOOLEAN -> TRUE");
            }
        });
        assertEquals("Projection policy return type 'BOOLEAN' is not PROJECTION_CONSTRAINT.",
            ex.getMessage());
    }

    @Test
    public void showAndDescribeReportThePolicy() {
        engine.execute("CREATE PROJECTION POLICY pp_c AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => TRUE) COMMENT = 'a comment'");
        final ResultSet listed = engine.executeQuery("SHOW PROJECTION POLICIES LIKE 'pp_c'");
        assertEquals("PROJECTION_POLICY", cell(listed, soleRowWhere(listed, "name", "PP_C"), "kind"));
        assertEquals("a comment", cell(listed, soleRowWhere(listed, "name", "PP_C"), "comment"));
        final ResultSet described = engine.executeQuery("DESCRIBE PROJECTION POLICY pp_c");
        assertEquals("()", described.getRows().get(0).getValue(1).toString());
        assertEquals("PROJECTION_CONSTRAINT", described.getRows().get(0).getValue(2).toString());
        assertEquals("PROJECTION_CONSTRAINT(ALLOW => TRUE)",
            described.getRows().get(0).getValue(3).toString());
    }

    @Test
    public void creatingOneTwiceIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE PROJECTION POLICY pp_allow AS () RETURNS PROJECTION_CONSTRAINT"
                    + " -> PROJECTION_CONSTRAINT(ALLOW => TRUE)");
            }
        });
        assertEquals("SQL compilation error:\nObject 'PP_ALLOW' already exists.", ex.getMessage());
    }

    @Test
    public void theBodyCanBeReplacedLater() {
        engine.execute("ALTER PROJECTION POLICY pp_allow SET BODY -> PROJECTION_CONSTRAINT(ALLOW => FALSE)");
        final ResultSet described = engine.executeQuery("DESCRIBE PROJECTION POLICY pp_allow");
        assertEquals("PROJECTION_CONSTRAINT(ALLOW => FALSE)",
            described.getRows().get(0).getValue(3).toString());
    }

    @Test
    public void attachingASecondPolicyNeedsForce() {
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_deny");
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_deny");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_allow");
            }
        });
        assertEquals("Specified column already attached to another 'PROJECTION_POLICY'."
            + "A column cannot be attached to multiple policies of same kind."
            + "please drop the current association in order to attach a new policy.", ex.getMessage());
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_allow FORCE");
    }

    @Test
    public void attachingAPolicyThatDoesNotExistIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY no_such_pp");
            }
        });
        assertEquals(hinted("SQL compilation error:\nProjection policy 'TEST_DB.TEST_SCHEMA.NO_SUCH_PP'"
            + " does not exist or not authorized."), ex.getMessage());
    }

    @Test
    public void anUnknownColumnIsRefusedAtItsOwnPosition() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pj_t ALTER COLUMN no_such_c SET PROJECTION POLICY pp_allow");
            }
        });
        assertEquals("SQL compilation error: error line 1 at position 30\ninvalid identifier 'NO_SUCH_C'",
            ex.getMessage());
    }

    @Test
    public void aDeniedColumnMayNotBeProjected() {
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_deny");
        assertRestricted("SELECT a FROM pj_t", "A");
        assertRestricted("SELECT a AS aa FROM pj_t", "A");
        assertRestricted("SELECT UPPER(a) FROM pj_t", "UPPER(A)");
        assertRestrictedItems("SELECT a, UPPER(a) FROM pj_t", "\n A\n UPPER(A)");
    }

    /** A star projects it too, and the star's items are the column names. */
    @Test
    public void aStarIsRestrictedAsWell() {
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_deny");
        assertRestricted("SELECT * FROM pj_t", "A");
    }

    /** Everything that is not a projection still works. */
    @Test
    public void theColumnStaysUsableWhereItIsNotProjected() {
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_deny");
        assertEquals("1", engine.executeQuery("SELECT b FROM pj_t WHERE a = 'x'")
            .getRows().get(0).getValue(0).toString());
        assertEquals("2", engine.executeQuery("SELECT COUNT(a) FROM pj_t")
            .getRows().get(0).getValue(0).toString());
        assertEquals("2", engine.executeQuery("SELECT COUNT(*) FROM pj_t")
            .getRows().get(0).getValue(0).toString());
        assertEquals(2, engine.executeQuery("SELECT b FROM pj_t ORDER BY a").getRows().size());
    }

    /** Only the statement's OWN select list is restricted. */
    @Test
    public void anInnerSelectMayProjectIt() {
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_deny");
        assertEquals(2, engine.executeQuery("SELECT b FROM (SELECT a, b FROM pj_t)").getRows().size());
    }

    @Test
    public void anAllowingPolicyChangesNothing() {
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_allow");
        assertEquals(2, engine.executeQuery("SELECT a FROM pj_t").getRows().size());
    }

    @Test
    public void unsettingReleasesTheColumn() {
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_deny");
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a UNSET PROJECTION POLICY");
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a UNSET PROJECTION POLICY");
        assertEquals(2, engine.executeQuery("SELECT a FROM pj_t").getRows().size());
    }

    @Test
    public void aColumnMayBeAttachedInItsDefinition() {
        engine.execute("CREATE TABLE pj_t2 (a VARCHAR WITH PROJECTION POLICY pp_deny, b NUMBER)");
        assertRestricted("SELECT a FROM pj_t2", "A");
        assertTrue(engine.executeQuery("SELECT GET_DDL('TABLE', 'pj_t2')").getRows().get(0)
            .getValue(0).toString().contains("WITH PROJECTION POLICY TEST_DB.TEST_SCHEMA.PP_DENY"));
    }

    @Test
    public void anAttachedPolicyCannotBeDropped() {
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a SET PROJECTION POLICY pp_deny");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP PROJECTION POLICY pp_deny");
            }
        });
        assertEquals("SQL compilation error: Policy PP_DENY cannot be dropped/replaced"
            + " as it is associated with one or more entities.", ex.getMessage());
        engine.execute("ALTER TABLE pj_t ALTER COLUMN a UNSET PROJECTION POLICY");
        engine.execute("DROP PROJECTION POLICY pp_deny");
    }

    /** PROJECTION is not a reserved word. */
    @Test
    public void projectionRemainsUsableAsAName() {
        engine.execute("CREATE TABLE projection (projection NUMBER)");
        engine.execute("INSERT INTO projection VALUES (7)");
        assertEquals("7", engine.executeQuery("SELECT projection FROM projection")
            .getRows().get(0).getValue(0).toString());
    }
}
