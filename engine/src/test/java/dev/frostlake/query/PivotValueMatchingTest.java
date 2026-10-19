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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A PIVOT places a row by the VALUE of its FOR column: a BOOLEAN meets {@code TRUE}, the text {@code 'true'} and
 * the number {@code 1}, a NUMBER meets the same number written with other digits, and a NULL meets the column
 * written {@code NULL}. A written value names its column as written, a keyword in upper case; a value read from
 * the rows names it as the account prints it, and {@code IN (ANY)} orders the columns by value.
 */
public class PivotValueMatchingTest extends BaseDatabaseTest {

    private static final String PD = "(SELECT id, %s FROM pd)";

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FZ VALUES (1, TRUE), (2, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE FN (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FN VALUES (1, TRUE), (2, FALSE), (3, NULL)");
        engine.execute("CREATE OR REPLACE TABLE PD (id INT, d DATE, f FLOAT, s VARCHAR, n NUMBER(10,2), ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO PD VALUES (1, '2024-01-01', 1.5, 'x', 1.50, '2024-01-01 10:00:00'),"
            + " (2, '2024-01-02', 2.5, 'y', 2.25, '2024-01-02 11:00:00')");
    }

    /** The one row a pivot answers, as NAME=value pairs in column order. */
    private List<String> pivoted(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertEquals(1, rs.getRows().size(), sql);
        final Row row = rs.getRows().get(0);
        final List<String> out = new ArrayList<>();
        final List<ResultSetColumn> columns = rs.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            out.add(columns.get(i).getName() + "=" + row.getValue(i));
        }
        return out;
    }

    @Test
    public void aBooleanMeetsTheValuesThatSpellIt() {
        assertEquals(List.of("TRUE=1"), pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (TRUE))"));
        assertEquals(List.of("FALSE=2"), pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (FALSE))"));
        assertEquals(List.of("TRUE=1", "FALSE=2"), pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (TRUE, FALSE))"));
        assertEquals(List.of("TRUE=1", "FALSE=2"), pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (true, false))"));
        assertEquals(List.of("TRUE=1"), pivoted("SELECT * FROM fz PIVOT(COUNT(id) FOR b IN (TRUE))"));
        assertEquals(List.of("T=1", "F=2"), pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (TRUE AS t, FALSE AS f))"));
        assertEquals(List.of("'true'=1", "'false'=2"),
            pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN ('true', 'false'))"));
        assertEquals(List.of("1=1", "0=2"), pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (1, 0))"));
        assertEquals(List.of("TRUE=1", "FALSE=2"),
            pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (TRUE, FALSE) DEFAULT ON NULL (0))"));
    }

    @Test
    public void anyOrdersTheColumnsByValueAndNamesThemAsTheAccountPrintsThem() {
        assertEquals(List.of("FALSE=2", "TRUE=1"), pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (ANY))"));
        assertEquals(List.of("FALSE=2", "TRUE=1"), pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (ANY ORDER BY b))"));
        assertEquals(List.of("TRUE=1", "FALSE=2"),
            pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (ANY ORDER BY b DESC))"));
        assertEquals(List.of("FALSE=2", "TRUE=1"),
            pivoted("SELECT * FROM fz PIVOT(SUM(id) FOR b IN (SELECT DISTINCT b FROM fz ORDER BY b))"));
        assertEquals(List.of("1.5=1", "2.5=2"),
            pivoted("SELECT * FROM " + String.format(PD, "f") + " PIVOT(SUM(id) FOR f IN (ANY))"));
        assertEquals(List.of("1.5=1", "2.25=2"),
            pivoted("SELECT * FROM " + String.format(PD, "n") + " PIVOT(SUM(id) FOR n IN (ANY))"));
        assertEquals(List.of("'x'=1", "'y'=2"),
            pivoted("SELECT * FROM " + String.format(PD, "s") + " PIVOT(SUM(id) FOR s IN (ANY))"));
        assertEquals(List.of("'2024-01-01'=1", "'2024-01-02'=2"),
            pivoted("SELECT * FROM " + String.format(PD, "d") + " PIVOT(SUM(id) FOR d IN (ANY))"));
        assertEquals(List.of("'2024-01-01 10:00:00.000000000Z'=1", "'2024-01-02 11:00:00.000000000Z'=2"),
            pivoted("SELECT * FROM " + String.format(PD, "ts") + " PIVOT(SUM(id) FOR ts IN (ANY))"));
        assertEquals(List.of("5=1", "10=2", "100=9"), pivoted("SELECT * FROM (SELECT id, id * 5 AS k FROM fz"
            + " UNION ALL SELECT 9, 100) PIVOT(SUM(id) FOR k IN (ANY))"));
    }

    @Test
    public void aNumberMeetsTheSameNumberHoweverItIsWritten() {
        assertEquals(List.of("1.5=1", "2.25=2"),
            pivoted("SELECT * FROM " + String.format(PD, "n") + " PIVOT(SUM(id) FOR n IN (1.5, 2.25))"));
        assertEquals(List.of("1.5=1", "2.5=2"),
            pivoted("SELECT * FROM " + String.format(PD, "f") + " PIVOT(SUM(id) FOR f IN (1.5, 2.5))"));
        assertEquals(List.of("'2024-01-01'=1", "'2024-01-02'=2"), pivoted("SELECT * FROM " + String.format(PD, "d")
            + " PIVOT(SUM(id) FOR d IN ('2024-01-01', '2024-01-02'))"));
    }

    @Test
    public void theRowsThatHoldNoValueHaveAColumnOfTheirOwn() {
        assertEquals(List.of("FALSE=2", "TRUE=1", "NULL=3"), pivoted("SELECT * FROM fn PIVOT(SUM(id) FOR b IN (ANY))"));
        assertEquals(List.of("NULL=3", "TRUE=1", "FALSE=2"),
            pivoted("SELECT * FROM fn PIVOT(SUM(id) FOR b IN (ANY ORDER BY b DESC))"));
        assertEquals(List.of("TRUE=1", "NULL=3"), pivoted("SELECT * FROM fn PIVOT(SUM(id) FOR b IN (TRUE, NULL))"));
        assertEquals(List.of("NULL=3", "TRUE=1"), pivoted("SELECT * FROM fn PIVOT(SUM(id) FOR b IN (null, true))"));
        assertEquals(List.of("FALSE=2", "TRUE=1", "NULL=3"),
            pivoted("SELECT * FROM fn PIVOT(SUM(id) FOR b IN (SELECT DISTINCT b FROM fn ORDER BY b))"));
    }
}
