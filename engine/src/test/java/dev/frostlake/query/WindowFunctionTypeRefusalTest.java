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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A name written WITH an {@code OVER} clause that is not a window function.
 *
 * <p>★ ONE SENTENCE FOR THREE DIFFERENT MISTAKES. An unresolvable name, an ordinary scalar and a table
 * function are all refused identically — {@code Invalid function type [NAME] for window function.} —
 * so what live judges is the name's KIND once resolved, not whether it resolves at all. Frostlake said
 * "Unsupported window function: NOSUCHFN", a wording live has nowhere.
 *
 * <p>★ THE MISSING PREFIX WAS THE REAL DEFECT. Without {@code SQL compilation error:} the refusal is
 * not a compile-time one, and that is load-bearing: it is what the view-body resolution reads to tell a
 * body that will not compile from one that merely failed while producing rows. So a VIEW over the shape
 * was CREATED, with null columns, and an empty relation answered instead of refusing. Both are fixed by
 * moving the judgement out of the row loop and into the plan, which is where live makes it.
 *
 * <p>★ IT OUTRANKS THE CLAUSE-PLACEMENT RULE. A call in WHERE reported "appears outside of SELECT,
 * QUALIFY, and ORDER BY clauses" — live reports the function type there instead, so the kind is settled
 * before the placement is.
 *
 * <p>Left for its own task: the MIRROR image, a window function used with NO over clause, which live
 * refuses as "Missing window specification for function [ROW_NUMBER()]" with the call re-printed from
 * the plan.
 */
public class WindowFunctionTypeRefusalTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE wt (a INT, b INT, t VARCHAR)");
        engine.execute("INSERT INTO wt VALUES (1, 10, 'x'), (2, 20, 'y')");
    }

    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder all = new StringBuilder("ACCEPTED:");
            while (rs.next()) {
                all.append(" ");
                for (int c = 0; c < rs.getColumns().size(); c++) {
                    if (c > 0) {
                        all.append("/");
                    }
                    all.append(String.valueOf(rs.getValue(c)));
                }
            }
            return all.toString();
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String invalidType(final String name) {
        return "SQL compilation error:|Invalid function type [" + name + "] for window function.";
    }

    /** ★ An unresolvable name with OVER is a function-TYPE complaint, not an unknown-name one. */
    @Test
    public void anUnresolvableNameWithOverIsAtypeComplaint() {
        assertEquals(invalidType("NOSUCHFN"),
            answer("SELECT nosuchfn(a) OVER (ORDER BY a) FROM wt"));
        assertEquals("SQL compilation error:|Unknown function NOSUCHFN.",
            answer("SELECT nosuchfn(a) FROM wt"),
            "★ and the SAME name without OVER keeps the unknown-name sentence — the OVER changes it");
    }

    /** Every shape of OVER clause, and an argument list or none. */
    @Test
    public void everyOverShapeIsRefusedAlike() {
        assertEquals(invalidType("NOSUCHFN"), answer("SELECT nosuchfn(a) OVER () FROM wt"));
        assertEquals(invalidType("NOSUCHFN"),
            answer("SELECT nosuchfn(a) OVER (PARTITION BY a ORDER BY b) FROM wt"));
        assertEquals(invalidType("NOSUCHFN"), answer("SELECT nosuchfn() OVER (ORDER BY a) FROM wt"));
    }

    /** ★ An ordinary SCALAR given an OVER clause is the same sentence — it is the KIND that is wrong. */
    @Test
    public void aScalarGivenAnOverClauseIsTheSameSentence() {
        assertEquals(invalidType("ABS"), answer("SELECT ABS(a) OVER (ORDER BY a) FROM wt"));
        assertEquals(invalidType("ABS"), answer("SELECT ABS(a) OVER () FROM wt"));
        assertEquals(invalidType("UPPER"), answer("SELECT UPPER(t) OVER (ORDER BY a) FROM wt"));
        assertEquals(invalidType("CURRENT_DATE"),
            answer("SELECT CURRENT_DATE() OVER (ORDER BY a) FROM wt"),
            "a no-argument scalar too");
    }

    /** ★ And so is a TABLE function. */
    @Test
    public void aTableFunctionGivenAnOverClauseIsTheSameSentence() {
        assertEquals(invalidType("FLATTEN"), answer("SELECT FLATTEN(a) OVER (ORDER BY a) FROM wt"));
        assertEquals(invalidType("SPLIT_TO_TABLE"),
            answer("SELECT SPLIT_TO_TABLE(t, ',') OVER (ORDER BY a) FROM wt"));
    }

    /** An AGGREGATE is a window function in Snowflake, and a real window function still runs. */
    @Test
    public void anAggregateAndArealWindowFunctionAreUntouched() {
        assertEquals("ACCEPTED: 10 30", answer("SELECT SUM(b) OVER (ORDER BY a) FROM wt"));
        assertEquals("ACCEPTED: 2 2", answer("SELECT COUNT(*) OVER () FROM wt"));
        assertEquals("ACCEPTED: 1 2", answer("SELECT ROW_NUMBER() OVER (ORDER BY a) FROM wt"));
    }

    /** The clause the call sits in does not change the sentence. */
    @Test
    public void everyClauseIsRefusedAlike() {
        assertEquals(invalidType("NOSUCHFN"),
            answer("SELECT a FROM wt QUALIFY nosuchfn(a) OVER (ORDER BY a) = 1"));
        assertEquals(invalidType("NOSUCHFN"),
            answer("SELECT a FROM wt ORDER BY nosuchfn(a) OVER (ORDER BY a)"));
        assertEquals(invalidType("ABS"),
            answer("SELECT a FROM wt QUALIFY ABS(a) OVER (ORDER BY a) = 1"));
    }

    /** ★ In WHERE the function TYPE speaks, not the clause-placement rule. */
    @Test
    public void inWhereTheTypeOutranksThePlacement() {
        assertEquals(invalidType("NOSUCHFN"),
            answer("SELECT a FROM wt WHERE nosuchfn(a) OVER (ORDER BY a) = 1"));
    }

    /** ★ It is a COMPILE-time refusal: no rows are needed to reach it. */
    @Test
    public void anEmptyRelationIsRefusedIdentically() {
        assertEquals(invalidType("NOSUCHFN"),
            answer("SELECT nosuchfn(a) OVER (ORDER BY a) FROM wt WHERE 1 = 0"));
        assertEquals(invalidType("ABS"),
            answer("SELECT ABS(a) OVER (ORDER BY a) FROM wt WHERE 1 = 0"));
    }

    /** ★ So a VIEW over the shape is REFUSED, where it used to be created with null columns. */
    @Test
    public void aViewOverTheShapeIsRefused() {
        assertEquals(invalidType("NOSUCHFN"), answer(
            "CREATE OR REPLACE VIEW v_wt AS SELECT nosuchfn(a) OVER (ORDER BY a) c FROM wt"));
        assertEquals(hinted("SQL compilation error:|View 'V_WT' does not exist or not authorized."),
            answer("DESCRIBE VIEW v_wt"),
            "and nothing was left behind to describe");
        assertEquals(invalidType("ABS"), answer(
            "CREATE OR REPLACE VIEW v_wt2 AS SELECT ABS(a) OVER (ORDER BY a) c FROM wt"));
    }

    /** A CTAS body is judged the same way. */
    @Test
    public void aCtasOverTheShapeIsRefused() {
        assertEquals(invalidType("NOSUCHFN"), answer(
            "CREATE OR REPLACE TABLE t_wt AS SELECT nosuchfn(a) OVER (ORDER BY a) c FROM wt"));
    }

    /** ★ The name is echoed as a refusal spells one: qualified parts kept, a quoted name verbatim. */
    @Test
    public void theNameIsEchoedTheWayARefusalSpellsOne() {
        assertEquals(invalidType("TEST_SCHEMA.NOSUCHFN"),
            answer("SELECT test_schema.nosuchfn(a) OVER (ORDER BY a) FROM wt"));
        assertEquals(invalidType("\"nosuchfn\""),
            answer("SELECT \"nosuchfn\"(a) OVER (ORDER BY a) FROM wt"),
            "a quoted name keeps its case AND its quotes, because \"a\" and a are different names");
    }
}
