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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Deep VARIANT extraction with functions nested inside functions: dotted and bracketed paths into a
 * three-level document (objects holding arrays of objects holding arrays), GET/GET_PATH composition,
 * scalar functions layered over extractions (LOWER/SPLIT_PART/INITCAP/IFF/COALESCE), array functions
 * stacked (ARRAY_TO_STRING over ARRAY_CAT over ARRAY_SLICE), double LATERAL FLATTEN into nested
 * arrays, and extractions used as join keys, filters, and aggregation inputs.
 *
 * <p>Documents differ on purpose: one line item has no {@code attrs} (missing paths), one customer
 * tier is JSON null (distinct from missing), one flags array is empty. Every projection casts to a
 * SQL type so embedded and live agree on value shape, and every query orders fully.
 */
public class ComplexJsonExtractionTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(ComplexJsonExtractionTest.class);

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE docs (id INTEGER, v VARIANT)");
        engine.execute("INSERT INTO docs SELECT 1, PARSE_JSON('"
            + "{\"order\": {\"id\": 1001,"
            + " \"customer\": {\"name\": \"Alice\", \"tier\": \"gold\","
            + "   \"contact\": {\"email\": \"ALICE@EXAMPLE.COM\", \"phones\": [\"555-0100\", \"555-0199\"]}},"
            + " \"lines\": ["
            + "   {\"sku\": \"A-1\", \"qty\": 2, \"price\": 25, \"attrs\": {\"color\": \"red\", \"dims\": [10, 20, 30]}},"
            + "   {\"sku\": \"B-2\", \"qty\": 1, \"price\": 99, \"attrs\": {\"color\": \"blue\", \"dims\": [5, 5, 5]}},"
            + "   {\"sku\": \"A-9\", \"qty\": 3, \"price\": 10}],"
            + " \"flags\": [\"rush\", \"gift\"],"
            + " \"meta\": {\"src\": \"web\", \"priority\": 1}}}')");
        engine.execute("INSERT INTO docs SELECT 2, PARSE_JSON('"
            + "{\"order\": {\"id\": 1002,"
            + " \"customer\": {\"name\": \"Bob\", \"tier\": null,"
            + "   \"contact\": {\"email\": \"BOB@EX.IO\", \"phones\": []}},"
            + " \"lines\": [{\"sku\": \"C-3\", \"qty\": 5, \"price\": 4}],"
            + " \"flags\": [\"rush\"],"
            + " \"meta\": {\"src\": \"api\", \"priority\": 3}}}')");
        engine.execute("INSERT INTO docs SELECT 3, PARSE_JSON('"
            + "{\"order\": {\"id\": 1003,"
            + " \"customer\": {\"name\": \"Cara\", \"tier\": \"silver\","
            + "   \"contact\": {\"email\": \"CARA@CO.NET\", \"phones\": [\"555-0400\"]}},"
            + " \"lines\": ["
            + "   {\"sku\": \"D-4\", \"qty\": 2, \"price\": 50, \"attrs\": {\"color\": \"green\", \"dims\": [1, 2]}},"
            + "   {\"sku\": \"D-5\", \"qty\": 4, \"price\": 25}],"
            + " \"flags\": [],"
            + " \"meta\": {\"src\": \"web\", \"priority\": 2}}}')");
    }

    /** Cell-by-cell row assertion: numbers compare as long, strings as text, null as SQL NULL. */
    private void assertRow(final ResultSet rs, final int row, final Object... expected) {
        for (int i = 0; i < expected.length; i++) {
            final Object actual = rs.getRows().get(row).getValue(i);
            final Object want = expected[i];
            if (want == null) {
                assertNull(actual, "row " + row + " col " + i);
            } else if (want instanceof Number) {
                assertEquals(((Number) want).longValue(), ((Number) actual).longValue(),
                    "row " + row + " col " + i);
            } else {
                assertEquals(want, String.valueOf(actual), "row " + row + " col " + i);
            }
        }
    }

    @Test
    public void dottedAndBracketedPathsReachEveryDepth() {
        logger.info("dot paths, bracket paths, array indexes and their mixture reach level-4 values");
        final ResultSet rs = engine.executeQuery("""
            SELECT v:order.customer.name::VARCHAR,
                   v['order']['customer']['tier']::VARCHAR,
                   v:order.lines[0].sku::VARCHAR,
                   v:order.lines[0].attrs.dims[2]::INTEGER,
                   v:order['lines'][1]:attrs.color::VARCHAR,
                   v:order.customer.contact.phones[1]::VARCHAR
            FROM docs WHERE id = 1
            """);
        assertEquals(1, rs.getRowCount());
        assertRow(rs, 0, "Alice", "gold", "A-1", 30, "blue", "555-0199");
    }

    @Test
    public void scalarFunctionsNestThreeDeepOverExtractions() {
        logger.info("INITCAP(SPLIT_PART(LOWER(extracted email))) and friends");
        final ResultSet rs = engine.executeQuery("""
            SELECT id,
                   LOWER(v:order.customer.contact.email::VARCHAR),
                   INITCAP(SPLIT_PART(LOWER(v:order.customer.contact.email::VARCHAR), '@', 1)),
                   UPPER(SUBSTR(SPLIT_PART(v:order.customer.contact.email::VARCHAR, '@', 2), 1, 2)),
                   LENGTH(CONCAT(v:order.customer.name::VARCHAR,
                                 TO_VARCHAR(ARRAY_SIZE(v:order.customer.contact.phones))))
            FROM docs ORDER BY id
            """);
        assertEquals(3, rs.getRowCount());
        assertRow(rs, 0, 1, "alice@example.com", "Alice", "EX", 6); // Alice + '2'
        assertRow(rs, 1, 2, "bob@ex.io", "Bob", "EX", 4);           // Bob + '0'
        assertRow(rs, 2, 3, "cara@co.net", "Cara", "CO", 5);        // Cara + '1'
    }

    @Test
    public void getAndGetPathComposeWithArrayAndObjectFunctions() {
        logger.info("GET(GET_PATH(...)) chains and OBJECT_KEYS/ARRAY_TO_STRING over extracted objects");
        final ResultSet rs = engine.executeQuery("""
            SELECT GET_PATH(v, 'order.lines[1].attrs.color')::VARCHAR,
                   GET(GET_PATH(v, 'order.customer'), 'name')::VARCHAR,
                   GET(GET(GET(v, 'order'), 'meta'), 'src')::VARCHAR,
                   ARRAY_SIZE(GET_PATH(v, 'order.lines')),
                   ARRAY_TO_STRING(OBJECT_KEYS(v:order.meta), ',')
            FROM docs WHERE id = 1
            """);
        assertEquals(1, rs.getRowCount());
        // OBJECT_KEYS lists keys in the object's stored (sorted) order: meta, priority < src
        assertRow(rs, 0, "blue", "Alice", "web", 3, "priority,src");
    }

    @Test
    public void missingPathsJsonNullAndCoalesceFallbacks() {
        logger.info("missing attrs vs JSON-null tier vs present values, folded through COALESCE/IFF");
        final ResultSet rs = engine.executeQuery("""
            SELECT id,
                   v:order.lines[0].attrs.color::VARCHAR,
                   COALESCE(v:order.customer.tier::VARCHAR, 'untiered'),
                   COALESCE(v:order.nope.deeper::VARCHAR, 'absent'),
                   IFF(IS_ARRAY(v:order.flags), ARRAY_SIZE(v:order.flags), -1)
            FROM docs ORDER BY id
            """);
        assertEquals(3, rs.getRowCount());
        assertRow(rs, 0, 1, "red", "gold", "absent", 2);
        assertRow(rs, 1, 2, null, "untiered", "absent", 1); // no attrs; JSON null tier
        assertRow(rs, 2, 3, "green", "silver", "absent", 0); // empty flags still an array
    }

    @Test
    public void arrayFunctionsStackedThreeDeep() {
        logger.info("ARRAY_TO_STRING(ARRAY_CAT(ARRAY_SLICE(flags), TO_ARRAY(UPPER(src))))");
        final ResultSet rs = engine.executeQuery("""
            SELECT id,
                   ARRAY_TO_STRING(
                     ARRAY_CAT(ARRAY_SLICE(v:order.flags, 0, 1),
                               TO_ARRAY(UPPER(v:order.meta.src::VARCHAR))), '|'),
                   ARRAY_CONTAINS('rush'::VARIANT, v:order.flags),
                   ARRAY_SIZE(ARRAY_CAT(v:order.flags, v:order.customer.contact.phones))
            FROM docs ORDER BY id
            """);
        assertEquals(3, rs.getRowCount());
        assertRow(rs, 0, 1, "rush|WEB", "true", 4);  // [rush,gift]+[2 phones]
        assertRow(rs, 1, 2, "rush|API", "true", 1);  // [rush]+[]
        assertRow(rs, 2, 3, "WEB", "false", 1);      // slice of [] is empty; []+[1 phone]
    }

    @Test
    public void objectConstructInsertAndReExtract() {
        logger.info("OBJECT_INSERT over OBJECT_CONSTRUCT of extracted values, read back through paths");
        final ResultSet rs = engine.executeQuery("""
            SELECT built:oid::INTEGER, built:who::VARCHAR, built:extra.n::INTEGER
            FROM (
              SELECT OBJECT_INSERT(
                       OBJECT_CONSTRUCT('oid', v:order.id,
                                        'who', LOWER(v:order.customer.name::VARCHAR)),
                       'extra', PARSE_JSON('{"n": 7}')) AS built
              FROM docs WHERE id = 2
            )
            """);
        assertEquals(1, rs.getRowCount());
        assertRow(rs, 0, 1002, "bob", 7);
    }

    @Test
    public void parseJsonOfConcatenatedTextThenExtract() {
        logger.info("PARSE_JSON over string concatenation of extracted pieces, extracted again");
        final ResultSet rs = engine.executeQuery("""
            SELECT PARSE_JSON('{"k":' || TO_VARCHAR(v:order.id) || ',"t":"' ||
                              v:order.customer.tier::VARCHAR || '"}'):k::INTEGER,
                   PARSE_JSON(TO_JSON(v:order.customer)):name::VARCHAR
            FROM docs WHERE id = 1
            """);
        assertEquals(1, rs.getRowCount());
        assertRow(rs, 0, 1001, "Alice");
    }

    @Test
    public void extractionsAsJoinKeysAndFilters() {
        engine.execute("CREATE TABLE tiers (tier VARCHAR, discount INTEGER)");
        engine.execute("INSERT INTO tiers VALUES ('gold', 20), ('silver', 10)");
        logger.info("JSON extraction inside the join condition and WHERE, nested LOWER on both sides");
        final ResultSet rs = engine.executeQuery("""
            SELECT d.id, v:order.customer.name::VARCHAR, t.discount
            FROM docs d
            JOIN tiers t ON LOWER(t.tier) = LOWER(d.v:order.customer.tier::VARCHAR)
            WHERE v:order.meta.src::VARCHAR = 'web'
            ORDER BY d.id
            """);
        assertEquals(2, rs.getRowCount());
        assertRow(rs, 0, 1, "Alice", 20);
        assertRow(rs, 1, 3, "Cara", 10);
    }

    @Test
    public void flattenLinesComputeTotalsAndQualifyTopLine() {
        logger.info("FLATTEN lines, qty*price arithmetic on variant numbers, QUALIFY the top line per doc");
        final ResultSet rs = engine.executeQuery("""
            SELECT d.id, f.value:sku::VARCHAR,
                   f.value:qty::INTEGER * f.value:price::INTEGER AS line_total
            FROM docs d, LATERAL FLATTEN(input => d.v:order.lines) f
            QUALIFY ROW_NUMBER() OVER (PARTITION BY d.id
                                       ORDER BY f.value:qty::INTEGER * f.value:price::INTEGER DESC,
                                                f.value:sku::VARCHAR) = 1
            ORDER BY d.id
            """);
        assertEquals(3, rs.getRowCount());
        assertRow(rs, 0, 1, "B-2", 99);   // 50, 99, 30 -> B-2
        assertRow(rs, 1, 2, "C-3", 20);
        assertRow(rs, 2, 3, "D-4", 100);  // 100, 100 tie -> sku order D-4
    }

    @Test
    public void doubleFlattenIntoNestedDimsWithOuterKeepingDimless() {
        logger.info("FLATTEN lines then their dims (outer), aggregating leaf numbers per document");
        final ResultSet rs = engine.executeQuery("""
            SELECT d.id,
                   COUNT(*) AS leg_rows,
                   COALESCE(SUM(dim.value::INTEGER), 0) AS dim_sum
            FROM docs d,
                 LATERAL FLATTEN(input => d.v:order.lines) line,
                 LATERAL FLATTEN(input => line.value:attrs.dims, outer => TRUE) dim
            GROUP BY d.id
            ORDER BY d.id
            """);
        assertEquals(3, rs.getRowCount());
        assertRow(rs, 0, 1, 7, 75);  // dims 3+3, dimless A-9 row; 60+15
        assertRow(rs, 1, 2, 1, 0);   // single dimless line survives via outer
        assertRow(rs, 2, 3, 3, 3);   // [1,2] + dimless D-5; 1+2
    }

    @Test
    public void aggregatesOverExtractionsWithNestedFunctionsInside() {
        logger.info("SUM/MAX/COUNT over extracted numbers wrapped in scalar functions, grouped by extraction");
        final ResultSet rs = engine.executeQuery("""
            SELECT v:order.meta.src::VARCHAR AS src,
                   COUNT(*) AS docs,
                   SUM(v:order.meta.priority::INTEGER) AS prio_sum,
                   MAX(LENGTH(v:order.customer.name::VARCHAR)) AS longest_name
            FROM docs
            GROUP BY src
            HAVING SUM(v:order.meta.priority::INTEGER) >= 3
            ORDER BY src
            """);
        assertEquals(2, rs.getRowCount());
        assertRow(rs, 0, "api", 1, 3, 3);
        assertRow(rs, 1, "web", 2, 3, 5);
    }

    @Test
    public void arrayAggRebuildsJsonFromRelationalRows() {
        logger.info("ARRAY_AGG over flattened, function-wrapped values, serialized back to one string");
        final ResultSet rs = engine.executeQuery("""
            SELECT d.id,
                   ARRAY_TO_STRING(ARRAY_AGG(UPPER(f.value:sku::VARCHAR))
                                   WITHIN GROUP (ORDER BY f.value:sku::VARCHAR), ',')
            FROM docs d, LATERAL FLATTEN(input => d.v:order.lines) f
            GROUP BY d.id
            ORDER BY d.id
            """);
        assertEquals(3, rs.getRowCount());
        assertRow(rs, 0, 1, "A-1,A-9,B-2");
        assertRow(rs, 1, 2, "C-3");
        assertRow(rs, 2, 3, "D-4,D-5");
    }
}
