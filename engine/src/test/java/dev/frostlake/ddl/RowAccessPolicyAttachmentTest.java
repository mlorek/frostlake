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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Attaching and detaching a ROW ACCESS POLICY. An object carries at most one, so every statement on
 * this surface can refuse, and the refusals are ordered: the policy must resolve, the ON columns must
 * resolve, their count must equal the policy's parameter count, and only then does the one-policy rule
 * apply. All of it is live-verified, including two oddities — re-attaching the SAME policy is refused
 * (a repeated SET MASKING POLICY is not), and the not-attached sentence says TABLE even for a view.
 */
public class RowAccessPolicyAttachmentTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE pol_t (id INT, dept VARCHAR)");
        engine.execute("CREATE TABLE typ_t (s VARCHAR, s20 VARCHAR(20), n NUMBER, m NUMBER(10,2),"
            + " d DATE, b BOOLEAN, ts TIMESTAMP, lt TIMESTAMP_LTZ, f FLOAT)");
        engine.execute("CREATE VIEW pol_v AS SELECT * FROM pol_t");
        engine.execute("CREATE ROW ACCESS POLICY rap1 AS (dept VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("CREATE ROW ACCESS POLICY rap2 AS (a VARCHAR, b INT) RETURNS BOOLEAN -> TRUE");
    }

    /** A second ADD is refused only while one is attached, so it doubles as the readback. */
    private void assertAttached(final String object, final boolean attached) {
        final String add = "ALTER " + ("pol_v".equals(object) ? "VIEW " : "TABLE ") + object
            + " ADD ROW ACCESS POLICY rap1 ON (dept)";
        if (attached) {
            final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute(add);
                }
            });
            assertEquals("Object " + object.toUpperCase() + " already has a ROW_ACCESS_POLICY."
                + " Only one ROW_ACCESS_POLICY is allowed at a time.", ex.getMessage());
        } else {
            engine.execute(add);
            engine.execute("ALTER " + ("pol_v".equals(object) ? "VIEW " : "TABLE ") + object
                + " DROP ROW ACCESS POLICY rap1");
        }
    }

    @Test
    public void dropAllDetachesTheAttachedPolicy() {
        engine.execute("ALTER TABLE pol_t ADD ROW ACCESS POLICY rap1 ON (dept)");
        assertAttached("pol_t", true);
        engine.execute("ALTER TABLE pol_t DROP ALL ROW ACCESS POLICIES");
        assertAttached("pol_t", false);
    }

    @Test
    public void dropAllIsANoOpWhenNothingIsAttached() {
        engine.execute("ALTER TABLE pol_t DROP ALL ROW ACCESS POLICIES");
        engine.execute("ALTER TABLE pol_t DROP ALL ROW ACCESS POLICIES");
        assertAttached("pol_t", false);
    }

    @Test
    public void dropAllDetachesAViewsPolicy() {
        engine.execute("ALTER VIEW pol_v ADD ROW ACCESS POLICY rap1 ON (dept)");
        assertAttached("pol_v", true);
        engine.execute("ALTER VIEW pol_v DROP ALL ROW ACCESS POLICIES");
        assertAttached("pol_v", false);
    }

    /** Only the plural spelling parses: live stops at the POLICY token, not at ALL. */
    @Test
    public void theSingularSpellingIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pol_t DROP ALL ROW ACCESS POLICY");
            }
        });
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 38 unexpected 'POLICY'.",
            ex.getMessage());
    }

    @Test
    public void addRefusesAPolicyThatDoesNotExist() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pol_t ADD ROW ACCESS POLICY no_such_p ON (dept)");
            }
        });
        assertEquals(hinted("SQL compilation error:\nRow access policy 'TEST_DB.TEST_SCHEMA.NO_SUCH_P'"
            + " does not exist or not authorized."), ex.getMessage());
    }

    @Test
    public void addRefusesAnUnknownColumnAtItsOwnPosition() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pol_t ADD ROW ACCESS POLICY rap1 ON (no_such_c)");
            }
        });
        assertEquals("SQL compilation error: error line 1 at position 49\ninvalid identifier 'NO_SUCH_C'",
            ex.getMessage());
    }

    @Test
    public void addRefusesTooManyColumns() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pol_t ADD ROW ACCESS POLICY rap1 ON (dept, id)");
            }
        });
        assertEquals("SQL compilation error:\nInvalid number of arguments for attaching policy 'RAP1'"
            + " to 'POL_T', expected 1 arguments, got 2 arguments.", ex.getMessage());
    }

    @Test
    public void addRefusesTooFewColumns() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pol_t ADD ROW ACCESS POLICY rap2 ON (dept)");
            }
        });
        assertEquals("SQL compilation error:\nInvalid number of arguments for attaching policy 'RAP2'"
            + " to 'POL_T', expected 2 arguments, got 1 arguments.", ex.getMessage());
    }

    /** One at a time — and unlike a masking policy, re-attaching the same one is not a no-op. */
    @Test
    public void addRefusesASecondPolicyEvenWhenItIsTheSameOne() {
        engine.execute("ALTER TABLE pol_t ADD ROW ACCESS POLICY rap1 ON (dept)");
        assertAttached("pol_t", true);
    }

    @Test
    public void dropRefusesAPolicyThatIsNotTheAttachedOne() {
        engine.execute("ALTER TABLE pol_t ADD ROW ACCESS POLICY rap1 ON (dept)");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pol_t DROP ROW ACCESS POLICY rap2");
            }
        });
        assertEquals("Policy RAP2 is not attached to TABLE POL_T.", ex.getMessage());
    }

    @Test
    public void dropRefusesWhenNothingIsAttached() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pol_t DROP ROW ACCESS POLICY rap1");
            }
        });
        assertEquals("Policy RAP1 is not attached to TABLE POL_T.", ex.getMessage());
    }

    /** The policy is resolved before the attachment is looked at, on DROP as well as ADD. */
    @Test
    public void dropRefusesAPolicyThatDoesNotExist() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE pol_t DROP ROW ACCESS POLICY no_such_p");
            }
        });
        assertEquals(hinted("SQL compilation error:\nRow access policy 'TEST_DB.TEST_SCHEMA.NO_SUCH_P'"
            + " does not exist or not authorized."), ex.getMessage());
    }

    /** A view is named as a TABLE in this one sentence — measured, not a typo. */
    @Test
    public void aViewIsCalledATableWhenItsDropFindsNothingAttached() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER VIEW pol_v DROP ROW ACCESS POLICY rap1");
            }
        });
        assertEquals("Policy RAP1 is not attached to TABLE POL_V.", ex.getMessage());
    }

    @Test
    public void aQualifiedPolicyNameDropsTheSameAttachment() {
        engine.execute("ALTER TABLE pol_t ADD ROW ACCESS POLICY rap1 ON (dept)");
        engine.execute("ALTER TABLE pol_t DROP ROW ACCESS POLICY test_db.test_schema.rap1");
        assertAttached("pol_t", false);
    }

    /** The column and the policy argument must be of one type family, and the message spells both. */
    @Test
    public void aTypeMismatchIsRefusedAndNamesBothTypes() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE typ_t ADD ROW ACCESS POLICY rap1 ON (n)");
            }
        });
        assertEquals("SQL compilation error: Column 'N' data type 'NUMBER(38,0)' does not match with"
            + " Row access policy data type 'VARCHAR(134217728)'.", ex.getMessage());
    }

    /** An unsized VARCHAR is 128MB wide in a POLICY signature, not the column default. */
    @Test
    public void everyOtherTypeIsSpelledCanonically() {
        final String[][] columns = {{"d", "DATE"}, {"b", "BOOLEAN"}, {"ts", "TIMESTAMP_NTZ(9)"},
            {"lt", "TIMESTAMP_LTZ(9)"}, {"f", "FLOAT"}, {"m", "NUMBER(10,2)"}};
        for (final String[] column : columns) {
            final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("ALTER TABLE typ_t ADD ROW ACCESS POLICY rap1 ON (" + column[0] + ")");
                }
            });
            assertEquals("SQL compilation error: Column '" + column[0].toUpperCase() + "' data type '"
                + column[1] + "' does not match with Row access policy data type"
                + " 'VARCHAR(134217728)'.", ex.getMessage());
        }
    }

    /** Within a family the parameters are not compared at all. */
    @Test
    public void aSizedColumnStillMatchesAnUnsizedArgument() {
        engine.execute("ALTER TABLE typ_t ADD ROW ACCESS POLICY rap1 ON (s20)");
        engine.execute("ALTER TABLE typ_t DROP ALL ROW ACCESS POLICIES");
        engine.execute("CREATE ROW ACCESS POLICY rap_n AS (v NUMBER) RETURNS BOOLEAN -> TRUE");
        engine.execute("ALTER TABLE typ_t ADD ROW ACCESS POLICY rap_n ON (m)");
    }

    /** FLOAT and NUMBER are different families, however numeric they both are. */
    @Test
    public void numberAndFloatDoNotMatch() {
        engine.execute("CREATE ROW ACCESS POLICY rap_n AS (v NUMBER) RETURNS BOOLEAN -> TRUE");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE typ_t ADD ROW ACCESS POLICY rap_n ON (f)");
            }
        });
        assertEquals("SQL compilation error: Column 'F' data type 'FLOAT' does not match with"
            + " Row access policy data type 'NUMBER(38,0)'.", ex.getMessage());
    }

    @Test
    public void aMaskedColumnCannotBeAPolicyArgument() {
        engine.execute("CREATE MASKING POLICY mp_s AS (v VARCHAR) RETURNS VARCHAR -> '***'");
        engine.execute("ALTER TABLE typ_t ALTER COLUMN s SET MASKING POLICY mp_s");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE typ_t ADD ROW ACCESS POLICY rap1 ON (s)");
            }
        });
        assertEquals("SQL compilation error: Column 'S' cannot be used as policy argument because"
            + " it is masked by another policy.", ex.getMessage());
    }

    /** The projection-policy twin of the masked-column rule — and it carries no prefix. */
    @Test
    public void aProjectedColumnCannotBeAPolicyArgumentEither() {
        engine.execute("CREATE PROJECTION POLICY pp_s AS () RETURNS PROJECTION_CONSTRAINT"
            + " -> PROJECTION_CONSTRAINT(ALLOW => TRUE)");
        engine.execute("ALTER TABLE typ_t ALTER COLUMN s SET PROJECTION POLICY pp_s");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE typ_t ADD ROW ACCESS POLICY rap1 ON (s)");
            }
        });
        assertEquals("Column 'S' cannot be used as policy argument because it has a projection"
            + " policy attached.", ex.getMessage());
    }

    @Test
    public void theCreateTableClauseMakesTheSameChecks() {
        final RuntimeException unknownPolicy = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE c1 (a VARCHAR) WITH ROW ACCESS POLICY no_such ON (a)");
            }
        });
        assertEquals(hinted("SQL compilation error:\nRow access policy 'TEST_DB.TEST_SCHEMA.NO_SUCH'"
            + " does not exist or not authorized."), unknownPolicy.getMessage());
        final RuntimeException arity = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE c3 (a VARCHAR, b VARCHAR)"
                    + " WITH ROW ACCESS POLICY rap1 ON (a, b)");
            }
        });
        assertEquals("SQL compilation error:\nInvalid number of arguments for attaching policy 'RAP1'"
            + " to 'C3', expected 1 arguments, got 2 arguments.", arity.getMessage());
        final RuntimeException mismatch = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE c4 (a NUMBER) WITH ROW ACCESS POLICY rap1 ON (a)");
            }
        });
        assertEquals("SQL compilation error: Column 'A' data type 'NUMBER(38,0)' does not match with"
            + " Row access policy data type 'VARCHAR(134217728)'.", mismatch.getMessage());
        engine.execute("CREATE TABLE c5 (a VARCHAR) WITH ROW ACCESS POLICY rap1 ON (a)");
    }

    /** The one difference between the paths: CREATE words an unknown column its own way. */
    @Test
    public void anUnknownColumnIsWordedDifferentlyAtCreateTime() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE TABLE c2 (a VARCHAR) WITH ROW ACCESS POLICY rap1 ON (no_such_c)");
            }
        });
        assertEquals("SQL compilation error:\ncolumn 'NO_SUCH_C' does not exist", ex.getMessage());
    }

    @Test
    public void theCreateViewClauseChecksThePolicyToo() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE VIEW vw1 WITH ROW ACCESS POLICY no_such ON (dept)"
                    + " AS SELECT dept FROM pol_t");
            }
        });
        assertEquals(hinted("SQL compilation error:\nRow access policy 'TEST_DB.TEST_SCHEMA.NO_SUCH'"
            + " does not exist or not authorized."), ex.getMessage());
    }

    /** A fully qualified policy is looked up in ITS database, not in whichever one is current. */
    @Test
    public void aPolicyInAnotherDatabaseAttachesByItsFullName() {
        engine.execute("CREATE OR REPLACE DATABASE fl_rap_other_db");
        engine.execute("CREATE OR REPLACE SCHEMA fl_rap_other_db.utils");
        engine.execute("CREATE OR REPLACE ROW ACCESS POLICY fl_rap_other_db.utils.rap_x"
            + " AS (dept VARCHAR) RETURNS BOOLEAN -> TRUE");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("ALTER TABLE pol_t ADD ROW ACCESS POLICY fl_rap_other_db.utils.rap_x ON (dept)");
        assertAttached("pol_t", true);
        engine.execute("ALTER TABLE pol_t DROP ROW ACCESS POLICY fl_rap_other_db.utils.rap_x");
        assertAttached("pol_t", false);
        engine.execute("DROP DATABASE IF EXISTS fl_rap_other_db");
    }
}
