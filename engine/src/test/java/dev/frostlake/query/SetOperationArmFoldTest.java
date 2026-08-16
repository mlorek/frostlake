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
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A set operation DECLARES the type its arms fold to, not the type of the arm that happens to be
 * written first. Frostlake computed the fold and then reported the leading arm anyway, so
 * {@code v UNION w} claimed VARCHAR(5) over a column that returns a nine-character string.
 *
 * <p>The arms fold by nearly the same rule as a conditional's branches, and the two are measured
 * together for that reason — but they are NOT the same rule, and three cells prove it:
 *
 * <pre>
 *   COALESCE(v, bo)   VARCHAR(134217728)   v UNION bo   VARCHAR(5)
 *   COALESCE(v, NULL) VARCHAR(134217728)   v UNION NULL VARCHAR(5)
 *   COALESCE(n, bo)   BOOLEAN              n UNION bo   refused outright
 * </pre>
 *
 * <p>So the unknown-length widening belongs to branches alone: an ARM keeps the width it declares.
 */
public class SetOperationArmFoldTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE sf (i INT, n NUMBER(10,2), m NUMBER(5,4), f FLOAT,"
            + " v VARCHAR(5), w VARCHAR(9), d DATE, ts TIMESTAMP_NTZ, tl TIMESTAMP_LTZ,"
            + " tz TIMESTAMP_TZ, tm TIME, bo BOOLEAN, bn BINARY(5))");
        engine.execute("INSERT INTO sf SELECT 1, 2.50, 0.5, 3.5, 'ab', 'cdefg', '2020-01-01',"
            + " '2020-01-01 10:00:00', '2020-01-01 10:00:00', '2020-01-01 10:00:00',"
            + " '10:00:00', TRUE, TO_BINARY('AB')");
    }

    /** The declared type of a query's first column, with the parameters that carry meaning. */
    private String typeOf(final String sql) {
        final DataType type = engine.executeQuery(sql).getColumns().get(0).getDataType();
        if (type == null) {
            return "null";
        }
        if (type instanceof NumericType && "NUMBER".equalsIgnoreCase(type.getName())) {
            return "NUMBER(" + ((NumericType) type).getPrecision() + ","
                + ((NumericType) type).getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        if (type instanceof BinaryType) {
            return type.getName() + "(" + ((BinaryType) type).getMaxLength() + ")";
        }
        return type.getName();
    }

    /** The type two arms fold to under the given operator. */
    private String armFold(final String operator, final String left, final String right) {
        return typeOf("SELECT " + left + " FROM sf " + operator + " SELECT " + right + " FROM sf");
    }

    /** A string arm takes the widest declared width, whichever side declares it. */
    @Test
    public void aStringArmTakesTheWidestWidth() {
        assertEquals("VARCHAR(9)", armFold("UNION", "v", "w"));
        assertEquals("VARCHAR(9)", armFold("UNION", "w", "v"));
        assertEquals("VARCHAR(9)", armFold("UNION ALL", "v", "w"));
        assertEquals("VARCHAR(9)", armFold("INTERSECT", "v", "w"));
        assertEquals("VARCHAR(9)", armFold("EXCEPT", "v", "w"));
    }

    /** A number takes the widest integer part beside the widest scale — never max-precision. */
    @Test
    public void aNumericArmTakesTheSupertype() {
        assertEquals("NUMBER(38,2)", armFold("UNION", "i", "n"));
        assertEquals("NUMBER(38,2)", armFold("UNION", "n", "i"));
        assertEquals("NUMBER(12,4)", armFold("UNION", "n", "m"),
            "ten integer digits beside four decimals, not NUMBER(10,4)");
        assertEquals("NUMBER(12,4)", armFold("EXCEPT", "n", "m"));
    }

    /** An approximate number swallows an exact one from either side. */
    @Test
    public void anApproximateArmSwallowsAnExactOne() {
        assertEquals("FLOAT", armFold("UNION", "n", "f"));
        assertEquals("FLOAT", armFold("UNION", "f", "n"));
        assertEquals("FLOAT", armFold("UNION ALL", "i", "f"));
        assertEquals("FLOAT", armFold("INTERSECT", "n", "f"));
    }

    /**
     * The temporal arms are NOT the clean total order the branches are. A DATE yields to every
     * timestamp from either side and a TIMESTAMP_TZ written FIRST absorbs anything, but a TZ arriving
     * SECOND after an unzoned or local timestamp is refused outright rather than folded — so
     * {@code tz UNION tl} is TIMESTAMP_TZ while {@code tl UNION tz} is a compile-time error. Only the
     * folding half is pinned here; Frostlake does not yet raise that refusal.
     */
    @Test
    public void aTemporalArmWidens() {
        assertEquals("TIMESTAMP_NTZ", armFold("UNION", "d", "ts"));
        assertEquals("TIMESTAMP_NTZ", armFold("UNION", "ts", "d"));
        assertEquals("TIMESTAMP_LTZ", armFold("UNION", "d", "tl"));
        assertEquals("TIMESTAMP_TZ", armFold("UNION", "d", "tz"));
        assertEquals("TIMESTAMP_LTZ", armFold("UNION", "ts", "tl"));
        assertEquals("TIMESTAMP_LTZ", armFold("UNION", "tl", "ts"));
        assertEquals("TIMESTAMP_LTZ", armFold("EXCEPT", "ts", "tl"));
        assertEquals("TIMESTAMP_TZ", armFold("UNION ALL", "tz", "tl"));
        assertEquals("TIMESTAMP_TZ", armFold("UNION", "tz", "ts"));
        assertEquals("TIMESTAMP_TZ", armFold("UNION", "tz", "d"));
    }

    /** The branch surface takes the wider of the very pair the arms refuse, in either order. */
    @Test
    public void aTemporalBranchTakesTheWiderEitherWay() {
        assertEquals("TIMESTAMP_TZ", typeOf("SELECT COALESCE(tl, tz) FROM sf"));
        assertEquals("TIMESTAMP_TZ", typeOf("SELECT COALESCE(ts, tz) FROM sf"));
        assertEquals("TIMESTAMP_TZ", typeOf("SELECT COALESCE(tz, tl) FROM sf"));
        assertEquals("TIMESTAMP_LTZ", typeOf("SELECT COALESCE(ts, tl) FROM sf"));
    }

    /** A BOOLEAN written first wins over the number after it. */
    @Test
    public void aLeadingBooleanArmWins() {
        assertEquals("BOOLEAN", armFold("UNION", "bo", "n"));
        assertEquals("BOOLEAN", armFold("UNION ALL", "bo", "i"));
        assertEquals("BOOLEAN", armFold("INTERSECT", "bo", "n"));
    }

    /**
     * And the split: the same two types fold ONE way as arms and another as branches. An arm keeps its
     * declared width where a branch widens to the 128MB conversion width.
     */
    @Test
    public void anArmKeepsItsWidthWhereABranchWidens() {
        assertEquals("VARCHAR(5)", armFold("UNION", "v", "bo"));
        assertEquals("VARCHAR(5)", armFold("UNION", "v", "NULL"));
        assertEquals("VARCHAR(5)", armFold("EXCEPT", "v", "NULL"));
        assertEquals("VARCHAR(134217728)", typeOf("SELECT COALESCE(v, bo) FROM sf"));
        assertEquals("VARCHAR(134217728)", typeOf("SELECT COALESCE(v, NULL) FROM sf"));
        assertEquals("BOOLEAN", typeOf("SELECT COALESCE(n, bo) FROM sf"),
            "the branch folds the pair the set operation refuses");
    }

    /** A trailing NULL arm contributes nothing, so the other arm's type stands. */
    @Test
    public void aTrailingNullArmContributesNothing() {
        assertEquals("NUMBER(38,0)", armFold("UNION", "i", "NULL"));
        assertEquals("NUMBER(10,2)", armFold("UNION", "n", "NULL"));
        assertEquals("DATE", armFold("UNION", "d", "NULL"));
    }

    /** Three arms fold as one chain, not pairwise-then-forgotten. */
    @Test
    public void threeArmsFoldAsOneChain() {
        assertEquals("VARCHAR(9)", typeOf(
            "SELECT v FROM sf UNION SELECT w FROM sf UNION SELECT v FROM sf"));
        assertEquals("NUMBER(38,2)", typeOf(
            "SELECT i FROM sf UNION SELECT n FROM sf UNION SELECT i FROM sf"));
        assertEquals("FLOAT", typeOf(
            "SELECT i FROM sf UNION SELECT n FROM sf UNION SELECT f FROM sf"));
        assertEquals("TIMESTAMP_LTZ", typeOf(
            "SELECT d FROM sf UNION SELECT ts FROM sf UNION SELECT tl FROM sf"));
    }
}
