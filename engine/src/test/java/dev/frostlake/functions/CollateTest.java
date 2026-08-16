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
 * COLLATE, in its function and its infix spelling: the collation it names is carried into every
 * comparison the call reaches, ranked against a column's own, and validated while the statement
 * compiles. Every cell is live-verified.
 */
public class CollateTest extends BaseDatabaseTest {

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

    /** Both spellings build one call, which binds tighter than || and =, and its collation decides the comparison. */
    @Test
    public void theInfixAndTheFunctionFormsCollateAComparison() {
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' = 'A'"));
        assertEquals("true",
            rows("SELECT 'a' = 'A' COLLATE 'en-ci'"));
        assertEquals("false",
            rows("SELECT 'a' = 'A'"));
        assertEquals("x",
            rows("SELECT 'x' COLLATE 'en-ci' || ''"));
        assertEquals("true",
            rows("SELECT COLLATE('a', 'en-ci') = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-cs' = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en' = 'A'"));
        assertRefused("SELECT 'a' COLLATE 'en-ci' = 'A' COLLATE 'en-cs'",
            "SQL compilation error: Incompatible collations: 'en-cs' and 'en-ci'");
        assertRefused("SELECT 'a' COLLATE 'xx-yy' = 'A'",
            "Invalid value 'xx-yy' for COLLATION. Reason: Unknown option: yy");
        assertEquals("false",
            rows("SELECT 'a' COLLATE '' = 'A'"));
        assertEquals("false",
            rows("SELECT ('a' COLLATE 'en-ci') COLLATE 'en-cs' = 'A'"));
        assertEquals("A, 3",
            rows("SELECT UPPER('a' COLLATE 'en-ci'), LENGTH('abc' COLLATE 'en-ci')"));
        assertEquals("2",
            rows("SELECT ROUND(2.5, 0, 'HALF_TO_EVEN' COLLATE 'en-ci')"));
        assertRefused("SELECT ROUND(2.5, 0, COLLATE('x', 'en-ci'))",
            "SQL compilation error: error line 1 at position 7\ninvalid argument for function [ROUND] unexpected argument [x] at position -1,");
        assertRefused("SELECT ROUND(2.5, 0, 'x' COLLATE 'en-ci')",
            "SQL compilation error: error line 1 at position 7\ninvalid argument for function [ROUND] unexpected argument [x] at position -1,");
        assertEquals("true",
            rows("SELECT 'b' COLLATE 'en-ci' > 'A'"));
        assertEquals("true",
            rows("SELECT 'b' > 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci'::VARCHAR = 'A'"));
        assertEquals("true",
            rows("SELECT 'a'::VARCHAR COLLATE 'en-ci' = 'A'"));
        assertEquals("a",
            rows("SELECT 'a' collate"));
        assertEquals("a",
            rows("SELECT 'a' COLLATE 'en-ci' c1"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE $$en-ci$$ = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci' COLLATE 'en-cs' = 'A'"));
        assertEquals("true",
            rows("SELECT 'x' COLLATE 'en-ci' || 'Y' = 'XY'"));
        assertEquals("true",
            rows("SELECT 'A' || 'b' COLLATE 'en-ci' = 'ab'"));
        assertEquals("true",
            rows("SELECT UPPER('a') COLLATE 'en-ci' = 'a'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' BETWEEN 'A' AND 'A'"));
        assertEquals("true",
            rows("SELECT 'abc' COLLATE 'en-ci' LIKE 'A%'"));
        assertEquals("true",
            rows("SELECT 'a' LIKE 'A' COLLATE 'en-ci'"));
        assertEquals("true",
            rows("SELECT 'abc' COLLATE 'en-ci' LIKE '%B%'"));
        assertEquals("true",
            rows("SELECT 'abc' COLLATE 'upper' LIKE 'ABC'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci' <> 'A'"));
        assertEquals("1",
            rows("SELECT CASE 'a' COLLATE 'en-ci' WHEN 'A' THEN 1 ELSE 0 END"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' IN ('A', 'b')"));
        assertEquals("true",
            rows("SELECT 'a' collate 'en-ci' = 'A'"));
        assertEquals("a",
            rows("SELECT 'a' COLLATE 'en-ci' AS \"COLLATE\""));
        assertEquals("1",
            rows("SELECT 1 AS collate"));
        assertEquals("a",
            rows("SELECT 'a' COLLATE"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci' IS DISTINCT FROM 'A'"));
        assertEquals("1",
            rows("SELECT IFF('a' COLLATE 'en-ci' = 'A', 1, 0)"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' ILIKE 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci' RLIKE 'A'"));
        assertRefused("SELECT 'a' COLLATE 'en-ci' LIKE ANY ('A')",
            "SQL compilation error: error line 1 at position 27\nFunction LIKE_ANY does not support collation.");
        assertEquals("true",
            rows("SELECT 'a%' COLLATE 'en-ci' LIKE 'A!%' ESCAPE '!'"));
        assertRefused("SELECT COALESCE('a' COLLATE 'de', 'b', 'c' COLLATE 'fr')",
            "SQL compilation error: Incompatible collations: 'fr' and 'de'");
        assertRefused("SELECT COALESCE('a' COLLATE 'de', 'b' COLLATE 'fr', 'c' COLLATE 'it')",
            "SQL compilation error: Incompatible collations: 'it' and 'fr'");
        assertRefused("SELECT CONCAT('a' COLLATE 'de', 'b' COLLATE 'fr', 'c' COLLATE 'it')",
            "SQL compilation error: Incompatible collations: 'de' and 'fr'");
        assertRefused("SELECT GREATEST('a' COLLATE 'de', 'b' COLLATE 'fr')",
            "SQL compilation error: Incompatible collations: 'de' and 'fr'");
        assertRefused("SELECT NVL('a' COLLATE 'de', 'b' COLLATE 'fr')",
            "SQL compilation error: Incompatible collations: 'fr' and 'de'");
        assertRefused("SELECT NVL2('x', 'a' COLLATE 'de', 'b' COLLATE 'fr')",
            "SQL compilation error: Incompatible collations: 'fr' and 'de'");
        assertRefused("SELECT DECODE('x', 'x', 'a' COLLATE 'de', 'b' COLLATE 'fr')",
            "SQL compilation error: Incompatible collations: 'de' and 'fr'");
        assertRefused("SELECT CASE WHEN TRUE THEN 'a' COLLATE 'de' ELSE 'b' COLLATE 'fr' END",
            "SQL compilation error: Incompatible collations: 'de' and 'fr'");
        assertEquals("null",
            rows("SELECT CASE 'a' WHEN 'b' COLLATE 'de' THEN 1 WHEN 'c' COLLATE 'fr' THEN 2 END"));
        assertRefused("SELECT 'a' COLLATE 'de' || 'b' || 'c' COLLATE 'fr'",
            "SQL compilation error: Incompatible collations: 'de' and 'fr'");
        assertRefused("SELECT 'a' IN ('b' COLLATE 'de', 'c' COLLATE 'fr')",
            "SQL compilation error: Incompatible collations: 'fr' and 'de'");
        assertEquals("true",
            rows("SELECT 'a' IN ('A' COLLATE 'en-ci', 'b')"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE '' = 'a' COLLATE 'utf8'"));
        assertRefused("SELECT 'a' COLLATE 'utf8' = 'A' COLLATE 'upper'",
            "SQL compilation error: Incompatible collations: 'upper' and 'utf8'");
        assertRefused("SELECT LEAST('a' COLLATE 'de', 'b' COLLATE 'fr')",
            "SQL compilation error: Incompatible collations: 'de' and 'fr'");
        assertRefused("SELECT IFNULL('a' COLLATE 'de', 'b' COLLATE 'fr')",
            "SQL compilation error: Incompatible collations: 'fr' and 'de'");
        assertRefused("SELECT CASE WHEN TRUE THEN 'a' COLLATE 'de' WHEN FALSE THEN 'b' COLLATE 'fr' END",
            "SQL compilation error: Incompatible collations: 'de' and 'fr'");
        assertEquals("false",
            rows("SELECT 'a' IN ('b' COLLATE 'de', 'c' COLLATE 'de')"));
        assertRefused("SELECT IFF(TRUE, 'a' COLLATE 'de', 'b') = 'A' COLLATE 'en-ci'",
            "SQL compilation error: Incompatible collations: 'en-ci' and 'de'");
        assertEquals("true",
            rows("SELECT 'b' COLLATE 'en-ci' BETWEEN 'A' AND 'C'"));
        assertEquals("true",
            rows("SELECT 'B' COLLATE 'en-ci' BETWEEN 'a' AND 'c'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' IS NOT DISTINCT FROM 'A'"));
        assertEquals("2",
            rows("SELECT CASE 'a' COLLATE 'en-ci' WHEN 'b' THEN 1 WHEN 'A' THEN 2 END"));
        assertEquals("true",
            rows("SELECT 'ab' COLLATE 'en-ci' = 'A' || 'B'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci' > 'A'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' >= 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci' != 'A'"));
        assertEquals("true",
            rows("SELECT 'a' LIKE 'a' COLLATE 'de' ESCAPE '!'"));
    }

    /** A locale compares by its language's rules, and the case, accent, punctuation, preference, case-conversion and trim options apply. */
    @Test
    public void aLocaleOrdersLinguisticallyAndTheOptionsApply() {
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-cs' < 'B'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-cs' < 'A'"));
        assertEquals("false",
            rows("SELECT 'A' COLLATE 'en-cs' < 'a'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' < 'B'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci' < 'A'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' <= 'A'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en' < 'B'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'utf8' < 'B'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE '' < 'B'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'bin' < 'B'"));
        assertEquals("true",
            rows("SELECT '-' COLLATE 'en' < '+'"));
        assertRefused("SELECT 'a' COLLATE 'ci' = 'A'",
            "Invalid value 'ci' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'ci' < 'B'",
            "Invalid value 'ci' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'upper' = 'A'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'lower' < 'B'"));
        assertEquals("true",
            rows("SELECT ' a ' COLLATE 'trim' = 'a'"));
        assertEquals("true",
            rows("SELECT ' a ' COLLATE 'rtrim' = ' a'"));
        assertEquals("false",
            rows("SELECT 'é' COLLATE 'en-ci' = 'E'"));
        assertEquals("true",
            rows("SELECT 'é' COLLATE 'en-ai' = 'e'"));
        assertEquals("true",
            rows("SELECT 'é' COLLATE 'en-ci-ai' = 'E'"));
        assertEquals("true",
            rows("SELECT 'A-B' COLLATE 'en-pi' = 'AB'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-fu' < 'A'"));
        assertRefused("SELECT 'a' COLLATE 'utf8-ci' = 'A'",
            "Invalid value 'utf8-ci' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'upper' < 'B'"));
        assertEquals("true",
            rows("SELECT '-' COLLATE 'en-ci' < '+'"));
        assertRefused("SELECT 'a' COLLATE 'ci' < 'B'",
            "Invalid value 'ci' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-cs-ai' = 'A'"));
        assertEquals("true",
            rows("SELECT 'é' COLLATE 'en-cs-ai' = 'e'"));
        assertEquals("false",
            rows("SELECT 'É' COLLATE 'en-cs-ai' = 'e'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-fu' < 'B'"));
        assertEquals("false",
            rows("SELECT 'B' COLLATE 'en-fu' < 'a'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-pi' < 'B'"));
        assertEquals("true",
            rows("SELECT 'ab' COLLATE 'en-pi' = 'a-b'"));
        assertEquals("true",
            rows("SELECT 'ab' COLLATE 'en-pi' = 'a b'"));
        assertEquals("false",
            rows("SELECT 'Ab' COLLATE 'en-ci-fu' < 'ab'"));
        assertEquals("true",
            rows("SELECT 'a b' COLLATE 'en-ci' = 'a b'"));
        assertEquals("true",
            rows("SELECT 'ab' COLLATE 'en-ci-pi' = 'A.B'"));
        assertEquals("true",
            rows("SELECT '_' COLLATE 'en' < 'a'"));
        assertEquals("true",
            rows("SELECT '1' COLLATE 'en' < 'a'"));
        assertEquals("false",
            rows("SELECT 'Z' COLLATE 'en' < 'a'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'upper' < '_'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'lower' < '_'"));
        assertEquals("true",
            rows("SELECT 'ä' COLLATE 'de' < 'b'"));
        assertEquals("false",
            rows("SELECT 'ä' COLLATE 'sv' < 'b'"));
        assertEquals("false",
            rows("SELECT 'ä' < 'b'"));
        assertRefused("SELECT 'ch' COLLATE 'cs' < 'h'",
            "Invalid value 'cs' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
    }

    /** The specifiers, their order, and the four refusal reasons. */
    @Test
    public void aSpecificationIsValidatedAndLowerCased() {
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'xx' = 'A'"));
        assertRefused("SELECT 'a' COLLATE 'en-zz' = 'A'",
            "Invalid value 'en-zz' for COLLATION. Reason: Unknown option: zz");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'zz-ci' = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci-cs' = 'A'"));
        assertRefused("SELECT 'a' COLLATE 'ci-en' = 'A'",
            "Invalid value 'ci-en' for COLLATION. Reason: Unknown option: en");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'EN-CI' = 'A'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en_US-ci' = 'A'"));
        assertEquals("a",
            rows("SELECT 'a' COLLATE 'bogus'"));
        assertRefused("SELECT 'a' COLLATE 'xx-yy' FROM (SELECT 1 AS z) WHERE FALSE",
            "Invalid value 'xx-yy' for COLLATION. Reason: Unknown option: yy");
        assertRefused("SELECT COLLATE('a', 'xx-yy')",
            "Invalid value 'xx-yy' for COLLATION. Reason: Unknown option: yy");
        assertRefused("CREATE OR REPLACE TABLE cv (c VARCHAR COLLATE 'xx-yy')",
            "Invalid value 'xx-yy' for COLLATION. Reason: Unknown option: yy");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci-ci' = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'de_DE' = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en_ZZ' = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'qq' = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-trim-rtrim' = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE ' en' = 'A'"));
        assertRefused("SELECT 'a' COLLATE 'en--ci' = 'A'",
            "Invalid value 'en--ci' for COLLATION. Reason: Unknown option:");
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'e' = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'english' = 'A'"));
        assertRefused("SELECT 'a' COLLATE 'yy-xx' = 'A'",
            "Invalid value 'yy-xx' for COLLATION. Reason: Unknown option: xx");
        assertRefused("SELECT 'a' COLLATE 'en-ZZ'",
            "Invalid value 'en-zz' for COLLATION. Reason: Unknown option: zz");
        assertRefused("SELECT 'a' COLLATE 'ai'",
            "Invalid value 'ai' for COLLATION. Reason: Accent sensitivity option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'pi'",
            "Invalid value 'pi' for COLLATION. Reason: Punctuation sensitivity option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'fu'",
            "Invalid value 'fu' for COLLATION. Reason: Upper/lower preference option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'upper-ci'",
            "Invalid value 'upper-ci' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-upper' = 'A'"));
        assertRefused("SELECT 'a' COLLATE 'upper-lower' = 'A'",
            "Invalid value 'upper-lower' for COLLATION. Reason: Options 'upper' and 'lower' are mutually exclusive");
        assertEquals("true",
            rows("SELECT ' a' COLLATE 'trim-ltrim' = 'a'"));
        assertEquals("true",
            rows("SELECT ' a ' COLLATE 'utf8-trim' = 'a'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'bin-upper' = 'A'"));
        assertRefused("SELECT 'a' COLLATE 'en-utf8'",
            "Invalid value 'en-utf8' for COLLATION. Reason: Unknown option: utf8");
        assertRefused("SELECT 'a' COLLATE 'utf8-en'",
            "Invalid value 'utf8-en' for COLLATION. Reason: Unknown option: en");
        assertEquals("true",
            rows("SELECT COLLATE('a', $$en-ci$$) = 'A'"));
        assertRefused("SELECT COLLATE('a', 'en' || '-ci')",
            "SQL compilation error:\nArgument number 2 for function 'COLLATE' needs to be a string literal.");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci-upper' = 'A'"));
        assertRefused("SELECT 'a' COLLATE 'ci-ai'",
            "Invalid value 'ci-ai' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-cs-ci' = 'A'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'de-AI' = 'A'"));
        assertRefused("SELECT 'a' COLLATE 'as'",
            "Invalid value 'as' for COLLATION. Reason: Accent sensitivity option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'cs'",
            "Invalid value 'cs' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'ps'",
            "Invalid value 'ps' for COLLATION. Reason: Punctuation sensitivity option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'fl'",
            "Invalid value 'fl' for COLLATION. Reason: Upper/lower preference option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'bin-ci'",
            "Invalid value 'bin-ci' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'upper-ai'",
            "Invalid value 'upper-ai' for COLLATION. Reason: Accent sensitivity option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'lower-pi'",
            "Invalid value 'lower-pi' for COLLATION. Reason: Punctuation sensitivity option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'utf8-fu'",
            "Invalid value 'utf8-fu' for COLLATION. Reason: Upper/lower preference option not allowed for the UTF8 collation");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'ZZ-CI' = 'A'"));
        assertRefused("SELECT 'a' COLLATE 'en-ci-zz-qq'",
            "Invalid value 'en-ci-zz-qq' for COLLATION. Reason: Unknown option: zz");
        assertRefused("SELECT 'a' COLLATE 'en-ZZ-ci'",
            "Invalid value 'en-zz-ci' for COLLATION. Reason: Unknown option: zz");
        assertEquals("a",
            rows("SELECT 'a' COLLATE '-'"));
        assertRefused("SELECT 'a' COLLATE 'ci-cs'",
            "Invalid value 'ci-cs' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertRefused("SELECT 'a' COLLATE 'upper-cs'",
            "Invalid value 'upper-cs' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertEquals("a",
            rows("SELECT 'a' COLLATE '-'"));
        assertEquals("a",
            rows("SELECT 'a' COLLATE '--'"));
        assertRefused("SELECT 'a' COLLATE '-ci'",
            "Invalid value '-ci' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertEquals("a",
            rows("SELECT 'a' COLLATE 'en-ci-'"));
        assertRefused("SELECT 'a' COLLATE '-en'",
            "Invalid value '-en' for COLLATION. Reason: Unknown option: en");
        assertRefused("SELECT 'a' COLLATE 'upper-lower-ci'",
            "Invalid value 'upper-lower-ci' for COLLATION. Reason: Options 'upper' and 'lower' are mutually exclusive");
        assertRefused("SELECT 'a' COLLATE 'ci-upper-lower'",
            "Invalid value 'ci-upper-lower' for COLLATION. Reason: Options 'upper' and 'lower' are mutually exclusive");
        assertRefused("SELECT 'a' COLLATE 'en-upper-lower'",
            "Invalid value 'en-upper-lower' for COLLATION. Reason: Options 'upper' and 'lower' are mutually exclusive");
        assertEquals("true",
            rows("SELECT ' a ' COLLATE 'rtrim-ltrim' = 'a'"));
        assertRefused("SELECT 'a' COLLATE 'trim-cs'",
            "Invalid value 'trim-cs' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-upper-upper' = 'A'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-fu-fl' < 'A'"));
        assertEquals("true",
            rows("SELECT ' a ' COLLATE 'ltrim-rtrim' = 'a'"));
        assertEquals("true",
            rows("SELECT ' a ' COLLATE 'trim-rtrim' = ' a'"));
        assertRefused("SELECT 'a' COLLATE 'lower-upper'",
            "Invalid value 'lower-upper' for COLLATION. Reason: Options 'upper' and 'lower' are mutually exclusive");
    }

    /** An explicit COLLATE outranks a column, a column outranks none, and two different ones at one level are refused in the construct's order. */
    @Test
    public void collationsAtOneLevelMustAgree() {
        assertRefused("SELECT 'a' COLLATE 'en-cs' = 'A' COLLATE 'en-ci'",
            "SQL compilation error: Incompatible collations: 'en-ci' and 'en-cs'");
        assertRefused("SELECT 'a' COLLATE 'de' = 'A' COLLATE 'fr'",
            "SQL compilation error: Incompatible collations: 'fr' and 'de'");
        assertRefused("SELECT 'a' COLLATE 'fr' = 'A' COLLATE 'de'",
            "SQL compilation error: Incompatible collations: 'de' and 'fr'");
        assertRefused("SELECT 'a' COLLATE 'en-ci' || 'b' COLLATE 'en-cs'",
            "SQL compilation error: Incompatible collations: 'en-ci' and 'en-cs'");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' = 'A' COLLATE 'EN-CI'"));
        assertRefused("SELECT 'a' COLLATE 'bin' = 'a' COLLATE 'utf8'",
            "SQL compilation error: Incompatible collations: 'utf8' and 'bin'");
        assertRefused("SELECT 'a' COLLATE 'en-ci' IN ('A' COLLATE 'en-cs')",
            "SQL compilation error: Incompatible collations: 'en-cs' and 'en-ci'");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' = 'A' COLLATE ''"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' = 'A' COLLATE 'en-ci'"));
        assertRefused("SELECT 'a' COLLATE 'de' < 'b' COLLATE 'fr'",
            "SQL compilation error: Incompatible collations: 'fr' and 'de'");
        assertRefused("SELECT 'a' COLLATE 'en-ci' IN ('A', 'b' COLLATE 'de')",
            "SQL compilation error: Incompatible collations: 'de' and 'en-ci'");
        assertRefused("SELECT 'a' COLLATE 'en-ci' LIKE 'A' COLLATE 'en-cs'",
            "SQL compilation error: Incompatible collations: 'en-ci' and 'en-cs'");
        engine.execute("CREATE OR REPLACE TABLE cp (s VARCHAR, c VARCHAR COLLATE 'en-ci', f VARCHAR COLLATE 'fr', n NUMBER)");
        engine.execute("INSERT INTO cp VALUES ('a','a','a',1), ('A','A','A',2)");
        engine.execute("CREATE OR REPLACE TABLE ce (s VARCHAR, c VARCHAR COLLATE 'en-ci', f VARCHAR COLLATE 'fr', n NUMBER)");
        assertRefused("SELECT 'a' COLLATE 'EN-CI' = 'A' COLLATE 'en-cs'",
            "SQL compilation error: Incompatible collations: 'en-cs' and 'en-ci'");
        assertRefused("SELECT 'a' COLLATE 'en-ci' BETWEEN 'A' COLLATE 'en-cs' AND 'b'",
            "SQL compilation error: Incompatible collations: 'en-cs' and 'en-ci'");
        assertRefused("SELECT 'a' COLLATE 'en-ci' BETWEEN 'A' AND 'b' COLLATE 'de'",
            "SQL compilation error: Incompatible collations: 'de' and 'en-ci'");
        assertRefused("SELECT 'a' COLLATE 'en-ci' IS DISTINCT FROM 'A' COLLATE 'de'",
            "SQL compilation error: Incompatible collations: 'de' and 'en-ci'");
        assertRefused("SELECT CONCAT('a' COLLATE 'en-ci', 'b' COLLATE 'de')",
            "SQL compilation error: Incompatible collations: 'en-ci' and 'de'");
        assertRefused("SELECT COUNT(*) FROM ce WHERE FALSE AND c = f",
            "SQL compilation error: Incompatible collations: 'fr' and 'en-ci'");
        assertRefused("SELECT c = f FROM ce",
            "SQL compilation error: Incompatible collations: 'fr' and 'en-ci'");
        assertEquals("1",
            rows("SELECT COUNT(*) FROM cp WHERE c IN ('A' COLLATE 'de')"));
        assertRefused("SELECT COUNT(*) FROM ce WHERE c LIKE f",
            "SQL compilation error: Incompatible collations: 'en-ci' and 'fr'");
        assertRefused("SELECT COUNT(*) FROM ce WHERE c IN (f)",
            "SQL compilation error: Incompatible collations: 'fr' and 'en-ci'");
        assertRefused("SELECT c || f FROM ce",
            "SQL compilation error: Incompatible collations: 'en-ci' and 'fr'");
        assertRefused("SELECT COUNT(*) FROM ce WHERE c BETWEEN f AND 'z'",
            "SQL compilation error: Incompatible collations: 'fr' and 'en-ci'");
        assertRefused("SELECT 'a' COLLATE 'en-ci' NOT IN ('A' COLLATE 'de')",
            "SQL compilation error: Incompatible collations: 'de' and 'en-ci'");
        assertRefused("SELECT COUNT(*) FROM ce GROUP BY c HAVING c = MAX(f)",
            "SQL compilation error: Incompatible collations: 'fr' and 'en-ci'");
        assertRefused("SELECT 'a' COLLATE 'en-ci' LIKE 'A' ESCAPE '!' COLLATE 'de'",
            "SQL compilation error:\nsyntax error line 1 at position 55 unexpected ''de''.");
        assertRefused("SELECT COUNT(*) FROM ce x JOIN ce y ON x.c = y.f",
            "SQL compilation error: Incompatible collations: 'fr' and 'en-ci'");
    }

    /** A column declared COLLATE compares under it in every predicate, and an explicit COLLATE or COLLATE '' overrides it. */
    @Test
    public void aCollatedColumnComparesUnderItsCollation() {
        engine.execute("CREATE OR REPLACE TABLE ct (s VARCHAR, c VARCHAR COLLATE 'en-ci')");
        engine.execute("INSERT INTO ct VALUES ('a', 'a'), ('A', 'A'), ('b', 'b'), ('B', 'B')");
        assertEquals("2",
            rows("SELECT COUNT(*) FROM ct WHERE c = 'A'"));
        assertEquals("2",
            rows("SELECT COUNT(*) FROM ct WHERE s COLLATE 'en-ci' = 'A'"));
        assertEquals("true | false | true | false",
            rows("SELECT s COLLATE 'en-ci' IN ('A') FROM ct ORDER BY s"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' LIKE 'A'"));
        assertEquals("VARCHAR(1)[LOB]",
            rows("SELECT SYSTEM$TYPEOF('a' COLLATE 'en-ci')"));
        assertEquals("a",
            rows("SELECT 'a' COLLATE 'en-ci' AS x"));
        engine.execute("CREATE OR REPLACE TABLE cp (s VARCHAR, c VARCHAR COLLATE 'en-ci', f VARCHAR COLLATE 'fr', e VARCHAR COLLATE '')");
        engine.execute("INSERT INTO cp VALUES ('a','a','a','a'), ('A','A','A','A')");
        assertEquals("1",
            rows("SELECT COUNT(*) FROM cp WHERE c = 'A' COLLATE 'en-cs'"));
        assertRefused("SELECT COUNT(*) FROM cp WHERE c = f",
            "SQL compilation error: Incompatible collations: 'fr' and 'en-ci'");
        assertEquals("4",
            rows("SELECT COUNT(*) FROM cp x JOIN cp y ON x.c = y.s"));
        assertEquals("1",
            rows("SELECT COUNT(*) FROM cp WHERE c COLLATE '' = 'A'"));
        assertEquals("1",
            rows("SELECT COUNT(*) FROM cp WHERE e = 'A'"));
        assertEquals("2",
            rows("SELECT COUNT(*) FROM cp WHERE c IN ('A')"));
        assertEquals("2",
            rows("SELECT COUNT(*) FROM cp WHERE c LIKE 'A'"));
        assertEquals("2",
            rows("SELECT COUNT(*) FROM cp WHERE c BETWEEN 'A' AND 'A'"));
        assertEquals("0",
            rows("SELECT COUNT(*) FROM cp WHERE c > 'A'"));
        assertEquals("1 | 1",
            rows("SELECT CASE c WHEN 'A' THEN 1 ELSE 0 END FROM cp ORDER BY s"));
        assertEquals("0",
            rows("SELECT COUNT(*) FROM cp WHERE c NOT IN ('A')"));
        assertEquals("true | true",
            rows("SELECT EQUAL_NULL(c, 'A') FROM cp ORDER BY s"));
        assertEquals("true | true",
            rows("SELECT c = 'A' FROM cp ORDER BY s"));
        assertEquals("0",
            rows("SELECT COUNT(*) FROM cp WHERE c != 'A'"));
        assertEquals("0",
            rows("SELECT COUNT(*) FROM cp WHERE c IS DISTINCT FROM 'A'"));
        assertEquals("2",
            rows("SELECT COUNT(*) FROM cp WHERE 'A' = c"));
        assertEquals("2",
            rows("SELECT COUNT(*) FROM cp WHERE c = s COLLATE 'en-cs'"));
        assertRefused("SELECT COUNT(*) FROM cp x JOIN cp y ON x.c = y.f",
            "SQL compilation error: Incompatible collations: 'fr' and 'en-ci'");
        assertEquals("1",
            rows("SELECT COUNT(*) FROM cp WHERE f = 'A'"));
        assertEquals("2",
            rows("SELECT COUNT(*) FROM cp WHERE c LIKE 'a%'"));
        assertEquals("0",
            rows("SELECT COUNT(*) FROM cp WHERE c NOT LIKE 'A'"));
        engine.execute("CREATE OR REPLACE TABLE cc AS SELECT 'a' COLLATE 'en-ci' AS k");
        engine.execute("CREATE OR REPLACE VIEW cvw AS SELECT 'a' COLLATE 'en-ci' AS k");
        assertEquals("[\"A\"] | [\"a\"]",
            rows("SELECT SPLIT(c, ',') FROM cp ORDER BY s"));
        assertEquals("true | false",
            rows("SELECT REGEXP_LIKE(c, 'A') FROM cp ORDER BY s"));
        assertEquals("true | true",
            rows("SELECT c IN ('A', 'b') FROM cp ORDER BY s"));
    }

    /** A column collation needs a string column and a valid specification, in either quoting, and is kept lower-cased. */
    @Test
    public void aColumnDefinitionValidatesAndStoresItsCollation() {
        engine.execute("CREATE OR REPLACE TABLE cu (u VARCHAR COLLATE 'EN-CI', n NUMBER)");
        assertEquals("",
            rows("SELECT COLLATION(u) FROM cu"));
        assertTrue(rows("SHOW COLUMNS IN TABLE cu").contains("\"fixed\":false,\"collation\":\"en-ci\"}"));
        assertTrue(rows("DESC TABLE cu").contains("VARCHAR(16777216) COLLATE 'en-ci'"));
        assertRefused("CREATE OR REPLACE TABLE cb (b VARCHAR COLLATE 'ci')",
            "Invalid value 'ci' for COLLATION. Reason: Case sensitivity option not allowed for the UTF8 collation");
        assertRefused("ALTER TABLE cu ADD COLUMN w VARCHAR COLLATE 'xx-yy'",
            "Invalid value 'xx-yy' for COLLATION. Reason: Unknown option: yy");
        assertRefused("CREATE OR REPLACE TABLE cn (x NUMBER COLLATE 'en-ci')",
            "SQL compilation error:\nCannot specify column collation for data type 'NUMBER(38,0)' for column 'X'");
        engine.execute("CREATE OR REPLACE TABLE cv2 (x VARCHAR COLLATE $$en-ci$$)");
        assertEquals("0",
            rows("SELECT COUNT(*) FROM cu WHERE u = 'A'"));
        engine.execute("CREATE OR REPLACE TABLE cu2 (u VARCHAR(10) COLLATE 'EN-CI')");
        engine.execute("INSERT INTO cu2 VALUES ('a')");
        assertEquals("en-ci",
            rows("SELECT COLLATION(u) FROM cu2"));
        engine.execute("ALTER TABLE cu2 ALTER COLUMN u SET DATA TYPE VARCHAR(20) COLLATE 'en-ci'");
        engine.execute("ALTER TABLE cu2 ALTER COLUMN u SET DATA TYPE VARCHAR(30) COLLATE 'EN-CI'");
        assertTrue(rows("SELECT GET_DDL('TABLE', 'cu2')").contains("U VARCHAR(30) COLLATE 'en-ci'"));
        assertRefused("ALTER TABLE cu2 ALTER COLUMN u SET DATA TYPE VARCHAR(40)",
            "SQL compilation error: cannot change column U from type \"VARCHAR(30) COLLATE 'en-ci'\" to \"VARCHAR(40)\" because they have incompatible collations.");
        engine.execute("CREATE OR REPLACE TABLE cu3 (u VARCHAR(10) COLLATE 'En-Ci')");
        engine.execute("ALTER TABLE cu3 ALTER COLUMN u SET DATA TYPE VARCHAR(20) COLLATE 'En-Ci'");
        engine.execute("ALTER TABLE cu3 ALTER COLUMN u SET DATA TYPE VARCHAR(30) COLLATE 'en-ci'");
        assertTrue(rows("SELECT GET_DDL('TABLE', 'cu3')").contains("U VARCHAR(30) COLLATE 'en-ci'"));
    }

    /** LIKE and ILIKE honour case, case conversion and trims, and refuse accent- or punctuation-insensitivity; LIKE ANY and LIKE ALL take no locale. */
    @Test
    public void likeMatchesUnderTheCollationItSupports() {
        assertEquals("false",
            rows("SELECT 'abc' COLLATE 'en' LIKE 'ABC'"));
        assertRefused("SELECT 'é' COLLATE 'en-ai' LIKE 'e'",
            "SQL compilation error: error line 1 at position 27\nFunction LIKE does not support collation: en-ai.");
        assertEquals("true",
            rows("SELECT ' a ' COLLATE 'trim' LIKE 'a'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci' NOT LIKE 'A'"));
        assertRefused("SELECT 'A-B' COLLATE 'en-pi' LIKE 'AB'",
            "SQL compilation error: error line 1 at position 29\nFunction LIKE does not support collation: en-pi.");
        assertEquals("false",
            rows("SELECT 'é' COLLATE 'en-ci' LIKE 'E'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'upper' LIKE 'A'"));
        assertEquals("true",
            rows("SELECT 'ab' COLLATE 'en-ci' LIKE 'A_'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ci' ILIKE 'A'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'de-ci' LIKE 'A'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'fr-ci' LIKE 'A%'"));
        assertRefused("SELECT 'a' COLLATE 'en-cs-ai' LIKE 'a'",
            "SQL compilation error: error line 1 at position 30\nFunction LIKE does not support collation: en-cs-ai.");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'lower' LIKE 'A'"));
        assertEquals("true",
            rows("SELECT 'a ' COLLATE 'rtrim' LIKE 'a'"));
        assertRefused("SELECT 'a' COLLATE 'en-ci-ai' LIKE 'A'",
            "SQL compilation error: error line 1 at position 30\nFunction LIKE does not support collation: en-ci-ai.");
        assertRefused("SELECT 'é' COLLATE 'en-ai' NOT LIKE 'e'",
            "SQL compilation error: error line 0 at position -1\nFunction LIKE does not support collation: en-ai.");
        assertRefused("SELECT 'é' COLLATE 'en-ai' ILIKE 'e'",
            "SQL compilation error: error line 1 at position 27\nFunction ILIKE does not support collation: en-ai.");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-fu' LIKE 'a'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'upper' LIKE ANY ('A')"));
        assertRefused("SELECT 'a' COLLATE 'en-cs' LIKE ANY ('a')",
            "SQL compilation error: error line 1 at position 27\nFunction LIKE_ANY does not support collation.");
        assertRefused("SELECT 'a' COLLATE 'en-ci' LIKE ALL ('A')",
            "SQL compilation error: error line 1 at position 27\nFunction LIKE_ALL does not support collation.");
        assertRefused("SELECT 'a' COLLATE 'en-ci' ILIKE ANY ('A')",
            "SQL compilation error: error line 1 at position 27\nFunction ILIKE_ANY does not support collation.");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'utf8' LIKE ANY ('a')"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'trim' LIKE ANY ('a')"));
        assertRefused("SELECT 'é' LIKE 'e' COLLATE 'en-ai'",
            "SQL compilation error: error line 1 at position 11\nFunction LIKE does not support collation: en-ai.");
        assertRefused("SELECT 'a' COLLATE 'en' LIKE ANY ('a')",
            "SQL compilation error: error line 1 at position 24\nFunction LIKE_ANY does not support collation.");
        assertRefused("SELECT 'a' LIKE ANY ('A' COLLATE 'en-ci')",
            "SQL compilation error: error line 1 at position 11\nFunction LIKE_ANY does not support collation.");
        assertEquals("false",
            rows("SELECT 'A' COLLATE 'en-ci' NOT LIKE 'a%'"));
        assertRefused("SELECT 'é' COLLATE 'en-ai' LIKE 'e' ESCAPE '!'",
            "SQL compilation error: error line 1 at position 27\nFunction LIKE does not support collation: en-ai.");
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-ps' LIKE 'a'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-as' LIKE 'a'"));
        assertEquals("true",
            rows("SELECT 'a' COLLATE 'en-fl' LIKE 'a'"));
        assertEquals("false",
            rows("SELECT 'a' COLLATE 'en-ci' RLIKE 'A'"));
    }

    /** COLLATE takes a string operand, named from the plan when it is not one, and a written literal specification. */
    @Test
    public void theOperandMustBeAStringAndTheSpecALiteral() {
        assertRefused("SELECT 1 COLLATE 'en-ci'",
            "SQL compilation error:\nargument needs to be a string: '1'");
        assertEquals("null",
            rows("SELECT NULL COLLATE 'en-ci'"));
        assertEquals("null",
            rows("SELECT 'a' COLLATE 'en-ci' = NULL"));
        assertRefused("SELECT TRUE COLLATE 'en-ci'",
            "SQL compilation error:\nargument needs to be a string: 'TRUE'");
        assertRefused("SELECT PARSE_JSON('\"a\"') COLLATE 'en-ci'",
            "SQL compilation error:\nargument needs to be a string: 'PARSE_JSON('\"a\"')'");
        assertEquals("true",
            rows("SELECT '1' COLLATE 'en-ci' = 1"));
        assertEquals("true",
            rows("SELECT 1 = '1' COLLATE 'en-ci'"));
        assertRefused("SELECT TO_DATE('2020-01-01') COLLATE 'en-ci'",
            "SQL compilation error:\nargument needs to be a string: 'TO_DATE('2020-01-01')'");
        engine.execute("CREATE OR REPLACE TABLE cp (s VARCHAR, c VARCHAR COLLATE 'en-ci', f VARCHAR COLLATE 'fr', n NUMBER)");
        engine.execute("INSERT INTO cp VALUES ('a','a','a',1), ('A','A','A',2)");
        engine.execute("CREATE OR REPLACE TABLE ce (s VARCHAR, c VARCHAR COLLATE 'en-ci', f VARCHAR COLLATE 'fr', n NUMBER)");
        assertRefused("SELECT n COLLATE 'en-ci' FROM cp",
            "SQL compilation error:\nargument needs to be a string: 'CP.N'");
        assertRefused("SELECT n COLLATE 'en-ci' FROM ce",
            "SQL compilation error:\nargument needs to be a string: 'CE.N'");
        assertRefused("SELECT COUNT(*) FROM ce WHERE n COLLATE 'en-ci' = '1'",
            "SQL compilation error:\nargument needs to be a string: 'CE.N'");
        assertRefused("SELECT (1 + 2) COLLATE 'en-ci'",
            "SQL compilation error:\nargument needs to be a string: '1 + 2'");
        assertRefused("SELECT 1.5 COLLATE 'en-ci'",
            "SQL compilation error:\nargument needs to be a string: '1.5'");
        assertRefused("SELECT X'41' COLLATE 'en-ci'",
            "SQL compilation error:\nargument needs to be a string: 'X'41''");
        assertRefused("SELECT cp.n COLLATE 'en-ci' FROM cp",
            "SQL compilation error:\nargument needs to be a string: 'CP.N'");
        assertRefused("SELECT COLLATE(n, 'en-ci') FROM ce",
            "SQL compilation error:\nargument needs to be a string: 'CE.N'");
        assertRefused("SELECT COLLATE(1, 'xx-yy')",
            "SQL compilation error:\nargument needs to be a string: '1'");
    }

    /**
     * A DEFAULT carrying an explicit collation is refused as a mismatched type however it is written;
     * COLLATE '' is taken, and a NULL specification is no literal.
     */
    @Test
    public void aCollatedDefaultIsRefused() {
        assertRefused("CREATE OR REPLACE TABLE d1 (s VARCHAR DEFAULT 'a' COLLATE 'utf8')",
            "SQL compilation error:\nDefault value data type does not match data type for column S");
        assertRefused("CREATE OR REPLACE TABLE d2 (s VARCHAR COLLATE 'en-ci' DEFAULT 'a' COLLATE 'en-ci')",
            "SQL compilation error:\nDefault value data type does not match data type for column S");
        engine.execute("CREATE OR REPLACE TABLE d3 (s VARCHAR COLLATE 'en-ci' DEFAULT 'a')");
        assertRefused("CREATE OR REPLACE TABLE d4 (s VARCHAR DEFAULT ('a' COLLATE 'utf8'))",
            "SQL compilation error:\nDefault value data type does not match data type for column S");
        assertRefused("CREATE OR REPLACE TABLE d5 (s VARCHAR DEFAULT COLLATE('a', 'utf8'))",
            "SQL compilation error:\nDefault value data type does not match data type for column S");
        engine.execute("CREATE OR REPLACE TABLE d6 (s VARCHAR DEFAULT 'a' COLLATE '')");
        assertRefused("CREATE OR REPLACE TABLE d7 (s VARCHAR COLLATE 'en-ci' DEFAULT 'a' COLLATE 'de')",
            "SQL compilation error:\nDefault value data type does not match data type for column S");
        assertRefused("CREATE OR REPLACE TABLE d8 (s VARCHAR DEFAULT 'a' COLLATE 'utf8' NOT NULL)",
            "SQL compilation error:\nDefault value data type does not match data type for column S");
        assertRefused("CREATE OR REPLACE TABLE d9 (s VARCHAR NOT NULL DEFAULT 'a' COLLATE 'utf8')",
            "SQL compilation error:\nDefault value data type does not match data type for column S");
        assertRefused("ALTER TABLE d3 ADD COLUMN t VARCHAR DEFAULT 'b' COLLATE 'utf8'",
            "SQL compilation error:\nInvalid column default expression [COLLATE('b', 'utf8')]");
        assertRefused("CREATE OR REPLACE TABLE d11 (n NUMBER DEFAULT 1 COLLATE 'utf8')",
            "SQL compilation error:\nargument needs to be a string: '1'");
        assertRefused("CREATE OR REPLACE TABLE d12 (s VARCHAR DEFAULT 'a' COLLATE 'en-ci')",
            "SQL compilation error:\nDefault value data type does not match data type for column S");
        assertRefused("CREATE OR REPLACE TABLE d13 (s VARCHAR COLLATE 'en-ci' DEFAULT ('a' COLLATE 'en-ci'))",
            "SQL compilation error:\nDefault value data type does not match data type for column S");
        assertRefused("CREATE OR REPLACE TABLE d14 (s VARCHAR DEFAULT 'a' || 'b' COLLATE 'utf8')",
            "SQL compilation error:\nDefault value data type does not match data type for column S");
        assertRefused("SELECT COLLATE(NULL, NULL)",
            "SQL compilation error:\nArgument number 2 for function 'COLLATE' needs to be a string literal.");
        assertRefused("SELECT COLLATE('a', NULL)",
            "SQL compilation error:\nArgument number 2 for function 'COLLATE' needs to be a string literal.");
        assertEquals("null",
            rows("SELECT COLLATE(NULL, 'en-ci')"));
    }
}
