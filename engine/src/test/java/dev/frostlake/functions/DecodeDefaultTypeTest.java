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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * The type DECODE answers without a default. No default is a default of NULL, read as a CASE's missing ELSE
 * is: a text DECODE without one is VARCHAR(134217728) whatever width its results carry - {@code DECODE(n, 1,
 * 'x')} and {@code DECODE(n, 1, v)} alike - while one with a default takes the widest result, and a NUMBER one
 * keeps its own type either way. Every answer is live-verified.
 */
public class DecodeDefaultTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void aTextDecodeWithoutADefaultIsFullWidth() {
        answer(
            "CREATE OR REPLACE TABLE iw (v VARCHAR(10), n INT)");
        answer(
            "INSERT INTO iw VALUES ('a', 1)");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(2)[LOB]",
            "SELECT SYSTEM$TYPEOF(DECODE('a', NULL, 'x')), SYSTEM$TYPEOF(DECODE(1, 2, 'x')), "
                + "SYSTEM$TYPEOF(DECODE(1, 2, 'x', 'yy'))");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(3)[LOB]",
            "SELECT SYSTEM$TYPEOF(DECODE(NULL, NULL, 'x')), SYSTEM$TYPEOF(DECODE(NULL, 1, 'x', 'yyy'))");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(2)[LOB]|VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(DECODE(v, NULL, 'x')), SYSTEM$TYPEOF(DECODE(v, 'a', 'x')), "
                + "SYSTEM$TYPEOF(DECODE(v, 'a', 'x', 'yz')), SYSTEM$TYPEOF(DECODE(v, 'a', v)) FROM iw");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(10)[LOB]|NUMBER(1,0)[SB1]|NUMBER(2,1)[SB1]",
            "SELECT SYSTEM$TYPEOF(DECODE(n, 1, 'x')), SYSTEM$TYPEOF(DECODE(n, 1, v)), SYSTEM$TYPEOF(DECODE(n, 1, "
                + "'x', v)), SYSTEM$TYPEOF(DECODE(n, 1, 2)), SYSTEM$TYPEOF(DECODE(n, 1, 2, 3.5)) FROM iw");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]|VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(DECODE(v::VARCHAR, NULL, 'x')), SYSTEM$TYPEOF(DECODE(NULL::VARCHAR, NULL, 'x')),"
                + " SYSTEM$TYPEOF(DECODE(NULL::VARCHAR(5), NULL, 'x')) FROM iw");
        assertAnswer(
            "VARCHAR(134217728)[LOB]|VARCHAR(2)[LOB]|VARCHAR(134217728)[LOB]",
            "SELECT SYSTEM$TYPEOF(DECODE(self_referencing_column_name, 'a', 'x')), "
                + "SYSTEM$TYPEOF(DECODE(self_referencing_column_name, NULL, 'x', 'yy')), "
                + "SYSTEM$TYPEOF(DECODE(table_name, NULL, 'x')) FROM information_schema.tables WHERE table_schema = "
                + "'TEST_SCHEMA' AND table_name = 'IW'");
        assertAnswer(
            "null|x|null",
            "SELECT DECODE(v, NULL, 'x'), DECODE(v, 'a', 'x'), DECODE(n, 2, 'x') FROM iw");
    }
}
