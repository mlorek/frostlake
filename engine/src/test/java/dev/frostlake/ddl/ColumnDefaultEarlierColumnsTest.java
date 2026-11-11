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
}
