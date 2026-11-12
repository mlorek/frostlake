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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A PIVOT's columns carry their static types, as UNPIVOT's do: a pass-through column its own type, a pivoted column
 * its aggregate's, so SYSTEM$TYPEOF reads the type and an expression over the column is typed by it, in a view too,
 * and a column list after the PIVOT keeps them. They were untyped: SYSTEM$TYPEOF read NULL[LOB] and "'FEB'" + 1 was
 * a VARCHAR column. Unmeasured on live.
 */
public class PivotColumnStaticTypeTest extends MeasuredCellsTestSupport {

    private static final String PIVOT = "(SELECT * FROM pcst PIVOT (SUM(amt) FOR m IN ('JAN', 'FEB')))";

    private void setUpSales() {
        engine.execute("CREATE OR REPLACE TABLE pcst (emp INT, m VARCHAR, amt NUMBER(10,2))");
        engine.execute("INSERT INTO pcst VALUES (1, 'JAN', 10), (2, 'FEB', 20)");
    }

    @Test
    public void aPivotedColumnIsTypedByItsAggregate() {
        setUpSales();
        final String pivoted = answer("SELECT SYSTEM$TYPEOF(\"'FEB'\") FROM " + PIVOT + " LIMIT 1");
        assertTrue(pivoted.startsWith("NUMBER(22,2)"), pivoted);
        final String passed = answer("SELECT SYSTEM$TYPEOF(emp) FROM " + PIVOT + " LIMIT 1");
        assertTrue(passed.startsWith("NUMBER(38,0)"), passed);
        final String sum = shape("SELECT \"'FEB'\" + 1 AS x FROM " + PIVOT);
        assertTrue(sum.startsWith("X:NUMBER("), sum);
    }

    @Test
    public void aViewOverThePivotRecordsTheTypes() {
        setUpSales();
        engine.execute("CREATE OR REPLACE VIEW pcsv AS SELECT \"'FEB'\" + 1 AS x, emp FROM " + PIVOT);
        final String described = answer("DESCRIBE VIEW pcsv");
        assertTrue(described.startsWith("X|NUMBER("), described);
    }

    @Test
    public void aColumnListKeepsTheTypes() {
        setUpSales();
        final String renamed = answer("SELECT SYSTEM$TYPEOF(f) FROM (SELECT * FROM pcst PIVOT (SUM(amt) FOR m IN"
            + " ('JAN', 'FEB')) AS p (e, j, f)) LIMIT 1");
        assertTrue(renamed.startsWith("NUMBER(22,2)"), renamed);
    }
}
