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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The VARIANT order: kind first (BOOLEAN < NUMBER < STRING < OBJECT < ARRAY < JSON null), then the
 * kind's own rule — numbers by value, strings by code point, arrays and objects member by member — and
 * the equality it implies (1 = 1.0, an object whatever its key order). Pinned through ORDER BY, MIN /
 * MAX, GREATEST / LEAST, the comparison operators, IN, DISTINCT, GROUP BY and the percentiles' key.
 * Every expectation is live-verified.
 */
public class VariantOrderingTest extends BaseDatabaseTest {

    @BeforeEach
    public void createRelations() {
        engine.execute("CREATE TABLE vo (id INTEGER, v VARIANT)");
        engine.execute("INSERT INTO vo SELECT 1, PARSE_JSON('10') UNION ALL SELECT 2, PARSE_JSON('9') UNION ALL SELECT 3, PARSE_JSON('8') "
            + "UNION ALL SELECT 4, PARSE_JSON('\"10\"') UNION ALL SELECT 5, PARSE_JSON('\"9\"') UNION ALL SELECT 6, PARSE_JSON('true') "
            + "UNION ALL SELECT 7, PARSE_JSON('false') UNION ALL SELECT 8, PARSE_JSON('null') UNION ALL SELECT 9, NULL "
            + "UNION ALL SELECT 10, PARSE_JSON('[1]') UNION ALL SELECT 11, PARSE_JSON('{\"a\":1}') UNION ALL SELECT 12, PARSE_JSON('-1.5') "
            + "UNION ALL SELECT 13, PARSE_JSON('1e2') UNION ALL SELECT 14, PARSE_JSON('\"B\"') UNION ALL SELECT 15, PARSE_JSON('\"a\"') "
            + "UNION ALL SELECT 16, PARSE_JSON('\"A\"') UNION ALL SELECT 17, PARSE_JSON('[0,5]') UNION ALL SELECT 18, PARSE_JSON('[]') "
            + "UNION ALL SELECT 19, PARSE_JSON('{\"b\":0}') UNION ALL SELECT 20, PARSE_JSON('{}') UNION ALL SELECT 21, PARSE_JSON('[1,2]') "
            + "UNION ALL SELECT 22, PARSE_JSON('[\"a\"]') UNION ALL SELECT 23, PARSE_JSON('{\"a\":0}') "
            + "UNION ALL SELECT 24, PARSE_JSON('{\"a\":1,\"b\":2}')");
    }

    private Row row(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRowCount(), sql);
        return result.getRows().get(0);
    }

    private String text(final String sql) {
        return String.valueOf(row(sql).getValue(0));
    }

    private static String cell(final Row row, final int index) {
        return String.valueOf(row.getValue(index));
    }

    private static String bool(final Row row, final int index) {
        return String.valueOf(row.getValue(index)).toLowerCase();
    }

    /** The ids in the order the values sort, as one text. */
    private String ordered(final String orderBy, final String where) {
        return text("SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY " + orderBy + ") FROM vo" + (where == null ? "" : " WHERE " + where));
    }

    @Test
    public void kindsOrderThenEachKindByItsOwnRule() {
        assertEquals("7,6,12,3,2,1,13,4,5,16,14,15,20,19,23,11,24,18,17,10,21,22,8,9", ordered("v, id", null));
        assertEquals("9,8,22,21,10,17,18,24,11,23,19,20,15,14,16,5,4,13,1,2,3,12,6,7", ordered("v DESC, id", null));
        assertEquals("9,7,6,12,3,2,1,13,4,5,16,14,15,20,19,23,11,24,18,17,10,21,22,8", ordered("v ASC NULLS FIRST, id", null));
        assertEquals("12,3,2,1,13", ordered("v", "id IN (1, 2, 3, 12, 13)"));
        assertEquals("4,5,16,14,15", ordered("v", "id IN (4, 5, 14, 15, 16)"));
        assertEquals("18,17,10,21,22", ordered("v", "id IN (10, 17, 18, 21, 22)"));
        assertEquals("20,19,23,11,24", ordered("v", "id IN (11, 19, 20, 23, 24)"));
        assertEquals("6,3,8,4,7,2,1,5", text("SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY v) FROM (SELECT 1 id, PARSE_JSON('NaN') v "
            + "UNION ALL SELECT 2, PARSE_JSON('Infinity') UNION ALL SELECT 3, PARSE_JSON('-Infinity') UNION ALL SELECT 4, PARSE_JSON('1') "
            + "UNION ALL SELECT 5, PARSE_JSON('\"a\"') UNION ALL SELECT 6, PARSE_JSON('true') UNION ALL SELECT 7, PARSE_JSON('1e300') "
            + "UNION ALL SELECT 8, PARSE_JSON('-1e300'))"));
        assertEquals("4,3,6,5,2,1", text("SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY v) FROM (SELECT 1 id, PARSE_JSON('\"é\"') v "
            + "UNION ALL SELECT 2, PARSE_JSON('\"z\"') UNION ALL SELECT 3, PARSE_JSON('\"Z\"') UNION ALL SELECT 4, PARSE_JSON('\"\"') "
            + "UNION ALL SELECT 5, PARSE_JSON('\"ab\"') UNION ALL SELECT 6, PARSE_JSON('\"a\"'))"));
    }

    @Test
    public void objectsOrderByTheirLargerKeyFirstThenByValue() {
        assertEquals("5,4,2,8,1,3,6,7", text("SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY v) FROM (SELECT 1 id, PARSE_JSON('{\"a\":0}') v "
            + "UNION ALL SELECT 2, PARSE_JSON('{\"b\":1}') UNION ALL SELECT 3, PARSE_JSON('{\"a\":5}') UNION ALL SELECT 4, PARSE_JSON('{\"b\":0}') "
            + "UNION ALL SELECT 5, PARSE_JSON('{\"c\":0}') UNION ALL SELECT 6, PARSE_JSON('{\"a\":\"x\"}') UNION ALL SELECT 7, PARSE_JSON('{\"a\":[1]}') "
            + "UNION ALL SELECT 8, PARSE_JSON('{\"a\":true}'))"));
        assertEquals("7,5,6,2,4,1,8,3", text("SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY v) FROM (SELECT 1 id, PARSE_JSON('{\"a\":1,\"b\":2}') v "
            + "UNION ALL SELECT 2, PARSE_JSON('{\"a\":1,\"b\":1}') UNION ALL SELECT 3, PARSE_JSON('{\"a\":2,\"b\":0}') "
            + "UNION ALL SELECT 4, PARSE_JSON('{\"b\":1,\"a\":1}') UNION ALL SELECT 5, PARSE_JSON('{\"a\":0,\"c\":9}') "
            + "UNION ALL SELECT 6, PARSE_JSON('{\"a\":1}') UNION ALL SELECT 7, PARSE_JSON('{\"z\":0}') "
            + "UNION ALL SELECT 8, PARSE_JSON('{\"a\":1,\"b\":2,\"c\":3}'))"));
        assertEquals("6,4,5,2,1,3", text("SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY v) FROM (SELECT 1 id, PARSE_JSON('{\"a\":{\"x\":1}}') v "
            + "UNION ALL SELECT 2, PARSE_JSON('{\"a\":{\"x\":0}}') UNION ALL SELECT 3, PARSE_JSON('{\"a\":null}') "
            + "UNION ALL SELECT 4, PARSE_JSON('{\"a\":0}') UNION ALL SELECT 5, PARSE_JSON('{\"a\":\"0\"}') "
            + "UNION ALL SELECT 6, PARSE_JSON('{\"a\":false}'))"));
        assertEquals("7,3,6,8,2,1,4,5", text("SELECT LISTAGG(id, ',') WITHIN GROUP (ORDER BY v) FROM (SELECT 1 id, PARSE_JSON('{\"a\":0,\"b\":0}') v "
            + "UNION ALL SELECT 2, PARSE_JSON('{\"a\":0,\"c\":0}') UNION ALL SELECT 3, PARSE_JSON('{\"b\":0,\"c\":0}') "
            + "UNION ALL SELECT 4, PARSE_JSON('{\"a\":0,\"b\":1}') UNION ALL SELECT 5, PARSE_JSON('{\"a\":1,\"b\":0}') "
            + "UNION ALL SELECT 6, PARSE_JSON('{\"aa\":0}') UNION ALL SELECT 7, PARSE_JSON('{\"b\":0}') UNION ALL SELECT 8, PARSE_JSON('{\"a\":0}'))"));
    }

    @Test
    public void extremesFollowTheOrder() {
        final Row all = row("SELECT MIN(v), MAX(v) FROM vo");
        assertEquals("false", bool(all, 0));
        assertEquals("null", cell(all, 1));
        final Row arrays = row("SELECT MIN(v), MAX(v) FROM vo WHERE id IN (10, 17, 18, 21, 22)");
        assertEquals("[]", cell(arrays, 0));
        assertEquals("[\"a\"]", cell(arrays, 1));
        final Row objects = row("SELECT MIN(v), MAX(v) FROM vo WHERE id IN (11, 19, 20, 23, 24)");
        assertEquals("{}", cell(objects, 0));
        assertEquals("{\"a\":1,\"b\":2}", cell(objects, 1));
        final Row picked = row("SELECT TO_VARCHAR(GREATEST(PARSE_JSON('7'), PARSE_JSON('\"x\"'))), TO_VARCHAR(LEAST(PARSE_JSON('7'), PARSE_JSON('\"x\"')))");
        assertEquals("x", cell(picked, 0));
        assertEquals("7", cell(picked, 1));
        assertEquals("9.000", text("SELECT MEDIAN(v) FROM vo WHERE id IN (1, 2, 3)"));
    }

    @Test
    public void comparisonsAndEqualityFollowTheOrder() {
        final Row across = row("SELECT PARSE_JSON('7') < PARSE_JSON('\"x\"'), PARSE_JSON('true') < PARSE_JSON('0'), "
            + "PARSE_JSON('\"x\"') < PARSE_JSON('{\"a\":1}'), PARSE_JSON('{\"a\":1}') < PARSE_JSON('[1]'), PARSE_JSON('[1]') < PARSE_JSON('null'), "
            + "PARSE_JSON('1') = PARSE_JSON('1.0'), PARSE_JSON('1') = PARSE_JSON('\"1\"'), PARSE_JSON('null') = PARSE_JSON('null'), "
            + "PARSE_JSON('null') IS NULL");
        final String[] expectedAcross = {"true", "true", "true", "true", "true", "true", "false", "true", "false"};
        for (int i = 0; i < expectedAcross.length; i++) {
            assertEquals(expectedAcross[i], bool(across, i), "column " + i);
        }
        final Row within = row("SELECT PARSE_JSON('[1]') < PARSE_JSON('[0,5]'), PARSE_JSON('[]') < PARSE_JSON('[1]'), "
            + "PARSE_JSON('{\"a\":1}') < PARSE_JSON('{\"b\":0}'), PARSE_JSON('{}') < PARSE_JSON('{\"a\":1}'), PARSE_JSON('\"a\"') < PARSE_JSON('\"B\"'), "
            + "PARSE_JSON('\"A\"') < PARSE_JSON('\"a\"'), PARSE_JSON('10') < PARSE_JSON('9'), PARSE_JSON('-1.5') < PARSE_JSON('1e2'), "
            + "PARSE_JSON('\"10\"') < PARSE_JSON('\"9\"'), PARSE_JSON('\"b\"') < PARSE_JSON('\"B\"'), PARSE_JSON('\"Z\"') < PARSE_JSON('\"a\"')");
        final String[] expectedWithin = {"false", "true", "false", "true", "false", "true", "false", "true", "true", "false", "true"};
        for (int i = 0; i < expectedWithin.length; i++) {
            assertEquals(expectedWithin[i], bool(within, i), "column " + i);
        }
        final Row nested = row("SELECT PARSE_JSON('{\"a\":1,\"b\":2}') = PARSE_JSON('{\"b\":2,\"a\":1}'), PARSE_JSON('{\"a\":1}') = PARSE_JSON('{\"a\":1.0}'), "
            + "PARSE_JSON('[1]') = PARSE_JSON('[1.0]'), PARSE_JSON('[1,2]') < PARSE_JSON('[1,\"a\"]'), PARSE_JSON('[[1]]') < PARSE_JSON('[{\"a\":1}]'), "
            + "PARSE_JSON('[null]') < PARSE_JSON('[1]'), PARSE_JSON('[true]') < PARSE_JSON('[0]')");
        final String[] expectedNested = {"true", "true", "true", "true", "false", "false", "true"};
        for (int i = 0; i < expectedNested.length; i++) {
            assertEquals(expectedNested[i], bool(nested, i), "column " + i);
        }
        final List<Row> above = engine.executeQuery("SELECT id FROM vo WHERE v > PARSE_JSON('9') ORDER BY id").getRows();
        final StringBuilder ids = new StringBuilder();
        for (final Row r : above) {
            ids.append(ids.length() == 0 ? "" : ",").append(cell(r, 0));
        }
        assertEquals("1,4,5,8,10,11,13,14,15,16,17,18,19,20,21,22,23,24", ids.toString());
        assertEquals("8", text("SELECT id FROM vo WHERE v = PARSE_JSON('null') ORDER BY id"));
        final Row membership = row("SELECT PARSE_JSON('1') IN (PARSE_JSON('1.0'), PARSE_JSON('2')), PARSE_JSON('\"1\"') IN (PARSE_JSON('1'))");
        assertEquals("true", bool(membership, 0));
        assertEquals("false", bool(membership, 1));
    }

    @Test
    public void distinctAndGroupingSeeOneValueWhateverItsNotation() {
        final String notations = "(SELECT PARSE_JSON('1') v UNION ALL SELECT PARSE_JSON('1.0') UNION ALL SELECT PARSE_JSON('\"1\"') "
            + "UNION ALL SELECT PARSE_JSON('1e0') UNION ALL SELECT TO_VARIANT(1))";
        final Row counted = row("SELECT COUNT(DISTINCT v), COUNT(*) FROM " + notations);
        assertEquals("2", cell(counted, 0));
        assertEquals("5", cell(counted, 1));
        final List<Row> grouped = engine.executeQuery("SELECT v, COUNT(*) FROM " + notations + " GROUP BY v ORDER BY v").getRows();
        assertEquals(2, grouped.size());
        assertEquals("4", cell(grouped.get(0), 1));
        assertEquals("1", cell(grouped.get(1), 1));
        assertEquals("3", text("SELECT COUNT(DISTINCT v) FROM (SELECT PARSE_JSON('[1]') v UNION ALL SELECT PARSE_JSON('[1.0]') "
            + "UNION ALL SELECT PARSE_JSON('{\"a\":1}') UNION ALL SELECT PARSE_JSON('{\"a\":1.0}') UNION ALL SELECT PARSE_JSON('{\"a\":1,\"b\":2}') "
            + "UNION ALL SELECT PARSE_JSON('{\"b\":2,\"a\":1}'))"));
        // A JSON null and a SQL NULL stay two values: DISTINCT keeps both, COUNT counts neither.
        final List<Row> nulls = engine.executeQuery("SELECT v FROM vo WHERE id IN (8, 9) ORDER BY v NULLS FIRST").getRows();
        assertEquals(2, nulls.size());
        assertNull(nulls.get(0).getValue(0));
        assertEquals("null", cell(nulls.get(1), 0));
        assertEquals(2, engine.executeQuery("SELECT DISTINCT v FROM vo WHERE id IN (8, 9)").getRowCount());
        assertEquals("0", text("SELECT COUNT(DISTINCT v) FROM vo WHERE id IN (8, 9)"));
    }
}
