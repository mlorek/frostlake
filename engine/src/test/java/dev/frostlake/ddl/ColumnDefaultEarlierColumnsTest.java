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
 * A CREATE TABLE column's DEFAULT may read the columns defined before it — by name, by a condition over them, or
 * qualified by the table's own name (live-verified): an insert that leaves the column out writes the default over the
 * row it inserts, the earlier columns' own defaults applied first, through INSERT … VALUES, INSERT … SELECT and a
 * MERGE's insert alike; such a default named by the DEFAULT keyword in a VALUES list is {@code Invalid expression
 * [DE904A.C2 + 1] in VALUES clause}, {@code UPDATE … SET c = DEFAULT} is refused as needing the non-updated value of
 * the column it reads, and DESCRIBE, GET_DDL and INFORMATION_SCHEMA tell the default back with each column it reads
 * qualified by the table's name.
 */
public class ColumnDefaultEarlierColumnsTest extends MeasuredCellsTestSupport {

    @Test
    public void aDefaultReadsTheColumnsBeforeIt() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE de904a (c2 INT, c INT DEFAULT c2 + 1)", "Table DE904A successfully created."},
            {"INSERT INTO de904a (c2) VALUES (5)", "1"},
            {"INSERT INTO de904a (c2, c) VALUES (6, DEFAULT)",
                "REFUSED SQL compilation error: | Invalid expression [DE904A.C2 + 1] in VALUES clause"},
            {"INSERT INTO de904a VALUES (7, DEFAULT)",
                "REFUSED SQL compilation error: | Invalid expression [DE904A.C2 + 1] in VALUES clause"},
            {"INSERT INTO de904a (c) VALUES (9)", "1"},
            {"INSERT INTO de904a (c2) SELECT 3", "1"},
            {"SELECT c2, c FROM de904a ORDER BY c2 NULLS LAST, c", "3|4 ;; 5|6 ;; null|9"},
            {"UPDATE de904a SET c = DEFAULT WHERE c2 = 3",
                "REFUSED SQL compilation error: | Unsupported use of DEFAULT in UPDATE SET clause: C=DEFAULT needs "
                + "non-updated value of column C2"},
            {"UPDATE de904a SET c2 = 20 WHERE c2 = 5", "1|0"},
            {"SELECT c2, c FROM de904a ORDER BY c2 NULLS LAST, c", "3|4 ;; 20|6 ;; null|9"},
            {"DESCRIBE TABLE de904a ->> SELECT \"name\", \"default\" FROM $1", "C2|null ;; C|DE904A.C2 + 1"},
            {"SELECT GET_DDL('TABLE', 'de904a')",
                "create or replace TABLE DE904A ( | \tC2 NUMBER(38,0), | \tC NUMBER(38,0) DEFAULT DE904A.C2 + 1 | );"},
            {"SELECT COLUMN_NAME, COLUMN_DEFAULT FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'DE904A' "
                + "ORDER BY ORDINAL_POSITION",
                "C2|null ;; C|DE904A.C2 + 1"},
            {"MERGE INTO de904a t USING (SELECT 11 AS k) s ON FALSE WHEN NOT MATCHED THEN INSERT (c2) VALUES (s.k)",
                "1"},
            {"SELECT c2, c FROM de904a WHERE c2 = 11", "11|12"},
            {"ALTER TABLE de904a ADD COLUMN e INT DEFAULT c2 + 10",
                "REFUSED SQL compilation error: | Invalid column default expression [DE904A.C2 + 10]"},
            {"CREATE OR REPLACE TABLE de904r (c2 INT, c INT DEFAULT c2)", "Table DE904R successfully created."},
            {"INSERT INTO de904r (c2) VALUES (5)", "1"},
            {"SELECT c2, c FROM de904r", "5|5"},
            {"CREATE OR REPLACE TABLE de904q (c2 BOOLEAN, c BOOLEAN DEFAULT NOT c2)",
                "Table DE904Q successfully created."},
            {"INSERT INTO de904q (c2) VALUES (TRUE)", "1"},
            {"SELECT c2, c FROM de904q", "true|false"},
            {"CREATE OR REPLACE TABLE de904s (c2 INT, c INT DEFAULT c2 * 2, d INT DEFAULT c + 1)",
                "Table DE904S successfully created."},
            {"INSERT INTO de904s (c2) VALUES (5)", "1"},
            {"INSERT INTO de904s (c2, c) VALUES (5, 100)", "1"},
            {"SELECT c2, c, d FROM de904s ORDER BY c", "5|10|11 ;; 5|100|101"},
            {"CREATE OR REPLACE TABLE de904u (c2 VARCHAR, c VARCHAR DEFAULT UPPER(c2))",
                "Table DE904U successfully created."},
            {"INSERT INTO de904u (c2) VALUES ('ab')", "1"},
            {"SELECT c2, c FROM de904u", "ab|AB"},
            {"CREATE OR REPLACE TABLE de904w (c2 INT DEFAULT 4, c INT DEFAULT c2 + 1)",
                "Table DE904W successfully created."},
            {"INSERT INTO de904w VALUES (DEFAULT, DEFAULT)",
                "REFUSED SQL compilation error: | Invalid expression [DE904W.C2 + 1] in VALUES clause"},
            {"INSERT INTO de904w (c) VALUES (DEFAULT)",
                "REFUSED SQL compilation error: | Invalid expression [DE904W.C2 + 1] in VALUES clause"},
            {"INSERT INTO de904w (c2) VALUES (DEFAULT)", "1"},
            {"SELECT c2, c FROM de904w ORDER BY c2, c", "4|5"},
            {"CREATE OR REPLACE TABLE de904x (c2 INT, c INT DEFAULT c2 + c2)", "Table DE904X successfully created."},
            {"CREATE OR REPLACE TABLE de904y (c2 INT, c INT DEFAULT IFF(c2 > 1, 1, 2))",
                "Table DE904Y successfully created."},
            {"CREATE OR REPLACE TABLE de904z (c2 INT, c INT DEFAULT c2 IS NULL)", "Table DE904Z successfully created."},
            {"CREATE OR REPLACE TABLE de904v (c2 INT, c INT DEFAULT de904v.c2 + 1)",
                "Table DE904V successfully created."},
        });
    }

    /**
     * A MERGE's matched branch refuses SET c = DEFAULT over such a column as an UPDATE does, while an insert branch's
     * DEFAULT is computed over its row; ALTER TABLE ADD COLUMN refuses a bare column read as it does a computed one;
     * the table's own name may qualify a read with its schema and its database too (all live-verified).
     */
    @Test
    public void mergesAnAddedColumnAndQualifiedReads() {
        engine.execute("CREATE OR REPLACE TABLE de999 (c1 INT, c2 INT DEFAULT 5, c3 INT DEFAULT c2 + 1)");
        engine.execute("INSERT INTO de999 (c1) VALUES (1)");
        assertCells(new String[][] {
            {"MERGE INTO de999 t USING (SELECT 1 AS k) s ON t.c1 = s.k WHEN MATCHED THEN UPDATE SET c3 = DEFAULT",
                "REFUSED SQL compilation error: | Unsupported use of DEFAULT in UPDATE SET clause: C3=DEFAULT needs "
                + "non-updated value of column C2"},
            {"MERGE INTO de999 t USING (SELECT 2 AS k) s ON t.c1 = s.k WHEN NOT MATCHED THEN INSERT (c1, c3) "
                + "VALUES (2, DEFAULT)", "1"},
            {"SELECT c1, c2, c3 FROM de999 ORDER BY c1", "1|5|6 ;; 2|5|6"},
            {"ALTER TABLE de999 ADD COLUMN e INT DEFAULT c2",
                "REFUSED SQL compilation error: | Invalid column default expression [DE999.C2]"},
            {"CREATE OR REPLACE TABLE dq1000 (c1 INT, c2 INT DEFAULT test_schema.dq1000.c1 + 1, "
                + "c3 INT DEFAULT test_db.test_schema.dq1000.c1)", "Table DQ1000 successfully created."},
            {"INSERT INTO dq1000 (c1) VALUES (4)", "1"},
            {"SELECT c1, c2, c3 FROM dq1000", "4|5|4"},
            {"DESCRIBE TABLE dq1000 ->> SELECT \"name\", \"default\" FROM $1",
                "C1|null ;; C2|TEST_SCHEMA.DQ1000.C1 + 1 ;; C3|TEST_DB.TEST_SCHEMA.DQ1000.C1"},
            {"CREATE OR REPLACE TABLE dq1000b (c1 INT, c2 INT DEFAULT nosuch.dq1000b.c1)",
                "REFUSED SQL compilation error: error line 1 at position 56 | invalid identifier 'NOSUCH.DQ1000B.C1'"},
            {"CREATE OR REPLACE TABLE dq1000b (c1 INT, c2 INT DEFAULT nosuch.test_schema.dq1000b.c1)",
                "REFUSED SQL compilation error: error line 1 at position 56 | invalid identifier "
                + "'NOSUCH.TEST_SCHEMA.DQ1000B.C1'"},
        });
    }

    /**
     * A default is told back with the conversions the plan makes: a number, a date or a FLOAT read as text is
     * {@code CAST(… AS VARCHAR(134217728))}, bracketed beside {@code ||}; a written cast keeps its own type; a number
     * beside a FLOAT is cast to FLOAT (all live-verified).
     */
    @Test
    public void aDefaultIsToldWithItsConversions() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t VARCHAR"
                + " DEFAULT 'x' || c2)",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "'x' || (CAST(DT1001.C2 AS VARCHAR(134217728)))"},
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t VARCHAR"
                + " DEFAULT c2 || 'x')",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "(CAST(DT1001.C2 AS VARCHAR(134217728))) || 'x'"},
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t VARCHAR"
                + " DEFAULT CONCAT(c2, 'x'))",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "CONCAT(CAST(DT1001.C2 AS VARCHAR(134217728)), 'x')"},
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t VARCHAR"
                + " DEFAULT UPPER(c2))",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "UPPER(CAST(DT1001.C2 AS VARCHAR(134217728)))"},
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t VARCHAR"
                + " DEFAULT 'x' || d)",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "'x' || (CAST(DT1001.D AS VARCHAR(134217728)))"},
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t VARCHAR"
                + " DEFAULT c2::VARCHAR || 'x')",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "(CAST(DT1001.C2 AS VARCHAR)) || 'x'"},
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t VARCHAR"
                + " DEFAULT s || c2)",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "DT1001.S || (CAST(DT1001.C2 AS VARCHAR(134217728)))"},
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t VARCHAR"
                + " DEFAULT 'x' || (c2 + 1))",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "'x' || (CAST(DT1001.C2 + 1 AS VARCHAR(134217728)))"},
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t INT DEFAULT"
                + " LENGTH(c2))",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "LENGTH(CAST(DT1001.C2 AS VARCHAR(134217728)))"},
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t VARCHAR"
                + " DEFAULT 'x' || f)",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "'x' || (CAST(DT1001.F AS VARCHAR(134217728)))"},
            {"CREATE OR REPLACE TABLE dt1001 (c2 INT, d DATE, s VARCHAR, f FLOAT, n NUMBER(10,2), t VARCHAR"
                + " DEFAULT 'x' || n)",
                "Table DT1001 successfully created."},
            {"DESCRIBE TABLE dt1001 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "'x' || (CAST(DT1001.N AS VARCHAR(134217728)))"},
            {"CREATE OR REPLACE TABLE dr1003 (a INT, s VARCHAR, n NUMBER(10,2), f FLOAT, t NUMBER(38,1) DEFAULT a"
                + " + 1.5)",
                "Table DR1003 successfully created."},
            {"DESCRIBE TABLE dr1003 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "(CAST(DR1003.A AS NUMBER(38,1))) + 1.5"},
            {"CREATE OR REPLACE TABLE dr1003 (a INT, s VARCHAR, n NUMBER(10,2), f FLOAT, t NUMBER(38,6) DEFAULT a / 3)",
                "Table DR1003 successfully created."},
            {"DESCRIBE TABLE dr1003 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "SCALED_ROUND_INT_DIVIDE(DR1003.A, 3)"},
            {"CREATE OR REPLACE TABLE dr1003 (a INT, s VARCHAR, n NUMBER(10,2), f FLOAT, t FLOAT DEFAULT a + f)",
                "Table DR1003 successfully created."},
            {"DESCRIBE TABLE dr1003 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "(CAST(DR1003.A AS FLOAT)) + DR1003.F"},
            {"CREATE OR REPLACE TABLE dr1003 (a INT, s VARCHAR, n NUMBER(10,2), f FLOAT, t NUMBER(38,6) DEFAULT s / 2)",
                "Table DR1003 successfully created."},
            {"DESCRIBE TABLE dr1003 ->> SELECT \"default\" FROM $1 WHERE \"name\" = 'T'",
                "(CAST(TO_NUMBER(DR1003.S, 18, 5) AS NUMBER(24,11))) / 2"},
        });
    }
}
