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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A column that holds nothing but an untyped NULL has no type of its own: DUAL's COLUMN1, a VALUES column written
 * as NULL in every row, a derived or a view column over the bare word. Arithmetic, a conditional and a set
 * operation over it take the other operand's type, as they do over the bare word, while the column itself
 * describes as a zero-width string and is stored as an ordinary one.
 */
public class UntypedNullColumnTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private String describedType(final String statement, final String column) {
        final ResultSet rs = engine.executeQuery(statement);
        return cell(rs, soleRowWhere(rs, "name", column), "type");
    }

    @Test
    public void arithmeticAndConditionalsTakeTheOtherOperandsType() {
        assertEquals("NUMBER(19,0)[SB1]", scalar("SELECT SYSTEM$TYPEOF(column1 + 1)"));
        assertEquals("NUMBER(19,0)[SB1]", scalar("SELECT SYSTEM$TYPEOF(column1 + 1) FROM DUAL"));
        assertEquals("NUMBER(19,0)[SB1]", scalar("SELECT SYSTEM$TYPEOF(column1 + 1) FROM (VALUES (NULL))"));
        assertEquals("NUMBER(1,0)[SB1]", scalar("SELECT SYSTEM$TYPEOF(COALESCE(column1, 1))"));
        assertEquals("NULL[LOB]", scalar("SELECT SYSTEM$TYPEOF(column1)"));
        assertEquals("NULL[LOB]", scalar("SELECT SYSTEM$TYPEOF(column1) FROM (VALUES (NULL), (NULL))"));
        assertEquals("NUMBER(19,0)[SB1]", scalar("SELECT SYSTEM$TYPEOF(x + 1) FROM (SELECT NULL AS x)"));
        // A column some row writes a value in is typed by that value.
        assertEquals("2", scalar("SELECT MAX(column1 + 1) FROM (VALUES (NULL), (1))"));
    }

    @Test
    public void aStoredQueryKeepsTheFoldedTypes() {
        engine.execute("CREATE OR REPLACE TABLE CT3 AS"
            + " SELECT column1 + 1 AS c, COALESCE(column1, 1) AS d, column1 AS e, column1 + 1.5 AS f,"
            + " NVL(column1, CURRENT_DATE) AS g, GREATEST(column1, 1) AS h, CASE WHEN TRUE THEN column1 ELSE 1 END AS i");
        assertEquals("NUMBER(19,0)", describedType("DESCRIBE TABLE CT3", "C"));
        assertEquals("NUMBER(1,0)", describedType("DESCRIBE TABLE CT3", "D"));
        assertEquals("VARCHAR(16777216)", describedType("DESCRIBE TABLE CT3", "E"));
        assertEquals("NUMBER(19,1)", describedType("DESCRIBE TABLE CT3", "F"));
        assertEquals("DATE", describedType("DESCRIBE TABLE CT3", "G"));
        assertEquals("NUMBER(1,0)", describedType("DESCRIBE TABLE CT3", "H"));
        assertEquals("NUMBER(1,0)", describedType("DESCRIBE TABLE CT3", "I"));
        engine.execute("CREATE OR REPLACE TABLE CT4 AS"
            + " SELECT column1 + 1 AS c, COALESCE(column1, 1) AS d, column1 AS e FROM (VALUES (NULL))");
        assertEquals("NUMBER(19,0)", describedType("DESCRIBE TABLE CT4", "C"));
        assertEquals("NUMBER(1,0)", describedType("DESCRIBE TABLE CT4", "D"));
        assertEquals("VARCHAR(16777216)", describedType("DESCRIBE TABLE CT4", "E"));
    }

    @Test
    public void aSetOperationFoldsItAway() {
        engine.execute("CREATE OR REPLACE TABLE U1 AS SELECT column1 AS c UNION ALL SELECT 1");
        assertEquals("NUMBER(1,0)", describedType("DESCRIBE TABLE U1", "C"));
        engine.execute("CREATE OR REPLACE TABLE U2 AS SELECT * FROM (VALUES (NULL)) UNION ALL SELECT 1");
        assertEquals("NUMBER(1,0)", describedType("DESCRIBE TABLE U2", "COLUMN1"));
        engine.execute("CREATE OR REPLACE TABLE U3 AS SELECT column1 AS c UNION ALL SELECT CURRENT_DATE");
        assertEquals("DATE", describedType("DESCRIBE TABLE U3", "C"));
        engine.execute("CREATE OR REPLACE TABLE U4 AS SELECT column1 AS c UNION ALL SELECT 'a'");
        assertEquals("VARCHAR(1)", describedType("DESCRIBE TABLE U4", "C"));
    }

    @Test
    public void theColumnItselfDescribesAsAZeroWidthString() {
        engine.executeQuery("SELECT * FROM DUAL");
        assertEquals("VARCHAR(0)", describedType("DESCRIBE RESULT LAST_QUERY_ID()", "COLUMN1"));
        engine.executeQuery("SELECT *");
        assertEquals("VARCHAR(0)", describedType("DESCRIBE RESULT LAST_QUERY_ID()", "COLUMN1"));
        engine.executeQuery("SELECT * FROM (VALUES (NULL))");
        assertEquals("VARCHAR(0)", describedType("DESCRIBE RESULT LAST_QUERY_ID()", "COLUMN1"));
        engine.executeQuery("SELECT column1 + 1");
        assertEquals("NUMBER(19,0)", describedType("DESCRIBE RESULT LAST_QUERY_ID()", "COLUMN1 + 1"));
    }

    @Test
    public void aViewOverTheBareWordDescribesAStringAndStillReadsAsTheBareWord() {
        engine.execute("CREATE OR REPLACE VIEW unv AS SELECT NULL AS x");
        assertEquals("VARCHAR(16777216)", describedType("DESCRIBE VIEW unv", "X"));
        assertEquals("NUMBER(19,0)[SB1]", scalar("SELECT SYSTEM$TYPEOF(x + 1) FROM unv"));
    }
}
