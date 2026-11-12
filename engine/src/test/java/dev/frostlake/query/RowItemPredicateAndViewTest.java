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

/**
 * A WHERE whose whole predicate is a subquery of one ROW item is refused by that ROW's type, the subquery named as the
 * account plans it, ahead of the item's own refusal. A view whose body selects a named ROW, or holds one in an EXISTS's
 * body, is created and types the column {@code ROW(ANY)}, and a read of it refuses the ROW at its place in the view's
 * CREATE text (all live-verified).
 */
public class RowItemPredicateAndViewTest extends MeasuredCellsTestSupport {

    private static final String ROW_ITEM = "Unsupported: SELECT list element with multiple columns.";

    private void createTable() {
        engine.execute("CREATE OR REPLACE TABLE ri910 (a INT, b VARCHAR, d DATE)");
        engine.execute("INSERT INTO ri910 VALUES (1, 'x', '2024-01-01'), (2, 'y', '2024-02-01')");
    }

    private static String rowItemAt(final int position) {
        return "REFUSED SQL compilation error: error line 1 at position " + position + " | " + ROW_ITEM;
    }

    @Test
    public void aPredicateThatIsASubqueryOfOneRowItem() {
        createTable();
        assertCells(new String[][] {
            {"SELECT a FROM ri910 WHERE (SELECT (1, 2))",
                "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] for predicate "
                + "[(SELECT ROW(1, 2) AS \"(1, 2)\" FROM (VALUES (null)) DUAL)]"},
            {"SELECT a FROM ri910 WHERE (SELECT (a, 2))",
                "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(38,0), NUMBER(1,0))] for predicate "
                + "[(SELECT ROW(CORRELATION(RI910.A), 2) AS \"(A, 2)\" FROM (VALUES (null)) DUAL)]"},
            {"SELECT a FROM ri910 WHERE (SELECT (1, 2) FROM ri910 LIMIT 1)",
                "REFUSED SQL compilation error: | Invalid data type [ROW(NUMBER(1,0), NUMBER(1,0))] for predicate "
                + "[(SELECT ROW(1, 2) AS \"(1, 2)\" FROM RI910 AS RI910 LIMIT 1 OFFSET 0)]"},
        });
    }

    @Test
    public void aViewKeepsANamedRowItemAndRefusesItWhereRead() {
        createTable();
        assertCells(new String[][] {
            {"CREATE OR REPLACE VIEW vr910 AS SELECT (1, 2) AS r", "View VR910 successfully created."},
            {"CREATE OR REPLACE VIEW vr910b AS SELECT a, (a, b) AS r FROM ri910",
                "View VR910B successfully created."},
            {"CREATE OR REPLACE VIEW vr910c AS SELECT a FROM ri910 WHERE EXISTS (SELECT (a, b) FROM ri910)",
                "View VR910C successfully created."},
            {"SELECT * FROM vr910", rowItemAt(49)},
            {"SELECT r FROM vr910", rowItemAt(49)},
            {"SELECT a FROM vr910b", rowItemAt(53)},
            {"SELECT COUNT(*) FROM vr910b", rowItemAt(53)},
            {"DESCRIBE VIEW vr910b ->> SELECT \"name\", \"type\" FROM $1", "A|NUMBER(38,0) ;; R|ROW(ANY)"},
            {"SELECT a FROM vr910c ORDER BY a", rowItemAt(74)},
        });
    }
}
