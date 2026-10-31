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
 * A derived relation's column can be NULL as the projection that made it judged its item, with a FROM or without:
 * a range refusal over a column of (SELECT 'inf'::FLOAT n) spells {not null}, through a CTE and a view too. A set
 * operation's column can be NULL where any arm's can, whatever the operator, and a BY NAME column always can; an
 * INSERT or a MERGE that writes such a column spells it the same way (all live-verified).
 */
public class DerivedColumnNullabilityTest extends MeasuredCellsTestSupport {

    @Test
    public void derivedRelations() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT 'inf'::FLOAT n)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT CHARINDEX('b', 'abc', n) FROM (SELECT 'inf'::FLOAT n)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT STRTOK('a b', ' ', n) FROM (SELECT 'inf'::FLOAT n)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](9,0){not null}, value inf",
            "SELECT DATEADD(day, n, '2024-01-01'::DATE) FROM (SELECT 'inf'::FLOAT n)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT 'inf'::FLOAT n UNION ALL SELECT 1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT NULLIF('inf'::FLOAT, 0) n)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "WITH c AS (SELECT 'inf'::FLOAT n) SELECT SUBSTR('abcdef', n) FROM c");
        assertAnswer("View V50 successfully created.", "CREATE OR REPLACE VIEW v50 AS SELECT 'inf'::FLOAT n");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM v50");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void setOperations() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE nn51 (k FLOAT, knn FLOAT NOT NULL)");
        engine.execute("INSERT INTO nn51 VALUES ('inf', 'inf')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT 'inf'::FLOAT n UNION ALL SELECT NULL)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT 'inf'::FLOAT n UNION SELECT 1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT 'inf'::FLOAT n INTERSECT SELECT NULLIF('inf'::FLOAT, 0))");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT 'inf'::FLOAT n EXCEPT SELECT NULL)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT NULLIF('inf'::FLOAT, 0) n EXCEPT SELECT 1)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT 'inf'::FLOAT n UNION ALL BY NAME SELECT 1 n)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT 1 x, 'inf'::FLOAT n UNION ALL BY NAME SELECT 2 x)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT knn n FROM nn51 UNION ALL SELECT 'inf'::FLOAT)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT knn n FROM nn51 UNION ALL SELECT k FROM nn51)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT k n FROM nn51 INTERSECT SELECT knn FROM nn51)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){not null}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT 'inf'::FLOAT n, 1 m)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT IFF(TRUE, 'inf'::FLOAT, NULL) n)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](18,0){nullable}, value inf",
            "SELECT SUBSTR('abcdef', n) FROM (SELECT (SELECT 'inf'::FLOAT) n)");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void writes() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE nn51 (k FLOAT, knn FLOAT NOT NULL)");
        engine.execute("INSERT INTO nn51 VALUES ('inf', 'inf')");
        assertAnswer("Table NN50 successfully created.", "CREATE OR REPLACE TABLE nn50 (n NUMBER(3,0) NOT NULL)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "INSERT INTO nn50 SELECT 12345");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn50 SELECT NULLIF(12345, 0)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "INSERT INTO nn50 SELECT 12345 UNION ALL SELECT 1");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn50 SELECT 12345 UNION ALL SELECT NULLIF(1, 0)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO nn50 (n) SELECT TRY_TO_NUMBER('12345')");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "MERGE INTO nn50 USING (SELECT NULLIF(12345, 0) v) s ON FALSE WHEN NOT MATCHED THEN INSERT VALUES (s.v)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "MERGE INTO nn50 USING (SELECT 12345 v) s ON FALSE WHEN NOT MATCHED THEN INSERT VALUES (s.v)");
        assertAnswer("1", "INSERT INTO nn50 VALUES (1)");
        assertAnswer("REFUSED DML operation to table NN50 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE nn50 SET n = s.v FROM (SELECT 12345 v) s");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}
