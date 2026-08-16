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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two collation refusals in live's own words. A simple CASE tests its subject against each WHEN value
 * and names the SUBJECT's collation first when the two disagree, where a written comparison names its
 * right side first. COLLATE given too many arguments echoes a call of three by its first two arguments
 * against 2, and a call of four or more whole against 3. Every cell is live-verified.
 */
public class CollationRefusalWordingTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String message) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains(message), sql + " -> " + refused.getMessage());
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aSimpleCaseNamesItsSubjectsCollationFirst() {
        assertRefused("SELECT CASE 'a' COLLATE 'en-ci' WHEN 'A' COLLATE 'de' THEN 1 END",
            "Incompatible collations: 'en-ci' and 'de'");
        assertRefused("SELECT CASE 'a' COLLATE 'de' WHEN 'A' COLLATE 'en-ci' THEN 1 END",
            "Incompatible collations: 'de' and 'en-ci'");
        assertRefused("SELECT CASE 'a' COLLATE 'en-ci' WHEN 'A' COLLATE 'de' THEN 1 ELSE 0 END",
            "Incompatible collations: 'en-ci' and 'de'");
        // Whichever WHEN disagrees, the subject is named first.
        assertRefused("SELECT CASE 'a' COLLATE 'en-ci' WHEN 'A' THEN 1 WHEN 'B' COLLATE 'de' THEN 2 END",
            "Incompatible collations: 'en-ci' and 'de'");
        assertRefused("""
            SELECT CASE 'a' COLLATE 'en-ci' WHEN 'A' COLLATE 'en-ci' THEN 1 WHEN 'b' COLLATE 'de' THEN 2 END""",
            "Incompatible collations: 'en-ci' and 'de'");
        assertRefused("SELECT CASE 'a' COLLATE 'en-ci' WHEN 'A' COLLATE 'de' THEN 1 WHEN 'b' COLLATE 'fr' THEN 2 END",
            "Incompatible collations: 'en-ci' and 'de'");
    }

    @Test
    public void aSimpleCaseOverAColumnNamesItsSubjectFirstInEveryClause() {
        engine.execute("CREATE OR REPLACE TABLE t (c NUMBER(5,2), s VARCHAR(5))");
        engine.execute("INSERT INTO t VALUES (1.25, 'ab'), (2.5, 'cd')");
        assertRefused("SELECT CASE s COLLATE 'en-ci' WHEN 'A' COLLATE 'de' THEN 1 END FROM t",
            "Incompatible collations: 'en-ci' and 'de'");
        assertRefused("SELECT CASE s COLLATE 'de' WHEN 'A' COLLATE 'en-ci' THEN 1 END FROM t",
            "Incompatible collations: 'de' and 'en-ci'");
        assertRefused("SELECT 1 FROM t WHERE CASE s COLLATE 'en-ci' WHEN 'A' COLLATE 'de' THEN TRUE END",
            "Incompatible collations: 'en-ci' and 'de'");
        assertEquals("1", scalar("SELECT COUNT(*) FROM t WHERE CASE s COLLATE 'en-ci' WHEN 'AB' THEN TRUE END"));
    }

    @Test
    public void theOtherConstructsKeepTheirOrder() {
        // A written comparison, a searched CASE's comparison among them, names its right side first.
        assertRefused("SELECT 'a' COLLATE 'en-ci' = 'A' COLLATE 'de'",
            "Incompatible collations: 'de' and 'en-ci'");
        assertRefused("SELECT CASE WHEN 'a' COLLATE 'en-ci' = 'A' COLLATE 'de' THEN 1 END",
            "Incompatible collations: 'de' and 'en-ci'");
        // A CASE's results settle left to right.
        assertRefused("SELECT CASE 'a' COLLATE 'en-ci' WHEN 'A' THEN 'x' COLLATE 'de' ELSE 'y' COLLATE 'fr' END",
            "Incompatible collations: 'de' and 'fr'");
        // Each WHEN is its own comparison: two WHEN values that disagree beside an uncollated subject
        // are no refusal, and a collated subject that agrees with its WHEN matches under it.
        assertEquals("1", scalar("SELECT CASE 'a' WHEN 'A' COLLATE 'en-ci' THEN 1 WHEN 'B' COLLATE 'de' THEN 2 END"));
        assertEquals("2",
            scalar("SELECT CASE 'a' COLLATE 'en-ci' WHEN 'b' THEN 1 WHEN 'A' COLLATE 'en-ci' THEN 2 END"));
    }

    @Test
    public void collateGivenThreeArgumentsEchoesItsFirstTwo() {
        assertRefused("SELECT COLLATE('a', 'en-ci', 'x')", """
            SQL compilation error: error line 1 at position 7
            too many arguments for function [COLLATE('a', 'en-ci')] expected 2, got 3""");
        assertRefused("SELECT COLLATE('a', 'en-ci', 1)", """
            SQL compilation error: error line 1 at position 7
            too many arguments for function [COLLATE('a', 'en-ci')] expected 2, got 3""");
        assertRefused("SELECT   COLLATE('a', 'en-ci', 'x')", """
            SQL compilation error: error line 1 at position 9
            too many arguments for function [COLLATE('a', 'en-ci')] expected 2, got 3""");
        assertRefused("SELECT 1, COLLATE('a', 'en-ci', 'x')", """
            SQL compilation error: error line 1 at position 10
            too many arguments for function [COLLATE('a', 'en-ci')] expected 2, got 3""");
        assertRefused("SELECT COLLATE(COLLATE('a', 'en-ci'), 'de', 'x')", """
            SQL compilation error: error line 1 at position 7
            too many arguments for function [COLLATE(COLLATE('a', 'en-ci'), 'de')] expected 2, got 3""");
        engine.execute("CREATE OR REPLACE TABLE t (c NUMBER(5,2), s VARCHAR(5))");
        assertRefused("SELECT COLLATE(s, 'en-ci', 'x') FROM t", """
            SQL compilation error: error line 1 at position 7
            too many arguments for function [COLLATE(T.S, 'en-ci')] expected 2, got 3""");
    }

    @Test
    public void collateGivenFourOrMoreArgumentsEchoesTheWholeCallAgainstThree() {
        assertRefused("SELECT COLLATE('a', 'en-ci', 'x', 'y')", """
            SQL compilation error: error line 1 at position 7
            too many arguments for function [COLLATE('a', 'en-ci', 'x', 'y')] expected 3, got 4""");
        assertRefused("SELECT COLLATE('a', 'en-ci', 1, 2)", """
            SQL compilation error: error line 1 at position 7
            too many arguments for function [COLLATE('a', 'en-ci', 1, 2)] expected 3, got 4""");
        assertRefused("SELECT COLLATE('a', 'en-ci', 'x', 'y', 'z')", """
            SQL compilation error: error line 1 at position 7
            too many arguments for function [COLLATE('a', 'en-ci', 'x', 'y', 'z')] expected 3, got 5""");
        engine.execute("CREATE OR REPLACE TABLE t (c NUMBER(5,2), s VARCHAR(5))");
        assertRefused("SELECT COLLATE(s, 'en-ci', 'x', 'y') FROM t", """
            SQL compilation error: error line 1 at position 7
            too many arguments for function [COLLATE(T.S, 'en-ci', 'x', 'y')] expected 3, got 4""");
        // Too few arguments keep their own sentence.
        assertRefused("SELECT COLLATE('a')", """
            SQL compilation error: error line 1 at position 7
            not enough arguments for function [COLLATE('a')], expected 2, got 1""");
    }
}
