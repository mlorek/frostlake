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

package dev.frostlake.ai;

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SPLIT_TEXT_RECURSIVE_CHARACTER and SPLIT_TEXT_MARKDOWN_HEADER — the two Cortex functions that never
 * contact a model, so every value below is exact and asserted as such.
 */
public class SplitTextTest extends BaseDatabaseTest {

    /** Four ten-character runs separated by spaces: 43 characters in all. */
    private static final String SAMPLE = "abcdefghij klmnopqrst uvwxyz0123 456789ABCD";

    private String text(final String sql) {
        final Object value = engine.executeQuery(sql).getRows().get(0).getValue(0);
        return value == null ? null : value.toString();
    }

    private String chunk(final String args) {
        return text("SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_RECURSIVE_CHARACTER(" + args + ")");
    }

    private String markdown(final String args) {
        return text("SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_MARKDOWN_HEADER(" + args + ")");
    }

    // ── The recursive character chunker ───────────────────────────────────────────

    @Test
    public void chunksAtTheSizeBound() {
        assertEquals("[\"abcdefghij\",\"klmnopqrs\",\"t uvwxyz01\",\"23 456789A\",\"BCD\"]",
            chunk("'" + SAMPLE + "','none',10,0"));
    }

    /** No chunk may exceed the bound, whatever the text. */
    @Test
    public void noChunkExceedsTheBound() {
        final String chunked = chunk("'" + SAMPLE + "','none',10,0");
        for (final String piece : chunked.replace("[", "").replace("]", "").split("\",\"")) {
            assertTrue(piece.replace("\"", "").length() <= 10, "over the bound: " + piece);
        }
    }

    /** The overlap is characters of the previous chunk repeated at the front of the next. */
    @Test
    public void overlapCarriesTheTailForward() {
        assertEquals(
            "[\"abcdefghij\",\"hij klmnop\",\"nopqrst uv\",\"uvwxyz012\",\"0123 45678\",\"6789ABCD\"]",
            chunk("'" + SAMPLE + "','none',10,3"));
    }

    /** An omitted overlap is zero. */
    @Test
    public void overlapDefaultsToZero() {
        assertEquals(chunk("'" + SAMPLE + "','none',10,0"), chunk("'" + SAMPLE + "','none',10"));
    }

    /** A text that already fits comes back whole, in a one-element array. */
    @Test
    public void textUnderTheBoundIsOneChunk() {
        assertEquals("[\"" + SAMPLE + "\"]", chunk("'" + SAMPLE + "','none',100,0"));
    }

    /** Markdown prefers a heading boundary to a line break, so a heading opens its chunk. */
    @Test
    public void markdownSplitsAtHeadings() {
        final String chunked = chunk("'# H1\\n\\npara one here.\\n\\n## H2\\n\\npara two here.','markdown',30,0");
        assertTrue(chunked.contains("# H1"), chunked);
        assertTrue(chunked.contains("## H2"), chunked);
    }

    /** An explicit separator array overrides the format's ladder entirely. */
    @Test
    public void explicitSeparatorsWin() {
        assertEquals("[\"a|b\",\"|c\",\"|d\",\"|e\"]",
            chunk("'a|b|c|d|e','none',3,0,ARRAY_CONSTRUCT('|')"));
    }

    @Test
    public void emptyTextChunksToAnEmptyArray() {
        assertEquals("[]", chunk("'','none',10,0"));
    }

    @Test
    public void nullArgumentsAnswerNull() {
        assertNull(engine.executeQuery(
            "SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_RECURSIVE_CHARACTER(NULL,'none',10,0)")
            .getRows().get(0).getValue(0));
        assertNull(engine.executeQuery(
            "SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_RECURSIVE_CHARACTER('abc',NULL,10,0)")
            .getRows().get(0).getValue(0));
        assertNull(engine.executeQuery(
            "SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_RECURSIVE_CHARACTER('abc','none',NULL)")
            .getRows().get(0).getValue(0));
    }

    /** The value is a real ARRAY, not a string that looks like one. */
    @Test
    public void theResultIsAnArray() {
        assertEquals("ARRAY", text(
            "SELECT TYPEOF(SNOWFLAKE.CORTEX.SPLIT_TEXT_RECURSIVE_CHARACTER('abc','none',10,0)::VARIANT)"));
        assertEquals("abcdefghij",
            text("SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_RECURSIVE_CHARACTER('"
                + SAMPLE + "','none',10,0)[0]"));
    }

    @Test
    public void anUnmodelledFormatIsRefused() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_RECURSIVE_CHARACTER('abc','nosuch',10,0)");
            }
        });
        assertTrue(error.getMessage().contains("Unsupported format"), error.getMessage());
    }

    @Test
    public void chunkSizeIsRequired() {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(
                    "SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_RECURSIVE_CHARACTER('abc','none')");
            }
        });
        assertTrue(error.getMessage().contains("expected 3, got 2"), error.getMessage());
    }

    // ── The markdown-header chunker ───────────────────────────────────────────────

    /**
     * Each element carries its text under {@code chunk} and the headings in force where it starts
     * under a NESTED {@code headers} object — measured, and not the flattened shape this once assumed.
     */
    @Test
    public void headingsBecomeElementMetadata() {
        assertEquals(
            "[{\"chunk\":\"alpha text.\",\"headers\":{\"h1\":\"Title\"}},"
            + "{\"chunk\":\"beta text.\",\"headers\":{\"h1\":\"Title\",\"h2\":\"Sub\"}}]",
            markdown("'# Title\\nalpha text.\\n## Sub\\nbeta text.',"
                + " OBJECT_CONSTRUCT('#','h1','##','h2'), 100, 0"));
    }

    /** A section over the bound is cut further, and every piece repeats the same headings. */
    @Test
    public void anOversizedSectionIsCutAndKeepsItsHeadings() {
        assertEquals(
            "[{\"chunk\":\"alpha text is quite\",\"headers\":{\"h1\":\"Title\"}},"
            + "{\"chunk\":\"long indeed here.\",\"headers\":{\"h1\":\"Title\"}},"
            + "{\"chunk\":\"beta.\",\"headers\":{\"h1\":\"Title\",\"h2\":\"Sub\"}}]",
            markdown("'# Title\\nalpha text is quite long indeed here.\\n## Sub\\nbeta.',"
                + " OBJECT_CONSTRUCT('#','h1','##','h2'), 20, 0"));
    }

    /** A heading closes every deeper one: a new h1 drops the h2 that was in force. */
    @Test
    public void aShallowerHeadingDropsTheDeeperOnes() {
        assertEquals(
            "[{\"chunk\":\"a\",\"headers\":{\"h1\":\"One\",\"h2\":\"Sub\"}},"
            + "{\"chunk\":\"b\",\"headers\":{\"h1\":\"Two\"}}]",
            markdown("'# One\\n## Sub\\na\\n# Two\\nb', OBJECT_CONSTRUCT('#','h1','##','h2'), 100, 0"));
    }

    /** Text ahead of the first heading is a section of its own, carrying an EMPTY headers object. */
    @Test
    public void aPreambleIsItsOwnElement() {
        assertEquals("[{\"chunk\":\"preamble.\",\"headers\":{}},"
            + "{\"chunk\":\"body.\",\"headers\":{\"h1\":\"Title\"}}]",
            markdown("'preamble.\\n# Title\\nbody.', OBJECT_CONSTRUCT('#','h1'), 100, 0"));
    }

    @Test
    public void markdownHeaderNullsAnswerNull() {
        assertNull(engine.executeQuery(
            "SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_MARKDOWN_HEADER(NULL, OBJECT_CONSTRUCT('#','h1'), 100, 0)")
            .getRows().get(0).getValue(0));
        assertNull(engine.executeQuery(
            "SELECT SNOWFLAKE.CORTEX.SPLIT_TEXT_MARKDOWN_HEADER('# a', NULL, 100, 0)")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void markdownHeaderOverlapDefaultsToZero() {
        assertEquals(markdown("'# T\\nbody.', OBJECT_CONSTRUCT('#','h1'), 100, 0"),
            markdown("'# T\\nbody.', OBJECT_CONSTRUCT('#','h1'), 100"));
    }

    @Test
    public void markdownHeaderResultIsAnArray() {
        assertEquals("ARRAY", text("SELECT TYPEOF(SNOWFLAKE.CORTEX.SPLIT_TEXT_MARKDOWN_HEADER("
            + "'# T\\nbody.', OBJECT_CONSTRUCT('#','h1'), 100, 0)::VARIANT)"));
    }

    // ── Both are qualified-only, like the rest of the original set ────────────────

    @Test
    public void neitherExistsBare() {
        for (final String sql : new String[] {
                "SELECT SPLIT_TEXT_RECURSIVE_CHARACTER('abc','none',10,0)",
                "SELECT SPLIT_TEXT_MARKDOWN_HEADER('# a', OBJECT_CONSTRUCT('#','h1'), 10, 0)" }) {
            final String failed = failureOf(sql);
            assertTrue(failed != null && failed.contains("Unknown function"), sql + " -> " + failed);
        }
    }

    private String failureOf(final String sql) {
        try {
            engine.executeQuery(sql);
            return null;
        } catch (final RuntimeException failed) {
            return failed.getMessage();
        }
    }
}
