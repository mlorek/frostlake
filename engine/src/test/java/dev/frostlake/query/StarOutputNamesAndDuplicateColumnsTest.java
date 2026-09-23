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
 * Two things a {@code SELECT *} answers for by itself: the OUTPUT names its REPLACE and RENAME
 * produce, which an ORDER BY key resolves to exactly as it resolves a written alias, and the
 * columns themselves, which the star projects by POSITION — a relation may hold two columns of one
 * name, and the name alone would read the first one twice.
 */
public class StarOutputNamesAndDuplicateColumnsTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE fz (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO fz VALUES (5, TRUE), (7, FALSE)");
        engine.execute("CREATE OR REPLACE TABLE gz (id INT, c INT)");
        engine.execute("INSERT INTO gz VALUES (5, 40), (7, 30)");
    }

    /** Every row of a result, columns joined by '|' and rows by ';'. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        while (rs.next()) {
            if (text.length() > 0) {
                text.append(';');
            }
            for (int c = 0; c < rs.getColumnCount(); c++) {
                text.append(c == 0 ? "" : "|").append(String.valueOf(rs.getValue(c)));
            }
        }
        return text.toString();
    }

    /** ORDER BY a REPLACE'd column sorts by the REPLACED value, as it would by a written alias. */
    @Test
    public void orderBySortsByTheReplacedValue() {
        assertEquals("-7|false;-5|true",
            rows("SELECT * REPLACE (id * -1 AS id) FROM fz ORDER BY id"));
        assertEquals("-5|true;-7|false",
            rows("SELECT * REPLACE (id * -1 AS id) FROM fz ORDER BY id DESC"));
    }

    /** A qualified star's REPLACE is read the same way. */
    @Test
    public void aQualifiedStarReplacementSortsTheSameWay() {
        assertEquals("-7|false;-5|true",
            rows("SELECT fz.* REPLACE (id * -1 AS id) FROM fz ORDER BY id"));
    }

    /** A QUALIFIED key names the source column, not the output one. */
    @Test
    public void aQualifiedKeyNamesTheSourceColumn() {
        assertEquals("-5|true;-7|false",
            rows("SELECT * REPLACE (id * -1 AS id) FROM fz ORDER BY fz.id"));
    }

    /** ORDER BY a RENAME'd column sorts by the column it renamed. */
    @Test
    public void orderByAcceptsARenamedColumn() {
        assertEquals("5|true;7|false", rows("SELECT * RENAME (id AS k) FROM fz ORDER BY k"));
        assertEquals("5|true;7|false", rows("SELECT * RENAME (id AS k) FROM fz ORDER BY id"));
    }

    /** REPLACE and RENAME together: the key names the renamed output and sorts by the replacement. */
    @Test
    public void aReplacedColumnKeepsItsExpressionThroughARename() {
        assertEquals("-7|false;-5|true",
            rows("SELECT * REPLACE (id * -1 AS id) RENAME (id AS k) FROM fz ORDER BY k"));
        assertEquals("-5|true;-7|false",
            rows("SELECT * REPLACE (id * -1 AS id) RENAME (id AS k) FROM fz ORDER BY id"));
    }

    /** The output name wins even where the source column it shadows is ambiguous across the join. */
    @Test
    public void theOutputNameOutranksAnAmbiguousSourceColumn() {
        assertEquals("-7|false;-5|true",
            rows("SELECT fz.* REPLACE (fz.id * -1 AS id) FROM fz JOIN gz ON fz.id = gz.id ORDER BY id"));
    }

    /** A DISTINCT over the same projection sorts by the replacement too. */
    @Test
    public void distinctSortsByTheReplacedValue() {
        assertEquals("-7|false;-5|true",
            rows("SELECT DISTINCT * REPLACE (id * -1 AS id) FROM fz ORDER BY id"));
    }

    /** A star over a relation holding two columns of one name projects both, each its own value. */
    @Test
    public void aStarProjectsTwoColumnsOfOneNameByPosition() {
        assertEquals("1|2", rows("SELECT t.* FROM (SELECT 1 a, 2 a) t"));
        assertEquals("1|2|3", rows("SELECT t.* FROM (SELECT 1 a, 2 a, 3 a) t"));
        assertEquals("1|x", rows("SELECT t.* FROM (SELECT 1 a, 'x' a) t"));
    }

    /** The same through a CTE, through a nested star, and under a WHERE. */
    @Test
    public void theSameHoldsThroughEveryRelationShape() {
        assertEquals("1|2", rows("WITH w AS (SELECT 1 a, 2 a) SELECT w.* FROM w"));
        assertEquals("1|2", rows("SELECT u.* FROM (SELECT t.* FROM (SELECT 1 a, 2 a) t) u"));
        assertEquals("1|2", rows("SELECT t.* FROM (SELECT 1 a, 2 a) t WHERE 1 = 1"));
        assertEquals("1|2;3|4",
            rows("SELECT t.* FROM (SELECT 1 a, 2 a UNION ALL SELECT 3, 4) t ORDER BY 1"));
    }

    /** A star's RENAME name is in scope for the items AFTER it, as a written alias is. */
    @Test
    public void aRenamedStarColumnIsReadableByLaterItems() {
        assertEquals("5|true|5;7|false|7", rows("SELECT * RENAME (id AS k), k FROM fz ORDER BY 1"));
        assertEquals("5|true|6;7|false|8", rows("SELECT * RENAME (id AS k), k + 1 AS j FROM fz ORDER BY 1"));
        assertEquals("5|true|5;7|false|7", rows("SELECT * RENAME id AS k, k FROM fz ORDER BY 1"));
    }

    /** The same with no FROM at all, where the renamed column is the synthesised one. */
    @Test
    public void aRenamedStarColumnIsReadableWithNoFrom() {
        assertEquals("null|null", rows("SELECT * RENAME (column1 AS z), z"));
    }

    /** A star beside a window function reads the duplicated columns by position as well. */
    @Test
    public void aStarBesideAWindowFunctionAgrees() {
        assertEquals("1|2|1",
            rows("SELECT t.*, ROW_NUMBER() OVER (ORDER BY 1) AS rn FROM (SELECT 1 a, 2 a) t"));
    }
}
