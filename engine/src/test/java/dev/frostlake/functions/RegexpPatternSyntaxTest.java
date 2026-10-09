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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regular expressions the REGEXP family and RLIKE read, and the words a pattern they cannot read is refused
 * in: {@code Invalid regular expression: '<pattern>', <reason>: <fragment>}. The account reads POSIX extended
 * syntax with {@code \d \s \w}, word boundaries and lazy repetition, and nothing else of Perl — no {@code (?…)}
 * group, no backreference, no {@code \p{…}} — and its position, occurrence, group and parameter arguments have
 * ranges of their own (all live-verified).
 */
public class RegexpPatternSyntaxTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private String answer(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        assertTrue(rs.next(), sql);
        final StringBuilder cells = new StringBuilder();
        for (int c = 0; c < rs.getColumnCount(); c++) {
            cells.append(c == 0 ? "" : "|").append(rs.getValue(c));
        }
        return cells.toString();
    }

    @Test
    public void aPatternTheAccountCannotReadIsRefusedInItsOwnWords() {
        final String[][] refused = {
            {"SELECT 'ab' RLIKE 'a\\\\'", "Invalid regular expression: 'a\\', trailing \\"},
            {"SELECT 'ab' RLIKE '\\\\'", "Invalid regular expression: '\\', trailing \\"},
            {"SELECT 'ab' RLIKE '('", "Invalid regular expression: '(', missing ): ("},
            {"SELECT 'ab' RLIKE ')'", "Invalid regular expression: ')', unexpected ): )"},
            {"SELECT 'ab' RLIKE 'a)'", "Invalid regular expression: 'a)', unexpected ): a)"},
            {"SELECT 'ab' RLIKE '(a'", "Invalid regular expression: '(a', missing ): (a"},
            {"SELECT 'ab' RLIKE '((a)'", "Invalid regular expression: '((a)', missing ): ((a)"},
            {"SELECT 'ab' RLIKE '[a'", "Invalid regular expression: '[a', missing ]: [a"},
            {"SELECT 'ab' RLIKE '['", "Invalid regular expression: '[', missing ]: ["},
            {"SELECT 'ab' RLIKE '[]'", "Invalid regular expression: '[]', missing ]: []"},
            {"SELECT 'ab' RLIKE '[^]'", "Invalid regular expression: '[^]', missing ]: [^]"},
            {"SELECT 'ab' RLIKE '[z-a]'", "Invalid regular expression: '[z-a]', invalid character class range: z-a"},
            {"SELECT 'ab' RLIKE '*'", "Invalid regular expression: '*', no argument for repetition operator: *"},
            {"SELECT 'ab' RLIKE '+'", "Invalid regular expression: '+', no argument for repetition operator: +"},
            {"SELECT 'ab' RLIKE '?'", "Invalid regular expression: '?', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE 'a{2,1}'", "Invalid regular expression: 'a{2,1}', invalid repetition size: {2,1}"},
            {"SELECT 'ab' RLIKE 'a{1001}'", "Invalid regular expression: 'a{1001}', invalid repetition size: {1001}"},
            {"SELECT 'ab' RLIKE '{2}'", "Invalid regular expression: '{2}', no argument for repetition operator: {2}"},
            {"SELECT 'ab' RLIKE 'a|*'", "Invalid regular expression: 'a|*', no argument for repetition operator: *"},
            {"SELECT 'ab' RLIKE '(?<x>a)b'", "Invalid regular expression: '(?<x>a)b', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?P<x>a)b'", "Invalid regular expression: '(?P<x>a)b', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?P<x>a)(?P<x>b)'", "Invalid regular expression: '(?P<x>a)(?P<x>b)', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?P<1>a)'", "Invalid regular expression: '(?P<1>a)', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?=a)'", "Invalid regular expression: '(?=a)', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?!a)'", "Invalid regular expression: '(?!a)', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?<=a)b'", "Invalid regular expression: '(?<=a)b', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?<!a)b'", "Invalid regular expression: '(?<!a)b', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?:a)'", "Invalid regular expression: '(?:a)', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?:a'", "Invalid regular expression: '(?:a', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?i)a'", "Invalid regular expression: '(?i)a', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?x) a'", "Invalid regular expression: '(?x) a', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?z)a'", "Invalid regular expression: '(?z)a', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?'", "Invalid regular expression: '(?', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '(?)'", "Invalid regular expression: '(?)', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE '\\\\p{L}'", "Invalid regular expression: '\\p{L}', invalid escape sequence: \\p"},
            {"SELECT 'ab' RLIKE '\\\\pL'", "Invalid regular expression: '\\pL', invalid escape sequence: \\p"},
            {"SELECT 'ab' RLIKE '\\\\p{Foo}'", "Invalid regular expression: '\\p{Foo}', invalid escape sequence: \\p"},
            {"SELECT 'ab' RLIKE '\\\\P{L}'", "Invalid regular expression: '\\P{L}', invalid escape sequence: \\P"},
            {"SELECT 'ab' RLIKE '\\\\1'", "Invalid regular expression: '\\1', invalid escape sequence: \\1"},
            {"SELECT 'ab' RLIKE '(a)\\\\1'", "Invalid regular expression: '(a)\\1', invalid escape sequence: \\1"},
            {"SELECT 'ab' RLIKE '\\\\k<x>'", "Invalid regular expression: '\\k<x>', invalid escape sequence: \\k"},
            {"SELECT 'ab' RLIKE '[[:foo:]]'", "Invalid regular expression: '[[:foo:]]', invalid character class range: [:foo:]"},
            {"SELECT 'ab' RLIKE '[[:alpha:]'", "Invalid regular expression: '[[:alpha:]', missing ]: [[:alpha:]"},
            {"SELECT 'ab' RLIKE '\\\\x{110000}'", "Invalid regular expression: '\\x{110000}', invalid escape sequence: \\x{110000"},
            {"SELECT 'ab' RLIKE '\\\\xZ'", "Invalid regular expression: '\\xZ', invalid escape sequence: \\xZ"},
            {"SELECT 'ab' RLIKE '\\\\Qa.b\\\\E'", "Invalid regular expression: '\\Qa.b\\E', invalid escape sequence: \\Q"},
            {"SELECT 'ab' RLIKE '\\\\h'", "Invalid regular expression: '\\h', invalid escape sequence: \\h"},
            {"SELECT 'ab' RLIKE '\\\\R'", "Invalid regular expression: '\\R', invalid escape sequence: \\R"},
            {"SELECT 'ab' RLIKE '\\\\Z'", "Invalid regular expression: '\\Z', invalid escape sequence: \\Z"},
            {"SELECT 'ab' RLIKE '\\\\z'", "Invalid regular expression: '\\z', invalid escape sequence: \\z"},
            {"SELECT 'ab' RLIKE '\\\\A'", "Invalid regular expression: '\\A', invalid escape sequence: \\A"},
            {"SELECT 'ab' RLIKE '\\\\G'", "Invalid regular expression: '\\G', invalid escape sequence: \\G"},
            {"SELECT 'ab' RLIKE '\\\\c'", "Invalid regular expression: '\\c', invalid escape sequence: \\c"},
            {"SELECT 'ab' RLIKE '\\\\cA'", "Invalid regular expression: '\\cA', invalid escape sequence: \\c"},
            {"SELECT 'ab' RLIKE '\\\\e'", "Invalid regular expression: '\\e', invalid escape sequence: \\e"},
            {"SELECT 'ab' RLIKE '\\\\N'", "Invalid regular expression: '\\N', invalid escape sequence: \\N"},
            {"SELECT 'ab' RLIKE '\\\\X'", "Invalid regular expression: '\\X', invalid escape sequence: \\X"},
            {"SELECT 'ab' RLIKE '\\\\y'", "Invalid regular expression: '\\y', invalid escape sequence: \\y"},
            {"SELECT 'ab' RLIKE '(*)'", "Invalid regular expression: '(*)', no argument for repetition operator: *"},
            {"SELECT 'ab' RLIKE '[\\\\d-z]'", "Invalid regular expression: '[\\d-z]', invalid character class range: -z"},
            {"SELECT 'ab' RLIKE '\\\\8'", "Invalid regular expression: '\\8', invalid escape sequence: \\8"},
            {"SELECT 'ab' RLIKE '(?i'", "Invalid regular expression: '(?i', no argument for repetition operator: ?"},
            {"SELECT 'ab' RLIKE 'a\\\\Eb'", "Invalid regular expression: 'a\\Eb', invalid escape sequence: \\E"},
        };
        for (final String[] cell : refused) {
            assertEquals(cell[1], refusal(cell[0]), cell[0]);
        }
    }

    @Test
    public void everyFunctionOfTheFamilyRefusesItTheSameWay() {
        final String[][] refused = {
            {"SELECT REGEXP_LIKE('ab', '(')", "Invalid regular expression: '(', missing ): ("},
            {"SELECT REGEXP_COUNT('ab', '(')", "Invalid regular expression: '(', missing ): ("},
            {"SELECT REGEXP_SUBSTR('ab', '(')", "Invalid regular expression: '(', missing ): ("},
            {"SELECT REGEXP_REPLACE('ab', '(', 'x')", "Invalid regular expression: '(', missing ): ("},
            {"SELECT REGEXP_INSTR('ab', '(')", "Invalid regular expression: '(', missing ): ("},
            {"SELECT REGEXP_SUBSTR_ALL('ab', '(')", "Invalid regular expression: '(', missing ): ("},
            {"SELECT 'ab' REGEXP '('", "Invalid regular expression: '(', missing ): ("},
            {"SELECT REGEXP_LIKE('ab', 'a{2,1}')", "Invalid regular expression: 'a{2,1}', invalid repetition size: {2,1}"},
            {"SELECT REGEXP_COUNT('ab', 'a{2,1}')", "Invalid regular expression: 'a{2,1}', invalid repetition size: {2,1}"},
            {"SELECT REGEXP_SUBSTR('ab', 'a{2,1}')", "Invalid regular expression: 'a{2,1}', invalid repetition size: {2,1}"},
            {"SELECT REGEXP_REPLACE('ab', 'a{2,1}', 'x')", "Invalid regular expression: 'a{2,1}', invalid repetition size: {2,1}"},
            {"SELECT REGEXP_INSTR('ab', 'a{2,1}')", "Invalid regular expression: 'a{2,1}', invalid repetition size: {2,1}"},
            {"SELECT REGEXP_SUBSTR_ALL('ab', 'a{2,1}')", "Invalid regular expression: 'a{2,1}', invalid repetition size: {2,1}"},
            {"SELECT 'ab' REGEXP 'a{2,1}'", "Invalid regular expression: 'a{2,1}', invalid repetition size: {2,1}"},
            {"SELECT REGEXP_LIKE('ab', '(?<x>a)b')", "Invalid regular expression: '(?<x>a)b', no argument for repetition operator: ?"},
            {"SELECT REGEXP_COUNT('ab', '(?<x>a)b')", "Invalid regular expression: '(?<x>a)b', no argument for repetition operator: ?"},
            {"SELECT REGEXP_SUBSTR('ab', '(?<x>a)b')", "Invalid regular expression: '(?<x>a)b', no argument for repetition operator: ?"},
            {"SELECT REGEXP_REPLACE('ab', '(?<x>a)b', 'x')", "Invalid regular expression: '(?<x>a)b', no argument for repetition operator: ?"},
            {"SELECT REGEXP_INSTR('ab', '(?<x>a)b')", "Invalid regular expression: '(?<x>a)b', no argument for repetition operator: ?"},
            {"SELECT REGEXP_SUBSTR_ALL('ab', '(?<x>a)b')", "Invalid regular expression: '(?<x>a)b', no argument for repetition operator: ?"},
            {"SELECT 'ab' REGEXP '(?<x>a)b'", "Invalid regular expression: '(?<x>a)b', no argument for repetition operator: ?"},
        };
        for (final String[] cell : refused) {
            assertEquals(cell[1], refusal(cell[0]), cell[0]);
        }
    }

    @Test
    public void whatTheAccountReadsIsRead() {
        final String[] read = {
            "SELECT 'ab' RLIKE 'a**'",
            "SELECT 'ab' RLIKE 'a++'",
            "SELECT 'ab' RLIKE 'a?+'",
            "SELECT 'ab' RLIKE 'a*+'",
            "SELECT 'ab' RLIKE 'a*?'",
            "SELECT 'ab' RLIKE 'a+?'",
            "SELECT 'ab' RLIKE 'a??'",
            "SELECT 'ab' RLIKE 'a{1000}'",
            "SELECT 'ab' RLIKE 'a{,2}'",
            "SELECT 'ab' RLIKE 'a{2'",
            "SELECT 'ab' RLIKE 'a{x}'",
            "SELECT 'ab' RLIKE 'x{2}{3}'",
            "SELECT 'ab' RLIKE '|'",
            "SELECT 'ab' RLIKE 'a||b'",
            "SELECT 'ab' RLIKE '[[:alpha:]]'",
            "SELECT 'ab' RLIKE '\\\\x{41}'",
            "SELECT 'ab' RLIKE '\\\\x41'",
            "SELECT 'ab' RLIKE '\\\\d'",
            "SELECT 'ab' RLIKE '\\\\b'",
            "SELECT 'ab' RLIKE '\\\\B'",
            "SELECT 'ab' RLIKE '\\\\a'",
            "SELECT 'ab' RLIKE '\\\\_'",
            "SELECT 'ab' RLIKE '\\\\-'",
            "SELECT 'ab' RLIKE 'a{2}{'",
            "SELECT 'ab' RLIKE '(|a)'",
            "SELECT 'ab' RLIKE '[a-]'",
            "SELECT 'ab' RLIKE '\\\\0'",
            "SELECT 'ab' RLIKE '\\\\07'",
            "SELECT 'ab' RLIKE '$^'",
            "SELECT 'ab' RLIKE '^*'",
            "SELECT 'ab' RLIKE '$+'",
        };
        for (final String sql : read) {
            answer(sql);
        }
        // A repetition of a repetition, a brace that opens no count, NUL and the vertical tab, POSIX classes.
        assertEquals("aaa|aaab|true",
            answer("SELECT REGEXP_SUBSTR('aaab', 'a**'), REGEXP_SUBSTR('aaab', 'a+*b'), 'a{,2}' RLIKE 'a{,2}'"));
        assertEquals("1|1",
            answer("SELECT REGEXP_COUNT(CHR(0) || CHR(11), '\\\\0'), REGEXP_COUNT(CHR(0) || CHR(11), '\\\\v')"));
        assertEquals("xx1 _-|aBxxxx|xxxxx-|xx1xxx",
            answer("SELECT REGEXP_REPLACE('aB1 _-', '[[:alpha:]]', 'x'), REGEXP_REPLACE('aB1 _-', '[[:^alpha:]]', 'x'),"
                + " REGEXP_REPLACE('aB1 _-', '[[:word:][:space:]]', 'x'), REGEXP_REPLACE('aB1 _-', '[^[:digit:]]', 'x')"));
        assertEquals("xxbxc[d^e|a-b]cxdxe|xxxx",
            answer("SELECT REGEXP_REPLACE('a-b]c[d^e', '[]a-]', 'x'), REGEXP_REPLACE('a-b]c[d^e', '[[^]', 'x'),"
                + " REGEXP_REPLACE('a&&b', '[a&&b]', 'x')"));
    }

    @Test
    public void aPatternIsJudgedOnlyWhenARowReachesIt() {
        engine.execute("CREATE OR REPLACE TABLE rx (s VARCHAR, p VARCHAR)");
        engine.execute("INSERT INTO rx VALUES ('ab', '('), ('ab', 'a')");
        assertEquals("Invalid regular expression: '(', missing ): (", refusal("SELECT s RLIKE p FROM rx ORDER BY p"));
        assertEquals("false", answer("SELECT s RLIKE p FROM rx WHERE p = 'a'"));
        assertEquals("null", answer("SELECT NULL RLIKE '('"));
        assertTrue(!engine.executeQuery("SELECT s RLIKE '(' FROM rx WHERE 1 = 0").next());
    }

    @Test
    public void thePositionOccurrenceGroupAndParameterArgumentsHaveTheirRanges() {
        final String[][] refused = {
            {"SELECT REGEXP_SUBSTR('ab', 'a', 0)", "Invalid parameter value: 0. Reason: Position must be positive"},
            {"SELECT REGEXP_SUBSTR('ab', 'a', -1)", "Invalid parameter value: -1. Reason: Position must be positive"},
            {"SELECT REGEXP_SUBSTR('ab', 'a', 1, 0)", "Invalid parameter value: 0. Reason: Occurrence must be positive"},
            {"SELECT REGEXP_SUBSTR('ab', '(a)', 1, 1, 'e', -1)", "Invalid parameter value: -1. Reason: Capture group must be non-negative"},
            {"SELECT REGEXP_INSTR('ab', 'a', 0)", "Invalid parameter value: 0. Reason: Position must be positive"},
            {"SELECT REGEXP_INSTR('ab', 'a', 1, 0)", "Invalid parameter value: 0. Reason: Occurrence must be positive"},
            {"SELECT REGEXP_INSTR('ab', 'a', 1, 1, 2)", "Invalid parameter value: 2. Reason: Return option must be 0, 1, or NULL"},
            {"SELECT REGEXP_INSTR('ab', 'a', 1, 1, -1)", "Invalid parameter value: -1. Reason: Return option must be 0, 1, or NULL"},
            {"SELECT REGEXP_COUNT('ab', 'a', 0)", "Invalid parameter value: 0. Reason: Position must be positive"},
            {"SELECT REGEXP_REPLACE('ab', 'a', 'x', 0)", "Invalid parameter value: 0. Reason: Position must be positive"},
            {"SELECT REGEXP_REPLACE('ab', 'a', 'x', 1, -1)", "Invalid parameter value: -1. Reason: Occurrence must not be negative"},
            {"SELECT REGEXP_SUBSTR_ALL('ab', 'a', 0)", "Invalid parameter value: 0. Reason: Position must be positive"},
            {"SELECT REGEXP_SUBSTR_ALL('ab', 'a', 1, 0)", "Invalid parameter value: 0. Reason: Occurrence must be positive"},
            {"SELECT REGEXP_LIKE('ab', 'a', 'zi')", "Invalid parameter value: zi. Reason: Unknown parameter: 'z'"},
            {"SELECT REGEXP_LIKE('ab', 'a', 'iz')", "Invalid parameter value: iz. Reason: Unknown parameter: 'z'"},
            {"SELECT REGEXP_LIKE('AB', 'ab', 'I')", "Invalid parameter value: I. Reason: Unknown parameter: 'I'"},
            {"SELECT REGEXP_COUNT('ab', 'a', 1, 'x')", "Invalid parameter value: x. Reason: Unknown parameter: 'x'"},
            {"SELECT REGEXP_SUBSTR('ab', 'a', 1, 1, ' i')", "Invalid parameter value:  i. Reason: Unknown parameter: ' '"},
        };
        for (final String[] cell : refused) {
            assertEquals(cell[1], refusal(cell[0]), cell[0]);
        }
        assertEquals("null", answer("SELECT REGEXP_SUBSTR('ab', '(a)', 1, 1, 'e', 5)"));
        // The later of c and i decides.
        assertEquals("false|true|true",
            answer("SELECT REGEXP_LIKE('AB', 'ab', 'ic'), REGEXP_LIKE('AB', 'ab', 'ci'), REGEXP_LIKE('ab', 'ab', '')"));
    }

    @Test
    public void aReplacementRefersOnlyToGroupsThePatternHas() {
        assertEquals("Replace string could not be rewritten, pattern: '(a)', replace string: '\\2', reason: invalid"
            + " replace group reference in replace string", refusal("SELECT REGEXP_REPLACE('ab', '(a)', '\\\\2')"));
        assertEquals("zz", answer("SELECT REGEXP_REPLACE('zz', '(a)', '\\\\2')"));
        assertEquals("[a]b|[a]b",
            answer("SELECT REGEXP_REPLACE('ab', '(a)', '[\\\\0]'), REGEXP_REPLACE('ab', '(a)', '[\\\\1]')"));
    }
}
