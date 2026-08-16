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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A submission carrying no statement at all is refused with ONE sentence, whatever the reason it is
 * empty — a lone semicolon, several of them, whitespace, a comment, or a comment and a semicolon
 * together: {@code SQL compilation error: Empty SQL statement.} (live-verified; the account answers
 * every one of these with error 900 / SQLSTATE 42601, not with a syntax error at the stray token).
 *
 * <p>The check reads the LEXER, not the text, so a semicolon inside a string literal or a comment
 * never counts as a statement — only a real token does.
 */
public class EmptySqlStatementTest extends BaseDatabaseTest {

    private static final String EMPTY = "SQL compilation error:\nEmpty SQL statement.";

    private void assertEmptyRefusal(final String sql) {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, "[" + sql + "]");
        assertEquals(EMPTY, ex.getMessage(), "[" + sql + "]");
    }

    @Test
    public void aLoneSemicolonIsAnEmptyStatement() {
        assertEmptyRefusal(";");
    }

    @Test
    public void severalSemicolonsAreStillEmpty() {
        assertEmptyRefusal(";;");
    }

    @Test
    public void whitespaceOnlyIsEmpty() {
        assertEmptyRefusal("   ");
    }

    @Test
    public void newlinesOnlyAreEmpty() {
        assertEmptyRefusal("\n\n");
    }

    @Test
    public void aLineCommentOnlyIsEmpty() {
        assertEmptyRefusal("-- nothing here");
    }

    @Test
    public void aBlockCommentOnlyIsEmpty() {
        assertEmptyRefusal("/* nothing */");
    }

    @Test
    public void aCommentFollowedByASemicolonIsEmpty() {
        assertEmptyRefusal("-- c\n;");
    }

    @Test
    public void aSemicolonFollowedByACommentIsEmpty() {
        assertEmptyRefusal("; -- c");
    }

    @Test
    public void whitespaceAroundASemicolonIsEmpty() {
        assertEmptyRefusal("  ;  ");
    }

    /**
     * The empty string is the same refusal on the ENGINE. It cannot be observed on the account
     * through JDBC: the driver refuses to send it client-side ("Invalid SQL text:", error 200021 /
     * SQLSTATE 03000), so the server sentence never comes back. Asserted embedded only, against the
     * rule the other nine cells measure.
     */
    @Test
    public void theEmptyStringIsEmptyToo() {
        Assumptions.assumeFalse(isLiveSnowflake(),
            "the Snowflake JDBC driver refuses an empty statement client-side, so the account's own "
            + "sentence is not observable through it");
        assertEmptyRefusal("");
    }

    /** A statement is a statement even when semicolons crowd it. */
    @Test
    public void aRealStatementIsNotEmpty() {
        assertEquals("1", engine.executeQuery("SELECT 1;;").getRows().get(0).getValue(0).toString());
    }

    /** A semicolon inside a literal is text, not a separator — the statement is real. */
    @Test
    public void aSemicolonInsideALiteralDoesNotMakeItEmpty() {
        assertEquals(";", engine.executeQuery("SELECT ';'").getRows().get(0).getValue(0).toString());
    }
}
