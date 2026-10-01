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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The storage tag SYSTEM$TYPEOF prints for REDUCE. The plan gives the accumulator an interval only where its
 * start is a numeric constant and the lambda hands the accumulator back as its leading value, picked by a
 * conditional or negated; the interval then survives being picked and negated again outside the call, and
 * nothing computed from it. A NULL body keeps the start's own type. Every cell is live-verified.
 */
public class ReduceAccumulatorTagTest extends BaseDatabaseTest {

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** SYSTEM$TYPEOF of REDUCE over [1, 2] from {@code start} through {@code body}. */
    private String tag(final String start, final String body) {
        return rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), " + start + ", (acc, x) -> " + body + "))");
    }

    @Test
    public void aConstantStartTagsTheAccumulatorByItsValue() {
        assertEquals("NUMBER(38,0)[SB1]", tag("1::NUMBER(5,0)", "acc"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "acc"));
        assertEquals("NUMBER(38,0)[SB4]", tag("100000::NUMBER(10,0)", "acc"));
        assertEquals("NUMBER(38,0)[SB4]", tag("-100000", "acc"));
        assertEquals("NUMBER(38,0)[SB8]", tag("2147483648", "acc"));
        assertEquals("NUMBER(38,1)[SB1]", tag("1.5", "acc"));
        assertEquals("NUMBER(38,2)[SB2]", tag("1.5::NUMBER(5,2)", "acc"));
        assertEquals("NUMBER(38,0)[SB4]", tag("1 * 100000", "acc"));
        assertEquals("NUMBER(38,0)[SB1]", tag("ABS(-7)", "acc"));
        assertEquals("NUMBER(38,0)[SB4]", tag("TO_NUMBER(100000)", "acc"));
    }

    @Test
    public void anyOtherStartKeepsTheDeclaredWidth() {
        engine.execute("CREATE OR REPLACE TABLE rat_t (k NUMBER(10,0))");
        engine.execute("INSERT INTO rat_t VALUES (100000), (200000)");
        assertEquals("NUMBER(38,0)[SB16]", tag("'5'::NUMBER", "acc"));
        assertEquals("NUMBER(38,0)[SB16]", tag("NULL::NUMBER", "acc"));
        assertEquals("NUMBER(38,0)[SB16]", tag("(SELECT 7)", "acc"));
        assertEquals("NUMBER(38,0)[SB16] | NUMBER(38,0)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), k, (acc, x) -> acc)) FROM rat_t"));
        assertEquals("NUMBER(38,0)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(REDUCE(ARRAY_CONSTRUCT(1, 2), 1, (acc NUMBER, x NUMBER) -> acc))"));
    }

    @Test
    public void aBodyLeadingWithTheAccumulatorKeepsAnInterval() {
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "(acc)"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "-(-acc)"));
        assertEquals("NUMBER(38,0)[SB4]", tag("100000", "-acc"));
        assertEquals("NUMBER(38,1)[SB1]", tag("1.5", "-acc"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "IFF(x > 5, acc, 0)"));
        assertEquals("NUMBER(38,0)[SB4]", tag("1", "IFF(x > 5, acc, 100000)"));
        assertEquals("NUMBER(38,0)[SB4]", tag("1", "IFF(x > 5, -acc, 100000)"));
        assertEquals("NUMBER(38,0)[SB4]", tag("1", "-IFF(x > 5, acc, 100000)"));
        assertEquals("NUMBER(38,1)[SB1]", tag("1", "IFF(x > 5, acc, 1.5)"));
        assertEquals("NUMBER(38,0)[SB4]", tag("1", "IFF(x > 5, acc, (SELECT 100000))"));
        assertEquals("NUMBER(38,0)[SB4]", tag("1", "IFF(x > 5, acc, '100000'::NUMBER)"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "IFF(x > 5, NULL, acc)"));
        assertEquals("NUMBER(38,0)[SB4]", tag("1", "CASE WHEN x > 5 THEN acc ELSE 100000 END"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "CASE WHEN x > 5 THEN acc END"));
        assertEquals("NUMBER(38,0)[SB4]", tag("1", "LEAST(acc, 100000)"));
        assertEquals("NUMBER(38,0)[SB4]", tag("1", "GREATEST(acc, 100000)"));
        assertEquals("NUMBER(38,0)[SB4]", tag("1", "NVL2(x, acc, 100000)"));
        assertEquals("NUMBER(38,0)[SB8]", tag("1", "COALESCE(acc, 100000000000)"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "COALESCE(NULL, acc)"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "NVL(acc, 5)"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "NULLIF(acc, 0)"));
        assertEquals("NUMBER(38,0)[SB4]", tag("100000", "IFF(x IS NULL, acc, 1)"));
    }

    /** DECODE, a simple CASE and a constant condition answer from their first branch alone. */
    @Test
    public void somePicksReadOnlyTheirFirstBranch() {
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "DECODE(x, 1, acc, 100000)"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "CASE acc WHEN 1 THEN acc ELSE 100000 END"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "IFF(TRUE, acc, 100000)"));
        assertEquals("NUMBER(38,0)[SB1]", tag("1", "CASE WHEN TRUE THEN acc ELSE 100000 END"));
    }

    @Test
    public void anyOtherBodyKeepsTheDeclaredWidth() {
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "5"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "IFF(x > 5, 5, 6)"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "IFF(acc > 0, 5, 6)"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "IFF(x > 5, 100000, acc)"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "IFF(TRUE, 100000, acc)"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "DECODE(x, 1, 100000, acc)"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "acc + 0"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "+acc"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "acc::NUMBER(38,0)"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "ABS(acc)"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "ZEROIFNULL(acc)"));
        assertEquals("NUMBER(38,0)[SB16]", tag("100000", "acc % 7"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "COALESCE(acc, acc + 1)"));
        assertEquals("NUMBER(38,0)[SB16]", tag("1", "IFF(x > 5, acc, 100000) + 0"));
    }

    @Test
    public void theIntervalIsPickedAndNegatedButNeverComputedFrom() {
        final String reduce = "REDUCE(ARRAY_CONSTRUCT(1, 2), 1, (acc, x) -> acc)";
        assertEquals("NUMBER(38,0)[SB1], NUMBER(38,0)[SB1], NUMBER(38,0)[SB16], NUMBER(38,0)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(-" + reduce + "), SYSTEM$TYPEOF(COALESCE(" + reduce + ", 0)),"
                + " SYSTEM$TYPEOF(" + reduce + " + 1), SYSTEM$TYPEOF(ABS(" + reduce + "))"));
        assertEquals("NUMBER(38,0)[SB16], NUMBER(10,0)[SB8], NUMBER(38,0)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(" + reduce + " % 7), SYSTEM$TYPEOF(" + reduce + "::NUMBER(10,0)),"
                + " SYSTEM$TYPEOF(ZEROIFNULL(" + reduce + "))"));
        assertEquals("NUMBER(38,0)[SB16]", rows("SELECT SYSTEM$TYPEOF("
            + "REDUCE(ARRAY_CONSTRUCT(1, 2), 100000::NUMBER(10,0), (acc, x) -> acc) * 1000)"));
        assertEquals("NUMBER(38,0)[SB4], NUMBER(38,0)[SB1], NUMBER(38,0)[SB1]",
            rows("SELECT SYSTEM$TYPEOF(IFF(RANDOM() > 0, " + reduce + ", 100000)), SYSTEM$TYPEOF(GREATEST("
                + reduce + ", " + reduce + ")), SYSTEM$TYPEOF(NULLIF(" + reduce + ", 0))"));
        assertEquals("NUMBER(38,0)[SB1]", rows("SELECT SYSTEM$TYPEOF(r) FROM (SELECT " + reduce + " AS r)"));
        assertEquals("NUMBER(38,0)[SB16], NUMBER(38,0)[SB1], NUMBER(38,0)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(r + 1), SYSTEM$TYPEOF(-r), SYSTEM$TYPEOF(ABS(r)) FROM (SELECT " + reduce
                + " AS r)"));
        assertEquals("NUMBER(38,0)[SB16], NUMBER(38,0)[SB1]", rows("WITH c AS (SELECT " + reduce + " AS r)"
            + " SELECT SYSTEM$TYPEOF(r * 2), SYSTEM$TYPEOF(COALESCE(r, 5)) FROM c"));
    }

    /** A SUM over the accumulator keeps its interval as it is; AVG and MEDIAN compute and keep none. */
    @Test
    public void aggregatesOverTheAccumulator() {
        final String reduce = "REDUCE(ARRAY_CONSTRUCT(1, 2), 1, (acc, x) -> acc)";
        assertEquals("NUMBER(38,0)[SB1], NUMBER(38,6)[SB16], NUMBER(38,3)[SB16]"
                + " | NUMBER(38,0)[SB1], NUMBER(38,6)[SB16], NUMBER(38,3)[SB16]",
            rows("SELECT SYSTEM$TYPEOF(SUM(r)), SYSTEM$TYPEOF(AVG(r)), SYSTEM$TYPEOF(MEDIAN(r)) FROM (SELECT "
                + reduce + " AS r UNION ALL SELECT " + reduce + ")"));
    }

    @Test
    public void aNullBodyKeepsTheStartsOwnType() {
        assertEquals("NUMBER(1,0)[SB1]", tag("1", "NULL"));
        assertEquals("NUMBER(2,1)[SB1]", tag("1.5", "NULL"));
        assertEquals("NUMBER(6,0)[SB4]", tag("100000", "NULL"));
        assertEquals("VARCHAR(2)[LOB]", tag("'ab'", "NULL"));
        assertEquals("VARIANT[LOB]", tag("NULL", "NULL"));
        assertEquals("NUMBER(1,0)[SB1]", tag("1", "IFF(TRUE, NULL, NULL)"));
        assertEquals("NUMBER(1,0)[SB1]", tag("1", "COALESCE(NULL, NULL)"));
        assertEquals("null", rows("SELECT REDUCE(ARRAY_CONSTRUCT(1, 2), 100000, (acc, x) -> NULL)"));
    }
}
