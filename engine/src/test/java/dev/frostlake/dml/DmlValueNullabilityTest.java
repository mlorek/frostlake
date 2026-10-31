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
 * A number too wide for a NOT NULL column is refused with the nullability of the value written: a value an
 * UPDATE ... FROM reads from a FROM relation is nullable as the column is, a derived table's as the projection
 * that made it judged; a scalar subquery an UPDATE or a MERGE writes is nullable when it reads a table or a name of
 * the statement around it, or when its item is; and one an INSERT ... VALUES writes is always nullable (all
 * live-verified).
 */
public class DmlValueNullabilityTest extends MeasuredCellsTestSupport {

    @Test
    public void updateValues() {
        engine.execute("CREATE OR REPLACE TABLE nn50 (n NUMBER(3,0) NOT NULL)");
        engine.execute("INSERT INTO nn50 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE src50 (v NUMBER(5,0))");
        engine.execute("INSERT INTO src50 VALUES (12345)");
        engine.execute("CREATE OR REPLACE TABLE src51 (v NUMBER(5,0) NOT NULL)");
        engine.execute("INSERT INTO src51 VALUES (12345)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = (SELECT NULLIF(12345, 0))");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = s.v FROM (SELECT NULLIF(12345, 0) v) s");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn50 SET n = s.v FROM (SELECT 12345 v) s");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn50 SET n = (SELECT 12345)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn50 SET n = (SELECT v FROM (SELECT 12345 v))");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = (SELECT v FROM (SELECT NULLIF(12345, 0) v))");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = s.v + 0 FROM (SELECT NULLIF(12345, 0) v) s");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn50 SET n = COALESCE(s.v, 12345) FROM (SELECT NULLIF(12345, 0) v) s");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = (SELECT NULLIF(12345, 0)) + 0");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = s.v FROM (SELECT NULLIF(12345, 0) v) s WHERE s.v IS NOT NULL");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = s.v FROM src50 s");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn50 SET n = s.v FROM src51 s");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = (SELECT v FROM src50)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = (SELECT v FROM src51)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = (SELECT MAX(v) FROM src51)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn50 t SET n = s.v FROM src51 s WHERE t.n = 1");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn50 SET n = s.v FROM src51 s, src50 r");
    }

    @Test
    public void mergeAndInsertValues() {
        engine.execute("CREATE OR REPLACE TABLE nn50 (n NUMBER(3,0) NOT NULL)");
        engine.execute("INSERT INTO nn50 VALUES (1)");
        engine.execute("CREATE OR REPLACE TABLE src51 (v NUMBER(5,0) NOT NULL)");
        engine.execute("INSERT INTO src51 VALUES (12345)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "MERGE INTO nn50 t USING (SELECT 1 k) s ON t.n = s.k WHEN MATCHED THEN UPDATE SET n = (SELECT "
                + "NULLIF(12345, 0))");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "MERGE INTO nn50 t USING (SELECT 1 k) s ON t.n = s.k WHEN MATCHED THEN UPDATE SET n = (SELECT 12345)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "MERGE INTO nn50 t USING (SELECT 1 k) s ON t.n = s.k WHEN MATCHED THEN UPDATE SET n = (SELECT v FROM "
                + "src51)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "MERGE INTO nn50 t USING (SELECT 2 k) s ON t.n = s.k WHEN NOT MATCHED THEN INSERT (n) VALUES ((SELECT "
                + "NULLIF(12345, 0)))");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "MERGE INTO nn50 t USING (SELECT 2 k) s ON t.n = s.k WHEN NOT MATCHED THEN INSERT (n) VALUES ((SELECT "
                + "12345))");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "MERGE INTO nn50 t USING (SELECT 2 k) s ON t.n = s.k WHEN NOT MATCHED THEN INSERT (n) VALUES ((SELECT v "
                + "FROM src51))");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn50 VALUES ((SELECT NULLIF(12345, 0)))");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn50 VALUES ((SELECT 12345))");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn50 SELECT (SELECT NULLIF(12345, 0))");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn50 SELECT (SELECT v FROM src51)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn50 SET n = (SELECT 12345 WHERE TRUE)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn50 SET n = (SELECT 12345 LIMIT 1)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE nn50 SET n = (SELECT n * 12345)");
    }
}
