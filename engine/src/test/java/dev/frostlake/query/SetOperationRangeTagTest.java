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
 * A set operation's numeric column is typed from its branches, and a branch value the type cannot hold is refused as a
 * cast to it is, naming the column nullable when a branch's column may hold NULL, as a table's does, and not null when
 * every branch is a literal (live-verified): 11 beside 0.333... of 37 places, in either order, under UNION, INTERSECT
 * and EXCEPT, and over table columns.
 */
public class SetOperationRangeTagTest extends MeasuredCellsTestSupport {

    @Test
    public void aBranchValueTheUnifiedTypeCannotHold() {
        engine.execute("CREATE OR REPLACE TABLE so910 (a NUMBER(38,37), b NUMBER(38,0), c NUMBER(10,2), d "
            + "NUMBER(5,0))");
        engine.execute("INSERT INTO so910 VALUES (0.3, 11, 1.25, 7)");
        assertCells(new String[][] {
            {"SELECT 0.3333333333333333333333333333333333333 UNION ALL SELECT 11",
                "REFUSED Number out of representable range: type FIXED[SB16](38,37){not null}, value 11"},
            {"SELECT 11 UNION ALL SELECT 0.3333333333333333333333333333333333333",
                "REFUSED Number out of representable range: type FIXED[SB16](38,37){not null}, value 11"},
            {"SELECT 0.3333333333333333333333333333333333333 UNION SELECT 1",
                "0.3333333333333333333333333333333333333 ;; 1.0000000000000000000000000000000000000"},
            {"SELECT 0.3333333333333333333333333333333333333 UNION ALL SELECT 0",
                "0.3333333333333333333333333333333333333 ;; 0E-37"},
            {"SELECT 0.3333333333333333333333333333333333333 UNION ALL SELECT -1",
                "0.3333333333333333333333333333333333333 ;; -1.0000000000000000000000000000000000000"},
            {"SELECT 0.3333333333333333333333333333333333333 INTERSECT SELECT 11",
                "REFUSED Number out of representable range: type FIXED[SB16](38,37){not null}, value 11"},
            {"SELECT 0.3333333333333333333333333333333333333 EXCEPT SELECT 11",
                "REFUSED Number out of representable range: type FIXED[SB16](38,37){not null}, value 11"},
            {"SELECT 0.5 UNION ALL SELECT 12345678901234567890123456789012345678",
                "REFUSED Number out of representable range: type FIXED[SB16](38,1){not null}, value "
                + "12345678901234567890123456789012345678"},
            {"SELECT 12345678901234567890123456789012345678 UNION ALL SELECT 0.5",
                "REFUSED Number out of representable range: type FIXED[SB16](38,1){not null}, value "
                + "12345678901234567890123456789012345678"},
            {"SELECT 1.5 UNION ALL SELECT 123", "1.5 ;; 123.0"},
            {"SELECT x, SYSTEM$TYPEOF(x) FROM (SELECT 1.5 x UNION ALL SELECT 123) ORDER BY 1",
                "1.5|NUMBER(4,1)[SB2] ;; 123.0|NUMBER(4,1)[SB2]"},
            {"SELECT a FROM so910 UNION ALL SELECT b FROM so910",
                "REFUSED Number out of representable range: type FIXED[SB16](38,37){nullable}, value 11"},
            {"SELECT b FROM so910 UNION ALL SELECT a FROM so910",
                "REFUSED Number out of representable range: type FIXED[SB16](38,37){nullable}, value 11"},
            {"SELECT c FROM so910 UNION ALL SELECT d FROM so910", "1.25 ;; 7.00"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT c x FROM so910 UNION ALL SELECT d FROM so910) LIMIT 1",
                "NUMBER(10,2)[SB2]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT a x FROM so910 UNION ALL SELECT 0.5) LIMIT 1",
                "NUMBER(38,37)[SB16]"},
            {"SELECT x FROM (SELECT a x FROM so910 UNION ALL SELECT 2) ORDER BY 1",
                "0.3000000000000000000000000000000000000 ;; 2.0000000000000000000000000000000000000"},
            {"SELECT 0.3333333333333333333333333333333333333 UNION ALL SELECT NULL",
                "0.3333333333333333333333333333333333333 ;; null"},
            {"SELECT 0.3333333333333333333333333333333333333 UNION ALL SELECT '11'",
                "REFUSED Numeric value '11' is out of range"},
            {"SELECT 0.3333333333333333333333333333333333333 x UNION ALL SELECT 11 ORDER BY x",
                "REFUSED Number out of representable range: type FIXED[SB16](38,37){not null}, value 11"},
        });
    }
}
