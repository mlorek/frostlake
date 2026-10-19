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
 * The file format a staged file's query names is found by its name as written: a quoted name of exactly its case, an
 * unquoted one upper-cased; a missing one is refused by the name as written, a schema-qualified one completed with the
 * session's database (live-verified).
 */
public class StageQueryFormatNameTest extends MeasuredCellsTestSupport {

    @Test
    public void aFormatIsFoundByItsNameAsWritten() {
        engine.execute("CREATE OR REPLACE STAGE ust");
        engine.execute("CREATE OR REPLACE FILE FORMAT ff TYPE = CSV");
        engine.execute("CREATE OR REPLACE FILE FORMAT \"lowff\" TYPE = CSV");
        assertCells(new String[][] {
            {"SELECT $1 FROM @ust (FILE_FORMAT => \"ff\")",
                "REFUSED SQL compilation error: | File format '\"ff\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT \"ff\"."},
            {"SELECT $1 FROM @ust (FILE_FORMAT => \"FF\")", ""},
            {"SELECT $1 FROM @ust (FILE_FORMAT => ff)", ""},
            {"SELECT $1 FROM @ust (FILE_FORMAT => 'ff')", ""},
            {"SELECT $1 FROM @ust (FILE_FORMAT => '\"ff\"')",
                "REFUSED SQL compilation error: | File format '\"ff\"' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT \"ff\"."},
            {"SELECT $1 FROM @ust (FILE_FORMAT => \"lowff\")", ""},
            {"SELECT $1 FROM @ust (FILE_FORMAT => lowff)",
                "REFUSED SQL compilation error: | File format 'LOWFF' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT LOWFF."},
            {"SELECT $1 FROM @ust (FILE_FORMAT => '\"lowff\"')", ""},
            {"SELECT $1 FROM @ust (FILE_FORMAT => 'lowff')",
                "REFUSED SQL compilation error: | File format 'LOWFF' does not exist or not authorized. Your "
                + "primary role <ROLE> must have at least one privilege granted on FILE FORMAT LOWFF."},
            {"SELECT $1 FROM @ust (FILE_FORMAT => test_schema.\"ff\")",
                "REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.\"ff\"' does not exist or not "
                + "authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
                + "TEST_DB.TEST_SCHEMA.\"ff\"."},
            {"SELECT $1 FROM @ust (FILE_FORMAT => test_db.test_schema.\"lowff\")", ""},
        });
    }
}
