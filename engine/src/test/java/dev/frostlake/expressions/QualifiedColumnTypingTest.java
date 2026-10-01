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

package dev.frostlake.expressions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A column named with its schema, or with its database and schema, is typed exactly as the plain
 * {@code table.column} spelling is (live-verified). Frostlake left it untyped, so every compile-time
 * argument rule stood aside and the statement answered, or failed at row time in its own words. A qualifier
 * that reaches another table, or names a relation through its alias, is still an invalid identifier first.
 */
public class QualifiedColumnTypingTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE fam (d DATE, f FLOAT, g VARCHAR(10), b BOOLEAN, n NUMBER(10,2))");
        engine.execute("INSERT INTO fam VALUES ('2024-01-15', 2.0, 'abc', TRUE, 1.5)");
        engine.execute("CREATE TABLE efam LIKE fam");
    }

    /** The one row's cells, as text. */
    private List<String> row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> cells = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            cells.add(String.valueOf(rs.getRows().get(0).getValue(i)));
        }
        return cells;
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    @Test
    public void aQualifiedColumnCarriesItsDeclaredType() {
        assertEquals(List.of("VARCHAR(10)[LOB]", "DATE[SB4]", "NUMBER(10,2)[SB2]", "NUMBER(9,0)[SB4]", "VARCHAR(20)[LOB]"),
            row("""
                SELECT SYSTEM$TYPEOF(TEST_SCHEMA.fam.g), SYSTEM$TYPEOF(TEST_DB.TEST_SCHEMA.fam.d),
                    SYSTEM$TYPEOF(TEST_SCHEMA.fam.n), SYSTEM$TYPEOF(TEST_SCHEMA.fam.d - TEST_SCHEMA.fam.d),
                    SYSTEM$TYPEOF(TEST_SCHEMA.fam.g || TEST_SCHEMA.fam.g)
                FROM fam"""));
        assertEquals(List.of("NUMBER(22,2)[SB8]"), row("SELECT SYSTEM$TYPEOF(SUM(TEST_SCHEMA.fam.n)) FROM fam"));
        engine.execute("CREATE VIEW gv AS SELECT TEST_SCHEMA.fam.g || 'x' AS gx FROM fam");
        final ResultSet described = engine.executeQuery("DESCRIBE VIEW gv");
        assertEquals("VARCHAR(11)", String.valueOf(described.getRows().get(0).getValue(described.getColumnIndex("type"))));
    }

    @Test
    public void compileTimeArgumentRulesReadTheQualifiedType() {
        final String dateAndFloat = "\nInvalid argument types for function '+': (DATE, FLOAT)";
        assertEquals("SQL compilation error: error line 1 at position 33" + dateAndFloat,
            refusal("SELECT TEST_DB.TEST_SCHEMA.fam.d + TEST_DB.TEST_SCHEMA.fam.f FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 25" + dateAndFloat,
            refusal("SELECT TEST_SCHEMA.fam.d + TEST_SCHEMA.fam.f FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 26" + dateAndFloat,
            refusal("SELECT TEST_SCHEMA.efam.d + TEST_SCHEMA.efam.f FROM efam"));
        assertEquals("SQL compilation error: error line 1 at position 58" + dateAndFloat,
            refusal("SELECT TEST_SCHEMA.fam.g FROM fam WHERE TEST_SCHEMA.fam.d + TEST_SCHEMA.fam.f IS NULL"));
        assertEquals("SQL compilation error: error line 1 at position 61" + dateAndFloat,
            refusal("SELECT TEST_SCHEMA.fam.g FROM fam ORDER BY TEST_SCHEMA.fam.d + TEST_SCHEMA.fam.f"));
        assertEquals("SQL compilation error: error line 1 at position 7"
            + "\nInvalid argument types for function 'DATE_ADDDAYSTODATE': (BOOLEAN, DATE)",
            refusal("SELECT DATEADD(day, TEST_DB.TEST_SCHEMA.fam.b, d) FROM fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(TEST_SCHEMA.FAM.G AS OBJECT)] for parameter 'TO_OBJECT'",
            refusal("SELECT CAST(TEST_SCHEMA.fam.g AS OBJECT) FROM fam"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(TEST_DB.TEST_SCHEMA.FAM.G AS OBJECT)] for parameter 'TO_OBJECT'",
            refusal("SELECT CAST(TEST_DB.TEST_SCHEMA.fam.g AS OBJECT) FROM fam"));
        assertEquals("SQL compilation error:\ninvalid type [TO_OBJECT(TEST_SCHEMA.FAM.G)] for parameter 'TO_OBJECT'",
            refusal("SELECT TO_OBJECT(TEST_SCHEMA.fam.g) FROM fam"));
    }

    @Test
    public void aQualifierThatNamesNoSuchRelationIsStillAnInvalidIdentifier() {
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'OTHER.FAM.D'",
            refusal("SELECT OTHER.fam.d + OTHER.fam.f FROM fam"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'TEST_SCHEMA.FAM.D'",
            refusal("SELECT TEST_SCHEMA.fam.d + TEST_SCHEMA.fam.f FROM fam f"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'TEST_DB.OTHER.FAM.D'",
            refusal("SELECT TEST_DB.OTHER.fam.d + 1 FROM fam"));
    }
}
