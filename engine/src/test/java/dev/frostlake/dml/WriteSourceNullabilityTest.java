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
 * A number that does not fit where it goes prints the type it missed with a nullability: a cast and TO_NUMBER take
 * the source's, {nullable} from a nullable column and {not null} from a literal, and name the declared width; a
 * write takes {nullable} when the column or the value's source can be NULL (all live-verified).
 */
public class WriteSourceNullabilityTest extends MeasuredCellsTestSupport {

    @Test
    public void aCastNamesItsWidthAndItsSourcesNullability() {
        assertAnswer("Table FL successfully created.", "CREATE TABLE fl (f FLOAT)");
        assertAnswer("1", "INSERT INTO fl VALUES ('NaN')");
        assertAnswer("Table FN successfully created.", "CREATE TABLE fn (f FLOAT NOT NULL)");
        assertAnswer("1", "INSERT INTO fn VALUES ('NaN')");
        assertAnswer("Table NI successfully created.", "CREATE TABLE ni (n INT)");
        assertAnswer("Table NN successfully created.", "CREATE TABLE nn (n INT NOT NULL)");
        assertAnswer("Table ND successfully created.", "CREATE TABLE nd (d NUMBER(10,2) NOT NULL)");
        assertAnswer("Table ND2 successfully created.", "CREATE TABLE nd2 (d NUMBER(10,2))");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.NK failed on column N with error: Number out "
                + "of representable range: type FIXED[SB16](38,0){nullable}, value nan",
            "CREATE TABLE nk AS SELECT f::INT n FROM fl");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.NK2 failed on column N with error: Number "
                + "out of representable range: type FIXED[SB16](38,0){not null}, value nan",
            "CREATE TABLE nk2 AS SELECT f::INT n FROM fn");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.NK3 failed on column N with error: Number "
                + "out of representable range: type FIXED[SB16](38,0){not null}, value nan",
            "CREATE TABLE nk3 AS SELECT 'NaN'::FLOAT::INT n");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.NK4 failed on column N with error: Number "
                + "out of representable range: type FIXED[SB8](10,2){nullable}, value nan",
            "CREATE TABLE nk4 AS SELECT f::NUMBER(10,2) n FROM fl");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.NK6 failed on column N with error: Number "
                + "out of representable range: type FIXED[SB16](38,0){nullable}, value nan",
            "CREATE TABLE nk6 (n INT NOT NULL) AS SELECT f FROM fl");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,0){nullable}, value nan",
            "SELECT f::INT FROM fl");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value nan",
            "SELECT f::INT FROM fn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value nan",
            "SELECT 'NaN'::FLOAT::INT");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](10,2){nullable}, value nan",
            "SELECT f::NUMBER(10,2) FROM fl");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](5,1){not null}, value nan",
            "SELECT CAST(f AS NUMBER(5,1)) FROM fn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,0){nullable}, value nan",
            "SELECT TO_NUMBER(f) FROM fl");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](10,2){nullable}, value nan",
            "SELECT TO_NUMBER(f, 10, 2) FROM fl");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,0){nullable}, value nan",
            "SELECT f::INT FROM (SELECT IFF(TRUE, 'NaN'::FLOAT, NULL) f)");
        assertAnswer("REFUSED DML operation to table NN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB16](38,0){nullable}, value nan",
            "INSERT INTO nn SELECT f FROM fl");
        assertAnswer("REFUSED DML operation to table NN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB16](38,0){not null}, value nan",
            "INSERT INTO nn SELECT 'NaN'::FLOAT");
        assertAnswer("REFUSED DML operation to table ND failed on column D with error: Number out of representable "
                + "range: type FIXED[SB8](10,2){not null}, value inf",
            "INSERT INTO nd SELECT 'inf'::FLOAT");
        assertAnswer("REFUSED DML operation to table ND failed on column D with error: Number out of representable "
                + "range: type FIXED[SB8](10,2){nullable}, value nan",
            "INSERT INTO nd SELECT f FROM fl");
        assertAnswer("REFUSED DML operation to table ND2 failed on column D with error: Number out of representable "
                + "range: type FIXED[SB8](10,2){nullable}, value nan",
            "INSERT INTO nd2 SELECT f FROM fn");
        assertAnswer("REFUSED DML operation to table ND2 failed on column D with error: Number out of representable "
                + "range: type FIXED[SB8](10,2){nullable}, value -inf",
            "INSERT INTO nd2 VALUES ('-inf'::FLOAT)");
        assertAnswer("REFUSED DML operation to table NI failed on column N with error: Number out of representable "
                + "range: type FIXED[SB16](38,0){not null}, value nan",
            "INSERT INTO ni SELECT f::INT FROM fn");
        assertAnswer("1", "INSERT INTO ni VALUES (1)");
        assertAnswer("REFUSED DML operation to table NI failed on column N with error: Number out of representable "
                + "range: type FIXED[SB16](38,0){nullable}, value nan",
            "UPDATE ni SET n = 'NaN'::FLOAT");
        assertAnswer("REFUSED DML operation to table NI failed on column N with error: Number out of representable "
                + "range: type FIXED[SB16](38,0){nullable}, value nan",
            "UPDATE ni SET n = (SELECT f FROM fn)");
        assertAnswer("REFUSED DML operation to table NI failed on column N with error: Number out of representable "
                + "range: type FIXED[SB16](38,0){nullable}, value nan",
            "MERGE INTO ni USING fn ON FALSE WHEN NOT MATCHED THEN INSERT (n) VALUES (f)");
        assertAnswer("REFUSED DML operation to table NN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB16](38,0){nullable}, value nan",
            "MERGE INTO nn USING fl ON FALSE WHEN NOT MATCHED THEN INSERT (n) VALUES (f)");
        assertAnswer("Table FN2 successfully created.", "CREATE TABLE fn2 (f FLOAT NOT NULL)");
        assertAnswer("1", "INSERT INTO fn2 VALUES (1)");
        assertAnswer("Table NK5 successfully created.", "CREATE TABLE nk5 AS SELECT f::INT n FROM fn2");
        assertAnswer("N|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null", "DESCRIBE TABLE nk5");
    }

    @Test
    public void toNumberAndAWriteNameTheirSourcesNullability() {
        assertAnswer("Table FZ successfully created.", "CREATE TABLE fz (f FLOAT, n NUMBER(10,0))");
        assertAnswer("1", "INSERT INTO fz VALUES (1e10, 12345)");
        assertAnswer("Table FN successfully created.", "CREATE TABLE fn (f FLOAT NOT NULL)");
        assertAnswer("1", "INSERT INTO fn VALUES ('NaN')");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 1e+10",
            "SELECT TO_NUMBER(f, 3) FROM fz");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "SELECT TO_NUMBER(n, 3) FROM fz");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](5,1){nullable}, value 1e+10",
            "SELECT TO_DECIMAL(f, 5, 1) FROM fz");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 1e+10",
            "SELECT f::NUMBER(3,0) FROM fz");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "SELECT n::NUMBER(3,0) FROM fz");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB8](10,2){not null}, value nan",
            "SELECT TO_NUMBER(f, 10, 2) FROM fn");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](5,0){not null}, value inf",
            "SELECT TO_NUMBER('inf'::FLOAT, 5)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB4](5,2){not null}, value -inf",
            "SELECT TO_NUMERIC('-inf'::FLOAT, 5, 2)");
        assertAnswer("REFUSED Number out of representable range: type FIXED[SB16](38,0){not null}, value nan",
            "SELECT TO_NUMBER(f) FROM fn");
        assertAnswer("Table WN successfully created.", "CREATE TABLE wn (n NUMBER(3,0) NOT NULL)");
        assertAnswer("Table WN2 successfully created.", "CREATE TABLE wn2 (n NUMBER(3,0))");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 1e+10",
            "INSERT INTO wn SELECT f FROM fz");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO wn SELECT n FROM fz");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "INSERT INTO wn SELECT 12345");
        assertAnswer("REFUSED DML operation to table WN2 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO wn2 SELECT 12345");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "INSERT INTO wn VALUES (12345)");
        assertAnswer("REFUSED DML operation to table WN2 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO wn2 VALUES (12345)");
        assertAnswer("REFUSED DML operation to table WN2 failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 1e+10",
            "INSERT INTO wn2 SELECT f FROM fz");
        assertAnswer("REFUSED DML operation to table FZ failed on column N with error: Number out of representable "
                + "range: type FIXED[SB8](10,0){nullable}, value 1000000000000",
            "UPDATE fz SET n = 1e12");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 1e+10",
            "INSERT INTO wn SELECT 1e10::FLOAT");
    }

    @Test
    public void updateMergeAndCtasNameTheirSourcesNullability() {
        assertAnswer("Table WN successfully created.", "CREATE TABLE wn (n NUMBER(3,0) NOT NULL, m NUMBER(10,0))");
        assertAnswer("1", "INSERT INTO wn VALUES (1, 12345)");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE wn SET n = m");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE wn SET n = 12345");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 1000",
            "UPDATE wn SET n = n * 1000");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE wn SET n = IFF(TRUE, 12345, NULL)");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value nan",
            "UPDATE wn SET n = 'NaN'::FLOAT");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "UPDATE wn SET n = m::FLOAT");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "UPDATE wn SET n = (SELECT 12345)");
        assertAnswer("Table SRC successfully created.",
            "CREATE TABLE src (k INT, v NUMBER(10,0), w NUMBER(10,0) NOT NULL)");
        assertAnswer("1", "INSERT INTO src VALUES (1, 12345, 12345)");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "MERGE INTO wn USING src ON TRUE WHEN MATCHED THEN UPDATE SET n = src.v");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "MERGE INTO wn USING src ON TRUE WHEN MATCHED THEN UPDATE SET n = src.w");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "MERGE INTO wn USING src ON FALSE WHEN NOT MATCHED THEN INSERT (n) VALUES (src.v)");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "MERGE INTO wn USING src ON FALSE WHEN NOT MATCHED THEN INSERT (n) VALUES (src.w)");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "MERGE INTO wn USING src s ON FALSE WHEN NOT MATCHED THEN INSERT (n) VALUES (s.v)");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "MERGE INTO wn USING (SELECT 12345 v) s ON FALSE WHEN NOT MATCHED THEN INSERT (n) VALUES (s.v)");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "INSERT INTO wn (n) SELECT w FROM src");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO wn (n) SELECT v FROM src");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){nullable}, value 12345",
            "INSERT INTO wn (n) SELECT * FROM (SELECT v FROM src)");
        assertAnswer("REFUSED DML operation to table WN failed on column N with error: Number out of representable "
                + "range: type FIXED[SB2](3,0){not null}, value 12345",
            "INSERT INTO wn (n) SELECT w + 0 FROM src");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.CT failed on column N with error: Number out "
                + "of representable range: type FIXED[SB2](3,0){not null}, value 12345",
            "CREATE TABLE ct (n NUMBER(3,0) NOT NULL) AS SELECT w FROM src");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.CT2 failed on column N with error: Number "
                + "out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "CREATE TABLE ct2 (n NUMBER(3,0)) AS SELECT w FROM src");
        assertAnswer("REFUSED DML operation to table TEST_DB.TEST_SCHEMA.CT3 failed on column N with error: Number "
                + "out of representable range: type FIXED[SB2](3,0){nullable}, value 12345",
            "CREATE TABLE ct3 (n NUMBER(3,0) NOT NULL) AS SELECT v FROM src");
    }
}
