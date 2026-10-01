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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A quoted qualifier or alias is matched by its exact name: {@code "FZ".*} reads FZ, {@code "fz".*} and
 * {@code "fz".id} read {@code FZ AS "fz"} in the select list, WHERE, GROUP BY and ORDER BY. An unquoted {@code fz}
 * names FZ alone, never the relation quoted as {@code "fz"}, where Frostlake compared upper-cased text with the quotes
 * kept and failed both ways (live-verified).
 */
public class QuotedQualifierTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE FZ (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO FZ VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE TABLE \"lower\" (id INT)");
        engine.execute("INSERT INTO \"lower\" VALUES (1)");
        engine.execute("CREATE TABLE \"Mixed\" (b BOOLEAN)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    /** Each row's cells joined by commas, rows by bars. */
    private String rows(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < result.getRowCount(); r++) {
            text.append(r > 0 ? " | " : "");
            for (int c = 0; c < result.getColumnCount(); c++) {
                text.append(c > 0 ? ", " : "").append(result.getRows().get(r).getValue(c));
            }
        }
        return text.toString();
    }

    @Test
    public void aQuotedStarQualifierNamesItsRelationExactly() {
        assertEquals("5, true | 7, false", rows("SELECT \"FZ\".* FROM FZ ORDER BY 1"));
        assertEquals("5, true | 7, false", rows("SELECT \"fz\".* FROM FZ AS \"fz\" ORDER BY 1"));
        assertEquals("5, true | 7, false", rows("SELECT \"x y\".* FROM FZ AS \"x y\" ORDER BY 1"));
        assertEquals("5, true, 5, true | 7, false, 7, false",
            rows("SELECT \"fz\".*, \"FZ2\".* FROM FZ AS \"fz\" JOIN FZ AS \"FZ2\" ON \"fz\".id = \"FZ2\".id ORDER BY 1"));
        assertEquals("5, true | 7, false", rows("SELECT \"fz\".* FROM FZ AS \"fz\" GROUP BY ALL ORDER BY 1"));
        assertEquals("1", rows("SELECT \"lower\".* FROM \"lower\""));
        assertEquals("5, true | 7, false", rows("SELECT test_db.test_schema.\"FZ\".* FROM FZ ORDER BY 1"));
        assertEquals("{\"FZ\".*}", engine.executeQuery("SELECT {\"FZ\".*} FROM FZ").getColumns().get(0).getName());
    }

    @Test
    public void aQuotedColumnQualifierNamesItsRelationExactly() {
        assertEquals("5 | 7", rows("SELECT \"fz\".id FROM FZ AS \"fz\" ORDER BY 1"));
        assertEquals("5 | 7", rows("SELECT \"fz\".\"ID\" FROM FZ AS \"fz\" ORDER BY 1"));
        assertEquals("5", rows("SELECT \"fz\".id FROM FZ AS \"fz\" WHERE \"fz\".b ORDER BY 1"));
        assertEquals("5, 1 | 7, 1", rows("SELECT \"fz\".id, COUNT(*) FROM FZ AS \"fz\" GROUP BY \"fz\".id ORDER BY 1"));
        assertEquals("7 | 5", rows("SELECT id FROM FZ AS \"fz\" ORDER BY \"fz\".id DESC"));
    }

    @Test
    public void anUnquotedQualifierNamesNoQuotedRelation() {
        assertEquals("SQL compilation error:\nObject 'FZ' does not exist or not authorized.",
            refusal("SELECT fz.* FROM FZ AS \"fz\""));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'FZ.ID'",
            refusal("SELECT fz.id FROM FZ AS \"fz\""));
        assertEquals("SQL compilation error:\nObject 'LOWER' does not exist or not authorized.",
            refusal("SELECT LOWER.* FROM \"lower\""));
        assertEquals("SQL compilation error:\nObject 'LOWER' does not exist or not authorized.",
            refusal("SELECT \"LOWER\".* FROM \"lower\""));
        assertEquals("SQL compilation error:\nObject '\"Fz\"' does not exist or not authorized.",
            refusal("SELECT \"Fz\".* FROM FZ AS \"fz\""));
    }

    @Test
    public void aRefusalSpellsTheRelationByItsCanonicalName() {
        final String toDate = " for parameter 'TO_DATE'";
        assertEquals("SQL compilation error:\ninvalid type [CAST(\"Mixed\".B AS DATE)]" + toDate,
            refusal("SELECT b::DATE FROM \"Mixed\""));
        assertEquals("SQL compilation error:\ninvalid type [CAST(\"p q\".B AS DATE)]" + toDate,
            refusal("SELECT b::DATE FROM FZ AS \"p q\""));
        assertEquals("SQL compilation error:\ninvalid type [CAST(P.B AS DATE)]" + toDate,
            refusal("SELECT b::DATE FROM FZ AS \"P\""));
        assertEquals("SQL compilation error:\ninvalid type [CAST(V1.COL1 AS DATE)]" + toDate,
            refusal("SELECT col1::DATE FROM (VALUES (TRUE) AS v1 (col1))"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(\"v1\".COL1 AS DATE)]" + toDate,
            refusal("SELECT col1::DATE FROM (VALUES (TRUE) AS \"v1\" (col1))"));
        assertEquals("SQL compilation error: error line 1 at position 7\ninvalid identifier 'V1.COL1'",
            refusal("SELECT v1.col1 FROM (VALUES (TRUE) AS \"v1\" (col1))"));
    }
}
