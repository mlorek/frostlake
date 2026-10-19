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
 * A key dropped by its columns must be there (live-verified): DROP FOREIGN KEY (y) drops the foreign key over y and
 * names a column the table lacks at itself, while a table holding none is refused in the account's own words,
 * {@code constraint 'FOREIGN KEYon' does not exist}, and so is a foreign key written with an ON DELETE or ON UPDATE
 * action. DROP UNIQUE and DROP PRIMARY KEY over a table that holds no such key are refused the same way.
 */
public class KeyDropByColumnsTest extends MeasuredCellsTestSupport {

    @Test
    public void aKeyDroppedByItsColumnsMustBeThere() {
        engine.execute("CREATE OR REPLACE TABLE p1 (id INT PRIMARY KEY)");
        engine.execute("CREATE OR REPLACE TABLE t2 (y INT)");
        engine.execute("CREATE OR REPLACE TABLE t3 (y INT)");
        engine.execute("CREATE OR REPLACE TABLE t4 (y INT)");
        assertCells(new String[][] {
            {"ALTER TABLE t2 ADD FOREIGN KEY (y) REFERENCES p1 (id) ON DELETE CASCADE ON UPDATE CASCADE",
                "Statement executed successfully."},
            {"ALTER TABLE t2 DROP FOREIGN KEY (y)",
                "REFUSED SQL compilation error: | constraint 'FOREIGN KEYon' does not exist"},
            {"ALTER TABLE t3 ADD FOREIGN KEY (y) REFERENCES p1 (id)", "Statement executed successfully."},
            {"ALTER TABLE t3 DROP FOREIGN KEY (y)", "Statement executed successfully."},
            {"ALTER TABLE t4 ADD CONSTRAINT fk4 FOREIGN KEY (y) REFERENCES p1 (id)",
                "Statement executed successfully."},
            {"ALTER TABLE t4 DROP FOREIGN KEY (y)", "Statement executed successfully."},
            {"ALTER TABLE t4 DROP FOREIGN KEY (nosuch)",
                "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'NOSUCH'"},
        });
        engine.execute("SHOW IMPORTED KEYS IN TABLE t4");
        assertCells(new String[][] {
            {"SELECT \"fk_name\", \"fk_column_name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))", ""},
            {"ALTER TABLE t4 DROP CONSTRAINT fk4", "REFUSED SQL compilation error: | constraint 'FK4' does not exist"},
            {"ALTER TABLE t3 DROP FOREIGN KEY (nosuch)",
                "REFUSED SQL compilation error: error line 1 at position 33 | invalid identifier 'NOSUCH'"},
            {"ALTER TABLE p1 DROP FOREIGN KEY (id)",
                "REFUSED SQL compilation error: | constraint 'FOREIGN KEYon' does not exist"},
            {"ALTER TABLE t2 DROP PRIMARY KEY",
                "REFUSED SQL compilation error: | constraint 'primary key' does not exist"},
            {"ALTER TABLE p1 DROP UNIQUE (id)",
                "REFUSED SQL compilation error: | constraint 'UNIQUE KEYon' does not exist"},
        });
    }
}
