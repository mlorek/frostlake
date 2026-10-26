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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A staged CSV file is read with an escaped RECORD_DELIMITER as that delimiter's character, the escape dropped, and
 * a field that does not start with its enclosure keeps the enclosure as data, TRIM_SPACE trimming and unquoting
 * it (all live-verified).
 */
public class StagedCsvEscapesAndEnclosuresTest extends MeasuredCellsTestSupport {

    /** Where each run writes the two files it stages. */
    @TempDir
    Path dir;

    @Test
    public void escapedDelimitersAndSpacedEnclosures() throws IOException {
        final Path delimited = Files.write(dir.resolve("rd2.txt"), "x\\|y,z|p,q|".getBytes(StandardCharsets.UTF_8));
        final Path enclosed = Files.write(dir.resolve("esc.csv"),
            "p1,p2\n\"e1\",\"e2\"\n\"q,1\" , \"w\"\n  \"sp\",x\n".getBytes(StandardCharsets.UTF_8));
        engine.execute("USE SCHEMA test_db.test_schema");
        engine.execute("CREATE OR REPLACE STAGE test_db.test_schema.st769");
        engine.execute("PUT 'file://" + delimited + "' @test_db.test_schema.st769 AUTO_COMPRESS=FALSE");
        engine.execute("PUT 'file://" + enclosed + "' @test_db.test_schema.st769 AUTO_COMPRESS=FALSE");
        engine.execute("CREATE OR REPLACE FILE FORMAT test_db.test_schema.f4 TYPE = CSV RECORD_DELIMITER = '|'");
        engine.execute("CREATE OR REPLACE FILE FORMAT test_db.test_schema.f5 TYPE = CSV RECORD_DELIMITER = '|' "
            + "FIELD_DELIMITER = NONE");
        engine.execute("CREATE OR REPLACE FILE FORMAT test_db.test_schema.f6 TYPE = CSV RECORD_DELIMITER = '|' "
            + "ESCAPE_UNENCLOSED_FIELD = NONE");
        engine.execute("CREATE OR REPLACE FILE FORMAT test_db.test_schema.f1 TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY "
            + "= '\"'");
        engine.execute("CREATE OR REPLACE FILE FORMAT test_db.test_schema.f2 TYPE = CSV FIELD_OPTIONALLY_ENCLOSED_BY "
            + "= '\"' TRIM_SPACE = TRUE");
        assertAnswer("1|x|y|3|z|1 ;; 2|p|1|q|1",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, LENGTH($1), $2, LENGTH($2) FROM @test_db.test_schema.st769/rd2.txt "
                + "(FILE_FORMAT => 'test_db.test_schema.f4') ORDER BY 1");
        assertAnswer("1|x|y,z|5 ;; 2|p,q|3",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, LENGTH($1) FROM @test_db.test_schema.st769/rd2.txt (FILE_FORMAT => "
                + "'test_db.test_schema.f5') ORDER BY 1");
        assertAnswer("1|x\\|2|null|null ;; 2|y|1|z|1 ;; 3|p|1|q|1",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, LENGTH($1), $2, LENGTH($2) FROM @test_db.test_schema.st769/rd2.txt "
                + "(FILE_FORMAT => 'test_db.test_schema.f6') ORDER BY 1");
        assertAnswer("1|p1|2|p2|2 ;; 2|e1|2|e2|2 ;; 3|q,1|3| \"w\"|4 ;; 4|  \"sp\"|6|x|1",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, LENGTH($1), $2, LENGTH($2) FROM @test_db.test_schema.st769/esc.csv "
                + "(FILE_FORMAT => 'test_db.test_schema.f1') ORDER BY 1");
        assertAnswer("1|p1|2|p2|2 ;; 2|e1|2|e2|2 ;; 3|q,1|3|w|1 ;; 4|sp|2|x|1",
            "SELECT METADATA$FILE_ROW_NUMBER, $1, LENGTH($1), $2, LENGTH($2) FROM @test_db.test_schema.st769/esc.csv "
                + "(FILE_FORMAT => 'test_db.test_schema.f2') ORDER BY 1");
    }
}
