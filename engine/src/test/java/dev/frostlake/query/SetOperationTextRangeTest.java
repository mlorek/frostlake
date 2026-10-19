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

import org.junit.jupiter.api.Test;

/**
 * A text beside a NUMBER in a set operation, GREATEST or LEAST converts as it does in a comparison: past the
 * 128-bit carrier it is not recognized, from 10^38 it is out of range, so no NUMBER the statement answers holds a
 * 39-digit value; 38 nines convert, and a text beside a FLOAT is that FLOAT's (all live-verified).
 */
public class SetOperationTextRangeTest extends MeasuredCellsTestSupport {

    @Test
    public void aTextPastANumbersRangeIsRefusedWhereverItMeetsOne() {
        engine.execute("CREATE OR REPLACE TABLE tst (id NUMBER(38,0))");
        engine.execute("INSERT INTO tst VALUES (5)");
        assertAnswer("REFUSED Numeric value '1e39' is not recognized", "SELECT id < '1e39' FROM tst");
        assertAnswer("REFUSED Numeric value '1e38' is out of range", "SELECT id < '1e38' FROM tst");
        assertAnswer("REFUSED Numeric value '1e39' is not recognized", "SELECT GREATEST(id, '1e39') FROM tst");
        assertAnswer("REFUSED Numeric value '-1e45' is not recognized", "SELECT LEAST(id, '-1e45') FROM tst");
        assertAnswer("REFUSED Numeric value '1e38' is out of range", "SELECT GREATEST(id, '1e38') FROM tst");
        assertAnswer("99999999999999999999999999999999999999",
            "SELECT GREATEST(id, '99999999999999999999999999999999999999') FROM tst");
        assertAnswer("REFUSED Numeric value '1e39' is not recognized", "SELECT id FROM tst UNION ALL SELECT '1e39'");
        assertAnswer("REFUSED Numeric value '1e38' is out of range", "SELECT id FROM tst UNION ALL SELECT '1e38'");
        assertAnswer("REFUSED Numeric value '1e39' is not recognized", "SELECT id FROM tst INTERSECT SELECT '1e39'");
        assertAnswer("REFUSED Numeric value '1e39' is not recognized", "SELECT id FROM tst EXCEPT SELECT '1e39'");
        assertAnswer("REFUSED Numeric value '1e38' is out of range", "SELECT id FROM tst UNION SELECT '1e38'");
        assertAnswer("REFUSED Numeric value '1e39' is not recognized", "SELECT '1e39' UNION ALL SELECT id FROM tst");
        answer("CREATE OR REPLACE TABLE tsw AS SELECT id FROM tst UNION ALL SELECT '1e39'");
        assertAnswer("5 ;; 99999999999999999999999999999999999999",
            "SELECT id FROM tst UNION ALL SELECT '99999999999999999999999999999999999999'");
        assertAnswer("5.0 ;; 12.5", "SELECT id FROM tst UNION ALL SELECT '12.5'");
        assertAnswer("REFUSED Numeric value 'abc' is not recognized", "SELECT id FROM tst UNION ALL SELECT 'abc'");
        assertAnswer("REFUSED Numeric value 'abc' is not recognized", "SELECT GREATEST(id, 'abc') FROM tst");
        assertAnswer("REFUSED Numeric value '1e39' is not recognized",
            "SELECT id FROM tst UNION ALL SELECT '1e39'::VARCHAR");
        assertAnswer("5", "SELECT COALESCE(id, '1e39') FROM tst");
        assertAnswer("5", "SELECT IFF(TRUE, id, '1e39') FROM tst");
        assertAnswer("REFUSED Numeric value '1e39' is not recognized", "SELECT id IN ('1e39') FROM tst");
    }
}
