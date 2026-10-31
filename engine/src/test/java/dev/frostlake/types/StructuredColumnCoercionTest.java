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
 * A structured value is written to a structured column only when it coerces to the column's type (Snowflake's
 * structured-type coercion rules): the same kind of type, and elements that coerce — a number to any number, a
 * timestamp to its own flavour or TIMESTAMP_LTZ and TIMESTAMP_TZ to each other, every other element to its own type,
 * an OBJECT's keys the same names in the same order. Any other pairing is refused while the write compiles, in the
 * column-type sentence (live-verified for a text element and for a MAP); a NUMBER element into a DECFLOAT one
 * compiles and is refused by the row.
 */
public class StructuredColumnCoercionTest extends MeasuredCellsTestSupport {

    private static final String MISMATCH = "REFUSED SQL compilation error: | Expression type does not match column "
        + "data type, expecting ";

    @Test
    public void anElementOfAnotherKindIsRefusedWhileCompiling() {
        engine.execute("CREATE OR REPLACE TABLE st4 (ar ARRAY(INT))");
        engine.execute("CREATE OR REPLACE TABLE st7 (o OBJECT(a INT))");
        engine.execute("CREATE OR REPLACE TABLE st3 (ar ARRAY(DECFLOAT))");
        assertCells(new String[][] {
            {"INSERT INTO st4 SELECT ['a']::ARRAY(VARCHAR)",
                MISMATCH + "ARRAY(NUMBER(38,0)) but got ARRAY(VARCHAR(134217728)) for column AR"},
            {"INSERT INTO st7 SELECT {'a':1}::MAP(VARCHAR, INT)",
                MISMATCH + "OBJECT(a NUMBER(38,0)) but got MAP(VARCHAR(134217728), NUMBER(38,0)) for column O"},
            {"INSERT INTO st3 SELECT ['1']::ARRAY(VARCHAR)",
                MISMATCH + "ARRAY(DECFLOAT(38)) but got ARRAY(VARCHAR(134217728)) for column AR"},
            {"INSERT INTO st4 SELECT [1]::ARRAY(INT)", "1"},
            {"INSERT INTO st3 SELECT [1]::ARRAY(NUMBER)", "REFUSED DML operation to table ST3 failed on column AR "
                + "with error: Typed object schema mismatch in conversion"},
        });
    }

    @Test
    public void theDocumentedPairsCoerce() {
        engine.execute("CREATE OR REPLACE TABLE sc_n (ar ARRAY(INT))");
        engine.execute("CREATE OR REPLACE TABLE sc_t (ar ARRAY(TIMESTAMP_TZ))");
        engine.execute("CREATE OR REPLACE TABLE sc_o (o OBJECT(a INT))");
        engine.execute("CREATE OR REPLACE TABLE sc_m (m MAP(VARCHAR, INT))");
        assertCells(new String[][] {
            {"INSERT INTO sc_n SELECT [1.5]::ARRAY(NUMBER(5,1))", "1"},
            {"INSERT INTO sc_n SELECT [1.5]::ARRAY(FLOAT)", "1"},
            {"INSERT INTO sc_t SELECT ['2024-01-01 00:00:00']::ARRAY(TIMESTAMP_LTZ)", "1"},
            {"INSERT INTO sc_t SELECT ['2024-01-01 00:00:00']::ARRAY(TIMESTAMP_NTZ)",
                MISMATCH + "ARRAY(TIMESTAMP_TZ(9)) but got ARRAY(TIMESTAMP_NTZ(9)) for column AR"},
            {"INSERT INTO sc_o SELECT {'b':1}::OBJECT(b INT)",
                MISMATCH + "OBJECT(a NUMBER(38,0)) but got OBJECT(b NUMBER(38,0)) for column O"},
            {"INSERT INTO sc_m SELECT {'a':1}::OBJECT(a INT)",
                MISMATCH + "MAP(VARCHAR(16777216), NUMBER(38,0)) but got OBJECT(a NUMBER(38,0)) for column M"},
            {"UPDATE sc_n SET ar = ['x']::ARRAY(VARCHAR)",
                MISMATCH + "ARRAY(NUMBER(38,0)) but got ARRAY(VARCHAR(134217728)) for column AR"},
        });
    }
}
