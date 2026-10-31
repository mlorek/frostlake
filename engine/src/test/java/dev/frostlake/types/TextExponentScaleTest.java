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
 * A text literal beside an exact number is read at its own digits' precision and scale, and one of more than 38
 * decimals is refused as an intermediate type no NUMBER can be: '1e-39' is NUMBER(40,39) (all live-verified).
 */
public class TextExponentScaleTest extends MeasuredCellsTestSupport {

    @Test
    public void beside() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertAnswer("", "SELECT id FROM fz WHERE id < '1e-38' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(40,39).",
            "SELECT id FROM fz WHERE id < '1e-39' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(41,40).",
            "SELECT id FROM fz WHERE id < '1e-40' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(46,45).",
            "SELECT id FROM fz WHERE id < '1e-45' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(51,50).",
            "SELECT id FROM fz WHERE id < '1e-50' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(61,60).",
            "SELECT id FROM fz WHERE id < '1e-60' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(76,75).",
            "SELECT id FROM fz WHERE id < '1e-75' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(101,100).",
            "SELECT id FROM fz WHERE id < '1e-100' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(151,150).",
            "SELECT id FROM fz WHERE id < '1e-150' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(201,200).",
            "SELECT id FROM fz WHERE id < '1e-200' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(251,250).",
            "SELECT id FROM fz WHERE id < '1e-250' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(301,300).",
            "SELECT id FROM fz WHERE id < '1e-300' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(308,307).",
            "SELECT id FROM fz WHERE id < '1e-307' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(309,308).",
            "SELECT id FROM fz WHERE id < '1e-308' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(310,309).",
            "SELECT id FROM fz WHERE id < '1e-309' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(321,320).",
            "SELECT id FROM fz WHERE id < '1e-320' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(401,400).",
            "SELECT id FROM fz WHERE id < '1e-400' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(309,308).",
            "SELECT id FROM fz WHERE id < '-1e-308' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(310,309).",
            "SELECT id FROM fz WHERE id < '1.5e-308' ORDER BY id");
        assertAnswer("REFUSED Numeric value '1e308' is not recognized",
            "SELECT id FROM fz WHERE id < '1e308' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(41,40).",
            "SELECT id FROM fz WHERE id < '0.0000000000000000000000000000000000000001' ORDER BY id");
        assertAnswer("", "SELECT id FROM fz WHERE id < '1e-37' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(41,40).",
            "SELECT id FROM fz WHERE id < '2.5e-39' ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(51,50).",
            "SELECT id FROM fz WHERE id = '1e-50'");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(51,50).",
            "SELECT '1e-50' < id FROM fz ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(51,50).",
            "SELECT id + '1e-50' FROM fz ORDER BY id");
        assertAnswer("", "SELECT id FROM fz WHERE id < '1e-50'::FLOAT ORDER BY id");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(51,50).",
            "SELECT SYSTEM$TYPEOF(id < '1e-50') FROM fz LIMIT 1");
        assertAnswer("REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(51,50).",
            "SELECT id FROM fz WHERE id IN ('1e-50') ORDER BY id");
    }
}
