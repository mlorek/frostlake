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
 * SKEW and KURTOSIS over a series that does not vary divide by its zero deviation, whatever its type, in an
 * aggregate and in a window, and KURTOSIS over a FLOAT or a text is a FLOAT where over a NUMBER it is a
 * NUMBER(38,12) (all live-verified).
 */
public class MomentRefusalsAndTypesTest extends MeasuredCellsTestSupport {

    @Test
    public void noSpreadDividesByZero() {
        engine.execute("CREATE OR REPLACE TABLE kc (f FLOAT, n NUMBER(10,2), i INT, d DECFLOAT)");
        engine.execute("INSERT INTO kc VALUES (2, 2, 2, 2), (2, 2, 2, 2), (2, 2, 2, 2), (2, 2, 2, 2)");
        assertAnswer("REFUSED Division by zero", "SELECT SKEW(f) FROM kc");
        assertAnswer("REFUSED Division by zero", "SELECT KURTOSIS(f) FROM kc");
        assertAnswer("REFUSED Division by zero", "SELECT SKEW(n) FROM kc");
        assertAnswer("REFUSED Division by zero", "SELECT KURTOSIS(n) FROM kc");
        assertAnswer("REFUSED Division by zero", "SELECT SKEW(i) FROM kc");
        assertAnswer("REFUSED Division by zero", "SELECT KURTOSIS(i) FROM kc");
        assertAnswer("REFUSED Division by zero", "SELECT SKEW(d) FROM kc");
        assertAnswer("REFUSED Division by zero", "SELECT KURTOSIS(d) FROM kc");
        assertAnswer("null", "SELECT SKEW(f) FROM kc WHERE f < 0");
        assertAnswer("REFUSED Division by zero", "SELECT SKEW(f) FROM (SELECT f FROM kc LIMIT 3)");
        assertAnswer("null", "SELECT SKEW(f) FROM (SELECT f FROM kc LIMIT 2)");
        assertAnswer("null", "SELECT KURTOSIS(f) FROM (SELECT f FROM kc LIMIT 3)");
        assertAnswer("REFUSED Division by zero", "SELECT SKEW(f) OVER () FROM kc LIMIT 1");
        assertAnswer("REFUSED Division by zero", "SELECT KURTOSIS(i) OVER () FROM kc LIMIT 1");
    }

    @Test
    public void kurtosisTypes() {
        engine.execute("CREATE OR REPLACE TABLE kt (f FLOAT, n NUMBER(10,2), i INT, t VARCHAR)");
        engine.execute("INSERT INTO kt VALUES (1, 1, 1, '1'), (2, 2, 2, '2'), (3, 3, 3, '3'), (5, 5, 5, '5')");
        assertAnswer("Table KTC successfully created.",
            "CREATE OR REPLACE TABLE ktc AS SELECT KURTOSIS(f) AS kf, KURTOSIS(i) AS ki, SKEW(i) AS si FROM kt");
        assertAnswer("KF|FLOAT|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; KI|NUMBER(38,12)|COLUMN|Y|null|N|N|"
                + "null|null|null|null|null|null ;; SI|FLOAT|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE ktc");
    }
}
