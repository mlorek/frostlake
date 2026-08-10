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
import dev.frostlake.types.BinaryType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a conditional's result column DECLARES once its branches are folded. COALESCE, IFF, NVL,
 * IFNULL, GREATEST, LEAST and CASE all fold the same way, and the pairs that decide the answer were
 * measured cell by cell on the account:
 *
 * <pre>
 *   COALESCE(n, f)       FLOAT               an approximate number swallows an exact one
 *   COALESCE(d, ts)      TIMESTAMP_NTZ       DATE &lt; TIMESTAMP_NTZ &lt; TIMESTAMP_LTZ &lt; TIMESTAMP_TZ
 *   COALESCE(i, NULL)    NUMBER(38,0)        a NULL branch carries no type and decides nothing
 *   COALESCE(NULL, v)    VARCHAR(134217728)  except beside a STRING, which takes the 128MB width
 *   COALESCE(NULL, NULL) VARCHAR(0)          nothing to decide at all
 *   COALESCE(n, bo)      BOOLEAN             a boolean outranks a number, in either order
 *   ZEROIFNULL(m)        NUMBER(6,4)         its zero is two integer digits wide, not one
 * </pre>
 *
 * <p>The FLOAT family is compared by NAME alone: the driver reports no precision or scale for it, so
 * a parameterised assertion could only pass on one of the two engines.
 */
public class ConditionalBranchTypeTest extends BaseDatabaseTest {

    /** The 128MB width a string takes when the branch beside it carries no length of its own. */
    private static final String WIDEST = "VARCHAR(134217728)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE cbt (i INT, n NUMBER(10,2), m NUMBER(5,4), f FLOAT,"
            + " v VARCHAR(5), w VARCHAR(9), d DATE, ts TIMESTAMP_NTZ, tl TIMESTAMP_LTZ,"
            + " bo BOOLEAN, tz TIMESTAMP_TZ, tm TIME, bn BINARY(5), p1 NUMBER(1,0),"
            + " p21 NUMBER(2,1), p99 NUMBER(9,9), p40 NUMBER(4,0), vt VARCHAR(30), vn VARCHAR(9))");
        engine.execute("INSERT INTO cbt SELECT 1, 2.50, 0.5, 3.5, 'ab', 'cdefg', '2020-01-01',"
            + " '2020-01-01 10:00:00', '2020-01-01 10:00:00', TRUE, '2020-01-01 10:00:00',"
            + " '10:00:00', TO_BINARY('AB'), 1, 1.5, 0.5, 1234, '2020-01-01 10:00:00', '123'");
    }

    /** The declared type of the expression's result column, with the parameters that are comparable. */
    private String typeOf(final String expr) {
        final ResultSet rs = engine.executeQuery("SELECT " + expr + " FROM cbt");
        final DataType type = rs.getColumns().get(0).getDataType();
        if (type == null) {
            return "null";
        }
        if (type instanceof NumericType && "NUMBER".equalsIgnoreCase(type.getName())) {
            final NumericType numeric = (NumericType) type;
            return "NUMBER(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        if (type instanceof BinaryType) {
            return type.getName() + "(" + ((BinaryType) type).getMaxLength() + ")";
        }
        return type.getName();
    }

    /** A FLOAT branch decides the whole conditional, whichever side it is written on. */
    @Test
    public void anApproximateNumberSwallowsAnExactOne() {
        assertEquals("FLOAT", typeOf("COALESCE(n, f)"));
        assertEquals("FLOAT", typeOf("COALESCE(f, i)"));
        assertEquals("FLOAT", typeOf("COALESCE(i, f)"));
        assertEquals("FLOAT", typeOf("COALESCE(m, f)"));
        assertEquals("FLOAT", typeOf("COALESCE(f, 1)"));
        assertEquals("FLOAT", typeOf("NVL(f, n)"));
        assertEquals("FLOAT", typeOf("GREATEST(n, f)"));
        assertEquals("FLOAT", typeOf("LEAST(n, f)"));
    }

    /** Two exact numbers keep the widest integer part beside the widest scale. */
    @Test
    public void twoExactNumbersTakeTheSupertype() {
        assertEquals("NUMBER(38,4)", typeOf("COALESCE(m, i)"));
        assertEquals("NUMBER(13,9)", typeOf("COALESCE(p99, p40)"));
        assertEquals("NUMBER(38,1)", typeOf("COALESCE(i, 1.5)"));
        assertEquals("NUMBER(2,1)", typeOf("IFF(bo, 1, 2.5)"));
    }

    /** A DATE widens into a TIMESTAMP, and the zoned flavours take the zone with them. */
    @Test
    public void temporalsWidenIntoOneAnother() {
        assertEquals("TIMESTAMP_NTZ", typeOf("COALESCE(d, ts)"));
        assertEquals("TIMESTAMP_NTZ", typeOf("COALESCE(ts, d)"));
        assertEquals("TIMESTAMP_NTZ", typeOf("IFF(bo, ts, d)"));
        assertEquals("TIMESTAMP_NTZ", typeOf("NVL(d, ts)"));
        assertEquals("TIMESTAMP_NTZ", typeOf("CASE WHEN bo THEN d ELSE ts END"));
        assertEquals("TIMESTAMP_LTZ", typeOf("COALESCE(ts, tl)"));
        assertEquals("TIMESTAMP_LTZ", typeOf("COALESCE(d, tl)"));
        assertEquals("TIMESTAMP_TZ", typeOf("COALESCE(ts, tz)"));
        assertEquals("TIMESTAMP_TZ", typeOf("COALESCE(tl, tz)"));
        assertEquals("TIMESTAMP_TZ", typeOf("COALESCE(d, tz)"));
        assertEquals("TIME", typeOf("COALESCE(tm, tm)"));
    }

    /** A NULL branch carries no type, so the others decide — number, temporal, boolean or binary. */
    @Test
    public void aNullBranchDecidesNothing() {
        assertEquals("NUMBER(38,0)", typeOf("COALESCE(i, NULL)"));
        assertEquals("NUMBER(38,0)", typeOf("IFF(bo, NULL, i)"));
        assertEquals("NUMBER(38,0)", typeOf("CASE WHEN bo THEN NULL ELSE i END"));
        assertEquals("NUMBER(1,0)", typeOf("COALESCE(NULL, 1)"));
        assertEquals("NUMBER(10,2)", typeOf("COALESCE(n, NULL)"));
        assertEquals("FLOAT", typeOf("COALESCE(f, NULL)"));
        assertEquals("FLOAT", typeOf("COALESCE(NULL, f)"));
        assertEquals("DATE", typeOf("COALESCE(NULL, d)"));
        assertEquals("TIMESTAMP_NTZ", typeOf("COALESCE(NULL, ts)"));
        assertEquals("TIMESTAMP_TZ", typeOf("COALESCE(NULL, tz)"));
        assertEquals("TIME", typeOf("COALESCE(tm, NULL)"));
        assertEquals("BOOLEAN", typeOf("COALESCE(NULL, bo)"));
        assertEquals("BOOLEAN", typeOf("COALESCE(bo, NULL)"));
        assertEquals("BINARY(5)", typeOf("COALESCE(NULL, bn)"));
        assertEquals("BINARY(5)", typeOf("COALESCE(bn, NULL)"));
    }

    /** Beside a STRING it is the one branch that DOES change the answer: the width goes to 128MB. */
    @Test
    public void aNullBranchWidensAStringToItsMaximum() {
        assertEquals(WIDEST, typeOf("COALESCE(NULL, w)"));
        assertEquals(WIDEST, typeOf("COALESCE(v, NULL)"));
        assertEquals(WIDEST, typeOf("COALESCE(v, NULL, w)"));
        assertEquals(WIDEST, typeOf("COALESCE(NULL, 'x')"));
        assertEquals(WIDEST, typeOf("CASE WHEN bo THEN NULL ELSE v END"));
        assertEquals("VARCHAR(9)", typeOf("COALESCE(v, w)"));
    }

    /** With nothing but NULL branches there is nothing to widen: a zero-width string. */
    @Test
    public void nothingButNullBranchesIsAZeroWidthString() {
        assertEquals("VARCHAR(0)", typeOf("COALESCE(NULL, NULL)"));
        assertEquals("VARCHAR(0)", typeOf("IFF(bo, NULL, NULL)"));
    }

    /** A boolean outranks a number in either order, and a string only when written first. */
    @Test
    public void aBooleanOutranksANumberAndLeadsAString() {
        assertEquals("BOOLEAN", typeOf("COALESCE(n, bo)"));
        assertEquals("BOOLEAN", typeOf("COALESCE(bo, n)"));
        assertEquals("BOOLEAN", typeOf("COALESCE(i, bo)"));
        assertEquals("BOOLEAN", typeOf("COALESCE(f, bo)"));
        assertEquals("BOOLEAN", typeOf("COALESCE(bo, v)"));
        assertEquals(WIDEST, typeOf("COALESCE(v, bo)"));
    }

    /** A string beside ONE other family joins that family — the number and the temporal alike. */
    @Test
    public void aStringJoinsTheOtherFamily() {
        assertEquals("TIMESTAMP_NTZ", typeOf("COALESCE(vt, ts)"));
        assertEquals("TIMESTAMP_NTZ", typeOf("COALESCE(ts, vt)"));
        assertEquals("TIMESTAMP_LTZ", typeOf("COALESCE(vt, tl)"));
        assertEquals("DATE", typeOf("COALESCE(vt, d)"));
        assertEquals("NUMBER(38,5)", typeOf("COALESCE(vn, i)"));
        assertEquals("NUMBER(38,5)", typeOf("COALESCE(i, vn)"));
        assertEquals("NUMBER(18,5)", typeOf("COALESCE(vn, p21)"));
        assertEquals("FLOAT", typeOf("COALESCE(vn, f)"));
    }

    /** ZEROIFNULL folds with a zero TWO integer digits wide, so a narrow argument gains one. */
    @Test
    public void zeroIfNullWidensANarrowIntegerPart() {
        assertEquals("NUMBER(2,0)", typeOf("ZEROIFNULL(p1)"));
        assertEquals("NUMBER(3,1)", typeOf("ZEROIFNULL(p21)"));
        assertEquals("NUMBER(11,9)", typeOf("ZEROIFNULL(p99)"));
        assertEquals("NUMBER(6,4)", typeOf("ZEROIFNULL(m)"));
        assertEquals("NUMBER(4,0)", typeOf("ZEROIFNULL(p40)"));
        assertEquals("NUMBER(10,2)", typeOf("ZEROIFNULL(n)"));
        assertEquals("NUMBER(38,0)", typeOf("ZEROIFNULL(i)"));
        assertEquals("FLOAT", typeOf("ZEROIFNULL(f)"));
    }

    /** Written out as COALESCE(x, 0) it is a DIFFERENT call, and the ordinary zero is one digit. */
    @Test
    public void anExplicitZeroBranchIsNarrowerThanZeroIfNull() {
        assertEquals("NUMBER(5,4)", typeOf("COALESCE(m, 0)"));
        assertEquals("NUMBER(5,4)", typeOf("IFNULL(m, 0)"));
        assertEquals("NUMBER(10,9)", typeOf("NVL(p99, 0)"));
        assertEquals("NUMBER(2,1)", typeOf("COALESCE(p21, 0)"));
    }
}
