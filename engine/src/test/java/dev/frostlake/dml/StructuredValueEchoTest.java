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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A semi-structured value written to a structured column, refused by the types before a row is read, and a
 * VALUES item refused with the expression the statement compiled: [..] as ARRAY_CONSTRUCT, {..} as
 * OBJECT_CONSTRUCT, :: as a CAST to the type in full, an element appended to a structured ARRAY converted to its
 * type (all live-verified).
 */
public class StructuredValueEchoTest extends MeasuredCellsTestSupport {

    @Test
    public void structuredWrites() {
        engine.execute("CREATE OR REPLACE TABLE t2079 (arr ARRAY(NUMBER(10,2)))");
        engine.execute("CREATE OR REPLACE TABLE o2079 (o OBJECT(a INT))");
        engine.execute("CREATE OR REPLACE TABLE m2079 (m MAP(VARCHAR, INT))");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(10,2))]",
            "INSERT INTO t2079 SELECT ARRAY_CONSTRUCT(1) FROM t2079");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(10,2))]",
            "INSERT INTO t2079 SELECT ARRAY_CONSTRUCT('zz'::NUMBER) FROM t2079");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(10,2))]",
            "INSERT INTO t2079 SELECT ARRAY_CONSTRUCT(1)");
        assertAnswer("1", "INSERT INTO t2079 SELECT ARRAY_CONSTRUCT(1)::ARRAY(NUMBER(10,2))");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(10,2))]",
            "INSERT INTO t2079 SELECT ARRAY_CONSTRUCT(1) FROM t2079");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(10,2))]",
            "INSERT INTO t2079 SELECT [1, 2] FROM t2079");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [VARIANT] and [ARRAY(NUMBER(10,2))]",
            "INSERT INTO t2079 SELECT PARSE_JSON('[1]') FROM t2079");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [VARIANT] and [ARRAY(NUMBER(10,2))]",
            "INSERT INTO t2079 SELECT TO_VARIANT(ARRAY_CONSTRUCT(1)) FROM t2079");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(10,2))]",
            "INSERT INTO t2079 SELECT ARRAY_CONSTRUCT('zz'::NUMBER) FROM t2079");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(10,2))]",
            "INSERT INTO t2079 (arr) SELECT ARRAY_CONSTRUCT(1) FROM t2079");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [OBJECT] and [OBJECT(a NUMBER(38,0))]",
            "INSERT INTO o2079 SELECT OBJECT_CONSTRUCT('a', 1)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [OBJECT] and [MAP(VARCHAR(16777216), "
                + "NUMBER(38,0))]",
            "INSERT INTO m2079 SELECT OBJECT_CONSTRUCT('a', 1)");
        assertAnswer("REFUSED SQL compilation error: | incompatible types: [ARRAY] and [ARRAY(NUMBER(10,2))]",
            "UPDATE t2079 SET arr = ARRAY_CONSTRUCT(2)");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [ARRAY_APPEND(CAST(ARRAY_CONSTRUCT(1) AS "
                + "ARRAY(NUMBER(10,2))), 2)] in VALUES clause",
            "INSERT INTO t2079 (arr) VALUES (ARRAY_APPEND([1]::ARRAY(NUMBER(10,2)), 2))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [ARRAY_APPEND(CAST(ARRAY_CONSTRUCT(1) AS "
                + "ARRAY(NUMBER(10,2))), TO_NUMBER('zz', 10, 2))] in VALUES clause",
            "INSERT INTO t2079 (arr) VALUES (ARRAY_APPEND([1]::ARRAY(NUMBER(10,2)), 'zz'))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [ARRAY_APPEND(CAST(ARRAY_CONSTRUCT(1) AS "
                + "ARRAY(NUMBER(10,2))), 2.5)] in VALUES clause",
            "INSERT INTO t2079 (arr) VALUES (ARRAY_APPEND([1]::ARRAY(NUMBER(10,2)), 2.5))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [ARRAY_CONSTRUCT(1, 2)] in VALUES clause",
            "INSERT INTO t2079 (arr) VALUES ([1, 2])");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [CAST(ARRAY_CONSTRUCT(1) AS ARRAY(NUMBER(10,"
                + "2)))] in VALUES clause",
            "INSERT INTO t2079 (arr) VALUES ([1]::ARRAY(NUMBER(10,2)))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [ARRAY_CONSTRUCT(1, 2)] in VALUES clause",
            "INSERT INTO t2079 (arr) VALUES (ARRAY_CONSTRUCT(1, 2))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [PARSE_JSON('[1]')] in VALUES clause",
            "INSERT INTO t2079 (arr) VALUES (PARSE_JSON('[1]'))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [OBJECT_CONSTRUCT('a', 1)] in VALUES clause",
            "INSERT INTO o2079 (o) VALUES ({'a': 1})");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [CAST(OBJECT_CONSTRUCT('a', 1) AS OBJECT(a "
                + "NUMBER(38,0)))] in VALUES clause",
            "INSERT INTO o2079 (o) VALUES ({'a': 1}::OBJECT(a INT))");
        assertAnswer("REFUSED SQL compilation error: | Invalid expression [CAST(1 AS VARIANT)] in VALUES clause",
            "INSERT INTO t2079 (arr) VALUES (1::VARIANT)");
        assertAnswer("1", "SELECT COUNT(*) FROM t2079");
    }
}
