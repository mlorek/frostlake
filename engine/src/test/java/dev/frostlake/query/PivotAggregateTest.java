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
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A PIVOT's aggregate is any one aggregate over one column, each pivoted cell holding what a grouped query would
 * compute over the cell's rows (live-verified): a text's MIN and MAX, a BOOLEAN's, a list, a median — and over a
 * cell no row reaches what the aggregate answers over none, a zero count or an empty list rather than a NULL.
 * The call is one bare aggregate name and one column: anything else is refused where it is written.
 */
public class PivotAggregateTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE flags (student VARCHAR, subject VARCHAR, ok BOOLEAN, score INTEGER, arr ARRAY,"
            + " \"low\" INTEGER, note VARCHAR(20))");
        engine.execute("INSERT INTO flags SELECT 'Alice', 'Math', TRUE, 2, ARRAY_CONSTRUCT(1, 2), 5, 'good'");
        engine.execute("INSERT INTO flags SELECT 'Alice', 'Math', FALSE, 3, ARRAY_CONSTRUCT(2, 3), 6, 'fine'");
        engine.execute("INSERT INTO flags SELECT 'Bob', 'Art', TRUE, 1, ARRAY_CONSTRUCT(9), 7, 'ok'");
    }

    /** The rows, cells joined by a bar and rows by a double semicolon. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder out = new StringBuilder();
        boolean firstRow = true;
        while (rs.next()) {
            out.append(firstRow ? "" : " ;; ");
            firstRow = false;
            for (int c = 0; c < rs.getColumnCount(); c++) {
                out.append(c == 0 ? "" : "|").append(rs.getValue(c));
            }
        }
        return out.toString();
    }

    /** One aggregate over one column, pivoted over both subjects and read student by student. */
    private String pivoted(final String column, final String aggregate) {
        return rows("SELECT * FROM (SELECT student, subject, " + column + " FROM flags f) PIVOT (" + aggregate
            + " FOR subject IN ('Math', 'Art')) ORDER BY 1");
    }

    private String refusal(final String column, final String aggregate) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                pivoted(column, aggregate);
            }
        }).getMessage();
    }

    /** The declared type of the first pivoted column. */
    private String pivotedType(final String sql) {
        final DataType type = engine.executeQuery(sql).getColumns().get(1).getDataType();
        if (type instanceof NumericType && !NumericType.isApproximate(type)) {
            return "NUMBER(" + ((NumericType) type).getPrecision() + "," + ((NumericType) type).getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        return type.getName();
    }

    @Test
    public void anyAggregateOverOneColumnFillsTheCells() {
        assertEquals("Alice|fine|null ;; Bob|null|ok", pivoted("note", "MIN(note)"));
        assertEquals("Alice|good|null ;; Bob|null|ok", pivoted("note", "MAX(note)"));
        assertEquals("Alice|true|null ;; Bob|null|true", pivoted("ok", "MAX(ok)"));
        assertEquals("Alice|false|null ;; Bob|null|true", pivoted("ok", "BOOLAND_AGG(ok)"));
        assertEquals("Alice|2.500|null ;; Bob|null|1.000", pivoted("score", "MEDIAN(score)"));
        assertEquals("Alice|2|null ;; Bob|null|1", pivoted("score", "MODE(score)"));
        assertEquals("Alice|0.500000|null ;; Bob|null|null", pivoted("score", "VARIANCE(score)"));
        assertEquals("Alice|0.250000|null ;; Bob|null|0.000000", pivoted("score", "VAR_POP(score)"));
        assertEquals("Alice|5|null ;; Bob|null|1", pivoted("score", "sum(score)"), "the name in any case");
    }

    @Test
    public void aCellNoRowReachesHoldsTheAggregateOverNoRows() {
        assertEquals("Alice|2|0 ;; Bob|0|1", pivoted("score", "COUNT(score)"), "a count of none is zero");
        assertEquals("Alice|2|0 ;; Bob|0|1", pivoted("arr", "COUNT(arr)"));
        assertEquals("Alice|1|0 ;; Bob|0|1", pivoted("ok", "COUNT_IF(ok)"));
        assertEquals("Alice|[1,2,3]|[] ;; Bob|[]|[9]", pivoted("arr", "ARRAY_UNION_AGG(arr)"), "a list of none is empty");
        assertEquals("1|Bob| ;; 2||Alice ;; 3||Alice", rows("""
            SELECT * FROM (SELECT student, subject, score FROM flags)
            PIVOT (LISTAGG(student) FOR subject IN (SELECT DISTINCT subject FROM flags ORDER BY 1)) ORDER BY 1"""),
            "and a text joined from none is the empty text");
        assertEquals("Alice|0|2 ;; Bob|1|0", rows("""
            SELECT * FROM (SELECT student, subject, score FROM flags)
            PIVOT (COUNT(score) FOR subject IN (ANY ORDER BY subject)) ORDER BY 1"""));
    }

    @Test
    public void defaultOnNullFillsTheNullCellsOnly() {
        assertEquals("Alice|3|-1 ;; Bob|-1|1", rows("""
            SELECT * FROM (SELECT student, subject, score FROM flags)
            PIVOT (MAX(score) FOR subject IN ('Math', 'Art') DEFAULT ON NULL (-1)) ORDER BY 1"""));
        assertEquals("Alice|2|0 ;; Bob|0|1", rows("""
            SELECT * FROM (SELECT student, subject, score FROM flags)
            PIVOT (COUNT(score) FOR subject IN ('Math', 'Art') DEFAULT ON NULL (-1)) ORDER BY 1"""),
            "a zero count is no NULL");
    }

    @Test
    public void aNullKeyKeepsItsGroupAndNoGroupingColumnMakesOneRow() {
        assertEquals("Alice|5 ;; Bob|null", rows("""
            SELECT * FROM (SELECT student, IFF(subject = 'Art', NULL, subject) AS subject, score FROM flags)
            PIVOT (SUM(score) FOR subject IN ('Math')) ORDER BY 1"""));
        assertEquals("2|0", rows(
            "SELECT * FROM (SELECT subject, score FROM flags) PIVOT (COUNT(score) FOR subject IN ('Math', 'Zzz'))"));
        assertEquals("0|0", rows("SELECT * FROM (SELECT subject, score FROM flags WHERE 1 = 0)"
            + " PIVOT (COUNT(score) FOR subject IN ('Math', 'Zzz'))"), "one row over no rows too");
        assertEquals("", rows("SELECT * FROM (SELECT student, subject, score FROM flags WHERE 1 = 0)"
            + " PIVOT (COUNT(score) FOR subject IN ('Math'))"), "and none when there is a column to group by");
    }

    @Test
    public void theColumnIsNamedAsAnIdentifierIs() {
        assertEquals("Alice|5|null ;; Bob|null|1", pivoted("score", "SUM(\"SCORE\")"));
        assertEquals("Alice|11|null ;; Bob|null|7", pivoted("\"low\"", "SUM(\"low\")"));
        assertTrue(refusal("\"low\"", "SUM(low)").contains("invalid identifier 'LOW'"));
        assertTrue(refusal("score", "SUM(f.score)").contains("invalid identifier 'F.SCORE'"));
    }

    @Test
    public void anythingButOneAggregateOverOneColumnIsRefused() {
        assertTrue(refusal("score", "COUNT(DISTINCT score)").contains("unexpected 'DISTINCT'."));
        assertTrue(refusal("score", "SUM(score + 1)").contains("unexpected '+'."));
        assertTrue(refusal("student", "MAX(UPPER(student))").contains("unexpected '('."));
        assertTrue(refusal("score", "COUNT(*)").contains("unexpected '*'."));
        assertTrue(refusal("score", "ANY_VALUE(score)").contains("'ANY_VALUE' is not a recognized aggregate function"));
        assertTrue(refusal("student", "UPPER(student)").contains("'UPPER' is not a recognized aggregate function"));
        assertTrue(refusal("score", "nosuch_fn(score)").contains("invalid function 'nosuch_fn'"));
        assertTrue(refusal("score", "\"SUM\"(score)").contains("invalid function '\"SUM\"'"));
        assertTrue(refusal("score", "GROUPING(score)").contains("invalid function 'GROUPING'"));
    }

    @Test
    public void aPivotedColumnDeclaresItsAggregatesType() {
        final String head = "SELECT * FROM (SELECT student, subject, ";
        assertEquals("BOOLEAN", pivotedType(head + "ok FROM flags) PIVOT (BOOLAND_AGG(ok) FOR subject IN ('Math'))"));
        assertEquals("NUMBER(13,0)", pivotedType(head + "ok FROM flags) PIVOT (COUNT_IF(ok) FOR subject IN ('Math'))"));
        assertEquals("ARRAY", pivotedType(head + "arr FROM flags) PIVOT (ARRAY_UNION_AGG(arr) FOR subject IN ('Math'))"));
        assertEquals("NUMBER(38,3)", pivotedType(head + "score FROM flags) PIVOT (MEDIAN(score) FOR subject IN ('Math'))"));
        assertEquals("VARCHAR(134217728)", pivotedType(head + "note FROM flags) PIVOT (MAX(note) FOR subject IN ('Math'))"),
            "a text over a written list declares no width of its own");
        assertEquals("VARCHAR(134217728)", pivotedType(head + "note FROM flags) PIVOT (MAX(note) FOR subject IN"
            + " ('Math', 'Art') DEFAULT ON NULL ('none'))"));
        assertEquals("VARCHAR(20)", pivotedType(head + "note FROM flags) PIVOT (MAX(note) FOR subject IN (ANY))"),
            "and over ANY it keeps the column's");
    }
}
