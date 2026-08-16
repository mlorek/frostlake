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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * An anonymous block run as a statement — directly, or as the text of an EXECUTE IMMEDIATE — answers one
 * row in a column named {@code anonymous block}: the value its RETURN produced, or NULL typed VARCHAR when
 * it ran to its end without one. A CALL keeps the procedure's name for that column, and a RETURN TABLE
 * answers its table's own columns. The column's type is read back the way a client can: DESCRIBE over a
 * table made from the block's result.
 */
public class AnonymousBlockResultTest extends BaseDatabaseTest {

    private static final String COLUMN = "anonymous block";

    /** Run a block that must answer exactly one row, and hand that result back. */
    private ResultSet oneRow(final String block) {
        final ResultSet rs = engine.executeQuery(block);
        assertEquals(1, rs.getRowCount(), block);
        assertEquals(1, rs.getColumns().size(), block);
        return rs;
    }

    /** The declared type of the previous statement's one result column, as DESCRIBE reads it back. */
    private String previousResultType(final String column) {
        engine.execute("CREATE OR REPLACE TABLE block_result AS SELECT * FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        return describeCell("block_result", column, "type");
    }

    @Test
    public void aReturnedValueAnswersInTheAnonymousBlockColumn() {
        final ResultSet rs = oneRow("BEGIN RETURN 1.7777; END;");
        assertEquals(COLUMN, rs.getColumns().get(0).getName());
        assertEquals(0, new BigDecimal("1.7777").compareTo(new BigDecimal(rs.getRows().get(0).getValue(0).toString())));
        assertEquals("NUMBER(5,4)", previousResultType(COLUMN));
    }

    @Test
    public void anExecuteImmediateOfABlockAnswersTheSameShape() {
        final ResultSet rs = oneRow("EXECUTE IMMEDIATE $$ BEGIN RETURN 1.7777; END; $$");
        assertEquals(COLUMN, rs.getColumns().get(0).getName());
        assertEquals("NUMBER(5,4)", previousResultType(COLUMN));
    }

    @Test
    public void aNestedBlocksReturnIsTheBlocksAnswer() {
        final ResultSet rs = oneRow("EXECUTE IMMEDIATE $$ BEGIN BEGIN RETURN 5; END; END; $$");
        assertEquals(COLUMN, rs.getColumns().get(0).getName());
        assertEquals(5L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
        assertEquals("NUMBER(1,0)", previousResultType(COLUMN));
    }

    @Test
    public void aBareReturnAnswersNull() {
        final ResultSet rs = oneRow("EXECUTE IMMEDIATE $$ BEGIN RETURN; END; $$");
        assertEquals(COLUMN, rs.getColumns().get(0).getName());
        assertNull(rs.getRows().get(0).getValue(0));
        assertEquals("VARCHAR(16777216)", previousResultType(COLUMN));
    }

    @Test
    public void aBlockWithoutReturnAnswersOneNullRow() {
        engine.execute("CREATE TABLE t (i INT)");
        final ResultSet rs = oneRow("BEGIN INSERT INTO t VALUES (1); SELECT 1; END;");
        assertEquals(COLUMN, rs.getColumns().get(0).getName());
        assertNull(rs.getRows().get(0).getValue(0));
        assertEquals(1L, ((Number) engine.executeQuery("SELECT COUNT(*) FROM t").getRows().get(0).getValue(0))
            .longValue(), "the block's statements still ran");
    }

    @Test
    public void anExecuteImmediateOfABlockWithoutReturnAnswersOneNullRow() {
        final ResultSet assigned = oneRow("EXECUTE IMMEDIATE $$ BEGIN LET x := 1; END; $$");
        assertEquals(COLUMN, assigned.getColumns().get(0).getName());
        assertNull(assigned.getRows().get(0).getValue(0));
        assertEquals("VARCHAR(16777216)", previousResultType(COLUMN));

        // A RETURN the run never reaches types nothing: the answer is still the NULL VARCHAR.
        final ResultSet skipped = oneRow("EXECUTE IMMEDIATE $$ BEGIN IF (FALSE) THEN RETURN 3; END IF; END; $$");
        assertEquals(COLUMN, skipped.getColumns().get(0).getName());
        assertNull(skipped.getRows().get(0).getValue(0));
        assertEquals("VARCHAR(16777216)", previousResultType(COLUMN));
    }

    @Test
    public void aCallKeepsTheProceduresNameForTheColumn() {
        engine.execute("""
            CREATE OR REPLACE PROCEDURE p1() RETURNS NUMBER(5,2) LANGUAGE SQL AS
            $$ BEGIN RETURN (SELECT 1.7777); END; $$""");
        final ResultSet rs = oneRow("CALL p1()");
        assertEquals("P1", rs.getColumns().get(0).getName());
        assertEquals(0, new BigDecimal("1.7777").compareTo(new BigDecimal(rs.getRows().get(0).getValue(0).toString())));
        // The subquery keeps its own type: the column follows the RETURN expression, not RETURNS.
        assertEquals("NUMBER(5,4)", previousResultType("P1"));
    }

    @Test
    public void aReturnTableAnswersItsOwnColumns() {
        final ResultSet rs = oneRow("""
            EXECUTE IMMEDIATE $$ DECLARE res RESULTSET DEFAULT (SELECT 1 AS a); BEGIN RETURN TABLE(res); END; $$""");
        assertEquals("A", rs.getColumns().get(0).getName());
        assertEquals(1L, ((Number) rs.getRows().get(0).getValue(0)).longValue());
    }
}
