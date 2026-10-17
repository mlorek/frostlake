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

import java.util.Locale;

/**
 * RELY is a key's own: two keys over one column each keep theirs, and a column's own UNIQUE is a key beside a
 * table-level one that spans the column. A primary key takes the place of a UNIQUE over its columns in its order, a
 * UNIQUE added over a key the table has is refused, a key a column's definition declares keeps its name when ADD COLUMN
 * adds it, and a copy keeps the primary key's order (live-verified).
 */
public class KeyRelyPerConstraintTest extends MeasuredCellsTestSupport {

    private static final String UNIQUE_KEYS = "SELECT \"column_name\", \"key_sequence\", \"rely\","
        + " IFF(\"constraint_name\" LIKE 'SYS_CONSTRAINT_%', 'SYS', \"constraint_name\")"
        + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY \"rely\", 4, 1, 2";

    private static final String PRIMARY_KEYS = "SELECT \"column_name\", \"key_sequence\", \"rely\" FROM"
        + " TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 2";

    private static String ddl(final String table) {
        return "SELECT GET_DDL('TABLE', '" + table + "')";
    }

    private static String constraints(final String table) {
        return "SELECT CONSTRAINT_TYPE, RELY FROM TEST_DB.INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = "
            + "'TEST_SCHEMA' AND TABLE_NAME = '" + table + "' ORDER BY 1, 2";
    }

    @Test
    public void twoKeysOverOneColumnEachKeepTheirOwnRely() {
        answer("CREATE OR REPLACE TABLE r5 (a INT, b INT, UNIQUE (a, b) RELY, UNIQUE (b))");
        assertCells(new String[][] {
            {ddl("r5"), "create or replace TABLE R5 ( | \tA NUMBER(38,0), | \tB NUMBER(38,0), | "
                + "\tunique (A, B) rely , | \tunique (B) | );"},
            {constraints("R5"), "UNIQUE|NO ;; UNIQUE|YES"},
        });
        answer("SHOW UNIQUE KEYS IN TABLE r5");
        assertAnswer("B|1|false|SYS ;; A|1|true|SYS ;; B|2|true|SYS", UNIQUE_KEYS);
        answer("CREATE OR REPLACE TABLE r15 (a INT, b INT, CONSTRAINT k15 PRIMARY KEY (b, a) RELY, UNIQUE (a) RELY, "
            + "UNIQUE (b) NORELY)");
        assertCells(new String[][] {
            {ddl("r15"), "create or replace TABLE R15 ( | \tA NUMBER(38,0) NOT NULL, | \tB NUMBER(38,0) NOT NULL, | "
                + "\tunique (A) rely , | \tunique (B), | \tconstraint K15 primary key (B, A) rely  | );"},
            {constraints("R15"), "PRIMARY KEY|YES ;; UNIQUE|NO ;; UNIQUE|YES"},
        });
        answer("SHOW PRIMARY KEYS IN TABLE r15");
        assertAnswer("B|1|true ;; A|2|true", PRIMARY_KEYS);
        answer("CREATE OR REPLACE TABLE r13 (a INT UNIQUE RELY, b INT, UNIQUE (a, b))");
        assertCells(new String[][] {
            {ddl("r13"), "create or replace TABLE R13 ( | \tA NUMBER(38,0), | \tB NUMBER(38,0), | "
                + "\tunique (A) rely , | \tunique (A, B) | );"},
            {constraints("R13"), "UNIQUE|NO ;; UNIQUE|YES"},
        });
        answer("CREATE OR REPLACE TABLE r9 (a INT, b INT, CONSTRAINT u9a UNIQUE (a, b), CONSTRAINT u9b UNIQUE (b))");
        assertCells(new String[][] {
            {"ALTER TABLE r9 MODIFY CONSTRAINT u9a RELY", "Statement executed successfully."},
            {ddl("r9"), "create or replace TABLE R9 ( | \tA NUMBER(38,0), | \tB NUMBER(38,0), | "
                + "\tconstraint U9A unique (A, B) rely , | \tconstraint U9B unique (B) | );"},
        });
    }

    @Test
    public void aPrimaryKeyTakesThePlaceOfAUniqueKeyOverItsColumns() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE r7 (a INT)", "Table R7 successfully created."},
            {"ALTER TABLE r7 ADD CONSTRAINT late_uq UNIQUE (a) RELY", "Statement executed successfully."},
            {"ALTER TABLE r7 ADD PRIMARY KEY (a)", "Statement executed successfully."},
            {ddl("r7"), "create or replace TABLE R7 ( | \tA NUMBER(38,0), | \tprimary key (A) | );"},
            {constraints("R7"), "PRIMARY KEY|NO"},
            {"ALTER TABLE r7 DROP CONSTRAINT late_uq", "REFUSED SQL compilation error: | constraint 'LATE_UQ' does not "
                + "exist"},
            {"CREATE OR REPLACE TABLE r9 (a INT, b INT, UNIQUE (a) RELY, PRIMARY KEY (a))", "Table R9 successfully "
                + "created."},
            {ddl("r9"), "create or replace TABLE R9 ( | \tA NUMBER(38,0) NOT NULL, | \tB NUMBER(38,0), | \tprimary key "
                + "(A) | );"},
            {"CREATE OR REPLACE TABLE r2 (a INT PRIMARY KEY UNIQUE)", "Table R2 successfully created."},
            {ddl("r2"), "create or replace TABLE R2 ( | \tA NUMBER(38,0) NOT NULL, | \tprimary key (A) | );"},
            {"CREATE OR REPLACE TABLE r8 (a INT PRIMARY KEY RELY)", "Table R8 successfully created."},
            {"ALTER TABLE r8 ADD CONSTRAINT u8 UNIQUE (a) NORELY", "REFUSED SQL compilation error: | Primary Key with "
                + "the signature already exists on the table"},
            {ddl("r8"), "create or replace TABLE R8 ( | \tA NUMBER(38,0) NOT NULL, | \tprimary key (A) rely  | );"},
            {"CREATE OR REPLACE TABLE r10 (a INT, b INT, UNIQUE (a, b) RELY)", "Table R10 successfully created."},
            {"ALTER TABLE r10 ADD UNIQUE (a, b)", "REFUSED SQL compilation error: | constraint with the same signature "
                + "already exists on table 'R10'"},
            {"ALTER TABLE r10 ADD PRIMARY KEY (b, a)", "Statement executed successfully."},
            {ddl("r10"), "create or replace TABLE R10 ( | \tA NUMBER(38,0), | \tB NUMBER(38,0), | \tunique (A, B) rely "
                + ", | \tprimary key (B, A) | );"},
            {"CREATE OR REPLACE TABLE r11 (a INT, b INT, UNIQUE (a, b) RELY)", "Table R11 successfully created."},
            {"ALTER TABLE r11 ADD PRIMARY KEY (a)", "Statement executed successfully."},
            {constraints("R11"), "PRIMARY KEY|NO ;; UNIQUE|YES"},
        });
    }

    @Test
    public void aKeyAddedWithItsColumnKeepsItsName() {
        answer("CREATE OR REPLACE TABLE r14 (a INT)");
        assertCells(new String[][] {
            {"ALTER TABLE r14 ADD COLUMN b INT CONSTRAINT pk14 PRIMARY KEY", "Statement executed successfully."},
            {ddl("r14"), "create or replace TABLE R14 ( | \tA NUMBER(38,0), | \tB NUMBER(38,0) NOT NULL, | "
                + "\tconstraint PK14 primary key (B) | );"},
            {"ALTER TABLE r14 ADD COLUMN c INT CONSTRAINT u14 UNIQUE RELY", "Statement executed successfully."},
        });
        answer("SHOW UNIQUE KEYS IN TABLE r14");
        assertAnswer("C|1|true|U14", UNIQUE_KEYS);
    }

    @Test
    public void aCopyKeepsThePrimaryKeysOrderAndEachKeysRely() {
        answer("CREATE OR REPLACE TABLE rpo (a INT, b INT, c INT, PRIMARY KEY (c, a))");
        answer("CREATE OR REPLACE TABLE rpo_l LIKE rpo");
        answer("CREATE OR REPLACE TABLE rpo_c CLONE rpo");
        for (final String copy : new String[] {"rpo_l", "rpo_c"}) {
            assertAnswer("create or replace TABLE " + copy.toUpperCase(Locale.ROOT) + " ( | \tA NUMBER(38,0) "
                + "NOT NULL, | \tB NUMBER(38,0), | \tC NUMBER(38,0) NOT NULL, | \tprimary key (C, A) | );", ddl(copy));
            answer("SHOW PRIMARY KEYS IN TABLE " + copy);
            assertAnswer("C|1|false ;; A|2|false", PRIMARY_KEYS);
        }
        answer("CREATE OR REPLACE TABLE r5 (a INT, b INT, UNIQUE (a, b) RELY, UNIQUE (b))");
        answer("CREATE OR REPLACE TABLE r5l LIKE r5");
        assertAnswer("create or replace TABLE R5L ( | \tA NUMBER(38,0), | \tB NUMBER(38,0), | \tunique (A, B) rely , | "
            + "\tunique (B) | );", ddl("r5l"));
    }
}
