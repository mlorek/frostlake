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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A statement that BEGINS with comments reports its positions from the first real token. Frostlake
 * counted the comment like any other text, so a refusal moved whenever someone put a header above
 * their SQL — and vendor SQL is full of headers.
 *
 * <p>The rule is a RE-BASING, not a stripping, and the last two cases are what prove it: a leading
 * comment shifts every later offset back by its width, while a comment BETWEEN tokens is ordinary text
 * that pushes offsets along. Whitespace alone never re-bases anything.
 */
public class LeadingCommentPositionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE ss (o OBJECT, s VARCHAR(10))");
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "accepted";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', ' ');
        }
    }

    private void assertPositioned(final String sql, final int line, final int position) {
        final String got = refusal(sql);
        assertTrue(got.contains("line " + line + " at position " + position),
            "[" + sql.replace("\n", "\\n") + "] expected line " + line + " position " + position
                + ", got: " + got);
    }

    /** Without a comment, leading WHITESPACE counts toward the position. */
    @Test
    public void whitespaceAloneStillCounts() {
        assertPositioned("NULL", 1, 0);
        assertPositioned("   NULL", 1, 3);
    }

    /** A leading comment does not: the first real token becomes line 1, position 0. */
    @Test
    public void aLeadingCommentReBasesThePosition() {
        assertPositioned("/* c */ NULL", 1, 0);
        assertPositioned("   /* c */   NULL", 1, 0);
        assertPositioned("/* a */ /* b */ NULL", 1, 0);
        assertPositioned("-- c\nNULL", 1, 0);
        assertPositioned("  -- c\n  NULL", 1, 0);
    }

    /** Newlines inside or after the comment do not advance the LINE either. */
    @Test
    public void aCommentsNewlinesDoNotAdvanceTheLine() {
        assertPositioned("/* c */\nNULL", 1, 0);
        assertPositioned("/* a\nb */ NULL", 1, 0);
    }

    /** The offset SHIFTS rather than zeroing — a later token keeps its distance from the first. */
    @Test
    public void laterOffsetsShiftByTheSameAmount() {
        assertPositioned("SELECT UPPER(o) FROM ss", 1, 7);
        assertPositioned("/* c */ SELECT UPPER(o) FROM ss", 1, 7);
        assertPositioned("-- c\nSELECT UPPER(o) FROM ss", 1, 7);
        assertPositioned("/* c */\nSELECT UPPER(o) FROM ss", 1, 7);
    }

    /** And a comment BETWEEN tokens is ordinary text that pushes the offset along. */
    @Test
    public void anInteriorCommentCountsNormally() {
        assertPositioned("SELECT /* c */ UPPER(o) FROM ss", 1, 15);
    }
}
