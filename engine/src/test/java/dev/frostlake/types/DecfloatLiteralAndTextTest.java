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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The typed literal DECFLOAT '<text>' is the DECFLOAT its text spells, a text that spells none refused while the
 * statement compiles; a DECFLOAT read as text anywhere - TO_VARCHAR, ||, CONCAT, LIKE, a text function's subject,
 * a hash - is its own text; and over a DECFLOAT the variance and deviation family, PERCENTILE_CONT and the
 * windowed SUM and AVG are DECFLOATs of 38 digits (all live-verified).
 */
public class DecfloatLiteralAndTextTest extends MeasuredCellsTestSupport {

    @Test
    public void theTypedLiteralReadsItsText() {
        engine.execute("CREATE OR REPLACE TABLE dag (d DECFLOAT)");
        engine.execute("INSERT INTO dag VALUES (DECFLOAT '1e1000'), (DECFLOAT '-2.47e999'), (DECFLOAT '22e-75')");
        engine.execute("CREATE OR REPLACE TABLE dsm (id INT, d DECFLOAT)");
        engine.execute("INSERT INTO dsm VALUES (1, DECFLOAT '123e7000'), (2, "
            + "12345678901234567890123456789::DECFLOAT), (3, '-4.2e-5432'::DECFLOAT), (4, "
            + "'1.00000000000000000000000000000000000014'::DECFLOAT), (5, "
            + "'1.00000000000000000000000000000000000015'::DECFLOAT)");
        assertCells(new String[][] {
            {"SELECT (DECFLOAT '1.5')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1.5')", "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '123e7000')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '123e7000')",
                "1.23e7002|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '-4.2e-5432')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '-4.2e-5432')",
                "-4.2e-5432|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (decfloat '1.5')::VARCHAR, SYSTEM$TYPEOF(decfloat '1.5')", "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT ' 1.5 ')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT ' 1.5 ')", "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1.00000000000000000000000000000000000015')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT "
                + "'1.00000000000000000000000000000000000015')",
                "1.0000000000000000000000000000000000002|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1.00000000000000000000000000000000000014')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT "
                + "'1.00000000000000000000000000000000000014')",
                "1.0000000000000000000000000000000000001|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1e16384')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1e16384')",
                "1e16384|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1e16385')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1e16385')",
                "10e16384|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '9.9e16384')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '9.9e16384')",
                "9.9e16384|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1e-16383')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1e-16383')",
                "1e-16383|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1e-16400')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1e-16400')", "0|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1e-20000')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1e-20000')", "0|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT ('1e20000'::DECFLOAT)::VARCHAR, SYSTEM$TYPEOF('1e20000'::DECFLOAT)",
                "REFUSED Numeric value '1e20000' is not recognized"},
            {"SELECT ('1e-20000'::DECFLOAT)::VARCHAR, SYSTEM$TYPEOF('1e-20000'::DECFLOAT)",
                "0|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT 'abc')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT 'abc')",
                "REFUSED SQL compilation error: | Can not convert parameter ''abc'' of type [VARCHAR] into expected "
                + "type [DECFLOAT]"},
            {"SELECT (DECFLOAT 'NaN')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT 'NaN')",
                "REFUSED SQL compilation error: | Can not convert parameter ''NaN'' of type [VARCHAR] into expected "
                + "type [DECFLOAT]"},
            {"SELECT (DECFLOAT 'inf')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT 'inf')",
                "REFUSED SQL compilation error: | Can not convert parameter ''inf'' of type [VARCHAR] into expected "
                + "type [DECFLOAT]"},
            {"SELECT ('NaN'::DECFLOAT)::VARCHAR, SYSTEM$TYPEOF('NaN'::DECFLOAT)",
                "REFUSED Numeric value 'NaN' is not recognized"},
            {"SELECT (DECFLOAT '')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '')",
                "REFUSED SQL compilation error: | Can not convert parameter '''' of type [VARCHAR] into expected "
                + "type [DECFLOAT]"},
            {"SELECT (DECFLOAT '1e1000' + DECFLOAT '-2.47e999')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1e1000' + DECFLOAT "
                + "'-2.47e999')",
                "7.53e999|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1e16384' * 10)::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1e16384' * 10)",
                "10e16384|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1e-16383' / 10)::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1e-16383' / 10)",
                "0|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (POWER(10::DECFLOAT, 20000))::VARCHAR, SYSTEM$TYPEOF(POWER(10::DECFLOAT, 20000))",
                "REFUSED Decfloat out of representable range, operation: POW(10, 20000)"},
            {"SELECT (EXP(100000::DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(EXP(100000::DECFLOAT))",
                "REFUSED Decfloat out of representable range, operation: EXP(100000)"},
            {"SELECT (DECFLOAT '1e10000' * DECFLOAT '1e10000')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1e10000' * DECFLOAT "
                + "'1e10000')",
                "REFUSED Decfloat out of representable range, operation: 1e10000 * 1e10000"},
            {"SELECT (DECFLOAT '0.1' + DECFLOAT '0.2')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '0.1' + DECFLOAT '0.2')",
                "0.3|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '-0')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '-0')", "0|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1.50')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1.50')", "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '+1.5')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '+1.5')", "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '.5')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '.5')", "0.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '5.')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '5.')", "5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '1E5')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1E5')", "100000|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '0x10')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '0x10')",
                "REFUSED SQL compilation error: | Can not convert parameter ''0x10'' of type [VARCHAR] into expected "
                + "type [DECFLOAT]"},
            {"SELECT (DECFLOAT '1,5')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT '1,5')",
                "REFUSED SQL compilation error: | Can not convert parameter ''1,5'' of type [VARCHAR] into expected "
                + "type [DECFLOAT]"},
            {"SELECT (TO_DECFLOAT('1e16385'))::VARCHAR, SYSTEM$TYPEOF(TO_DECFLOAT('1e16385'))",
                "10e16384|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (TRY_TO_DECFLOAT('1e16385'))::VARCHAR, SYSTEM$TYPEOF(TRY_TO_DECFLOAT('1e16385'))",
                "10e16384|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (TRY_CAST('1e16385' AS DECFLOAT))::VARCHAR, SYSTEM$TYPEOF(TRY_CAST('1e16385' AS DECFLOAT))",
                "10e16384|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT (DECFLOAT '12345678901234567890123456789012345678901234567890')::VARCHAR, SYSTEM$TYPEOF(DECFLOAT "
                + "'12345678901234567890123456789012345678901234567890')",
                "1.2345678901234567890123456789012345679e49|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT SUM(d)::VARCHAR, AVG(d)::VARCHAR, MAX(d)::VARCHAR, MIN(d)::VARCHAR FROM dag",
                "7.53e999|2.51e999|1e1000|-2.47e999"},
            {"SELECT DECFLOAT '1.5' = 1.5, DECFLOAT '1.5' AS x", "true|1.5"},
        });
    }

    @Test
    public void itsTextIsItsOwnWhereverItIsRead() {
        assertCells(new String[][] {
            {"SELECT TO_VARCHAR(1::DECFLOAT / 3), TO_CHAR(1::DECFLOAT / 3), (1::DECFLOAT / 3) || '', "
                + "CONCAT(1::DECFLOAT / 3, ''), LENGTH(1::DECFLOAT / 3)",
                "3.3333333333333333333333333333333333333e-1|3.3333333333333333333333333333333333333e-1|"
                + "3.3333333333333333333333333333333333333e-1|3.3333333333333333333333333333333333333e-1|42"},
            {"SELECT TO_VARCHAR(1e40::DECFLOAT), 1e40::DECFLOAT || '', TO_VARCHAR(1.50::DECFLOAT), CONCAT_WS(',', "
                + "1::DECFLOAT / 3, 1.5::DECFLOAT)",
                "1.0000000000000000303786028427003666891e40|1.0000000000000000303786028427003666891e40|1.5|"
                + "3.3333333333333333333333333333333333333e-1,1.5"},
            {"SELECT UPPER(1::DECFLOAT / 3), SUBSTR(1::DECFLOAT / 3, 1, 6), SPLIT_PART(1::DECFLOAT / 3, '.', 1), "
                + "REPLACE(1::DECFLOAT / 3, '3', 'x')",
                "3.3333333333333333333333333333333333333E-1|3.3333|3|x.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxe-1"},
            {"SELECT TO_VARCHAR(1::DECFLOAT / 3, '9.999'), TO_CHAR(1.5::DECFLOAT, '0.00'), TO_VARCHAR(DECFLOAT "
                + "'123e7000')",
                " 0.333| 1.50|1.23e7002"},
            {"SELECT (1::DECFLOAT / 3) = '3.3333333333333333333333333333333333333e-1', (1::DECFLOAT / 3)::VARCHAR = "
                + "TO_VARCHAR(1::DECFLOAT / 3)",
                "true|true"},
            {"SELECT ARRAY_TO_STRING(ARRAY_CONSTRUCT(1::DECFLOAT / 3, 1.5::DECFLOAT), ',')",
                "3.3333333333333333333333333333333333333e-1,1.5"},
            {"SELECT CAST(1::DECFLOAT / 3 AS VARCHAR(10))",
                "REFUSED String '3.3333333333333333333333333333333333333e-1' is too long and would be truncated"},
            {"SELECT CAST(1::DECFLOAT / 3 AS VARCHAR(50))", "3.3333333333333333333333333333333333333e-1"},
            {"SELECT 1::DECFLOAT / 3 LIKE '3.3%', ILIKE(1::DECFLOAT / 3, '%e-1')", "true|true"},
            {"SELECT TO_VARCHAR(-0.5::DECFLOAT), TO_VARCHAR(0::DECFLOAT), TO_VARCHAR(100::DECFLOAT), "
                + "TO_VARCHAR(1E-10::DECFLOAT)",
                "-0.5|0|100|0.0000000001"},
            {"SELECT 1::DECFLOAT / 3::NUMBER(38,37)::VARCHAR, (1::DECFLOAT / 3)::INT, (2.5::DECFLOAT)::INT, "
                + "(-2.5::DECFLOAT)::INT",
                "0.33333333333333333333333333333333333333|0|3|-3"},
            {"SELECT TO_BOOLEAN(1::DECFLOAT), TO_BOOLEAN(0::DECFLOAT), (1.5::DECFLOAT)::BOOLEAN", "true|false|true"},
            {"SELECT TO_DATE(1.5::DECFLOAT)",
                "REFUSED SQL compilation error: | invalid type [TO_DATE(TO_DECFLOAT(1.5))] for parameter 'TO_DATE'"},
            {"SELECT TO_TIMESTAMP(1600000000::DECFLOAT)",
                "REFUSED SQL compilation error: | invalid type [TO_TIMESTAMP(TO_DECFLOAT(1600000000))] for parameter "
                + "'TO_TIMESTAMP_NTZ'"},
            {"SELECT TO_BINARY(1.5::DECFLOAT)",
                "REFUSED SQL compilation error: | invalid type [TO_BINARY(TO_DECFLOAT(1.5))] for parameter "
                + "'TO_BINARY'"},
            {"SELECT HEX_ENCODE(1.5::DECFLOAT)", "312E35"},
            {"SELECT MD5(1::DECFLOAT / 3), SHA1(1.5::DECFLOAT)",
                "05486607f69bcb502d84c7bddab773e1|aa8f289ebe6d4db1b4a1038b8931ec8c2b5399fb"},
        });
    }

    @Test
    public void theLiteralRefusesWhatSpellsNoDecfloat() {
        assertCells(new String[][] {
            {"SELECT DECFLOAT '1e20000'",
                "REFUSED SQL compilation error: | Can not convert parameter ''1e20000'' of type [VARCHAR] into "
                + "expected type [DECFLOAT]"},
            {"SELECT DECFLOAT '1e-20000'::VARCHAR", "0"},
            {"SELECT DECFLOAT 'abc' FROM (SELECT 1 AS a WHERE FALSE)",
                "REFUSED SQL compilation error: | Can not convert parameter ''abc'' of type [VARCHAR] into expected "
                + "type [DECFLOAT]"},
            {"SELECT DECFLOAT '1.5' + DECFLOAT 'x'",
                "REFUSED SQL compilation error: | Can not convert parameter ''x'' of type [VARCHAR] into expected "
                + "type [DECFLOAT]"},
            {"SELECT DECFLOAT '1.5'::NUMBER(10,2), DECFLOAT '1.5'::FLOAT, SYSTEM$TYPEOF(DECFLOAT '1.5'::NUMBER(10,2))",
                "1.50|1.5|NUMBER(10,2)[SB2]"},
            {"SELECT DECFLOAT($$1.5$$)", "REFUSED SQL compilation error: | Unknown function DECFLOAT."},
            {"SELECT DECFLOAT '1.5' AS d, d * 2", "1.5|3"},
            {"SELECT TYPEOF(TO_VARIANT(DECFLOAT '1.5'))", "DECFLOAT"},
            {"SELECT DECFLOAT ''''",
                "REFUSED SQL compilation error: | Can not convert parameter '''''' of type [VARCHAR] into expected "
                + "type [DECFLOAT]"},
            {"SELECT DECFLOAT '1e5'::VARCHAR, DECFLOAT '1E+5'::VARCHAR, DECFLOAT '1e-5'::VARCHAR, DECFLOAT "
                + "'00012.5000'::VARCHAR",
                "100000|100000|0.00001|12.5"},
        });
    }

    @Test
    public void theStatisticsKeepADecfloat() {
        assertCells(new String[][] {
            {"SELECT STDDEV(x)::VARCHAR, SYSTEM$TYPEOF(STDDEV(x)), VARIANCE(x)::VARCHAR, SYSTEM$TYPEOF(VARIANCE(x)) "
                + "FROM (SELECT 1::DECFLOAT AS x UNION ALL SELECT 2::DECFLOAT UNION ALL SELECT 4::DECFLOAT)",
                "1.527525231651946668862682397909336163|DECFLOAT(38)[DECFLOAT18]|"
                + "2.3333333333333333333333333333333333333|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT STDDEV_POP(x)::VARCHAR, STDDEV_SAMP(x)::VARCHAR, VAR_POP(x)::VARCHAR, VAR_SAMP(x)::VARCHAR, "
                + "VARIANCE_POP(x)::VARCHAR, VARIANCE_SAMP(x)::VARCHAR FROM (SELECT 1::DECFLOAT AS x UNION ALL SELECT "
                + "2::DECFLOAT UNION ALL SELECT 4::DECFLOAT)",
                "1.2472191289246471285279162441055164339|1.527525231651946668862682397909336163|"
                + "1.5555555555555555555555555555555555556|2.3333333333333333333333333333333333333|"
                + "1.5555555555555555555555555555555555556|2.3333333333333333333333333333333333333"},
            {"SELECT SYSTEM$TYPEOF(STDDEV_POP(x)), SYSTEM$TYPEOF(VAR_SAMP(x)) FROM (SELECT 1::DECFLOAT AS x)",
                "DECFLOAT(38)[DECFLOAT18]|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT STDDEV(x)::VARCHAR FROM (SELECT 1::DECFLOAT AS x)", "null"},
            {"SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY x)::VARCHAR, SYSTEM$TYPEOF(PERCENTILE_CONT(0.5) "
                + "WITHIN GROUP (ORDER BY x)) FROM (SELECT 1::DECFLOAT AS x UNION ALL SELECT 2::DECFLOAT)",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT PERCENTILE_DISC(0.5) WITHIN GROUP (ORDER BY x)::VARCHAR, SYSTEM$TYPEOF(PERCENTILE_DISC(0.5) "
                + "WITHIN GROUP (ORDER BY x)) FROM (SELECT 1::DECFLOAT AS x UNION ALL SELECT 2::DECFLOAT)",
                "1|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT STDDEV(x) OVER ()::VARCHAR FROM (SELECT 1::DECFLOAT AS x UNION ALL SELECT 2::DECFLOAT) LIMIT 1",
                "7.0710678118654752440084436210484903928e-1"},
            {"SELECT MODE(x)::VARCHAR, SYSTEM$TYPEOF(MODE(x)) FROM (SELECT 1.5::DECFLOAT AS x)",
                "1.5|DECFLOAT(38)[DECFLOAT18]"},
            {"SELECT APPROX_PERCENTILE(x, 0.5)::VARCHAR, SYSTEM$TYPEOF(APPROX_PERCENTILE(x, 0.5)) FROM (SELECT "
                + "1::DECFLOAT AS x UNION ALL SELECT 2::DECFLOAT)",
                "1.5|FLOAT[DOUBLE]"},
            {"SELECT BITAND(5::DECFLOAT, 3), BIT_AND(x) FROM (SELECT 5::DECFLOAT AS x)",
                "REFUSED SQL compilation error: error line 1 at position 31 | not enough arguments for function "
                + "[BIT_AND(\"values\".X)], expected 2, got 1"},
            {"SELECT WIDTH_BUCKET(2.5::DECFLOAT, 0, 10, 5), WIDTH_BUCKET(1.4::DECFLOAT, 0, 10, 5), WIDTH_BUCKET(5, "
                + "0::DECFLOAT, 10, 5)",
                "2|1|3"},
            {"SELECT SUBSTR('abcdef', 2.5::DECFLOAT, 2), LEFT('abcdef', 1.5::DECFLOAT), ROUND(1.2345, 2.5::DECFLOAT)",
                "cd|ab|1.2350"},
        });
    }
}
