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
 * A FLOAT written into an exact NUMBER column takes its binary value rounded half up to the scale, by every
 * write path, and so does a VARIANT holding a double - TO_VARIANT of a FLOAT, a JSON number written with an
 * exponent - written there or read by TO_NUMBER, where a JSON decimal is read exactly (all live-verified).
 */
public class FloatIntoExactColumnsTest extends MeasuredCellsTestSupport {

    @Test
    public void everyWritePath() {
        engine.execute("CREATE OR REPLACE TABLE fw (n NUMBER(38,20), m NUMBER(10,2), k NUMBER(38,0), j NUMBER(5,1))");
        engine.execute("CREATE OR REPLACE TABLE ff (f FLOAT)");
        engine.execute("INSERT INTO ff VALUES (0.1), (2.675)");
        assertAnswer("1", "INSERT INTO fw (n, m) SELECT 0.1::FLOAT, 2.675::FLOAT");
        assertAnswer("0.10000000000000000555|2.67", "SELECT n, m FROM fw");
        assertAnswer("1", "DELETE FROM fw");
        assertAnswer("1", "INSERT INTO fw (n, m) VALUES (0.1::FLOAT, 2.675::FLOAT)");
        assertAnswer("0.10000000000000000555|2.67", "SELECT n, m FROM fw");
        assertAnswer("1|0", "UPDATE fw SET n = (1/3)::FLOAT, m = -2.675::FLOAT");
        assertAnswer("0.33333299999999999041|-2.67", "SELECT n, m FROM fw");
        assertAnswer("1", "DELETE FROM fw");
        assertAnswer("1",
            "MERGE INTO fw USING (SELECT 0.7::FLOAT AS f) s ON FALSE WHEN NOT MATCHED THEN INSERT (n, m) VALUES (s.f,"
                + " s.f)");
        assertAnswer("0.69999999999999995559|0.70", "SELECT n, m FROM fw");
        assertAnswer("1",
            "MERGE INTO fw USING (SELECT 1.005::FLOAT AS f) s ON TRUE WHEN MATCHED THEN UPDATE SET n = s.f, m = s.f");
        assertAnswer("1.00499999999999989342|1.00", "SELECT n, m FROM fw");
        assertAnswer("1", "DELETE FROM fw");
        assertAnswer("2", "INSERT INTO fw (n, m) SELECT f, f FROM ff");
        assertAnswer("0.10000000000000000555|0.10 ;; 2.67499999999999982236|2.67", "SELECT n, m FROM fw ORDER BY n");
        assertAnswer("2", "DELETE FROM fw");
        assertAnswer("1", "INSERT INTO fw (k, j) SELECT 1e20::FLOAT, 0.25::FLOAT");
        assertAnswer("1", "INSERT INTO fw (k, j) SELECT 1.2345678901234567e25::FLOAT, 0.35::FLOAT");
        assertAnswer("1", "INSERT INTO fw (k, j) SELECT 2.5::FLOAT, -0.25::FLOAT");
        assertAnswer("3|-0.3 ;; 100000000000000000000|0.3 ;; 12345678901234566097272832|0.3",
            "SELECT k, j FROM fw ORDER BY k");
        assertAnswer("3", "DELETE FROM fw");
        assertAnswer("1", "INSERT INTO fw (n, m) SELECT TO_VARIANT(0.1::FLOAT), TO_VARIANT(2.675::FLOAT)");
        assertAnswer("0.10000000000000000555|2.67", "SELECT n, m FROM fw");
        assertAnswer("1", "DELETE FROM fw");
        assertAnswer("1", "INSERT ALL INTO fw (n, m) VALUES (f, f) SELECT 0.1::FLOAT AS f");
        assertAnswer("0.10000000000000000555|0.10", "SELECT n, m FROM fw");
        assertAnswer("1", "DELETE FROM fw");
        assertAnswer("1", "INSERT INTO fw (n, m) SELECT '0.1'::FLOAT, '2.675'");
        assertAnswer("0.10000000000000000555|2.68", "SELECT n, m FROM fw");
        assertAnswer("Table FW2 successfully created.",
            "CREATE OR REPLACE TABLE fw2 (n NUMBER(38,20), m NUMBER(10,2)) AS SELECT 0.1::FLOAT, 2.675::FLOAT");
        assertAnswer("0.10000000000000000555|2.67", "SELECT n, m FROM fw2");
        assertAnswer("0.10000000000000000555|2.67|0.3",
            "SELECT 0.1::FLOAT::NUMBER(38,20), 2.675::FLOAT::NUMBER(10,2), CAST(0.35::FLOAT AS NUMBER(5,1))");
    }

    @Test
    public void variantDoubles() {
        engine.execute("CREATE OR REPLACE TABLE vw (n NUMBER(38,20), m NUMBER(10,2), k NUMBER(5,0))");
        assertAnswer("1",
            "INSERT INTO vw SELECT TO_VARIANT(0.1::FLOAT), TO_VARIANT(2.675::FLOAT), TO_VARIANT(2.5::FLOAT)");
        assertAnswer("1", "INSERT INTO vw SELECT PARSE_JSON('0.1'), PARSE_JSON('2.675'), PARSE_JSON('2.5')");
        assertAnswer("1", "INSERT INTO vw SELECT PARSE_JSON('1e-1'), PARSE_JSON('2.675e0'), PARSE_JSON('25e-1')");
        assertAnswer("0.10000000000000000000|2.68|3 ;; 0.10000000000000000555|2.67|3 ;; 0.10000000000000000555|2.67|3",
            "SELECT n, m, k FROM vw ORDER BY n, m");
        assertAnswer("3|0", "UPDATE vw SET n = TO_VARIANT(0.7::FLOAT), m = PARSE_JSON('0.125e0') WHERE k = 3");
        assertAnswer("0.69999999999999995559|0.13|3 ;; 0.69999999999999995559|0.13|3 ;; 0.69999999999999995559|0.13|3",
            "SELECT n, m, k FROM vw ORDER BY n, m, k");
        assertAnswer("0.10000000000000000555|0.10000000000000000555|2.67",
            "SELECT TO_VARIANT(0.1::FLOAT)::NUMBER(38,20), PARSE_JSON('1e-1')::NUMBER(38,20), "
                + "PARSE_JSON('2.675e0')::NUMBER(10,2)");
        assertAnswer("0.10000000000000000555|2.67",
            "SELECT TO_NUMBER(TO_VARIANT(0.1::FLOAT), 38, 20), TO_DECIMAL(PARSE_JSON('2.675e0'), 10, 2)");
        assertAnswer("Table VW2 successfully created.",
            "CREATE OR REPLACE TABLE vw2 (n NUMBER(38,20)) AS SELECT TO_VARIANT(0.1::FLOAT)");
        assertAnswer("0.10000000000000000555", "SELECT n FROM vw2");
        assertAnswer("Table VF successfully created.", "CREATE OR REPLACE TABLE vf (f FLOAT, v VARIANT)");
        assertAnswer("1", "INSERT INTO vf SELECT 0.1, TO_VARIANT(0.1::FLOAT)");
        assertAnswer("1", "INSERT INTO vw (n, m) SELECT v, v FROM vf");
        assertAnswer("0.10000000000000000555|0.10", "SELECT n, m FROM vw WHERE k IS NULL");
    }
}
