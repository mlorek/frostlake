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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code --} sequence inside a single-quoted string literal is part of the string, not the start of a
 * line comment. A prior quote-blind regex pre-strip truncated any literal containing {@code --}; the
 * ANTLR lexer now handles comments (respecting string literals).
 */
public class StringLiteralDashTest extends BaseDatabaseTest {

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void dashesInsideStringLiteralNoFrom() {
        assertEquals("a--b", scalar("SELECT 'a--b'"));
        assertEquals("--lead", scalar("SELECT '--lead'"));
        assertEquals("trail--", scalar("SELECT 'trail--'"));
    }

    @Test
    public void splitPartOnDoubleDashDelimiter() {
        assertEquals("bbb-BBB", scalar("SELECT SPLIT_PART('aaa--bbb-BBB--ccc', '--', 2)"));
    }

    @Test
    public void concatenatedDashes() {
        assertEquals("x--y", scalar("SELECT 'x' || '--' || 'y'"));
    }

    @Test
    public void dashesInStringFromTablePath() {
        engine.execute("CREATE TABLE t (s VARCHAR)");
        engine.execute("INSERT INTO t VALUES ('a--b')");
        assertEquals("a--b", scalar("SELECT s FROM t"));
        assertEquals("bbb", scalar("SELECT SPLIT_PART(s, '--', 2) FROM (SELECT 'aaa--bbb' AS s) x"));
    }

    @Test
    public void trailingLineCommentStillStripped() {
        // A genuine -- comment OUTSIDE a string is still ignored.
        assertEquals("3", scalar("SELECT 1 + 2 -- this is a comment"));
    }
}
