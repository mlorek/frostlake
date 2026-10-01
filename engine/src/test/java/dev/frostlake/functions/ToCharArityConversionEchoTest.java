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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TO_CHAR and TO_VARCHAR refuse a format beside a VARCHAR or a VARIANT with the arity sentence, and the call they
 * echo prints each conversion as the function the plan made of it: TO_DATE, TO_TIME, TO_TIMESTAMP_NTZ, TO_NUMBER,
 * FIXED_TO_FIXED, TO_DOUBLE, TO_BOOLEAN, TO_BINARY, TO_CHAR, identity, TEXT_TO_TEXT, GET, the typed nulls, and
 * IFNULL for COALESCE. Each expected answer is the account's own.
 */
public class ToCharArityConversionEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (s VARCHAR, d DATE, v VARIANT, n NUMBER(10,2), f FLOAT)");
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

    @Test
    public void theEchoPrintsEachConversionAsItsPlannedFunction() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_DATE('2024-01-15')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT('2024-01-15'::DATE), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_DATE('2024-01-15')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(CAST('2024-01-15' AS DATE)), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_TIME('10:00:00')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT('10:00:00'::TIME), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_TIMESTAMP_NTZ('2024-01-15 10:00:00')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT('2024-01-15 10:00:00'::TIMESTAMP_NTZ), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_TIMESTAMP_LTZ(T.S)), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(s::TIMESTAMP_LTZ), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_DATE(T.S)), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(s::DATE), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_NUMBER('5')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT('5'::NUMBER), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(FIXED_TO_FIXED(5 AS NUMBER(38,0)[UNKNOWN])), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(5::NUMBER), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_DOUBLE('1.5')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT('1.5'::FLOAT), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_BOOLEAN('true')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT('true'::BOOLEAN), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_BINARY('ab')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT('ab'::BINARY), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_CHAR(T.D)), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(d::VARCHAR), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_CHAR(1)), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(1::VARCHAR), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(identity('x'), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR('x'::VARCHAR, 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(GET(T.V, 'a'), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(v:a, 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(T.D), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(d), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(T.S, 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(s, 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_DATE('2024-01-15')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(TO_DATE('2024-01-15')), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(PARSE_JSON('1'), '999')] expected 1, got 2",
            answer("SELECT TO_CHAR(PARSE_JSON('1'), '999') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_VARCHAR(TO_VARIANT(TO_DATE('2024-01-15')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_VARCHAR(TO_VARIANT('2024-01-15'::DATE), 'YYYY') FROM t"));
    }

    @Test
    public void theEchoPrintsEachConversionAsItsPlannedFunction2() {
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(SYSTEM$NULL_TO_TEXT(NULL), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(NULL::VARCHAR, 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(T.S || 'x', 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(s || 'x', 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(UPPER(T.S), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(UPPER(s), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(GET(T.V, 'a'), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(v['a'], 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_DOUBLE(1.5)), '999')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(1.5::FLOAT), '999') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(FIXED_TO_FIXED(T.N AS NUMBER(12,4)[UNKNOWN])), '999')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(n::NUMBER(12,4)), '999') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(identity(T.N)), '999')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(n::NUMBER(10,2)), '999') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TEXT_TO_TEXT(T.S), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(s::VARCHAR(5), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(IFNULL(T.S, 'x'), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(COALESCE(s, 'x'), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_NUMBER(T.F)), '999')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(f::NUMBER), '999') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_DOUBLE(T.N)), '999')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(n::FLOAT), '999') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(identity(TO_VARIANT(T.S)), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(s::VARIANT), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(TO_TIMESTAMP(T.D)), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(d::TIMESTAMP), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_CHAR(GET(T.V, 'a')), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(v:a::VARCHAR, 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(SYSTEM$NULL_TO_VARIANT(NULL), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(NULL), 'YYYY') FROM t"));
        assertEquals("SQL compilation error: error line 1 at position 7|too many arguments for function [TO_CHAR(TO_VARIANT(1), 'YYYY')] expected 1, got 2",
            answer("SELECT TO_CHAR(TO_VARIANT(1), 'YYYY') FROM t"));
    }
}
