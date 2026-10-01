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

package dev.frostlake.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A call in the FROM form no name accepts is refused at its FROM; when the call stands in a select item of a
 * statement's own query, live's recovery reads that FROM as the query's FROM clause and refuses the first fault the
 * reading meets as well (live-verified).
 */
public class AnsiFromClauseReadingTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        }).getMessage();
    }

    private static String lines(final String... lines) {
        return "SQL compilation error:\n" + String.join("\n", lines);
    }

    private static String at(final int line, final int position, final String token) {
        return "syntax error line " + line + " at position " + position + " unexpected '" + token + "'.";
    }

    @Test
    public void inASelectItemTheFromIsReadAsTheQuerysFromClause() {
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) FROM wt0"));
        assertEquals(lines(at(1, 17, "FROM"), at(1, 23, ")")), refusal("SELECT EXTRACT(1 FROM d) FROM wt0"));
        assertEquals(lines(at(1, 23, "FROM"), at(1, 29, ")")), refusal("SELECT DATE_PART('wks' FROM d) FROM wt0"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d)"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d), 1 FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 28, "+")), refusal("SELECT EXTRACT('wks' FROM d + 1) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) + 1 FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 24, "2")), refusal("SELECT SUBSTRING(b FROM 2) FROM t"));
        assertEquals(lines(at(1, 16, "FROM"), at(1, 22, ")")), refusal("SELECT TRIM(' ' FROM b) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) AS x FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 25, ")")), refusal("SELECT EXTRACT(1.5 FROM d) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 32, "1")), refusal("SELECT EXTRACT('wks' FROM d FOR 1) FROM t"));
        assertEquals(lines(at(1, 22, "FROM"), at(1, 28, ")")), refusal("SELECT (EXTRACT('wks' FROM d)) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d) FROM t WHERE TRUE"));
        assertEquals(lines(at(1, 23, "FROM"), at(1, 28, "1")), refusal("SELECT SUBSTRING('abc' FROM 1) FROM t"));
        assertEquals(lines(at(1, 24, "FROM"), at(1, 30, ")")), refusal("SELECT EXTRACT(b || 'x' FROM d) FROM t"));
        assertEquals(lines(at(1, 19, "FROM"), at(1, 25, ")")), refusal("SELECT DATE_PART(1 FROM d) FROM t"));
        assertEquals(lines(at(1, 17, "FROM"), at(1, 23, ")")), refusal("SELECT FOO('wks' FROM d) FROM t"));
        assertEquals(lines(at(1, 24, "FROM"), at(1, 30, ")")), refusal("SELECT a, EXTRACT('wks' FROM d) FROM t"));
        assertEquals(lines(at(1, 18, "FROM"), at(1, 24, ")")), refusal("SELECT EXTRACT(:x FROM d) FROM t"));
        assertEquals(lines(at(1, 26, "FROM"), at(1, 32, ")")), refusal("SELECT CAST(EXTRACT('wks' FROM d) AS INT) FROM t"));
        assertEquals(lines(at(1, 21, "FROM"), at(1, 27, ")")), refusal("SELECT EXTRACT('wks' FROM d)::INT FROM t"));
    }

    @Test
    public void elsewhereTheFromIsTheOnlyLine() {
        assertEquals(lines(at(1, 27, "FROM")), refusal("SELECT UPPER(EXTRACT('wks' FROM d)) FROM wt0"));
        assertEquals(lines(at(1, 36, "FROM")), refusal("SELECT 1 FROM t WHERE EXTRACT('wks' FROM d) = 1"));
        assertEquals(lines(at(1, 27, "FROM")), refusal("SELECT UPPER(EXTRACT('wks' FROM d))"));
        assertEquals(lines(at(1, 39, "FROM")), refusal("SELECT 1 FROM t ORDER BY EXTRACT('wks' FROM d)"));
    }
}
