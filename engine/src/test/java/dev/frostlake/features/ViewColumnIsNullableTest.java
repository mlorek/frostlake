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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * INFORMATION_SCHEMA.COLUMNS carries a VIEW column's NOT NULL, on the same rule DESCRIBE VIEW's
 * {@code null?} cell uses: a projected column reference keeps it, an expression over that same column
 * loses it, and an outer join clears it on the side it null-extends.
 *
 * <p>Live-verified against the base table as a control — the two surfaces agree row for row, in this
 * view's spelling ({@code YES}/{@code NO}) rather than DESCRIBE's ({@code Y}/{@code N}).
 */
public class ViewColumnIsNullableTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE vn_t (k NUMBER NOT NULL, v VARCHAR(4) NOT NULL, w VARCHAR(4))");
        engine.execute("CREATE TABLE vn_r (k NUMBER NOT NULL, r VARCHAR(4) NOT NULL)");
    }

    private String isNullable(final String relation, final String column) {
        final ResultSet rs = engine.executeQuery(
            "SELECT column_name, is_nullable FROM test_db.information_schema.columns"
            + " WHERE table_name = '" + relation + "'");
        return cell(rs, soleRowWhere(rs, "column_name", column), "is_nullable");
    }

    /** The control: a base table reports its own declaration. */
    @Test
    public void aBaseTableReportsItsOwnNullability() {
        assertEquals("NO", isNullable("VN_T", "K"));
        assertEquals("NO", isNullable("VN_T", "V"));
        assertEquals("YES", isNullable("VN_T", "W"));
    }

    /** And a view carries it through a projected column reference. */
    @Test
    public void aViewCarriesTheNotNullThrough() {
        engine.execute("CREATE VIEW vn_v AS SELECT k, v, w FROM vn_t");
        assertEquals("NO", isNullable("VN_V", "K"));
        assertEquals("NO", isNullable("VN_V", "V"));
        assertEquals("YES", isNullable("VN_V", "W"));
    }

    /** An expression over the same column accepts NULL. */
    @Test
    public void anExpressionColumnIsNullable() {
        engine.execute("CREATE VIEW vn_e AS SELECT UPPER(v) AS uv, v || 'x' AS cv FROM vn_t");
        assertEquals("YES", isNullable("VN_E", "UV"));
        assertEquals("YES", isNullable("VN_E", "CV"));
    }

    /** An outer join clears it on the side it null-extends, and only that side. */
    @Test
    public void anOuterJoinClearsTheNullExtendedSide() {
        engine.execute("CREATE VIEW vn_j AS SELECT l.v AS lv, r.r AS rr"
            + " FROM vn_t l LEFT JOIN vn_r r ON l.k = r.k");
        assertEquals("NO", isNullable("VN_J", "LV"));
        assertEquals("YES", isNullable("VN_J", "RR"));
    }
}
