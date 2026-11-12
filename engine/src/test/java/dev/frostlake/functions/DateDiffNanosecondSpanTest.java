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
 * DATEDIFF and TIMESTAMPDIFF in nanoseconds count a span of any length: the count is a NUMBER(38,0), which holds
 * centuries of nanoseconds. A span past about 292 years was refused with Java's own "long overflow", a table row
 * holding one failing the whole query.
 */
public class DateDiffNanosecondSpanTest extends MeasuredCellsTestSupport {

    @Test
    public void aLongSpanIsCountedExactly() {
        assertAnswer("9467107200000000000",
            "SELECT DATEDIFF(nanosecond, '1900-01-01'::TIMESTAMP, '2200-01-01'::TIMESTAMP)::VARCHAR");
        assertAnswer("-9467107200000000000",
            "SELECT DATEDIFF(nanosecond, '2200-01-01'::TIMESTAMP, '1900-01-01'::TIMESTAMP)::VARCHAR");
        assertAnswer("15778454400000000000",
            "SELECT TIMESTAMPDIFF(nanosecond, '1700-01-01'::TIMESTAMP, '2200-01-01'::TIMESTAMP)::VARCHAR");
        assertAnswer("6311433600000000000",
            "SELECT DATEDIFF(nanosecond, '2000-01-01'::TIMESTAMP, '2200-01-01'::TIMESTAMP)::VARCHAR");
        engine.execute("CREATE OR REPLACE TABLE ddn (a TIMESTAMP, b TIMESTAMP)");
        engine.execute("INSERT INTO ddn VALUES ('1900-01-01', '2200-01-01'), ('2024-01-01', '2024-01-02')");
        assertAnswer("86400000000000 ;; 9467107200000000000",
            "SELECT DATEDIFF(ns, a, b)::VARCHAR FROM ddn ORDER BY 1");
    }
}
