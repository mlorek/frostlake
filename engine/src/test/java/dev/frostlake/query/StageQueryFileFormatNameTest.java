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
 * A stage query's FILE_FORMAT names a file format, looked up as any name is (live-verified): one that is not there is
 * {@code File format 'X' does not exist or not authorized.}, a type word such as CSV names a format of that name too,
 * and a quoted name names the format of exactly its case.
 */
public class StageQueryFileFormatNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aStageQuerysFileFormatNamesAFileFormat() {
        engine.execute("CREATE OR REPLACE STAGE cs");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff_csv TYPE = CSV");
        assertCells(new String[][] {
            {"SELECT $1 FROM @cs (FILE_FORMAT => 'x', PATTERN => 'y')",
                "REFUSED SQL compilation error: | File format 'X' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT X."},
            {"SELECT $1 FROM @cs (FILE_FORMAT => 'ff_csv', PATTERN => 'y')", ""},
            {"SELECT $1 FROM @cs (FILE_FORMAT => 'CSV')",
                "REFUSED SQL compilation error: | File format 'CSV' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT CSV."},
            {"SELECT $1 FROM @cs (FILE_FORMAT => 'test_db.test_schema.ff_csv')", ""},
            {"SELECT $1 FROM @cs (FILE_FORMAT => 'nosuch_db.s.f')",
                "REFUSED SQL compilation error: | Database 'NOSUCH_DB' does not exist or not authorized. Your "
                + "primary role <ROLE> must have USAGE or any other privilege granted on DATABASE NOSUCH_DB."},
            {"SELECT $1 FROM @cs (PATTERN => 'y', FILE_FORMAT => 'x')",
                "REFUSED SQL compilation error: | File format 'X' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on FILE FORMAT X."},
            {"SELECT $1 FROM @cs (FILE_FORMAT => 'FF_CSV')", ""},
            {"SELECT $1 FROM @cs (FILE_FORMAT => 'parquet')",
                "REFUSED SQL compilation error: | File format 'PARQUET' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT PARQUET."},
            {"SELECT $1 FROM @cs (FILE_FORMAT => '\"ff_csv\"')",
                "REFUSED SQL compilation error: | File format '\"ff_csv\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT \"ff_csv\"."},
        });
    }
}
