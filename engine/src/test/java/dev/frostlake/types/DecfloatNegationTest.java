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
 * A DECFLOAT negates, and takes its ABS, as a DECFLOAT: 38 significant digits over an exponent range of its own
 * (documented), so neither ever meets NUMBER's 128-bit window. Both were refused past 10^38 as out of NUMBER(38,0)'s
 * range, ORDER BY -d too, while 0 - d answered.
 */
public class DecfloatNegationTest extends MeasuredCellsTestSupport {

    @Test
    public void aDecfloatNegatesAndTakesItsMagnitudeAsOne() {
        engine.execute("CREATE OR REPLACE TABLE dn (d DECFLOAT)");
        engine.execute("INSERT INTO dn VALUES ('1e100'), ('1.25'), ('-3e50')");
        assertAnswer("3e50|3e50 ;; -1.25|1.25 ;; -1e100|1e100",
            "SELECT (-d)::VARCHAR, ABS(d)::VARCHAR FROM dn ORDER BY d");
        assertAnswer("1e100 ;; 1.25 ;; -3e50", "SELECT d::VARCHAR FROM dn ORDER BY -d");
        assertAnswer("-1e100|1e100", "SELECT (-('1e100'::DECFLOAT))::VARCHAR, ABS('-1e100'::DECFLOAT)::VARCHAR");
    }
}
