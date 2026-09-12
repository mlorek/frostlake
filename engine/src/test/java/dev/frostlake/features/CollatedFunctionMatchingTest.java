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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The functions that MATCH text read the collation their arguments settled on, and so do the
 * comparisons against a subquery. A collated CONTAINS looks for what the collation calls equal, a
 * collated REPLACE rewrites every such substring, NULLIF and DECODE compare by it, GREATEST and LEAST
 * order by it, and {@code IN (SELECT …)}, {@code = ANY} and {@code <> ALL} decide membership by it.
 * TRIM is the exception live names: with a collation it takes only whitespace as its trim characters.
 * Live-verified.
 */
public class CollatedFunctionMatchingTest extends BaseDatabaseTest {

    /** A table with a plain and a case-insensitive text column, holding 'a' and 'A'. */
    private void createCaseInsensitiveSource() {
        engine.execute("CREATE OR REPLACE TABLE fn_coll (s VARCHAR, c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO fn_coll VALUES ('a','a'),('A','A')");
    }

    /** The query's rows as one string, columns joined by ',' and rows by '|'. */
    private String rows(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final StringBuilder text = new StringBuilder();
        for (int r = 0; r < rs.getRowCount(); r++) {
            if (r > 0) {
                text.append('|');
            }
            for (int c = 0; c < rs.getColumns().size(); c++) {
                if (c > 0) {
                    text.append(',');
                }
                final Object value = rs.getRows().get(r).getValue(c);
                text.append(value == null ? "NULL" : value.toString().toUpperCase());
            }
        }
        return text.toString();
    }

    /** NULLIF and DECODE compare their operands under the collation. */
    @Test
    public void nullifAndDecodeCompareUnderTheCollation() {
        createCaseInsensitiveSource();
        assertEquals("NULL|NULL", rows("SELECT NULLIF(c, 'A') FROM fn_coll ORDER BY s"));
        assertEquals("NULL", rows("SELECT NULLIF('a' COLLATE 'en-ci', 'A')"));
        assertEquals("1|1", rows("SELECT DECODE(c, 'A', 1, 0) FROM fn_coll ORDER BY s"));
    }

    /** GREATEST and LEAST order their arguments by the collation. */
    @Test
    public void extremeFunctionsOrderUnderTheCollation() {
        assertEquals("B", rows("SELECT GREATEST('a' COLLATE 'en-ci', 'B')"));
        assertEquals("A", rows("SELECT LEAST('a' COLLATE 'en-ci', 'B')"));
    }

    /** CONTAINS, STARTSWITH, ENDSWITH, POSITION and CHARINDEX find what the collation matches. */
    @Test
    public void substringSearchesMatchUnderTheCollation() {
        createCaseInsensitiveSource();
        assertEquals("TRUE|TRUE", rows("SELECT CONTAINS(c, 'A') FROM fn_coll ORDER BY s"));
        assertEquals("TRUE|TRUE", rows("SELECT STARTSWITH(c, 'A') FROM fn_coll ORDER BY s"));
        assertEquals("TRUE|TRUE", rows("SELECT ENDSWITH(c, 'A') FROM fn_coll ORDER BY s"));
        assertEquals("1|1", rows("SELECT POSITION('A', c) FROM fn_coll ORDER BY s"));
        assertEquals("1|1", rows("SELECT CHARINDEX('A', c) FROM fn_coll ORDER BY s"));
    }

    /** REPLACE, SPLIT and SPLIT_PART rewrite and split on what the collation matches. */
    @Test
    public void replaceAndSplitMatchUnderTheCollation() {
        createCaseInsensitiveSource();
        assertEquals("Z|Z", rows("SELECT REPLACE(c, 'A', 'z') FROM fn_coll ORDER BY s"));
        assertEquals("|", rows("SELECT SPLIT_PART(c || 'Ab', 'A', 2) FROM fn_coll ORDER BY s"));
        assertEquals("|", rows("SELECT SPLIT_PART(c, 'A', 1) FROM fn_coll ORDER BY s"));
        assertEquals("[\"\",\"\",\"B\"]|[\"\",\"\",\"B\"]",
            rows("SELECT TO_JSON(SPLIT(c || 'Ab', 'A')) FROM fn_coll ORDER BY s"));
    }

    /** A comparison against a subquery decides membership under the subject's collation. */
    @Test
    public void subqueryComparisonsUseTheSubjectsCollation() {
        createCaseInsensitiveSource();
        assertEquals("2", rows("SELECT COUNT(*) FROM fn_coll WHERE c IN (SELECT 'A')"));
        assertEquals("0", rows("SELECT COUNT(*) FROM fn_coll WHERE c NOT IN (SELECT 'A')"));
        assertEquals("2", rows("SELECT COUNT(*) FROM fn_coll WHERE c = ANY (SELECT 'A')"));
        assertEquals("0", rows("SELECT COUNT(*) FROM fn_coll WHERE c > ALL (SELECT 'A')"));
        assertEquals("TRUE", rows("SELECT 'a' COLLATE 'en-ci' = ANY (SELECT 'A')"));
        assertEquals("FALSE", rows("SELECT 'a' COLLATE 'en-ci' <> ALL (SELECT 'A')"));
    }

    /** With a collation, TRIM's characters must be whitespace — anything else is refused. */
    @Test
    public void trimWithACollationTakesOnlyWhitespace() {
        createCaseInsensitiveSource();
        assertEquals("A", rows("SELECT TRIM(c) FROM fn_coll LIMIT 1"));
        assertEquals("A", rows("SELECT TRIM(c, ' ') FROM fn_coll LIMIT 1"));
        for (final String function : new String[] {"TRIM", "LTRIM", "RTRIM"}) {
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT " + function + "(c, 'x') FROM fn_coll LIMIT 1");
                }
            });
            assertTrue(refused.getMessage().contains("Function " + function + " with collations requires"
                    + " whitespace-only constant as the trim characters."),
                "unexpected refusal for " + function + ": " + refused.getMessage());
        }
    }
}
