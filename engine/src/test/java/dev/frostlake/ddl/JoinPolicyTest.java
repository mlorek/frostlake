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
 * JOIN POLICY — the table it guards may not be read on its own: a query must INNER join it to a
 * DIFFERENT table on an equality between their columns.
 *
 * <p>Live answers three distinct sentences and this pins each: no join and an OUTER join are the
 * generic violation, a join that cannot satisfy the constraint (comma join, constant ON) names the
 * unsatisfied constraint, and joining the table to itself is called a potential cross-join. Unlike
 * the projection and aggregation policies, an INNER select gets no exemption.
 */
public class JoinPolicyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE JOIN POLICY jp_req AS () RETURNS JOIN_CONSTRAINT"
            + " -> JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
        engine.execute("CREATE JOIN POLICY jp_free AS () RETURNS JOIN_CONSTRAINT"
            + " -> JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)");
        engine.execute("CREATE TABLE jn_t (id NUMBER, v VARCHAR)");
        engine.execute("CREATE TABLE jn_u (id NUMBER, w VARCHAR)");
        engine.execute("INSERT INTO jn_t VALUES (1,'a'),(2,'b')");
        engine.execute("INSERT INTO jn_u VALUES (1,'x'),(2,'y')");
    }

    private void assertRefused(final String sql, final String sentence) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertEquals("SQL compilation error: " + sentence, ex.getMessage());
    }

    @Test
    public void aPolicyWithArgumentsIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE JOIN POLICY jp_bad AS (v VARCHAR) RETURNS JOIN_CONSTRAINT"
                    + " -> JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)");
            }
        });
        assertEquals("Join policy must have exactly zero arguments, got 1 arguments.", ex.getMessage());
    }

    @Test
    public void anotherReturnTypeIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE JOIN POLICY jp_b AS () RETURNS BOOLEAN -> TRUE");
            }
        });
        assertEquals("Join policy return type 'BOOLEAN' is not JOIN_CONSTRAINT.", ex.getMessage());
    }

    /**
     * The constraint function is callable outside a policy body — unlike AGGREGATION_CONSTRAINT — and
     * answers {@code true}, measured directly against the account. It is not asserted through the
     * JDBC harness, which cannot marshal what live reports for it (data type 1111); what IS asserted
     * two-sided is that a body built on it decides the policy, which every enforcement case below does.
     */
    @Test
    public void aPolicyBodyDecidesFromTheConstraintFunction() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_free");
        assertEquals(2, engine.executeQuery("SELECT * FROM jn_t").getRows().size());
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_req FORCE");
        assertRefused("SELECT * FROM jn_t",
            "Join Policy violation, please contact the policy admin for details");
    }

    @Test
    public void showAndDescribeReportThePolicy() {
        final ResultSet listed = engine.executeQuery("SHOW JOIN POLICIES LIKE 'jp_req'");
        assertEquals("JOIN_POLICY", cell(listed, soleRowWhere(listed, "name", "JP_REQ"), "kind"));
        final ResultSet described = engine.executeQuery("DESCRIBE JOIN POLICY jp_req");
        assertEquals("()", described.getRows().get(0).getValue(1).toString());
        assertEquals("JOIN_CONSTRAINT", described.getRows().get(0).getValue(2).toString());
        assertEquals("JOIN_CONSTRAINT(JOIN_REQUIRED => TRUE)",
            described.getRows().get(0).getValue(3).toString());
    }

    @Test
    public void attachingASecondPolicyNeedsForce() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_req");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_free");
            }
        });
        assertEquals("Object JN_T already has a JOIN_POLICY. Only one JOIN_POLICY is allowed"
            + " at a time.", ex.getMessage());
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_free FORCE");
    }

    @Test
    public void attachingAPolicyThatDoesNotExistIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE jn_t SET JOIN POLICY no_such_jp");
            }
        });
        assertEquals("SQL compilation error:\nJoin policy 'TEST_DB.TEST_SCHEMA.NO_SUCH_JP'"
            + " does not exist or not authorized.", ex.getMessage());
    }

    @Test
    public void unsettingWhenNothingIsAttachedIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE jn_t UNSET JOIN POLICY");
            }
        });
        assertEquals("Any policy of kind JOIN_POLICY is not attached to TABLE JN_T.", ex.getMessage());
    }

    @Test
    public void anInnerJoinOnColumnsIsWhatItWants() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_req");
        assertEquals(2, engine.executeQuery(
            "SELECT t.v, u.w FROM jn_t t JOIN jn_u u ON t.id = u.id").getRows().size());
    }

    @Test
    public void readingTheTableAloneIsRefused() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_req");
        assertRefused("SELECT * FROM jn_t",
            "Join Policy violation, please contact the policy admin for details");
        assertRefused("SELECT COUNT(*) FROM jn_t",
            "Join Policy violation, please contact the policy admin for details");
    }

    /** An outer join does not satisfy it, and earns the generic sentence rather than the join one. */
    @Test
    public void anOuterJoinIsRefused() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_req");
        assertRefused("SELECT t.v FROM jn_t t LEFT JOIN jn_u u ON t.id = u.id",
            "Join Policy violation, please contact the policy admin for details");
    }

    @Test
    public void aJoinThatCannotSatisfyItNamesTheConstraint() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_req");
        assertRefused("SELECT t.v FROM jn_t t, jn_u u",
            "Join Policy violation, invalid join condition with reason: Unsatisfied constraint(s).");
        assertRefused("SELECT t.v FROM jn_t t JOIN jn_u u ON 1 = 1",
            "Join Policy violation, invalid join condition with reason: Unsatisfied constraint(s).");
    }

    @Test
    public void joiningTheTableToItselfIsACrossJoin() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_req");
        assertRefused("SELECT a.v FROM jn_t a JOIN jn_t b ON a.id = b.id",
            "Join Policy violation, invalid join condition with reason: Potential cross-join.");
    }

    /** No exemption for an inner select — the policy judges every select that reads the table. */
    @Test
    public void anInnerSelectIsJudgedToo() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_req");
        assertRefused("SELECT COUNT(*) FROM (SELECT * FROM jn_t)",
            "Join Policy violation, please contact the policy admin for details");
    }

    @Test
    public void aPolicyThatRequiresNothingLetsEverythingThrough() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_free");
        assertEquals(2, engine.executeQuery("SELECT * FROM jn_t").getRows().size());
    }

    @Test
    public void getDdlRendersTheAttachment() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_req");
        final String ddl = engine.executeQuery("SELECT GET_DDL('TABLE', 'jn_t')")
            .getRows().get(0).getValue(0).toString();
        assertTrue(ddl.contains("WITH JOIN POLICY TEST_DB.TEST_SCHEMA.JP_REQ"), ddl);
    }

    @Test
    public void anAttachedPolicyCannotBeDropped() {
        engine.execute("ALTER TABLE jn_t SET JOIN POLICY jp_req");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP JOIN POLICY jp_req");
            }
        });
        assertEquals("SQL compilation error: Policy JP_REQ cannot be dropped/replaced"
            + " as it is associated with one or more entities.", ex.getMessage());
    }

    @Test
    public void aTableMayCarryOneFromTheStart() {
        engine.execute("CREATE TABLE jn_t3 (id NUMBER) WITH JOIN POLICY jp_free");
        engine.execute("INSERT INTO jn_t3 VALUES (1)");
        assertEquals(1, engine.executeQuery("SELECT * FROM jn_t3").getRows().size());
    }
}
