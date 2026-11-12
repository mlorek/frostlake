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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEFAULT ON NULL replaces the pivot result's NULLs with its value (documented), and the pivoted columns' type folds
 * the aggregate's and the default's together as NVL(aggregate, default) does (live-verified): over NUMBER(22,2) a 0
 * keeps NUMBER(22,2), 0.125 makes NUMBER(23,3) and shows the aggregate's 10 as 10.000, a 30-digit number makes
 * NUMBER(32,2), a FLOAT makes FLOAT, and a text that is no number is refused as the cast refuses it.
 */
public class PivotDefaultOnNullTest extends MeasuredCellsTestSupport {

    private static final String PIVOT = "SELECT * FROM pdon PIVOT (SUM(amt) FOR m IN ('JAN', 'FEB')"
        + " DEFAULT ON NULL (%s)) ORDER BY emp";

    private void setUpSales() {
        engine.execute("CREATE OR REPLACE TABLE pdon (emp INT, m VARCHAR, amt NUMBER(10,2))");
        engine.execute("INSERT INTO pdon VALUES (1, 'JAN', 10), (2, 'FEB', 20)");
    }

    private static final String TYPES = "DESCRIBE TABLE pdonc ->> SELECT \"name\", \"type\" FROM $1";

    @Test
    public void theDefaultFoldsIntoTheColumnsType() {
        setUpSales();
        assertAnswer("1|10.00|0.00 ;; 2|0.00|20.00", String.format(PIVOT, "0"));
        assertAnswer("1|10.000|0.125 ;; 2|0.125|20.000", String.format(PIVOT, "0.125"));
        engine.execute("CREATE OR REPLACE TABLE pdonc AS " + String.format(PIVOT, "0.125"));
        assertAnswer("1|10.000|0.125 ;; 2|0.125|20.000", "SELECT * FROM pdonc ORDER BY emp");
        assertAnswer("EMP|NUMBER(38,0) ;; 'JAN'|NUMBER(23,3) ;; 'FEB'|NUMBER(23,3)", TYPES);
        engine.execute("CREATE OR REPLACE TABLE pdonc AS " + String.format(PIVOT, "0"));
        assertAnswer("EMP|NUMBER(38,0) ;; 'JAN'|NUMBER(22,2) ;; 'FEB'|NUMBER(22,2)", TYPES);
        assertAnswer("1|10.00|null ;; 2|null|20.00", String.format(PIVOT, "NULL"));
    }

    @Test
    public void aWiderDefaultWidensTheColumns() {
        setUpSales();
        assertAnswer("1|10.00|123456789012345678901234567890.00 ;; 2|123456789012345678901234567890.00|20.00",
            String.format(PIVOT, "123456789012345678901234567890"));
        engine.execute("CREATE OR REPLACE TABLE pdonc AS " + String.format(PIVOT, "123456789012345678901234567890"));
        assertAnswer("EMP|NUMBER(38,0) ;; 'JAN'|NUMBER(32,2) ;; 'FEB'|NUMBER(32,2)", TYPES);
        assertAnswer("1|10.0|1.5 ;; 2|1.5|20.0", String.format(PIVOT, "1.5::FLOAT"));
        engine.execute("CREATE OR REPLACE TABLE pdonc AS " + String.format(PIVOT, "1.5::FLOAT"));
        assertAnswer("EMP|NUMBER(38,0) ;; 'JAN'|FLOAT ;; 'FEB'|FLOAT", TYPES);
        final String text = answer(String.format(PIVOT, "'x'"));
        assertTrue(text.contains("Numeric value 'x' is not recognized"), text);
    }
}
