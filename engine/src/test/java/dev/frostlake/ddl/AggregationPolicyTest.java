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

/**
 * AGGREGATION POLICY — attached to a TABLE, it forbids reading rows one at a time. A query must
 * aggregate, may not use an aggregate that hands back one row's value, and sees groups below the
 * policy's minimum folded into a single remainder group whose keys read NULL.
 *
 * <p>Every rule here is measured, including the two that reasoning would get wrong: STDDEV is allowed
 * while VARIANCE is not, and ENTITY KEY makes the minimum count DISTINCT ENTITIES rather than rows.
 */
public class AggregationPolicyTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE AGGREGATION POLICY ap_3 AS () RETURNS AGGREGATION_CONSTRAINT"
            + " -> AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 3)");
        engine.execute("CREATE AGGREGATION POLICY ap_none AS () RETURNS AGGREGATION_CONSTRAINT"
            + " -> NO_AGGREGATION_CONSTRAINT()");
        engine.execute("CREATE TABLE ag_t (dept VARCHAR, sal NUMBER, eid NUMBER)");
        engine.execute("INSERT INTO ag_t VALUES ('a',10,1),('a',20,2),('a',30,3),('a',40,4),"
            + "('b',50,5),('b',60,6),('c',70,7),('c',80,8)");
    }

    private String rowsOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        for (final Row row : rs.getRows()) {
            for (int i = 0; i < rs.getColumns().size(); i++) {
                out.append(row.getValue(i)).append('|');
            }
            out.append(';');
        }
        return out.toString();
    }

    @Test
    public void aPolicyWithArgumentsIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE AGGREGATION POLICY ap_bad AS (v VARCHAR)"
                    + " RETURNS AGGREGATION_CONSTRAINT -> NO_AGGREGATION_CONSTRAINT()");
            }
        });
        assertEquals("Aggregation policy must have exactly zero arguments, got 1 arguments.",
            ex.getMessage());
    }

    @Test
    public void anotherReturnTypeIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("CREATE AGGREGATION POLICY ap_b AS () RETURNS BOOLEAN -> TRUE");
            }
        });
        assertEquals("Aggregation policy return type 'BOOLEAN' is not AGGREGATION_CONSTRAINT.",
            ex.getMessage());
    }

    /**
     * One of the two constraint functions is body-only. Its counterpart NO_AGGREGATION_CONSTRAINT()
     * IS callable anywhere and answers an empty object — measured directly against the account, but
     * not asserted here: the JDBC harness cannot marshal that OBJECT result (data type 1111).
     */
    @Test
    public void onlyTheNoConstraintFunctionIsCallableOnItsOwn() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 3)");
            }
        });
        assertTrue(ex.getMessage().endsWith("The AGGREGATION_CONSTRAINT function can only be called"
            + " from a body of an aggregation constraint policy."), ex.getMessage());
    }

    @Test
    public void showAndDescribeReportThePolicy() {
        final ResultSet listed = engine.executeQuery("SHOW AGGREGATION POLICIES LIKE 'ap_3'");
        assertEquals("AGGREGATION_POLICY", cell(listed, soleRowWhere(listed, "name", "AP_3"), "kind"));
        final ResultSet described = engine.executeQuery("DESCRIBE AGGREGATION POLICY ap_3");
        assertEquals("()", described.getRows().get(0).getValue(1).toString());
        assertEquals("AGGREGATION_CONSTRAINT", described.getRows().get(0).getValue(2).toString());
        assertEquals("AGGREGATION_CONSTRAINT(MIN_GROUP_SIZE => 3)",
            described.getRows().get(0).getValue(3).toString());
    }

    @Test
    public void attachingASecondPolicyNeedsForce() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_none");
            }
        });
        assertEquals("Object AG_T already has a AGGREGATION_POLICY."
            + " Only one AGGREGATION_POLICY is allowed at a time.", ex.getMessage());
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_none FORCE");
    }

    @Test
    public void attachingAPolicyThatDoesNotExistIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY no_such_ap");
            }
        });
        assertEquals("SQL compilation error:\nAggregation policy 'TEST_DB.TEST_SCHEMA.NO_SUCH_AP'"
            + " does not exist or not authorized.", ex.getMessage());
    }

    /** Detaching when nothing is attached is an ERROR here — the opposite of a projection policy. */
    @Test
    public void unsettingWhenNothingIsAttachedIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE ag_t UNSET AGGREGATION POLICY");
            }
        });
        assertEquals("Any policy of kind AGGREGATION_POLICY is not attached to TABLE AG_T.",
            ex.getMessage());
    }

    @Test
    public void readingRowsIsRefused() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        final RuntimeException star = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM ag_t");
            }
        });
        assertEquals("SQL compilation error: Aggregation policy violation: aggregation required.",
            star.getMessage());
        final RuntimeException column = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT dept FROM ag_t");
            }
        });
        assertEquals("SQL compilation error: Aggregation policy violation: aggregation required.",
            column.getMessage());
    }

    @Test
    public void anAggregateOverTheWholeTableIsAllowed() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        assertEquals("8", engine.executeQuery("SELECT COUNT(*) FROM ag_t")
            .getRows().get(0).getValue(0).toString());
        assertEquals("360", engine.executeQuery("SELECT SUM(sal) FROM ag_t")
            .getRows().get(0).getValue(0).toString());
    }

    /** Groups under the floor are folded into one remainder row whose key reads NULL. */
    @Test
    public void smallGroupsAreFoldedIntoARemainder() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        assertEquals("a|4|;null|4|;", rowsOf("SELECT dept, COUNT(*) FROM ag_t GROUP BY dept ORDER BY dept"));
        assertEquals("a|100|;null|260|;", rowsOf("SELECT dept, SUM(sal) FROM ag_t GROUP BY dept ORDER BY dept"));
    }

    /** DISTINCT folds exactly as GROUP BY does — the same floor, over the distinct values. */
    @Test
    public void distinctFoldsTheSameWay() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        assertEquals("a|;null|;", rowsOf("SELECT DISTINCT dept FROM ag_t ORDER BY dept"));
    }

    /** Every key column of a folded multi-column DISTINCT reads NULL. */
    @Test
    public void aMultiColumnDistinctNullsEveryKey() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        assertEquals("null|null|;", rowsOf("SELECT DISTINCT dept, sal FROM ag_t ORDER BY dept, sal"));
    }

    /** The remainder row is emitted even when the folded rows themselves fall short. */
    @Test
    public void distinctEmitsTheRemainderRowRegardless() {
        engine.execute("CREATE TABLE ag_s (dept VARCHAR)");
        engine.execute("INSERT INTO ag_s VALUES ('a'),('a'),('a'),('a'),('b')");
        engine.execute("ALTER TABLE ag_s SET AGGREGATION POLICY ap_3");
        assertEquals("a|;null|;", rowsOf("SELECT DISTINCT dept FROM ag_s ORDER BY dept"));
    }

    /** An ENTITY KEY counts entities for DISTINCT too, even when the projection drops that column. */
    @Test
    public void distinctCountsEntitiesWhenAKeyNamesThem() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3 ENTITY KEY (dept)");
        assertEquals("null|;", rowsOf("SELECT DISTINCT dept FROM ag_t ORDER BY dept"));
    }

    /** A grouped query needs no aggregate at all, and folds the same way. */
    @Test
    public void aGroupedQueryWithoutAnAggregateFoldsToo() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        assertEquals("a|;null|;", rowsOf("SELECT dept FROM ag_t GROUP BY dept ORDER BY dept"));
    }

    /** ENTITY KEY counts DISTINCT entities: one dept per group is below a floor of three. */
    @Test
    public void anEntityKeyChangesWhatCounts() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3 ENTITY KEY (dept)");
        assertEquals("null|8|;", rowsOf("SELECT dept, COUNT(*) FROM ag_t GROUP BY dept ORDER BY dept"));
    }

    @Test
    public void theAggregatesThatWouldLeakARowAreRefused() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        for (final String call : new String[] {"MIN(sal)", "MAX(sal)", "MEDIAN(sal)",
                                               "VARIANCE(sal)", "ARRAY_AGG(sal)"}) {
            final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT " + call + " FROM ag_t");
                }
            });
            assertEquals("SQL compilation error: " + call.toUpperCase() + " violated Aggregation Policy.",
                ex.getMessage());
        }
    }

    /** Measured, not reasoned: STDDEV passes where VARIANCE does not. */
    @Test
    public void stddevIsAllowed() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        assertEquals(1, engine.executeQuery("SELECT STDDEV(sal) FROM ag_t").getRows().size());
    }

    /** Only the statement's own select is judged — an inner one may read rows. */
    @Test
    public void anInnerSelectMayReadRows() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        assertEquals("8", engine.executeQuery("SELECT COUNT(*) FROM (SELECT * FROM ag_t)")
            .getRows().get(0).getValue(0).toString());
    }

    @Test
    public void aPolicyThatConstrainsNothingLetsEverythingThrough() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_none");
        assertEquals(8, engine.executeQuery("SELECT dept FROM ag_t").getRows().size());
    }

    @Test
    public void getDdlRendersTheAttachment() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3 ENTITY KEY (eid)");
        final String ddl = engine.executeQuery("SELECT GET_DDL('TABLE', 'ag_t')")
            .getRows().get(0).getValue(0).toString();
        assertTrue(ddl.contains("WITH AGGREGATION POLICY TEST_DB.TEST_SCHEMA.AP_3 ENTITY KEY (EID)"), ddl);
    }

    @Test
    public void anAttachedPolicyCannotBeDropped() {
        engine.execute("ALTER TABLE ag_t SET AGGREGATION POLICY ap_3");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("DROP AGGREGATION POLICY ap_3");
            }
        });
        assertEquals("SQL compilation error: Policy AP_3 cannot be dropped/replaced"
            + " as it is associated with one or more entities.", ex.getMessage());
    }

    @Test
    public void aTableMayCarryOneFromTheStart() {
        engine.execute("CREATE TABLE ag_t2 (n NUMBER) WITH AGGREGATION POLICY ap_3");
        engine.execute("INSERT INTO ag_t2 VALUES (1), (2), (3)");
        assertEquals("3", engine.executeQuery("SELECT COUNT(*) FROM ag_t2")
            .getRows().get(0).getValue(0).toString());
    }

    /** Neither AGGREGATION nor ENTITY is a reserved word. */
    @Test
    public void theNewKeywordsRemainUsableAsNames() {
        engine.execute("CREATE TABLE aggregation (aggregation NUMBER, entity NUMBER)");
        engine.execute("INSERT INTO aggregation VALUES (1, 2)");
        assertEquals("1", engine.executeQuery("SELECT aggregation FROM aggregation")
            .getRows().get(0).getValue(0).toString());
    }
}
