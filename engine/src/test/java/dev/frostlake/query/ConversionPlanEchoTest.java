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
 * The conversions the plan inserts, as a refusal's echo re-prints them: a text literal that spells a number meets
 * a membership subquery typed by its own digits, a string function converts a value that is no text to the 128MB
 * text first, a text moved to an exact number is TO_NUMBER, and a TRY_CAST in the conversion spelling is its TRY_
 * function. Each expected answer is the account's own.
 */
public class ConversionPlanEchoTest extends BaseDatabaseTest {

    private static final String ARITY = "SQL compilation error: error line 1 at position 7|";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE re (a INT)");
        engine.execute("CREATE TABLE ft (b INT, d DATE)");
        engine.execute("CREATE TABLE t (s VARCHAR)");
        engine.execute("CREATE TABLE rt (n INT, f FLOAT, n52 NUMBER(5,2), d DATE, b BOOLEAN)");
        engine.execute("INSERT INTO rt VALUES (1, 1.5, 1.25, '2020-01-01', TRUE), (22, 2.5, 2.5, '2021-02-02', FALSE)");
    }

    /** The refusal as one line, each line break as |, or ACCEPTED. */
    private String answer(final String sql) {
        try {
            engine.executeQuery(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The first cell of the answer, as text. */
    private String value(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    /** CHARINDEX refusing its one argument, a membership test, whose re-print is the echo. */
    private String membership(final String test) {
        return answer("SELECT CHARINDEX(" + test + ")");
    }

    private static String notEnough(final String echo) {
        return ARITY + "not enough arguments for function [CHARINDEX(" + echo + ")], expected 2, got 1";
    }

    @Test
    public void aTextLiteralThatSpellsANumberIsTypedByItsDigits() {
        final String dual = " FROM (VALUES (null)) DUAL)";
        assertEquals(notEnough("(TO_NUMBER('5', 1, 0)) = ANY(SELECT 5 AS \"5\"" + dual),
            membership("'5' IN (SELECT 5)"));
        assertEquals(notEnough("(TO_NUMBER('5.5', 2, 1)) = ANY(SELECT CAST(5 AS NUMBER(2,1)) AS \"5\"" + dual),
            membership("'5.5' IN (SELECT 5)"));
        assertEquals(notEnough("(TO_NUMBER('5', 2, 1)) = ANY(SELECT 5.5 AS \"5.5\"" + dual),
            membership("'5' IN (SELECT 5.5)"));
        assertEquals(notEnough("(TO_NUMBER('-12', 2, 0)) = ANY(SELECT 5 AS \"5\"" + dual),
            membership("'-12' IN (SELECT 5)"));
        assertEquals(notEnough("(TO_NUMBER('1e3', 4, 0)) = ANY(SELECT 5 AS \"5\"" + dual),
            membership("'1e3' IN (SELECT 5)"));
        assertEquals(notEnough("(TO_NUMBER('1.50', 2, 1)) = ANY(SELECT CAST(5 AS NUMBER(2,1)) AS \"5\"" + dual),
            membership("'1.50' IN (SELECT 5)"));
        assertEquals(notEnough("(TO_NUMBER('.5', 2, 1)) = ANY(SELECT CAST(5 AS NUMBER(2,1)) AS \"5\"" + dual),
            membership("'.5' IN (SELECT 5)"));
        assertEquals(notEnough("(TO_NUMBER('1e-3', 4, 3)) = ANY(SELECT CAST(5 AS NUMBER(4,3)) AS \"5\"" + dual),
            membership("'1e-3' IN (SELECT 5)"));
        assertEquals(notEnough("(TO_NUMBER('5', 5, 0)) = ANY(SELECT 12345 AS \"12345\"" + dual),
            membership("'5' IN (SELECT 12345)"));
        assertEquals(notEnough("(TO_NUMBER('1e40')) = ANY(SELECT 5 AS \"5\"" + dual),
            membership("'1e40' IN (SELECT 5)"));
        assertEquals(notEnough("(TO_NUMBER('5')) = ANY(SELECT RE.A AS \"A\" FROM RE AS RE)"),
            membership("'5' IN (SELECT a FROM re)"));
        assertEquals(notEnough("(TO_NUMBER('5.123', 6, 3)) = ANY(SELECT CAST(RT.N52 AS NUMBER(6,3)) AS \"N52\" FROM RT AS RT)"),
            membership("'5.123' IN (SELECT n52 FROM rt)"));
        assertEquals(notEnough("(TO_NUMBER(' 5', 18, 5)) = ANY(SELECT CAST(5 AS NUMBER(18,5)) AS \"5\"" + dual),
            membership("' 5' IN (SELECT 5)"), "a text with a space reads as a column does");
        assertEquals(notEnough("(TO_NUMBER('5', 1, 0)) != ALL(SELECT 5 AS \"5\"" + dual),
            membership("'5' NOT IN (SELECT 5)"));
    }

    @Test
    public void aStringFunctionConvertsAValueThatIsNoTextFirst() {
        assertEquals("SQL compilation error:|Can not convert parameter 'ANY(SELECT FT.B AS \"B\", UPPER(CAST(FT.D AS"
                + " VARCHAR(134217728))) AS \"UPPER(D)\", 3 AS \"3\" FROM FT AS FT)' of type [ROW(NUMBER(38,0),"
                + " VARCHAR(134217728), NUMBER(1,0))] into expected type [ROW(NUMBER(38,0), NUMBER(38,0))]",
            answer("SELECT 1 FROM re WHERE (a, a) IN (SELECT b, UPPER(d), 3 FROM ft)"));
        assertEquals(ARITY + "too many arguments for function [ABS(LOWER(CAST(RT.N AS VARCHAR(134217728))), 1)] expected 1, got 2",
            answer("SELECT ABS(LOWER(n), 1) FROM rt"));
        assertEquals(ARITY + "too many arguments for function [ABS(TRIM(CAST(RT.N52 AS VARCHAR(134217728))), 1)] expected 1, got 2",
            answer("SELECT ABS(TRIM(n52), 1) FROM rt"));
        assertEquals(ARITY + "too many arguments for function [ABS(SUBSTR(CAST(RT.D AS VARCHAR(134217728)), 1, 2), 1)]"
                + " expected 1, got 2",
            answer("SELECT ABS(SUBSTR(d, 1, 2), 1) FROM rt"));
        assertEquals(ARITY + "too many arguments for function [ABS(SUBSTR(CAST(RT.N AS VARCHAR(134217728)), 1, 1), 1)]"
                + " expected 1, got 2",
            answer("SELECT ABS(LEFT(n, 1), 1) FROM rt"));
        assertEquals(ARITY + "too many arguments for function [ABS(LPAD(CAST(RT.N AS VARCHAR(134217728)), 5, '0'), 1)]"
                + " expected 1, got 2",
            answer("SELECT ABS(LPAD(n, 5, '0'), 1) FROM rt"));
        assertEquals(ARITY + "too many arguments for function [ABS(UPPER(CAST(RT.F AS VARCHAR(134217728))), 1)] expected 1, got 2",
            answer("SELECT ABS(UPPER(f), 1) FROM rt"));
        assertEquals(ARITY + "too many arguments for function [ABS(UPPER(CAST(TRUE AS VARCHAR(134217728))), 1)] expected 1, got 2",
            answer("SELECT ABS(UPPER(TRUE), 1) FROM rt"));
        assertEquals("SQL compilation error:|Invalid data type [VARCHAR(134217728)] for predicate [UPPER(CAST(RT.D AS"
                + " VARCHAR(134217728)))]",
            answer("SELECT 1 FROM rt WHERE UPPER(d)"));
        assertEquals("SQL compilation error:|argument 1 to function RANDOM needs to be constant, found 'RIGHT2(CAST(RT.N AS"
                + " VARCHAR(134217728)), 2)'",
            answer("SELECT RANDOM(RIGHT(n, 2)) FROM rt"));
        assertEquals("VARCHAR(134217728)[LOB]", value("SELECT SYSTEM$TYPEOF(UPPER(d)) FROM rt"));
        assertEquals("VARCHAR(134217728)[LOB]", value("SELECT SYSTEM$TYPEOF(SUBSTR(n, 1, 2)) FROM rt"));
        assertEquals("VARCHAR(134217728)[LOB]", value("SELECT SYSTEM$TYPEOF(UPPER(1))"));
    }

    @Test
    public void aTextMovedToAnExactNumberIsToNumber() {
        assertEquals(ARITY + "too many arguments for function [ABS(TO_NUMBER('5'), 1)] expected 1, got 2",
            answer("SELECT ABS('5'::NUMBER, 1)"));
        assertEquals(ARITY + "too many arguments for function [ABS(TO_NUMBER('5', 10, 2), 1)] expected 1, got 2",
            answer("SELECT ABS('5'::NUMBER(10,2), 1)"));
        assertEquals(ARITY + "too many arguments for function [ABS(TO_NUMBER('5', 10, 0), 1)] expected 1, got 2",
            answer("SELECT ABS('5'::NUMBER(10), 1)"));
        assertEquals(ARITY + "too many arguments for function [ABS(TO_NUMBER(T.S), 1)] expected 1, got 2",
            answer("SELECT ABS(s::NUMBER, 1) FROM t"));
        assertEquals(ARITY + "too many arguments for function [ABS(CAST('5' AS FLOAT), 1)] expected 1, got 2",
            answer("SELECT ABS('5'::FLOAT, 1)"), "a FLOAT target keeps its cast");
        assertEquals("SQL compilation error:|Invalid data type [NUMBER(38,0)] for predicate [TO_NUMBER(T.S)]",
            answer("SELECT 1 FROM t WHERE s::NUMBER"));
        assertEquals(ARITY + "too many arguments for function [ABS(TRY_CAST(T.S AS NUMBER(10,2)), 1)] expected 1, got 2",
            answer("SELECT ABS(TRY_CAST(s AS NUMBER(10,2)), 1) FROM t"), "the plan keeps its TRY_CAST");
    }

    @Test
    public void aTryCastInTheConversionSpellingIsItsTryFunction() {
        final String prefix = ARITY + "too many arguments for function [TO_CHAR(TO_VARIANT(";
        final String suffix = "), 'YYYY')] expected 1, got 2";
        assertEquals(prefix + "TRY_TO_DATE(T.S)" + suffix,
            answer("SELECT TO_CHAR(TO_VARIANT(TRY_CAST(s AS DATE)), 'YYYY') FROM t"));
        assertEquals(prefix + "TRY_TO_NUMBER(T.S)" + suffix,
            answer("SELECT TO_CHAR(TO_VARIANT(TRY_CAST(s AS INT)), 'YYYY') FROM t"));
        assertEquals(prefix + "TRY_TO_NUMBER(T.S)" + suffix,
            answer("SELECT TO_CHAR(TO_VARIANT(TRY_CAST(s AS DECIMAL(10,2))), 'YYYY') FROM t"));
        assertEquals(prefix + "TRY_TO_TIMESTAMP_NTZ(T.S)" + suffix,
            answer("SELECT TO_CHAR(TO_VARIANT(TRY_CAST(s AS TIMESTAMP)), 'YYYY') FROM t"));
        assertEquals(prefix + "TRY_TO_BOOLEAN(T.S)" + suffix,
            answer("SELECT TO_CHAR(TO_VARIANT(TRY_CAST(s AS BOOLEAN)), 'YYYY') FROM t"));
        assertEquals(prefix + "TRY_TO_TIME(T.S)" + suffix,
            answer("SELECT TO_CHAR(TO_VARIANT(TRY_CAST(s AS TIME)), 'YYYY') FROM t"));
        assertEquals(prefix + "TRY_TO_DOUBLE(T.S)" + suffix,
            answer("SELECT TO_CHAR(TO_VARIANT(TRY_CAST(s AS DOUBLE)), 'YYYY') FROM t"));
        assertEquals(prefix + "identity(T.S)" + suffix,
            answer("SELECT TO_CHAR(TO_VARIANT(TRY_CAST(s AS VARCHAR)), 'YYYY') FROM t"));
        assertEquals(ARITY + "too many arguments for function [TO_CHAR(TO_VARIANT(TRY_TO_TEXT(T.S)), 'x')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(TRY_CAST(s AS VARCHAR(5))), 'x') FROM t"));
    }
}
