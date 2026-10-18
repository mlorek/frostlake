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
 * ALTER TABLE ... MODIFY | ALTER UNIQUE (columns) and MODIFY | ALTER PRIMARY KEY set the RELY of the key over
 * exactly those columns (live-verified). A list no UNIQUE covers is refused as {@code Object 'UNIQUE KEYon' does
 * not exist or not authorized.}, a FOREIGN KEY list as {@code Object 'FOREIGN KEYon' ...}, a missing primary key
 * as {@code Object 'primary key' ...}, a column the table lacks as an invalid identifier, and the columns must be
 * parenthesized. A primary key put in a UNIQUE's place leaves no UNIQUE to drop. GET_DDL lists the keys an ALTER
 * adds in the order they were made, a column's own key among them.
 */
public class KeyModifyByColumnsTest extends MeasuredCellsTestSupport {

    @Test
    public void modifyUniqueAndPrimaryKeyByColumns() {
        engine.execute("CREATE OR REPLACE TABLE kt (a INT, b INT, CONSTRAINT u1 UNIQUE (b))");
        engine.execute("CREATE OR REPLACE TABLE kt2 (a INT UNIQUE)");
        engine.execute("CREATE OR REPLACE TABLE kt3 (a INT)");
        engine.execute("CREATE OR REPLACE TABLE kt4 (a INT, b INT, UNIQUE (a, b) RELY, UNIQUE (b))");
        engine.execute("CREATE OR REPLACE TABLE kt6 (a INT, b INT, UNIQUE (b), UNIQUE (a))");
        engine.execute("CREATE OR REPLACE TABLE kt7 (a INT, b INT, c INT, UNIQUE (c), UNIQUE (a, b), UNIQUE (b) RELY)");
        engine.execute("CREATE OR REPLACE TABLE kt5 (a INT, b INT, c INT)");
        assertCells(new String[][] {
            {"ALTER TABLE kt MODIFY UNIQUE (b) RELY", "Statement executed successfully."},
            {"SHOW UNIQUE KEYS IN TABLE kt ->> SELECT \"column_name\", \"rely\" FROM $1 ORDER BY 1", "B|true"},
            {"ALTER TABLE kt ALTER UNIQUE (b) NORELY", "Statement executed successfully."},
            {"SHOW UNIQUE KEYS IN TABLE kt ->> SELECT \"column_name\", \"rely\" FROM $1 ORDER BY 1", "B|false"},
            {"ALTER TABLE kt MODIFY UNIQUE (a) RELY",
                "REFUSED SQL compilation error: | Object 'UNIQUE KEYon' does not exist or not authorized."},
            {"ALTER TABLE kt MODIFY PRIMARY KEY RELY",
                "REFUSED SQL compilation error: | Object 'primary key' does not exist or not authorized."},
            {"ALTER TABLE kt ALTER PRIMARY KEY NORELY",
                "REFUSED SQL compilation error: | Object 'primary key' does not exist or not authorized."},
            {"ALTER TABLE kt MODIFY UNIQUE (b, a) RELY",
                "REFUSED SQL compilation error: | Object 'UNIQUE KEYon' does not exist or not authorized."},
            {"ALTER TABLE kt MODIFY UNIQUE b RELY",
                "REFUSED SQL compilation error: | syntax error line 1 at position 29 unexpected 'b'. | syntax error "
                + "line 1 at position 31 unexpected 'RELY'."},
            {"ALTER TABLE kt MODIFY UNIQUE (b) ENFORCED", "Statement executed successfully."},
            {"ALTER TABLE kt MODIFY UNIQUE (b) NOT ENFORCED RELY", "Statement executed successfully."},
            {"SHOW UNIQUE KEYS IN TABLE kt ->> SELECT \"column_name\", \"rely\" FROM $1 ORDER BY 1", "B|true"},
            {"ALTER TABLE kt MODIFY UNIQUE (nosuch) RELY",
                "REFUSED SQL compilation error: error line 1 at position 30 | invalid identifier 'NOSUCH'"},
            {"ALTER TABLE kt MODIFY FOREIGN KEY (a) RELY",
                "REFUSED SQL compilation error: | Object 'FOREIGN KEYon' does not exist or not authorized."},
            {"ALTER TABLE kt2 ADD PRIMARY KEY (a)", "Statement executed successfully."},
            {"SHOW UNIQUE KEYS IN TABLE kt2 ->> SELECT \"column_name\", \"rely\" FROM $1 ORDER BY 1", ""},
            {"SHOW PRIMARY KEYS IN TABLE kt2 ->> SELECT \"column_name\" FROM $1", "A"},
            {"ALTER TABLE kt2 DROP UNIQUE (a)",
                "REFUSED SQL compilation error: | constraint 'UNIQUE KEYon' does not exist"},
            {"ALTER TABLE kt2 MODIFY PRIMARY KEY RELY", "Statement executed successfully."},
            {"SHOW PRIMARY KEYS IN TABLE kt2 ->> SELECT \"column_name\", \"rely\" FROM $1", "A|true"},
            {"ALTER TABLE kt3 ADD COLUMN b INT CONSTRAINT pk PRIMARY KEY", "Statement executed successfully."},
            {"ALTER TABLE kt3 ADD COLUMN c INT CONSTRAINT u UNIQUE RELY", "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'kt3')",
                "create or replace TABLE KT3 ( | 	A NUMBER(38,0), | 	B NUMBER(38,0) NOT NULL, | 	C NUMBER(38,0), | "
                + "	constraint PK primary key (B), | 	constraint U unique (C) rely  | );"},
            {"CREATE OR REPLACE TABLE kt4c CLONE kt4", "Table KT4C successfully created."},
            {"SELECT GET_DDL('TABLE', 'kt4')",
                "create or replace TABLE KT4 ( | 	A NUMBER(38,0), | 	B NUMBER(38,0), | 	unique (A, B) rely , | "
                + "	unique (B) | );"},
            {"CREATE OR REPLACE TABLE kt6c CLONE kt6", "Table KT6C successfully created."},
            {"CREATE OR REPLACE TABLE kt7c CLONE kt7", "Table KT7C successfully created."},
            {"ALTER TABLE kt5 ADD CONSTRAINT u5 UNIQUE (a)", "Statement executed successfully."},
            {"ALTER TABLE kt5 ADD CONSTRAINT f5 FOREIGN KEY (c) REFERENCES kt3 (b)",
                "Statement executed successfully."},
            {"ALTER TABLE kt5 ADD CONSTRAINT p5 PRIMARY KEY (b)", "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'kt5')",
                "create or replace TABLE KT5 ( | 	A NUMBER(38,0), | 	B NUMBER(38,0), | 	C NUMBER(38,0), | "
                + "	constraint U5 unique (A), | 	constraint F5 foreign key (C) references "
                + "TEST_DB.TEST_SCHEMA.KT3(B), | 	constraint P5 primary key (B) | );"},
        });
    }

    @Test
    public void keysAlterAddsKeepTheirOrder() {
        engine.execute("CREATE OR REPLACE TABLE kt8 (a INT, b INT, c INT, UNIQUE (a) RELY, UNIQUE (b) RELY, UNIQUE "
            + "(c))");
        engine.execute("CREATE OR REPLACE TABLE kt9 (a INT, b INT, PRIMARY KEY (a) RELY, UNIQUE (b))");
        engine.execute("CREATE OR REPLACE TABLE kt10 (a INT, b INT, UNIQUE (a) RELY, UNIQUE (b) RELY)");
        engine.execute("CREATE OR REPLACE TABLE kt11 (a INT, b INT, c INT, UNIQUE (c) RELY, PRIMARY KEY (a), UNIQUE "
            + "(b))");
        engine.execute("CREATE OR REPLACE TABLE kt12 (a INT, b INT)");
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE kt8c CLONE kt8", "Table KT8C successfully created."},
            {"CREATE OR REPLACE TABLE kt9c CLONE kt9", "Table KT9C successfully created."},
            {"CREATE OR REPLACE TABLE kt10c CLONE kt10", "Table KT10C successfully created."},
            {"CREATE OR REPLACE TABLE kt11c CLONE kt11", "Table KT11C successfully created."},
            {"SELECT GET_DDL('TABLE', 'kt11')",
                "create or replace TABLE KT11 ( | 	A NUMBER(38,0) NOT NULL, | 	B NUMBER(38,0), | 	C NUMBER(38,0), | "
                + "	unique (C) rely , | 	unique (B), | 	primary key (A) | );"},
            {"ALTER TABLE kt12 ADD UNIQUE (b) RELY", "Statement executed successfully."},
            {"ALTER TABLE kt12 ADD PRIMARY KEY (a)", "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'kt12')",
                "create or replace TABLE KT12 ( | 	A NUMBER(38,0), | 	B NUMBER(38,0), | 	unique (B) rely , | "
                + "	primary key (A) | );"},
            {"CREATE OR REPLACE TABLE kt12c CLONE kt12", "Table KT12C successfully created."},
            {"ALTER TABLE kt12 MODIFY UNIQUE (b) NORELY", "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'kt12')",
                "create or replace TABLE KT12 ( | 	A NUMBER(38,0), | 	B NUMBER(38,0), | 	unique (B), | 	primary key "
                + "(A) | );"},
            {"SHOW UNIQUE KEYS IN TABLE kt12 ->> SELECT \"column_name\", \"rely\" FROM $1", "B|false"},
            {"ALTER TABLE kt12 MODIFY CONSTRAINT nosuch_c RELY",
                "REFUSED SQL compilation error: | Object 'NOSUCH_C' does not exist or not authorized."},
        });
    }
}
