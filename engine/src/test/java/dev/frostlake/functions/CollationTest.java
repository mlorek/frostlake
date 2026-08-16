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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * COLLATION: the collation an expression carries, lower-cased — an explicit COLLATE, a collated column,
 * or what a concatenation, a conditional or a string function inherits — and NULL for none. Every cell
 * is live-verified.
 */
public class CollationTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    /** Every row's cells, a comma between cells and a bar between rows. */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (out.length() > 0) {
                out.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    out.append(", ");
                }
                out.append(row.getValue(i));
            }
        }
        return out.toString();
    }

    /** COLLATION reports an explicit collation lower-cased, and NULL for none, for the empty specification and for a NULL operand. */
    @Test
    public void collationReadsAnExplicitCollate() {
        assertEquals("en-ci",
            rows("SELECT COLLATION('x' COLLATE 'en-ci' || '')"));
        assertEquals("en-ci",
            rows("SELECT COLLATION('a' COLLATE 'en-ci')"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(COLLATE('a', 'en-ci'))"));
        assertEquals("en-ci",
            rows("SELECT COLLATION('a' COLLATE 'EN-CI')"));
        assertEquals("en_us-ci",
            rows("SELECT COLLATION('a' COLLATE 'en_us-CI')"));
        assertEquals("null",
            rows("SELECT COLLATION(1)"));
        assertEquals("null",
            rows("SELECT COLLATION(NULL)"));
        assertEquals("null",
            rows("SELECT COLLATION('a')"));
        assertEquals("null",
            rows("SELECT COLLATION('a' COLLATE '')"));
        assertEquals("utf8",
            rows("SELECT COLLATION('a' COLLATE 'utf8')"));
        assertEquals("utf8",
            rows("SELECT COLLATION('a' COLLATE 'UTF8')"));
        assertEquals(" en-ci",
            rows("SELECT COLLATION('a' COLLATE ' EN-CI')"));
        assertEquals("-",
            rows("SELECT COLLATION('a' COLLATE '-')"));
        assertEquals("null",
            rows("SELECT COLLATION(NULL COLLATE 'en-ci')"));
        assertEquals("de",
            rows("SELECT COLLATION('a' COLLATE 'de' || 'b' COLLATE '')"));
    }

    /** A collated column, and the functions that hand its collation on; a cast or REPLACE hands on none. */
    @Test
    public void collationReadsAColumnAndWhatInheritsIt() {
        engine.execute("CREATE OR REPLACE TABLE cp (s VARCHAR, c VARCHAR COLLATE 'en-ci', f VARCHAR COLLATE 'fr', n NUMBER)");
        engine.execute("INSERT INTO cp VALUES ('a','a','a',1), ('A','A','A',2)");
        engine.execute("CREATE OR REPLACE TABLE ce (s VARCHAR, c VARCHAR COLLATE 'en-ci', f VARCHAR COLLATE 'fr', n NUMBER)");
        assertEquals("en-ci",
            rows("SELECT COLLATION(LEFT(c, 1)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(RIGHT(c, 1)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(LPAD(c, 3)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(RPAD(c, 3)) FROM cp LIMIT 1"));
        assertEquals("null",
            rows("SELECT COLLATION(REPLACE(c, 'a', 'b')) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(CONCAT(c, s)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(CONCAT_WS(',', c, s)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(INITCAP(c)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(REVERSE(c)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(LTRIM(c)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(RTRIM(c)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(NVL(c, s)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(IFNULL(c, s)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(NVL2(c, c, s)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(NULLIF(c, s)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(GREATEST(c, s)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(LEAST(c, s)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(DECODE(c, 'a', c, s)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(CASE WHEN TRUE THEN c ELSE s END) FROM cp LIMIT 1"));
        assertEquals("null",
            rows("SELECT COLLATION(CASE c WHEN 'a' THEN s END) FROM cp LIMIT 1"));
        assertEquals("null",
            rows("SELECT COLLATION(TO_VARCHAR(c)) FROM cp LIMIT 1"));
        assertEquals("null",
            rows("SELECT COLLATION(c::STRING) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(SPLIT_PART(c, ',', 1)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(TRANSLATE(c, 'a', 'b')) FROM cp LIMIT 1"));
        assertEquals("null",
            rows("SELECT COLLATION(REPEAT(c, 2)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(MAX(c)) FROM cp"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(MIN(c)) FROM cp"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(ANY_VALUE(c)) FROM cp"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(LISTAGG(c)) FROM cp"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(SUBSTRING(c, 1)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(COALESCE(s, c)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(IFF(TRUE, s, c)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(LOWER(c)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(c || 'x') FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION('x' || c) FROM cp LIMIT 1"));
        assertEquals("null",
            rows("SELECT COLLATION(CAST(c AS VARCHAR(5))) FROM cp LIMIT 1"));
        assertEquals("null",
            rows("SELECT COLLATION(TRY_CAST(c AS VARCHAR)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(SUBSTR(c, 1, 1)) FROM cp LIMIT 1"));
        assertRefused("SELECT COLLATION(CONCAT(c, f)) FROM cp LIMIT 1",
            "SQL compilation error: Incompatible collations: 'en-ci' and 'fr'");
        assertRefused("SELECT COLLATION(IFF(TRUE, c, f)) FROM cp LIMIT 1",
            "SQL compilation error: Incompatible collations: 'fr' and 'en-ci'");
        assertRefused("SELECT COLLATION(COALESCE(c, f)) FROM cp LIMIT 1",
            "SQL compilation error: Incompatible collations: 'fr' and 'en-ci'");
        assertRefused("SELECT COLLATION(UPPER(c) || LOWER(f)) FROM cp LIMIT 1",
            "SQL compilation error: Incompatible collations: 'en-ci' and 'fr'");
        assertEquals("null",
            rows("SELECT COLLATION(c COLLATE '') FROM cp LIMIT 1"));
        assertEquals("fr",
            rows("SELECT COLLATION(c COLLATE '' || f) FROM cp LIMIT 1"));
        assertEquals("de",
            rows("SELECT COLLATION(COLLATE(c, 'de')) FROM cp LIMIT 1"));
    }

    /** COLLATE '' on a column is no collation at all. */
    @Test
    public void aColumnDeclaredWithTheEmptySpecificationHasNone() {
        engine.execute("CREATE OR REPLACE TABLE cp (s VARCHAR, c VARCHAR COLLATE 'en-ci', f VARCHAR COLLATE 'fr', e VARCHAR COLLATE '')");
        engine.execute("INSERT INTO cp VALUES ('a','a','a','a'), ('A','A','A','A')");
        assertEquals("null, fr, null",
            rows("SELECT COLLATION(e), COLLATION(f), COLLATION(s) FROM cp LIMIT 1"));
        assertEquals("en-ci, en-ci, en-ci, en-ci, en-ci, null, en-ci",
            rows("SELECT COLLATION(c || s), COLLATION(UPPER(c)), COLLATION(TRIM(c)), COLLATION(IFF(TRUE, c, s)), COLLATION(COALESCE(c, s)), COLLATION(c::VARCHAR), COLLATION(SUBSTR(c, 1)) FROM cp LIMIT 1"));
        assertEquals("en-ci",
            rows("SELECT COLLATION(LOWER('a' COLLATE 'en-ci'))"));
    }

    /** The collation of a collated column and of an uncollated one, read in a query. */
    @Test
    public void collationOfAColumnInAQuery() {
        engine.execute("CREATE OR REPLACE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO ct VALUES ('a', 'a'), ('A', 'A'), ('b', 'b'), ('B', 'B')");
        assertEquals("en-ci, null",
            rows("SELECT COLLATION(c), COLLATION(s) FROM ct LIMIT 1"));
    }
}
