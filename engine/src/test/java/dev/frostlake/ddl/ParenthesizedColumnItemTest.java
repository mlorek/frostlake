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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Parentheses around a select item's column reference are TRANSPARENT at any depth: the projected
 * column takes the source column's name, its canonical type and its NOT NULL, exactly as the bare
 * reference does. {@code (v)}, {@code ((v))} and {@code (t.v)} all agree with {@code v}.
 *
 * <p>Live-verified. The type is the half that used to be wrong — a parenthesized reference fell
 * through to the expression path and reported the VARCHAR placeholder.
 */
public class ParenthesizedColumnItemTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE pr_t (k NUMBER(5,1) NOT NULL, v VARCHAR(4) NOT NULL, w VARCHAR(4))");
    }

    /** The described column of a view over {@code definition}: name, type and null? in one string. */
    private String describedItem(final String definition) {
        engine.execute("CREATE OR REPLACE VIEW pr_v AS " + definition);
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW pr_v");
        return cell(rs, rs.getRows().get(0), "name") + "|" + cell(rs, rs.getRows().get(0), "type")
            + "|" + cell(rs, rs.getRows().get(0), "null?");
    }

    /** One pair of parentheses changes nothing about the column. */
    @Test
    public void oneParenthesisPairIsTransparent() {
        assertEquals("V|VARCHAR(4)|N", describedItem("SELECT v FROM pr_t"));
        assertEquals("V|VARCHAR(4)|N", describedItem("SELECT (v) FROM pr_t"));
    }

    /** Nor does any number of them. */
    @Test
    public void nestedParenthesesAreTransparentToo() {
        assertEquals("V|VARCHAR(4)|N", describedItem("SELECT ((v)) FROM pr_t"));
        assertEquals("V|VARCHAR(4)|N", describedItem("SELECT ( v ) FROM pr_t"));
    }

    /** A qualified reference inside them still names the COLUMN, not the qualifier. */
    @Test
    public void aQualifiedReferenceInParenthesesNamesTheColumn() {
        assertEquals("V|VARCHAR(4)|N", describedItem("SELECT (t.v) FROM pr_t t"));
    }

    /** The numeric family keeps its precision and scale through them. */
    @Test
    public void aNumericColumnKeepsItsPrecisionAndScale() {
        assertEquals("K|NUMBER(5,1)|N", describedItem("SELECT (k) FROM pr_t"));
    }

    /** An alias still wins over the column's name; the type and nullability come from the column. */
    @Test
    public void anAliasStillNamesTheColumn() {
        assertEquals("PA|VARCHAR(4)|N", describedItem("SELECT (v) AS pa FROM pr_t"));
    }

    /** And a nullable source stays nullable — the transparency carries the real answer, not NOT NULL. */
    @Test
    public void aNullableColumnStaysNullable() {
        assertEquals("W|VARCHAR(4)|Y", describedItem("SELECT (w) FROM pr_t"));
    }

    /** SHOW COLUMNS' descriptor agrees: the parenthesized item carries the column's own length. */
    @Test
    public void theDescriptorCarriesTheColumnsOwnLength() {
        engine.execute("CREATE OR REPLACE VIEW pr_v AS SELECT (v) FROM pr_t");
        final ResultSet rs = engine.executeQuery("SHOW COLUMNS IN VIEW test_db.test_schema.pr_v");
        assertEquals("""
            {"type":"TEXT","length":4,"byteLength":16,"nullable":false,"fixed":false}""",
            cell(rs, soleRowWhere(rs, "column_name", "V"), "data_type"));
    }
}
