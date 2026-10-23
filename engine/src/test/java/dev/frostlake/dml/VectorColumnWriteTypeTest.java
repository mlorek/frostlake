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

package dev.frostlake.dml;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A VECTOR column takes a VECTOR of its own type and nothing else: an ARRAY, a VARIANT, a text and a VECTOR of
 * another element type are refused while the INSERT, the UPDATE or the MERGE compiles, "Expression type does not
 * match column data type, expecting VECTOR(FLOAT, 3) but got ARRAY for column V" (all live-verified).
 */
public class VectorColumnWriteTypeTest extends MeasuredCellsTestSupport {

    @Test
    public void vectorColumnWrites() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Table VV53 successfully created.", "CREATE OR REPLACE TABLE vv53 (v VECTOR(FLOAT,3))");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(FLOAT, 3) but got ARRAY for column V",
            "INSERT INTO vv53 SELECT [1,2,3]");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(FLOAT, 3) but got VARIANT for column V",
            "INSERT INTO vv53 SELECT PARSE_JSON('[1,2,3]')");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(FLOAT, 3) but got VARCHAR(7) for column V",
            "INSERT INTO vv53 SELECT '[1,2,3]'");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(FLOAT, 3) but got VECTOR(INT, 3) for column V",
            "INSERT INTO vv53 SELECT [1,2,3]::VECTOR(INT,3)");
        assertAnswer("1", "INSERT INTO vv53 SELECT [1,2,3]::VECTOR(FLOAT,3)");
        assertAnswer("1", "INSERT INTO vv53 SELECT NULL");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(FLOAT, 3) but got ARRAY for column V",
            "INSERT INTO vv53 (v) SELECT [1,2,NULL]");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(FLOAT, 3) but got ARRAY for column V",
            "UPDATE vv53 SET v = [1,2,3]");
        assertAnswer("2|0", "UPDATE vv53 SET v = [1,2,3]::VECTOR(FLOAT,3)");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(FLOAT, 3) but got ARRAY for column V",
            "MERGE INTO vv53 USING (SELECT [1,2,3] a) s ON FALSE WHEN NOT MATCHED THEN INSERT VALUES (s.a)");
        assertAnswer("Table VV54 successfully created.", "CREATE OR REPLACE TABLE vv54 (v VECTOR(INT,2))");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(INT, 2) but got ARRAY for column V",
            "INSERT INTO vv54 SELECT [1,2]");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(INT, 2) but got ARRAY for column V",
            "INSERT INTO vv54 SELECT ARRAY_CONSTRUCT(1,2)");
        assertAnswer("Table VA53 successfully created.", "CREATE OR REPLACE TABLE va53 (a ARRAY)");
        assertAnswer("1", "INSERT INTO va53 SELECT [1,2,3]");
        assertAnswer("REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "VECTOR(FLOAT, 3) but got ARRAY for column V",
            "INSERT INTO vv53 SELECT a FROM va53");
        assertAnswer("2", "SELECT COUNT(*) FROM vv53");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
