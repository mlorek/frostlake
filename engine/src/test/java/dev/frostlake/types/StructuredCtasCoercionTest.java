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

package dev.frostlake.types;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A column-listed CTAS judges a structured source column by the structured coercion rules, refusing one that does not
 * coerce to its column while the statement compiles as {@code incompatible types: [MAP(VARCHAR(134217728),
 * NUMBER(38,0))] and [OBJECT(a NUMBER(38,0))]} (live-verified), an ARRAY of numbers into an ARRAY of texts and the
 * other way round included; INSERT ... SELECT, a narrowing NUMBER element, a set operation and IFF keep their rules.
 */
public class StructuredCtasCoercionTest extends MeasuredCellsTestSupport {

    @Test
    public void aStructuredSourceColumnThatDoesNotCoerce() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE ct912 (o OBJECT(a INT)) AS SELECT {'a':1}::MAP(VARCHAR, INT)",
                "REFUSED SQL compilation error: | incompatible types: [MAP(VARCHAR(134217728), NUMBER(38,0))] and "
                + "[OBJECT(a NUMBER(38,0))]"},
            {"CREATE OR REPLACE TABLE ct912b (ar ARRAY(INT)) AS SELECT ['a']::ARRAY(VARCHAR)",
                "REFUSED SQL compilation error: | incompatible types: [ARRAY(VARCHAR(134217728))] and "
                + "[ARRAY(NUMBER(38,0))]"},
            {"CREATE OR REPLACE TABLE ct912c (ar ARRAY(VARCHAR)) AS SELECT [1, 2]::ARRAY(INT)",
                "REFUSED SQL compilation error: | incompatible types: [ARRAY(NUMBER(38,0))] and "
                + "[ARRAY(VARCHAR(16777216))]"},
            {"SELECT ar, SYSTEM$TYPEOF(ar) FROM ct912c",
                "REFUSED SQL compilation error: | Object 'CT912C' does not exist or not authorized. Your primary "
                + "role <ROLE> must have at least one privilege granted on TABLE CT912C."},
            {"CREATE OR REPLACE TABLE ct912d (ar ARRAY(VARCHAR))", "Table CT912D successfully created."},
            {"INSERT INTO ct912d SELECT [1, 2]::ARRAY(INT)",
                "REFUSED SQL compilation error: | Expression type does not match column data type, expecting "
                + "ARRAY(VARCHAR(16777216)) but got ARRAY(NUMBER(38,0)) for column AR"},
            {"SELECT ar FROM ct912d", ""},
            {"CREATE OR REPLACE TABLE ct912e (ar ARRAY(NUMBER(5,1)))", "Table CT912E successfully created."},
            {"INSERT INTO ct912e SELECT [1.25]::ARRAY(NUMBER(10,2))", "1"},
            {"INSERT INTO ct912e SELECT [12345.6]::ARRAY(NUMBER(10,2))",
                "REFUSED DML operation to table CT912E failed on column AR with error: Typed object schema "
                + "mismatch in conversion"},
            {"SELECT ar FROM ct912e", "[1.3]"},
            {"CREATE OR REPLACE TABLE ct912f (ar ARRAY(NUMBER(5,1))) AS SELECT [1.25]::ARRAY(NUMBER(10,2))",
                "Table CT912F successfully created."},
            {"SELECT ar FROM ct912f", "[1.3]"},
            {"SELECT x, SYSTEM$TYPEOF(x) FROM (SELECT [1]::ARRAY(INT) x UNION ALL SELECT ['a']::ARRAY(VARCHAR))",
                "REFUSED SQL compilation error: | inconsistent data type for result columns for set operator input "
                + "branches, expected ARRAY(VARCHAR(134217728)), got ARRAY(NUMBER(38,0))"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, [1]::ARRAY(INT), ['a']::ARRAY(VARCHAR)))",
                "REFUSED SQL compilation error: | Can not convert parameter 'CAST(ARRAY_CONSTRUCT('a') AS "
                + "ARRAY(VARCHAR(134217728)))' of type [ARRAY(VARCHAR(134217728))] into expected type "
                + "[ARRAY(NUMBER(38,0))]"},
        });
    }
}
