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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The non-numeric argument families over the numeric surface, spelled the way a real account spells
 * them (live-verified cell by cell, every sentence below).
 *
 * <p>★ THE WHOLE NUMERIC FAMILY REFUSES A BOOLEAN, A TEMPORAL, A BINARY AND A GEOGRAPHY, in every
 * position, at the call's own position: "Invalid argument types for function 'ABS': (DATE)",
 * {@code MOD(1, TRUE)} listing "(NUMBER(1,0), BOOLEAN)". ZEROIFNULL was the one member that did, and
 * the rest reached the row and failed in Frostlake's own words — or answered.
 *
 * <p>★ TRUNC IS THE ARITY-DEPENDENT MEMBER: over a temporal it is DATE_TRUNC with a unit, so
 * {@code TRUNC(d, 'MONTH')} answers while {@code TRUNC(d)} alone is "(DATE)"; a unit that is not a
 * string literal is the date-part sentence whatever its family, and a temporal beside a NUMERIC first
 * argument is the family refusal.
 *
 * <p>★ NULLIFZERO IS NULLIF(x, 0): a temporal, a binary, an OBJECT or an ARRAY takes the conditional's
 * "Can not convert parameter '0' of type [NUMBER(1,0)] into expected type [DATE]", a GEOGRAPHY is
 * refused under the name 'NULLIF' at the NULLIFZERO call's place, a BOOLEAN passes (FALSE is the zero),
 * and a text is read as a number.
 *
 * <p>★ BOTH SIGNS REFUSE AT THE OPERATOR under Snowflake's names, 'NEGATE' and 'UNARY PLUS'; unary
 * plus is a node of its own that converts a text or a VARIANT to a FLOAT exactly as the minus does.
 *
 * <p>★ THE CONVERSIONS AND THE CASTS SHARE ONE MATRIX: a pair the plain cast cannot carry is the
 * conversion sentence while the statement compiles — for TO_X the call echoed with its source and its
 * own base name, for CAST the target rendered with its default parameters, for TRY_CAST the source
 * alone — while a castable pair converts (TO_NUMBER(TRUE) is 1, typed NUMBER(2,0) whatever width the
 * call declares) and TRY_CAST keeps its own sentence with the nominal target for those.
 */
public class NumericFamilyArgumentRefusalTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return e.getMessage();
    }

    private String cell(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private static String argumentTypes(final int position, final String function, final String types) {
        return "SQL compilation error: error line 1 at position " + position
            + "\nInvalid argument types for function '" + function + "': (" + types + ")";
    }

    private static String conversion(final String echo, final String parameter) {
        return "SQL compilation error:\ninvalid type [" + echo + "] for parameter '" + parameter + "'";
    }

    private static String tryCast(final String source, final String target) {
        return "SQL compilation error:\nFunction TRY_CAST cannot be used with arguments of types "
            + source + " and " + target;
    }

    private static String cannotConvert(final String parameter, final String type, final String expected) {
        return "SQL compilation error:\nCan not convert parameter '" + parameter + "' of type ["
            + type + "] into expected type [" + expected + "]";
    }

    private static String unit(final String echo, final String function) {
        // The one sentence of the family that stays on the prefix's own line (live-verified).
        return "SQL compilation error: Date/time component [" + echo + " ]for function " + function
            + " needs to be an identifier or a string literal.";
    }

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE tt (d DATE, t TIME, ts TIMESTAMP_NTZ, tl TIMESTAMP_LTZ,"
            + " b BOOLEAN, bn BINARY, b5 BINARY(5), n NUMBER(5,2), f FLOAT, s VARCHAR(3), v VARIANT,"
            + " o OBJECT, a ARRAY)");
        engine.execute("INSERT INTO tt SELECT '2020-01-01', '10:00:00', '2020-01-01 10:00:00',"
            + " '2020-01-01 10:00:00', TRUE, TO_BINARY('0A0B'), TO_BINARY('0A0B'), 1.5, 1.5, '5',"
            + " PARSE_JSON('7'), OBJECT_CONSTRUCT('k', 1), ARRAY_CONSTRUCT(1)");
    }

    @Test
    public void theNumericFamilyRefusesEveryOtherFamilyAtTheCall() {
        assertEquals(argumentTypes(7, "ABS", "BOOLEAN"), refusal("SELECT ABS(TRUE)"));
        assertEquals(argumentTypes(7, "ABS", "BOOLEAN"), refusal("SELECT ABS(b) FROM tt"));
        assertEquals(argumentTypes(10, "ABS", "BOOLEAN"), refusal("SELECT 1, ABS(b) FROM tt"));
        assertEquals(argumentTypes(7, "ABS", "DATE"), refusal("SELECT ABS(d) FROM tt"));
        assertEquals(argumentTypes(7, "ABS", "DATE"), refusal("SELECT ABS(CURRENT_DATE)"));
        assertEquals(argumentTypes(7, "ABS", "TIME(9)"), refusal("SELECT ABS(t) FROM tt"));
        assertEquals(argumentTypes(7, "ABS", "TIMESTAMP_NTZ(9)"), refusal("SELECT ABS(ts) FROM tt"));
        assertEquals(argumentTypes(7, "ABS", "TIMESTAMP_LTZ(9)"), refusal("SELECT ABS(tl) FROM tt"));
        assertEquals(argumentTypes(7, "ABS", "BINARY(8388608)"), refusal("SELECT ABS(bn) FROM tt"));
        assertEquals(argumentTypes(7, "ABS", "BINARY(5)"), refusal("SELECT ABS(b5) FROM tt"));
        assertEquals(argumentTypes(7, "ROUND", "DATE"), refusal("SELECT ROUND(d) FROM tt"));
        assertEquals(argumentTypes(7, "ROUND", "DATE, NUMBER(1,0)"), refusal("SELECT ROUND(d, 1) FROM tt"));
        assertEquals(argumentTypes(7, "ROUND", "DATE, VARCHAR(5)"), refusal("SELECT ROUND(d, 'MONTH') FROM tt"));
        assertEquals(argumentTypes(7, "ROUND", "NUMBER(2,1), DATE"), refusal("SELECT ROUND(1.5, d) FROM tt"));
        assertEquals(argumentTypes(7, "ROUND", "TIME(9)"), refusal("SELECT ROUND(TO_TIME('12:00:00'))"));
        assertEquals(argumentTypes(7, "SIGN", "BOOLEAN"), refusal("SELECT SIGN(TRUE)"));
        assertEquals(argumentTypes(7, "SIGN", "BINARY(8388608)"), refusal("SELECT SIGN(bn) FROM tt"));
        assertEquals(argumentTypes(7, "CEIL", "TIME(9)"), refusal("SELECT CEIL(t) FROM tt"));
        assertEquals(argumentTypes(7, "FLOOR", "DATE"), refusal("SELECT FLOOR(d) FROM tt"));
        assertEquals(argumentTypes(7, "SQRT", "DATE"), refusal("SELECT SQRT(d) FROM tt"));
        assertEquals(argumentTypes(7, "EXP", "DATE"), refusal("SELECT EXP(d) FROM tt"));
        assertEquals(argumentTypes(7, "SQUARE", "BOOLEAN"), refusal("SELECT SQUARE(b) FROM tt"));
        assertEquals(argumentTypes(7, "LN", "BOOLEAN"), refusal("SELECT LN(TRUE)"));
        assertEquals(argumentTypes(7, "CBRT", "BOOLEAN"), refusal("SELECT CBRT(TRUE)"));
        assertEquals(argumentTypes(7, "SIN", "BOOLEAN"), refusal("SELECT SIN(TRUE)"));
        assertEquals(argumentTypes(7, "DEGREES", "BOOLEAN"), refusal("SELECT DEGREES(TRUE)"));
        assertEquals(argumentTypes(7, "FACTORIAL", "BOOLEAN"), refusal("SELECT FACTORIAL(TRUE)"));
        assertEquals(argumentTypes(7, "MOD", "BOOLEAN, NUMBER(1,0)"), refusal("SELECT MOD(TRUE, 2)"));
        assertEquals(argumentTypes(7, "MOD", "NUMBER(1,0), BOOLEAN"), refusal("SELECT MOD(1, TRUE)"));
        assertEquals(argumentTypes(7, "MOD", "DATE, DATE"), refusal("SELECT MOD(d, d) FROM tt"));
        assertEquals(argumentTypes(7, "POWER", "BOOLEAN, BOOLEAN"), refusal("SELECT POWER(TRUE, TRUE)"));
        assertEquals(argumentTypes(7, "POWER", "NUMBER(1,0), DATE"), refusal("SELECT POWER(2, d) FROM tt"));
        assertEquals(argumentTypes(7, "LOG", "NUMBER(2,0), DATE"), refusal("SELECT LOG(10, d) FROM tt"));
        assertEquals(argumentTypes(7, "ATAN2", "BOOLEAN, NUMBER(1,0)"), refusal("SELECT ATAN2(TRUE, 1)"));
        assertEquals(argumentTypes(7, "BITAND", "DATE, NUMBER(1,0)"), refusal("SELECT BITAND(d, 1) FROM tt"));
        assertEquals(argumentTypes(7, "DIV0", "BOOLEAN, NUMBER(1,0)"), refusal("SELECT DIV0(TRUE, 1)"));
        assertEquals(argumentTypes(7, "GETBIT", "BOOLEAN, NUMBER(1,0)"), refusal("SELECT GETBIT(TRUE, 0)"));
        assertEquals(argumentTypes(7, "HAVERSINE", "BOOLEAN, NUMBER(1,0), NUMBER(1,0), NUMBER(1,0)"),
            refusal("SELECT HAVERSINE(TRUE, 1, 2, 3)"));
        assertEquals(argumentTypes(7, "WIDTH_BUCKET", "BOOLEAN, NUMBER(1,0), NUMBER(2,0), NUMBER(1,0)"),
            refusal("SELECT WIDTH_BUCKET(TRUE, 0, 10, 2)"));
        assertEquals(argumentTypes(7, "UNIFORM", "BOOLEAN, NUMBER(1,0), NUMBER(19,0)"),
            refusal("SELECT UNIFORM(TRUE, 2, RANDOM())"));
        assertEquals(argumentTypes(7, "ZEROIFNULL", "DATE"), refusal("SELECT ZEROIFNULL(d) FROM tt"));
        assertEquals(argumentTypes(10, "ZEROIFNULL", "DATE"), refusal("SELECT 1, ZEROIFNULL(d) FROM tt"));
        // A NUMBER, a text and NULL are read as they always were.
        assertEquals("1.50", cell("SELECT ABS(n) FROM tt"));
        assertEquals("5.0", cell("SELECT ABS(s) FROM tt"));
        assertEquals("null", cell("SELECT ABS(NULL)"));
        assertEquals("NUMBER(19,0)", cell("SELECT SPLIT_PART(SYSTEM$TYPEOF(RANDOM()), '[', 1)"));
    }

    @Test
    public void truncOverATemporalIsDateTruncOnlyBesideAUnit() {
        assertEquals("2020-01-01", cell("SELECT TRUNC(d, 'MONTH')::VARCHAR FROM tt"));
        assertEquals("2020-01-01", cell("SELECT TRUNCATE(d, 'MONTH')::VARCHAR FROM tt"));
        assertEquals("2020-01-01 10:00:00.000", cell("SELECT TRUNC(ts, 'HOUR')::VARCHAR FROM tt"));
        assertEquals("10:00:00", cell("SELECT TRUNC(t, 'HOUR')::VARCHAR FROM tt"));
        assertEquals("null", cell("SELECT TRUNC(d, NULL) FROM tt"));
        assertEquals(argumentTypes(7, "TRUNC", "DATE"), refusal("SELECT TRUNC(d) FROM tt"));
        assertEquals(argumentTypes(7, "TRUNCATE", "DATE"), refusal("SELECT TRUNCATE(d) FROM tt"));
        assertEquals(argumentTypes(7, "TRUNC", "TIME(9)"), refusal("SELECT TRUNC(t) FROM tt"));
        assertEquals(argumentTypes(7, "TRUNC", "TIMESTAMP_NTZ(9)"), refusal("SELECT TRUNC(ts) FROM tt"));
        assertEquals(argumentTypes(7, "TRUNC", "BOOLEAN"), refusal("SELECT TRUNC(b) FROM tt"));
        assertEquals(unit("TO_CHAR(1)", "TRUNC"), refusal("SELECT TRUNC(d, 1) FROM tt"));
        assertEquals(unit("TO_CHAR(1)", "TRUNC"), refusal("SELECT TRUNC(ts, 1) FROM tt"));
        assertEquals(unit("TO_CHAR(TT.D)", "TRUNC"), refusal("SELECT TRUNC(d, d) FROM tt"));
        assertEquals(unit("TO_CHAR(TT.N)", "TRUNC"), refusal("SELECT TRUNC(d, n) FROM tt"));
        assertEquals(unit("TT.S", "TRUNC"), refusal("SELECT TRUNC(d, s) FROM tt"));
        assertEquals(argumentTypes(7, "TRUNC", "NUMBER(2,1), DATE"), refusal("SELECT TRUNC(1.5, d) FROM tt"));
        assertEquals(argumentTypes(7, "TRUNC", "NUMBER(2,1), BOOLEAN"), refusal("SELECT TRUNC(1.5, TRUE)"));
    }

    @Test
    public void nullIfZeroIsJudgedAsNullIfOverAnImplicitZero() {
        assertEquals(cannotConvert("0", "NUMBER(1,0)", "DATE"), refusal("SELECT NULLIFZERO(d) FROM tt"));
        assertEquals(cannotConvert("0", "NUMBER(1,0)", "DATE"), refusal("SELECT 1, NULLIFZERO(d) FROM tt"));
        assertEquals(cannotConvert("0", "NUMBER(1,0)", "TIME(9)"), refusal("SELECT NULLIFZERO(t) FROM tt"));
        assertEquals(cannotConvert("0", "NUMBER(1,0)", "TIMESTAMP_NTZ(9)"), refusal("SELECT NULLIFZERO(ts) FROM tt"));
        assertEquals(cannotConvert("0", "NUMBER(1,0)", "BINARY(8388608)"), refusal("SELECT NULLIFZERO(bn) FROM tt"));
        assertEquals(cannotConvert("0", "NUMBER(1,0)", "BINARY(5)"), refusal("SELECT NULLIFZERO(b5) FROM tt"));
        assertEquals(cannotConvert("0", "NUMBER(1,0)", "OBJECT"), refusal("SELECT NULLIFZERO(o) FROM tt"));
        assertEquals(cannotConvert("0", "NUMBER(1,0)", "ARRAY"), refusal("SELECT NULLIFZERO(a) FROM tt"));
        assertEquals(cannotConvert("0", "NUMBER(1,0)", "DATE"), refusal("SELECT NULLIF(d, 0) FROM tt"));
        assertEquals(cannotConvert("TT.D", "DATE", "NUMBER(1,0)"), refusal("SELECT NULLIF(0, d) FROM tt"));
        assertEquals("null", cell("SELECT NULLIFZERO(FALSE)"));
        assertEquals("true", cell("SELECT NULLIFZERO(TRUE)"));
        assertEquals("true", cell("SELECT NULLIFZERO(b) FROM tt"));
        assertEquals("null", cell("SELECT NULLIFZERO(NOT b) FROM tt"));
        assertEquals("true", cell("SELECT NULLIF(b, 0) FROM tt"));
        assertEquals("null", cell("SELECT NULLIFZERO(0)"));
        assertEquals("1.50", cell("SELECT NULLIFZERO(n) FROM tt"));
        assertEquals("5", cell("SELECT NULLIFZERO(s) FROM tt"));
        assertEquals("null", cell("SELECT NULLIFZERO('0')"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT NULLIFZERO('x')"));
        // A GEOGRAPHY takes the conditional's NAME at the NULLIFZERO call's place — over an empty
        // table too, the type surface needs no geo pack.
        engine.execute("CREATE OR REPLACE TABLE gz (g GEOGRAPHY)");
        assertEquals(argumentTypes(7, "NULLIF", "GEOGRAPHY, NUMBER(1,0)"), refusal("SELECT NULLIFZERO(g) FROM gz"));
        assertEquals(argumentTypes(10, "NULLIF", "GEOGRAPHY, NUMBER(1,0)"), refusal("SELECT 1, NULLIFZERO(g) FROM gz"));
        assertEquals(argumentTypes(10, "NULLIF", "GEOGRAPHY, NUMBER(1,0)"), refusal("SELECT 1, NULLIF(g, 0) FROM gz"));
    }

    @Test
    public void aSignRefusesAtTheOperatorUnderSnowflakesName() {
        assertEquals(argumentTypes(7, "NEGATE", "BOOLEAN"), refusal("SELECT -TRUE"));
        assertEquals(argumentTypes(7, "NEGATE", "DATE"), refusal("SELECT -d FROM tt"));
        assertEquals(argumentTypes(7, "NEGATE", "TIME(9)"), refusal("SELECT -t FROM tt"));
        assertEquals(argumentTypes(7, "NEGATE", "TIMESTAMP_NTZ(9)"), refusal("SELECT -ts FROM tt"));
        assertEquals(argumentTypes(7, "NEGATE", "BINARY(8388608)"), refusal("SELECT -bn FROM tt"));
        assertEquals(argumentTypes(7, "NEGATE", "BINARY(5)"), refusal("SELECT -b5 FROM tt"));
        assertEquals(argumentTypes(7, "NEGATE", "BOOLEAN"), refusal("SELECT -IFF(TRUE, TRUE, FALSE)"));
        assertEquals(argumentTypes(7, "UNARY PLUS", "BOOLEAN"), refusal("SELECT +TRUE"));
        assertEquals(argumentTypes(10, "UNARY PLUS", "BOOLEAN"), refusal("SELECT 1, +TRUE"));
        assertEquals(argumentTypes(7, "UNARY PLUS", "BOOLEAN"), refusal("SELECT +(TRUE)"));
        assertEquals(argumentTypes(7, "UNARY PLUS", "DATE"), refusal("SELECT +d FROM tt"));
        assertEquals(argumentTypes(7, "UNARY PLUS", "DATE"), refusal("SELECT +IFF(TRUE, d, d) FROM tt"));
        assertEquals(argumentTypes(7, "UNARY PLUS", "TIME(9)"), refusal("SELECT +t FROM tt"));
        assertEquals(argumentTypes(7, "UNARY PLUS", "TIMESTAMP_NTZ(9)"), refusal("SELECT +ts FROM tt"));
        assertEquals(argumentTypes(7, "UNARY PLUS", "BINARY(8388608)"), refusal("SELECT +bn FROM tt"));
        assertEquals(argumentTypes(7, "UNARY PLUS", "BINARY(5)"), refusal("SELECT +b5 FROM tt"));
        assertEquals(argumentTypes(7, "UNARY PLUS", "OBJECT"), refusal("SELECT +o FROM tt"));
        assertEquals(argumentTypes(7, "UNARY PLUS", "ARRAY"), refusal("SELECT +a FROM tt"));
        assertEquals(argumentTypes(23, "UNARY PLUS", "BOOLEAN"), refusal("SELECT n FROM tt WHERE +b"));
        // What the plus READS: a number as itself, a text and a VARIANT as a FLOAT, NULL as NULL.
        assertEquals("1.50", cell("SELECT +n FROM tt"));
        assertEquals("1.5", cell("SELECT +f FROM tt"));
        assertEquals("5.0", cell("SELECT +'5'"));
        assertEquals("5.0", cell("SELECT +s FROM tt"));
        assertEquals("7.0", cell("SELECT +v FROM tt"));
        assertEquals("-7.0", cell("SELECT -v FROM tt"));
        assertEquals("null", cell("SELECT +NULL"));
        assertEquals("FLOAT[DOUBLE]", cell("SELECT SYSTEM$TYPEOF(+'5')"));
        assertEquals("FLOAT[DOUBLE]", cell("SELECT SYSTEM$TYPEOF(-'3')"));
        assertEquals("FLOAT[DOUBLE]", cell("SELECT SYSTEM$TYPEOF(+s) FROM tt"));
        assertEquals("FLOAT[DOUBLE]", cell("SELECT SYSTEM$TYPEOF(+v) FROM tt"));
        assertEquals("FLOAT[DOUBLE]", cell("SELECT SYSTEM$TYPEOF(-v) FROM tt"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT +'x'"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT -'x'"));
    }

    @Test
    public void theConversionsRefuseUncastablePairsWhileCompiling() {
        assertEquals(conversion("TO_DOUBLE(TRUE)", "TO_DOUBLE"), refusal("SELECT TO_DOUBLE(TRUE)"));
        assertEquals(conversion("TO_DOUBLE(TT.B)", "TO_DOUBLE"), refusal("SELECT TO_DOUBLE(b) FROM tt"));
        assertEquals(conversion("TO_DOUBLE(TT.D)", "TO_DOUBLE"), refusal("SELECT TO_DOUBLE(d) FROM tt"));
        assertEquals(conversion("TO_DOUBLE(TT.BN)", "TO_DOUBLE"), refusal("SELECT TO_DOUBLE(bn) FROM tt"));
        assertEquals(conversion("TO_DOUBLE(TT.O)", "TO_DOUBLE"), refusal("SELECT TO_DOUBLE(o) FROM tt"));
        assertEquals(conversion("TO_BINARY(TRUE)", "TO_BINARY"), refusal("SELECT TO_BINARY(TRUE)"));
        assertEquals(conversion("TO_BINARY(123)", "TO_BINARY"), refusal("SELECT TO_BINARY(123)"));
        assertEquals(conversion("TO_BINARY(TT.O)", "TO_BINARY"), refusal("SELECT TO_BINARY(o) FROM tt"));
        assertEquals(conversion("TO_DATE(TRUE)", "TO_DATE"), refusal("SELECT TO_DATE(TRUE)"));
        assertEquals(conversion("TO_DATE(123)", "TO_DATE"), refusal("SELECT TO_DATE(123)"));
        assertEquals(conversion("TO_DATE(TT.O)", "TO_DATE"), refusal("SELECT TO_DATE(o) FROM tt"));
        assertEquals(conversion("TO_TIME(TRUE)", "TO_TIME"), refusal("SELECT TO_TIME(TRUE)"));
        assertEquals(conversion("TO_TIME(123)", "TO_TIME"), refusal("SELECT TO_TIME(123)"));
        assertEquals(conversion("TO_TIME(TT.O)", "TO_TIME"), refusal("SELECT TO_TIME(o) FROM tt"));
        assertEquals(conversion("TO_TIME(TT.F)", "TO_TIME"), refusal("SELECT TO_TIME(f) FROM tt"));
        assertEquals(conversion("TO_TIMESTAMP(TRUE)", "TO_TIMESTAMP_NTZ"), refusal("SELECT TO_TIMESTAMP(TRUE)"));
        assertEquals(conversion("TO_TIMESTAMP_NTZ(TRUE)", "TO_TIMESTAMP_NTZ"), refusal("SELECT TO_TIMESTAMP_NTZ(TRUE)"));
        assertEquals(conversion("TO_TIMESTAMP_TZ(TRUE)", "TO_TIMESTAMP_TZ"), refusal("SELECT TO_TIMESTAMP_TZ(TRUE)"));
        assertEquals(conversion("TO_BOOLEAN(TT.D)", "TO_BOOLEAN"), refusal("SELECT TO_BOOLEAN(d) FROM tt"));
        assertEquals(conversion("TO_BOOLEAN(TT.BN)", "TO_BOOLEAN"), refusal("SELECT TO_BOOLEAN(bn) FROM tt"));
        assertEquals(conversion("TO_BOOLEAN(TT.T)", "TO_BOOLEAN"), refusal("SELECT TO_BOOLEAN(t) FROM tt"));
        assertEquals(conversion("TO_BOOLEAN(TT.F)", "TO_BOOLEAN"), refusal("SELECT TO_BOOLEAN(f) FROM tt"));
        assertEquals(conversion("TO_BOOLEAN(TT.O)", "TO_BOOLEAN"), refusal("SELECT TO_BOOLEAN(o) FROM tt"));
        assertEquals(conversion("TO_NUMBER(TT.D)", "TO_NUMBER"), refusal("SELECT TO_NUMBER(d) FROM tt"));
        assertEquals(conversion("TO_NUMBER(TT.BN)", "TO_NUMBER"), refusal("SELECT TO_NUMBER(bn) FROM tt"));
        assertEquals(conversion("TO_NUMBER(TT.O)", "TO_NUMBER"), refusal("SELECT TO_NUMBER(o) FROM tt"));
        assertEquals(conversion("TO_NUMBER(TT.A)", "TO_NUMBER"), refusal("SELECT TO_NUMBER(a) FROM tt"));
        assertEquals(conversion("TO_DECIMAL(TT.D)", "TO_DECIMAL"), refusal("SELECT TO_DECIMAL(d) FROM tt"));
        assertEquals(conversion("TO_NUMERIC(TT.D)", "TO_NUMERIC"), refusal("SELECT TO_NUMERIC(d) FROM tt"));
        assertEquals(conversion("TO_DECIMAL(TT.D)", "TO_DECIMAL"), refusal("SELECT TO_DECIMAL(d, 5, 2) FROM tt"));
        assertEquals("SQL compilation error:\nargument needs to be a string: 'TRUE'",
            refusal("SELECT TO_NUMBER(TRUE, '999')"));
        assertEquals("SQL compilation error:\nargument needs to be a string: 'TT.N'",
            refusal("SELECT TO_NUMBER(n, '999') FROM tt"));
        assertEquals("SQL compilation error:\nargument needs to be a string: 'TT.D'",
            refusal("SELECT TO_NUMBER(d, '999') FROM tt"));
        // The castable pairs convert, and a BOOLEAN converts to the unscaled 1 / 0 typed NUMBER(2,0)
        // whatever width the call declares.
        assertEquals("1", cell("SELECT TO_NUMBER(TRUE)"));
        assertEquals("0", cell("SELECT TO_NUMBER(FALSE)"));
        assertEquals("1", cell("SELECT TO_DECIMAL(TRUE, 5, 1)"));
        assertEquals("1", cell("SELECT TO_NUMERIC(b) FROM tt"));
        assertEquals("NUMBER(2,0)[SB1]", cell("SELECT SYSTEM$TYPEOF(TO_NUMBER(TRUE))"));
        assertEquals("NUMBER(2,0)[SB1]", cell("SELECT SYSTEM$TYPEOF(TO_DECIMAL(TRUE, 5, 1))"));
        assertEquals("2020-01-01", cell("SELECT TO_DATE(ts)::VARCHAR FROM tt"));
        assertEquals("10:00:00", cell("SELECT TO_TIME(ts)::VARCHAR FROM tt"));
        assertEquals("2020-01-01 00:00:00.000", cell("SELECT TO_TIMESTAMP(d)::VARCHAR FROM tt"));
        assertEquals("true", cell("SELECT TO_BOOLEAN(123)"));
        assertEquals("1.5", cell("SELECT TO_DOUBLE(f) FROM tt"));
        assertEquals("0A0B", cell("SELECT HEX_ENCODE(TO_BINARY(bn)) FROM tt"));
    }

    @Test
    public void theCastsFollowTheSameMatrix() {
        assertEquals(conversion("CAST(TRUE AS FLOAT)", "TO_DOUBLE"), refusal("SELECT TRUE::FLOAT"));
        assertEquals(conversion("CAST(TRUE AS FLOAT)", "TO_DOUBLE"), refusal("SELECT TRUE::DOUBLE"));
        assertEquals(conversion("CAST(TRUE AS FLOAT)", "TO_DOUBLE"), refusal("SELECT TRUE::REAL"));
        assertEquals(conversion("CAST(TRUE AS FLOAT)", "TO_DOUBLE"), refusal("SELECT TRUE::FLOAT8"));
        assertEquals(conversion("CAST(TT.B AS FLOAT)", "TO_DOUBLE"), refusal("SELECT b::FLOAT FROM tt"));
        assertEquals(conversion("TRY_CAST(TRUE)", "TO_DOUBLE"), refusal("SELECT TRY_CAST(TRUE AS FLOAT)"));
        assertEquals(conversion("TRY_CAST(TRUE)", "TO_DOUBLE"), refusal("SELECT TRY_CAST(TRUE AS DOUBLE)"));
        assertEquals(conversion("TRY_CAST(TT.B)", "TO_DOUBLE"), refusal("SELECT TRY_CAST(b AS FLOAT) FROM tt"));
        assertEquals(tryCast("BOOLEAN", "NUMBER(2,0)"), refusal("SELECT TRY_CAST(TRUE AS NUMBER)"));
        assertEquals(tryCast("BOOLEAN", "NUMBER(2,0)"), refusal("SELECT TRY_CAST(TRUE AS NUMBER(5,1))"));
        assertEquals(tryCast("NUMBER(5,2)", "NUMBER(38,0)"), refusal("SELECT TRY_CAST(n AS NUMBER) FROM tt"));
        assertEquals(tryCast("TIME(9)", "TIME(9)"), refusal("SELECT TRY_CAST(t AS TIME) FROM tt"));
        assertEquals(tryCast("DATE", "TIMESTAMP_NTZ(9)"), refusal("SELECT TRY_CAST(d AS TIMESTAMP) FROM tt"));
        assertEquals(tryCast("NUMBER(5,2)", "VARCHAR(134217728)"), refusal("SELECT TRY_CAST(n AS VARCHAR) FROM tt"));
        assertEquals(conversion("TRY_CAST(TRUE)", "TO_TIME"), refusal("SELECT TRY_CAST(TRUE AS TIME)"));
        assertEquals(conversion("TRY_CAST(TT.D)", "TO_TIME"), refusal("SELECT TRY_CAST(d AS TIME) FROM tt"));
        assertEquals(conversion("TRY_CAST(123)", "TO_TIME"), refusal("SELECT TRY_CAST(123 AS TIME)"));
        assertEquals(conversion("TRY_CAST(TT.BN)", "TO_NUMBER"), refusal("SELECT TRY_CAST(bn AS NUMBER) FROM tt"));
        assertEquals(conversion("TRY_CAST(123)", "TO_BINARY"), refusal("SELECT TRY_CAST(123 AS BINARY)"));
        assertEquals(conversion("TRY_CAST(TT.O)", "TO_NUMBER"), refusal("SELECT TRY_CAST(o AS NUMBER) FROM tt"));
        assertEquals(conversion("TRY_CAST(TRUE)", "TO_DATE"), refusal("SELECT TRY_CAST(TRUE AS DATE)"));
        assertEquals(conversion("TRY_CAST(TT.F)", "TO_DATE"), refusal("SELECT TRY_CAST(f AS DATE) FROM tt"));
        assertEquals(conversion("TRY_CAST(TO_DOUBLE(1.5))", "TO_DATE"), refusal("SELECT TRY_CAST(1.5::FLOAT AS DATE)"));
        assertEquals(conversion("TRY_CAST(TRUE)", "TO_TIMESTAMP_LTZ"), refusal("SELECT TRY_CAST(TRUE AS TIMESTAMP_LTZ)"));
        assertEquals("true", cell("SELECT TRY_CAST(TRUE AS BOOLEAN)"));
        assertEquals("2020-01-01", cell("SELECT TRY_CAST(d AS DATE)::VARCHAR FROM tt"));
        assertEquals("0A0B", cell("SELECT HEX_ENCODE(TRY_CAST(bn AS BINARY)) FROM tt"));
        assertEquals("1.5", cell("SELECT TRY_CAST(f AS FLOAT) FROM tt"));
        assertEquals("5", cell("SELECT TRY_CAST(s AS NUMBER) FROM tt"));
        assertEquals(conversion("CAST(TT.D AS TIME(9))", "TO_TIME"), refusal("SELECT CAST(d AS TIME) FROM tt"));
        assertEquals(conversion("CAST(123 AS TIME(9))", "TO_TIME"), refusal("SELECT CAST(123 AS TIME)"));
        assertEquals(conversion("CAST(TRUE AS TIME(9))", "TO_TIME"), refusal("SELECT CAST(TRUE AS TIME)"));
        assertEquals(conversion("CAST(TT.B AS TIME(9))", "TO_TIME"), refusal("SELECT CAST(b AS TIME) FROM tt"));
        assertEquals(conversion("CAST(TT.D AS TIME(3))", "TO_TIME"), refusal("SELECT CAST(d AS TIME(3)) FROM tt"));
        assertEquals(conversion("CAST(TT.F AS TIME(9))", "TO_TIME"), refusal("SELECT CAST(f AS TIME) FROM tt"));
        assertEquals(conversion("CAST(TRUE AS DATE)", "TO_DATE"), refusal("SELECT CAST(TRUE AS DATE)"));
        assertEquals(conversion("CAST(123 AS DATE)", "TO_DATE"), refusal("SELECT CAST(123 AS DATE)"));
        assertEquals(conversion("CAST(TT.O AS DATE)", "TO_DATE"), refusal("SELECT CAST(o AS DATE) FROM tt"));
        assertEquals(conversion("CAST(TT.T AS DATE)", "TO_DATE"), refusal("SELECT CAST(t AS DATE) FROM tt"));
        assertEquals(conversion("CAST(TT.BN AS DATE)", "TO_DATE"), refusal("SELECT CAST(bn AS DATE) FROM tt"));
        assertEquals(conversion("CAST(TT.F AS DATE)", "TO_DATE"), refusal("SELECT CAST(f AS DATE) FROM tt"));
        assertEquals(conversion("CAST(TO_DOUBLE(1.5) AS DATE)", "TO_DATE"), refusal("SELECT CAST(1.5::FLOAT AS DATE)"));
        assertEquals(conversion("CAST(123 AS BINARY(67108864))", "TO_BINARY"), refusal("SELECT CAST(123 AS BINARY)"));
        assertEquals(conversion("CAST(TRUE AS BINARY(67108864))", "TO_BINARY"), refusal("SELECT CAST(TRUE AS BINARY)"));
        assertEquals(conversion("CAST(TT.N AS BINARY(67108864))", "TO_BINARY"), refusal("SELECT CAST(n AS BINARY) FROM tt"));
        assertEquals(conversion("CAST(TT.D AS NUMBER(38,0))", "TO_NUMBER"), refusal("SELECT CAST(d AS NUMBER) FROM tt"));
        assertEquals(conversion("CAST(TT.D AS NUMBER(5,2))", "TO_NUMBER"), refusal("SELECT CAST(d AS NUMBER(5,2)) FROM tt"));
        assertEquals(conversion("CAST(TT.T AS NUMBER(38,0))", "TO_NUMBER"), refusal("SELECT CAST(t AS NUMBER) FROM tt"));
        assertEquals(conversion("CAST(TT.BN AS NUMBER(38,0))", "TO_NUMBER"), refusal("SELECT CAST(bn AS NUMBER) FROM tt"));
        assertEquals(conversion("CAST(TT.B5 AS NUMBER(38,0))", "TO_NUMBER"), refusal("SELECT CAST(b5 AS NUMBER) FROM tt"));
        assertEquals(conversion("CAST(TT.O AS NUMBER(38,0))", "TO_NUMBER"), refusal("SELECT CAST(o AS NUMBER) FROM tt"));
        assertEquals(conversion("CAST(TT.A AS NUMBER(38,0))", "TO_NUMBER"), refusal("SELECT CAST(a AS NUMBER) FROM tt"));
        assertEquals(conversion("CAST(TT.D AS FLOAT)", "TO_DOUBLE"), refusal("SELECT CAST(d AS FLOAT) FROM tt"));
        assertEquals(conversion("CAST(TT.D AS BOOLEAN)", "TO_BOOLEAN"), refusal("SELECT CAST(d AS BOOLEAN) FROM tt"));
        assertEquals(conversion("CAST(TT.BN AS BOOLEAN)", "TO_BOOLEAN"), refusal("SELECT CAST(bn AS BOOLEAN) FROM tt"));
        assertEquals(conversion("CAST(TT.TS AS BOOLEAN)", "TO_BOOLEAN"), refusal("SELECT CAST(ts AS BOOLEAN) FROM tt"));
        assertEquals(conversion("CAST(TT.F AS BOOLEAN)", "TO_BOOLEAN"), refusal("SELECT CAST(f AS BOOLEAN) FROM tt"));
        assertEquals(conversion("CAST(TRUE AS TIMESTAMP_NTZ(9))", "TO_TIMESTAMP_NTZ"), refusal("SELECT CAST(TRUE AS TIMESTAMP)"));
        assertEquals(conversion("CAST(TRUE AS TIMESTAMP_LTZ(9))", "TO_TIMESTAMP_LTZ"), refusal("SELECT CAST(TRUE AS TIMESTAMP_LTZ)"));
        assertEquals(conversion("CAST(TRUE AS TIMESTAMP_TZ(3))", "TO_TIMESTAMP_TZ"), refusal("SELECT CAST(TRUE AS TIMESTAMP_TZ(3))"));
        assertEquals(conversion("CAST(TT.F AS TIMESTAMP_NTZ(9))", "TO_TIMESTAMP_NTZ"), refusal("SELECT CAST(f AS TIMESTAMP) FROM tt"));
        // The castable pairs convert; a BOOLEAN into an exact number is the unscaled 1 typed NUMBER(2,0).
        assertEquals("1", cell("SELECT TRUE::NUMBER"));
        assertEquals("1", cell("SELECT TRUE::NUMBER(5,1)"));
        assertEquals("NUMBER(2,0)[SB1]", cell("SELECT SYSTEM$TYPEOF(TRUE::NUMBER(5,1))"));
        assertEquals("1", cell("SELECT TRUE::NUMBER(5,1)::VARCHAR"));
        assertEquals("2020-01-01", cell("SELECT CAST(d AS VARCHAR) FROM tt"));
    }
}
