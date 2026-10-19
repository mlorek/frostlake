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
 * A text that converts to a NUMBER beside one — compared, in arithmetic, in a CAST to a bare NUMBER — is read
 * into the signed 128-bit carrier (live-verified): from 10^38 on it is out of range, past 2^127 - 1 it is not
 * recognized, written with an exponent or not; below 10^38 it converts.
 */
public class TextBesideNumberRangeTest extends MeasuredCellsTestSupport {

    @Test
    public void aTextPastTheCarrierIsNotRecognized() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertCells(new String[][] {
            {"DECLARE x FLOAT DEFAULT 1e308; c CURSOR FOR SELECT id FROM fz WHERE id < ?; r VARCHAR; BEGIN OPEN c "
                + "USING (x); FETCH c INTO r; RETURN r; END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 93 : Numeric value "
                + "'1e+308' is not recognized"},
            {"SELECT id FROM fz WHERE id < '1e+308'", "REFUSED Numeric value '1e+308' is not recognized"},
            {"SELECT id FROM fz WHERE id < '1e+30' ORDER BY id", "5 ;; 7"},
            {"SELECT id FROM fz WHERE id < '1e5' ORDER BY id", "5 ;; 7"},
            {"SELECT id FROM fz WHERE id < '1e37' ORDER BY id", "5 ;; 7"},
            {"SELECT id FROM fz WHERE id < '1e38' ORDER BY id", "REFUSED Numeric value '1e38' is out of range"},
            {"SELECT id FROM fz WHERE id < '9.9e37' ORDER BY id", "5 ;; 7"},
            {"SELECT id FROM fz WHERE id < '1e39' ORDER BY id", "REFUSED Numeric value '1e39' is not recognized"},
            {"SELECT id FROM fz WHERE id < '1.5e2' ORDER BY id", "5 ;; 7"},
            {"SELECT 5 < '1e+308'", "REFUSED Numeric value '1e+308' is not recognized"},
            {"SELECT id FROM fz WHERE id = '7e0'", "7"},
            {"SELECT id FROM fz WHERE id < '-1e+308'", "REFUSED Numeric value '-1e+308' is not recognized"},
            {"SELECT id + '1e+308' FROM fz", "REFUSED Numeric value '1e+308' is not recognized"},
            {"SELECT id FROM fz WHERE id IN ('1e+308')", "REFUSED Numeric value '1e+308' is not recognized"},
            {"SELECT id FROM fz WHERE id BETWEEN 1 AND '1e+308'", "REFUSED Numeric value '1e+308' is not recognized"},
        });
    }

    @Test
    public void theBoundariesOfTheCarrier() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        assertCells(new String[][] {
            {"SELECT id FROM fz WHERE id < '1.7e38' ORDER BY id", "REFUSED Numeric value '1.7e38' is out of range"},
            {"SELECT id FROM fz WHERE id < '1.8e38' ORDER BY id", "REFUSED Numeric value '1.8e38' is not recognized"},
            {"SELECT id FROM fz WHERE id < '99999999999999999999999999999999999999' ORDER BY id", "5 ;; 7"},
            {"SELECT id FROM fz WHERE id < '999999999999999999999999999999999999999' ORDER BY id",
                "REFUSED Numeric value '999999999999999999999999999999999999999' is not recognized"},
            {"SELECT id FROM fz WHERE id < '170141183460469231731687303715884105727' ORDER BY id",
                "REFUSED Numeric value '170141183460469231731687303715884105727' is out of range"},
            {"SELECT id FROM fz WHERE id < '170141183460469231731687303715884105728' ORDER BY id",
                "REFUSED Numeric value '170141183460469231731687303715884105728' is not recognized"},
            {"SELECT id FROM fz WHERE id < '1e-37' ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id < '1e-38' ORDER BY id", ""},
            {"SELECT id FROM fz WHERE id < '1e-30' ORDER BY id", ""},
            {"SELECT 5 < '1e38'", "REFUSED Numeric value '1e38' is out of range"},
            {"SELECT id FROM fz WHERE id = '1e38'", "REFUSED Numeric value '1e38' is out of range"},
            {"SELECT id + '1e38' FROM fz ORDER BY id", "REFUSED Numeric value '1e38' is out of range"},
            {"SELECT CAST('1e38' AS NUMBER)", "REFUSED Numeric value '1e38' is out of range"},
            {"SELECT '1e39'::NUMBER", "REFUSED Numeric value '1e39' is not recognized"},
            {"SELECT id FROM fz WHERE id < '1E38' ORDER BY id", "REFUSED Numeric value '1E38' is out of range"},
            {"SELECT id FROM fz WHERE id < '10e37' ORDER BY id", "REFUSED Numeric value '10e37' is out of range"},
            {"SELECT id FROM fz WHERE id < '0.1e39' ORDER BY id", "REFUSED Numeric value '0.1e39' is out of range"},
            {"SELECT id FROM fz WHERE id < '1.5e38' ORDER BY id", "REFUSED Numeric value '1.5e38' is out of range"},
            {"SELECT id FROM fz WHERE '1e39' > id ORDER BY id", "REFUSED Numeric value '1e39' is not recognized"},
            {"SELECT id FROM fz WHERE id < '1e38' AND FALSE", ""},
            {"SELECT id FROM fz WHERE id < ' 1e39' ORDER BY id", "REFUSED Numeric value '1e39' is not recognized"},
            {"SELECT id FROM fz WHERE id < '1e+38' ORDER BY id", "REFUSED Numeric value '1e+38' is out of range"},
        });
    }
}
