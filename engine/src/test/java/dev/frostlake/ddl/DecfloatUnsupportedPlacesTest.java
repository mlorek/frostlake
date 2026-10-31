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

package dev.frostlake.ddl;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A DECFLOAT column is refused by a hybrid table - "This feature is not supported for hybrid tables:
 * 'DECFLOAT(38)'." - and by search optimization ON EQUALITY or SUBSTRING, at the column; a clustering key, an
 * expression of it and search optimization of the whole table take one (all live-verified).
 */
public class DecfloatUnsupportedPlacesTest extends MeasuredCellsTestSupport {

    @Test
    public void decfloatPlaces() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("REFUSED This feature is not supported for hybrid tables: 'DECFLOAT(38)'.",
            "CREATE OR REPLACE HYBRID TABLE h49 (id INT PRIMARY KEY, d DECFLOAT)");
        assertAnswer("Table CK49 successfully created.", "CREATE OR REPLACE TABLE ck49 (d DECFLOAT) CLUSTER BY (d)");
        assertAnswer("Table CK49B successfully created.",
            "CREATE OR REPLACE TABLE ck49b (d DECFLOAT, n INT) CLUSTER BY (n)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE ck49b CLUSTER BY (d)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE ck49b CLUSTER BY (d + 1)");
        assertAnswer("Table CK49C successfully created.",
            "CREATE OR REPLACE TABLE ck49c (d DECFLOAT) CLUSTER BY (d::NUMBER)");
        assertAnswer("Table CK49D successfully created.",
            "CREATE OR REPLACE TABLE ck49d (d DECFLOAT) CLUSTER BY (TO_VARCHAR(d))");
        assertAnswer("Table SO49 successfully created.", "CREATE OR REPLACE TABLE so49 (d DECFLOAT, n INT)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 53 | Expression "
                + "EQUALITY(IDX_SRC_TABLE.D) cannot be used in search optimization.",
            "ALTER TABLE so49 ADD SEARCH OPTIMIZATION ON EQUALITY(d)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE so49 ADD SEARCH OPTIMIZATION ON EQUALITY(n)");
        assertAnswer("REFUSED SQL compilation error: error line 1 at position 54 | Expression "
                + "SUBSTRING(IDX_SRC_TABLE.D) cannot be used in search optimization.",
            "ALTER TABLE so49 ADD SEARCH OPTIMIZATION ON SUBSTRING(d)");
        assertAnswer("Table SO49B successfully created.", "CREATE OR REPLACE TABLE so49b (d DECFLOAT, n INT)");
        assertAnswer("Statement executed successfully.", "ALTER TABLE so49b ADD SEARCH OPTIMIZATION");
        assertAnswer("Table CK49E successfully created.",
            "CREATE OR REPLACE TABLE ck49e CLUSTER BY (d) AS SELECT DECFLOAT '1' d");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
