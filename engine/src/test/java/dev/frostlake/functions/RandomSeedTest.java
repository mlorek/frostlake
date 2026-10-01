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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * RANDOM's seed, live-verified:
 *
 * <ul>
 *   <li>a NULL seed, SQL or JSON, is refused when a row's value is read, where Frostlake drew a value;</li>
 *   <li>a null test over RANDOM, or over what cannot turn it into a NULL, is settled without reading it;</li>
 *   <li>a FROM-less item is computed only for a row that is read, so a row a WHERE or a LIMIT removes never
 *       refuses;</li>
 *   <li>and a seed converts as a cast to FIXED does: rounded half away from zero, a VARIANT through its member
 *       with a boolean as 1 or 0, anything else failing the variant's cast, and an ARRAY or OBJECT refused by
 *       the argument types.</li>
 * </ul>
 */
public class RandomSeedTest extends BaseDatabaseTest {

    private static final String NULL_SEED = "Invalid parameter value: NULL. Reason: seed must not be NULL";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE t (a INT)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    /** The one row's cells, as text. */
    private List<String> row(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> cells = new ArrayList<>();
        for (int i = 0; i < rs.getColumns().size(); i++) {
            cells.add(String.valueOf(rs.getRows().get(0).getValue(i)).toLowerCase());
        }
        return cells;
    }

    private int rowCount(final String sql) {
        return engine.executeQuery(sql).getRows().size();
    }

    @Test
    public void aNullSeedIsRefusedWhenItsValueIsRead() {
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(NULL)"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(NULL) = RANDOM(NULL)"));
        assertEquals(NULL_SEED, refusal("SELECT 1 WHERE RANDOM(NULL) > 0"));
        assertEquals(NULL_SEED, refusal("SELECT COALESCE(RANDOM(NULL), 1)"));
        assertEquals(NULL_SEED, refusal("SELECT ZEROIFNULL(RANDOM(NULL))"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(TRY_TO_NUMBER('x'))"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(NULL) FROM VALUES (1), (2)"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(NULL) IS NOT NULL, RANDOM(NULL)"));
        assertEquals(NULL_SEED, refusal("SELECT COUNT(RANDOM(NULL))"));
        assertEquals(NULL_SEED, refusal("SELECT UNIFORM(1, 10, RANDOM(NULL))"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(PARSE_JSON('null')) IS NOT NULL"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(PARSE_JSON('{}'):a) IS NOT NULL"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(PARSE_JSON('null')::NUMBER) IS NOT NULL"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(NULL)::VARCHAR IS NOT NULL"));
        assertEquals(NULL_SEED, refusal("SELECT NULLIF(RANDOM(NULL), 1) IS NOT NULL"));
        assertEquals(NULL_SEED, refusal("SELECT HASH(RANDOM(NULL)) IS NOT NULL"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(NULL) IN (1, 2)"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(NULL) IS NOT NULL AND RANDOM(NULL) > 0"));
        assertEquals(NULL_SEED, refusal("SELECT RANDOM(NULL) AS r WHERE r IS NOT NULL"));
        assertEquals(NULL_SEED, refusal("SELECT 1 AS k, RANDOM(NULL) AS r WHERE k = 1"));
    }

    @Test
    public void aNullTestOverRandomIsSettledWithoutReadingIt() {
        assertEquals(List.of("true", "false", "true", "true", "true"), row("""
            SELECT RANDOM(NULL) IS NOT NULL, RANDOM(NULL) IS NULL, RANDOM(NULL) IS DISTINCT FROM NULL,
                RANDOM(TRY_TO_NUMBER('x')) IS NOT NULL, RANDOM(NULLIF(1, 1)) IS NOT NULL"""));
        assertEquals(List.of("true", "false", "true", "true", "false", "true", "true"), row("""
            SELECT RANDOM(NULL) + 1 IS NOT NULL, (RANDOM(NULL) + 1) IS NULL, COALESCE(RANDOM(NULL), 1) IS NOT NULL,
                ABS(RANDOM(NULL)) IS NOT NULL, RANDOM(NULL) IS NOT DISTINCT FROM NULL,
                (RANDOM(NULL) = RANDOM(NULL)) IS NOT NULL, (RANDOM(NULL) > 0) IS NOT NULL"""));
        assertEquals(List.of("2", "false", "1", "false", "true", "0", "y", "true"), row("""
            SELECT CASE WHEN RANDOM(NULL) IS NULL THEN 1 ELSE 2 END, RANDOM(NULL) IS NULL OR FALSE,
                NVL2(RANDOM(NULL), 1, 2), EQUAL_NULL(RANDOM(NULL), NULL), ZEROIFNULL(RANDOM(NULL)) IS NOT NULL,
                IFF(RANDOM(NULL) IS NULL, RANDOM(NULL), 0), IFF(RANDOM(NULL) IS NOT NULL, 'y', 'n'),
                NOT (RANDOM(NULL) IS NULL)"""));
        assertEquals(2, rowCount("SELECT RANDOM(NULL) IS NOT NULL FROM VALUES (1), (2)"));
        assertEquals(List.of("1"), row("SELECT COUNT(*) FROM VALUES (1) WHERE RANDOM(NULL) IS NOT NULL"));
    }

    @Test
    public void aRowNobodyReadsNeverReadsItsItems() {
        assertEquals(0, rowCount("SELECT RANDOM(NULL) WHERE 1 = 0"));
        assertEquals(0, rowCount("SELECT RANDOM(NULL) LIMIT 0"));
        assertEquals(0, rowCount("SELECT RANDOM(NULL), 1 LIMIT 0"));
        assertEquals(0, rowCount("SELECT RANDOM(NULL) AS r, 1 AS k WHERE k = 2"));
        assertEquals(0, rowCount("SELECT RANDOM(NULL) FROM (SELECT 1) WHERE FALSE"));
        assertEquals(0, rowCount("SELECT 1/0 WHERE 1 = 0"));
        assertEquals(0, rowCount("SELECT 'a'::INT WHERE 1 = 0"));
        assertEquals(0, rowCount("SELECT DISTINCT 1/0 WHERE 1 = 0"));
        assertEquals(0, rowCount("SELECT 1 LIMIT 1 OFFSET 1"));
        assertEquals(0, rowCount("SELECT 1 FETCH FIRST 0 ROWS ONLY"));
        assertEquals(List.of("1"), row("SELECT COUNT(*) FROM (SELECT RANDOM(NULL) r)"));
        assertEquals(List.of("0"), row("SELECT COUNT(*) FROM (SELECT 1/0 x WHERE 1 = 0)"));
        assertEquals(List.of("null"), row("SELECT (SELECT 1/0 WHERE 1 = 0)"));
        assertEquals(1, rowCount("SELECT 1/0 WHERE 1 = 0 UNION ALL SELECT 2"));
        engine.execute("INSERT INTO t SELECT 1/0 WHERE 1 = 0");
        assertEquals(List.of("0"), row("SELECT COUNT(*) FROM t"));
        assertEquals("Division by zero", refusal("SELECT 1/0 AS x WHERE x > 0"));
        assertEquals("Division by zero", refusal("SELECT 1/0 AS x, x + 1"));
        assertEquals("Numeric value 'a' is not recognized", refusal("SELECT 'a'::INT, 1/0"));
    }

    @Test
    public void aSeedConvertsAsACastToFixed() {
        assertEquals(List.of("true", "true", "true", "true", "true", "true", "true"), row("""
            SELECT RANDOM(1.5) = RANDOM(2), RANDOM(2.5) = RANDOM(3), RANDOM(-1.5) = RANDOM(-2), RANDOM(1.4) = RANDOM(1),
                RANDOM(0.5) = RANDOM(1), RANDOM('1.5') = RANDOM(2), RANDOM('-0.5') = RANDOM(-1)"""));
        assertEquals(List.of("true", "true", "true", "true", "true"), row("""
            SELECT RANDOM(1.5::FLOAT) = RANDOM(2), RANDOM(-2.5::FLOAT) = RANDOM(-3), RANDOM('1e3') = RANDOM(1000),
                RANDOM(' 7 ') = RANDOM(7), RANDOM('7') = RANDOM(7)"""));
        assertEquals(List.of("true", "true", "true", "true", "true", "true", "true", "true"), row("""
            SELECT RANDOM(PARSE_JSON('"7"')) = RANDOM(7), RANDOM(PARSE_JSON('1.5')) = RANDOM(2),
                RANDOM(PARSE_JSON('-0.5')) = RANDOM(-1), RANDOM(PARSE_JSON('true')) = RANDOM(1),
                RANDOM(PARSE_JSON('false')) = RANDOM(0), RANDOM(PARSE_JSON('"1.5"')) = RANDOM(2),
                RANDOM(TO_VARIANT(TRUE)) = RANDOM(1), RANDOM(PARSE_JSON('1e3')) = RANDOM(1000)"""));
        assertEquals("Failed to cast variant value \"x\" to FIXED", refusal("SELECT RANDOM(PARSE_JSON('\"x\"'))"));
        assertEquals("Failed to cast variant value \"x\" to FIXED",
            refusal("SELECT RANDOM(PARSE_JSON('\"x\"')) IS NOT NULL"));
        assertEquals("Failed to cast variant value \"\" to FIXED", refusal("SELECT RANDOM(PARSE_JSON('\"\"'))"));
        assertEquals("Failed to cast variant value [1] to FIXED", refusal("SELECT RANDOM(PARSE_JSON('[1]'))"));
        assertEquals("Failed to cast variant value {\"a\":1} to FIXED", refusal("SELECT RANDOM(PARSE_JSON('{\"a\":1}'))"));
        assertEquals("Numeric value 'x' is not recognized", refusal("SELECT RANDOM('x')"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RANDOM': (ARRAY)",
            refusal("SELECT RANDOM(ARRAY_CONSTRUCT(1))"));
        assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function 'RANDOM': (OBJECT)",
            refusal("SELECT RANDOM(OBJECT_CONSTRUCT('a', 1))"));
    }
}
