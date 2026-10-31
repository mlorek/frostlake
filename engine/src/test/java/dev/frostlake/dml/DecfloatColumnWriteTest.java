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
 * A DECFLOAT written into a VARIANT or a text column is held in that column's own form of it, by every write path -
 * INSERT … SELECT, INSERT … VALUES, a multi-table INSERT, UPDATE, UPDATE … FROM and both MERGE branches: a VARIANT
 * holds the DECFLOAT member TO_VARIANT makes, so TYPEOF is DECFLOAT and it equals TO_VARIANT(DECFLOAT …)
 * (live-verified for INSERT … SELECT), and a text column the text its cast to VARCHAR gives, as every value written
 * there reads (live-verified rule), so 1e100 fits a VARCHAR(10).
 */
public class DecfloatColumnWriteTest extends MeasuredCellsTestSupport {

    @Test
    public void everyWritePathHoldsADecfloatInAVariantAsADecfloat() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE tv (k INT, v VARIANT)", "Table TV successfully created."},
            {"CREATE OR REPLACE TABLE dsrc (d DECFLOAT)", "Table DSRC successfully created."},
            {"INSERT INTO dsrc VALUES (DECFLOAT '1e-50')", "1"},
            {"INSERT INTO tv SELECT 1, DECFLOAT '1e-50'", "1"},
            {"INSERT INTO tv VALUES (2, DECFLOAT '1e-50')", "1"},
            {"INSERT INTO tv (k) VALUES (3), (5), (6)", "3"},
            {"UPDATE tv SET v = DECFLOAT '1e-50' WHERE k = 3", "1|0"},
            {"MERGE INTO tv USING (SELECT DECFLOAT '1e-50' d) s ON FALSE "
                + "WHEN NOT MATCHED THEN INSERT (k, v) VALUES (4, s.d)", "1"},
            {"MERGE INTO tv USING dsrc ON tv.k = 5 WHEN MATCHED THEN UPDATE SET v = dsrc.d", "1"},
            {"UPDATE tv SET v = dsrc.d FROM dsrc WHERE tv.k = 6", "1|0"},
            {"INSERT ALL INTO tv (k, v) VALUES (k, d) SELECT 7 k, DECFLOAT '1e-50' d", "1"},
            {"INSERT ALL INTO tv SELECT 8, DECFLOAT '1e-50'", "1"},
            {"SELECT k, TYPEOF(v), v::VARCHAR, v = TO_VARIANT(DECFLOAT '1e-50') FROM tv ORDER BY k",
                "1|DECFLOAT|1e-50|true ;; 2|DECFLOAT|1e-50|true ;; 3|DECFLOAT|1e-50|true ;; "
                    + "4|DECFLOAT|1e-50|true ;; 5|DECFLOAT|1e-50|true ;; 6|DECFLOAT|1e-50|true ;; "
                    + "7|DECFLOAT|1e-50|true ;; 8|DECFLOAT|1e-50|true"},
            // A VALUES column whose rows fold to a DECFLOAT is written as one; a NUMBER stays a NUMBER.
            {"INSERT INTO tv VALUES (9, DECFLOAT '2'), (10, 3)", "2"},
            {"INSERT INTO tv SELECT 11, 1.5", "1"},
            {"SELECT k, TYPEOF(v), v::VARCHAR FROM tv WHERE k >= 9 ORDER BY k",
                "9|DECFLOAT|2 ;; 10|DECFLOAT|3 ;; 11|DECIMAL|1.5"},
        });
    }

    @Test
    public void everyWritePathHoldsADecfloatInATextColumnAsItsText() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE vc (k INT, v VARCHAR, w VARCHAR(10))", "Table VC successfully created."},
            {"CREATE OR REPLACE TABLE dsrc (d DECFLOAT)", "Table DSRC successfully created."},
            {"INSERT INTO dsrc VALUES (DECFLOAT '1e-50')", "1"},
            {"INSERT INTO vc (k, v) SELECT 1, DECFLOAT '1e-50'", "1"},
            {"INSERT INTO vc (k, v) VALUES (2, DECFLOAT '1e-50')", "1"},
            {"INSERT INTO vc (k, w) SELECT 3, DECFLOAT '1e100'", "1"},
            {"INSERT INTO vc (k, w) VALUES (4, DECFLOAT '1e100')", "1"},
            {"INSERT INTO vc (k) VALUES (5), (6), (7)", "3"},
            {"UPDATE vc SET v = DECFLOAT '1' / 3, w = DECFLOAT '1e100' WHERE k = 5", "1|0"},
            {"MERGE INTO vc USING dsrc ON vc.k = 6 WHEN MATCHED THEN UPDATE SET v = dsrc.d", "1"},
            {"UPDATE vc SET v = dsrc.d FROM dsrc WHERE vc.k = 7", "1|0"},
            {"MERGE INTO vc USING dsrc ON FALSE WHEN NOT MATCHED THEN INSERT (k, v, w) VALUES (8, dsrc.d, dsrc.d)", "1"},
            {"INSERT ALL INTO vc (k, v, w) VALUES (k, d, d) SELECT 9 k, DECFLOAT '1e-50' d", "1"},
            {"SELECT k, v, LENGTH(v), w FROM vc ORDER BY k",
                "1|1e-50|5|null ;; 2|1e-50|5|null ;; 3|null|null|1e100 ;; 4|null|null|1e100 ;; "
                    + "5|3.3333333333333333333333333333333333333e-1|42|1e100 ;; 6|1e-50|5|null ;; "
                    + "7|1e-50|5|null ;; 8|1e-50|5|1e-50 ;; 9|1e-50|5|1e-50"},
            {"SELECT COUNT(*) FROM vc WHERE v = (DECFLOAT '1e-50')::VARCHAR", "6"},
            // Plain text where its cast writes plain text, and a value past the width is still refused.
            {"INSERT INTO vc (k, v) SELECT 10, DECFLOAT '12345.5'", "1"},
            {"SELECT v FROM vc WHERE k = 10", "12345.5"},
            {"INSERT INTO vc (k, w) SELECT 11, DECFLOAT '1' / 3",
                "REFUSED DML operation to table VC failed on column W with error: String "
                    + "'3.3333333333333333333333333333333333333e-1' is too long and would be truncated"},
        });
    }
}
