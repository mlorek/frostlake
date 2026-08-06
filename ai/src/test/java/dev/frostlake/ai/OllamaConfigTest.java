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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the pack finds its model server: {@code frostlake.properties}, overridable per key by a system
 * property of the same name so a test or a one-off run points elsewhere without editing a file.
 */
public class OllamaConfigTest {

    @AfterEach
    public void clearOverrides() {
        System.clearProperty(OllamaConfig.URL);
        System.clearProperty(OllamaConfig.MODEL);
        System.clearProperty(OllamaConfig.EMBED_MODEL);
        System.clearProperty(OllamaConfig.TIMEOUT_MS);
    }

    @Test
    public void defaultsPointAtALocalServer() {
        assertEquals("http://localhost:11434", OllamaConfig.url());
        assertEquals("llama3.2", OllamaConfig.model());
        assertEquals("nomic-embed-text", OllamaConfig.embedModel());
        assertEquals(60000, OllamaConfig.timeoutMs());
    }

    @Test
    public void aSystemPropertyWins() {
        System.setProperty(OllamaConfig.URL, "http://elsewhere:9999");
        System.setProperty(OllamaConfig.MODEL, "some-model");
        System.setProperty(OllamaConfig.EMBED_MODEL, "some-embed-model");
        System.setProperty(OllamaConfig.TIMEOUT_MS, "1234");
        assertEquals("http://elsewhere:9999", OllamaConfig.url());
        assertEquals("some-model", OllamaConfig.model());
        assertEquals("some-embed-model", OllamaConfig.embedModel());
        assertEquals(1234, OllamaConfig.timeoutMs());
    }

    /**
     * The file is looked for where the engine looks for it, home directory included — the location that
     * matters when the engine is embedded in a SQL client whose working directory is its own install
     * directory. Asserted by reading whichever file is actually present, so the test neither writes to
     * the developer's home nor depends on one being there.
     */
    @Test
    public void theConfigFileIsLookedForWhereTheEngineLooks() {
        final Path home = Path.of(System.getProperty("user.home"), ".frostlake", "frostlake.properties");
        final Path working = Path.of("frostlake.properties");
        if (!Files.isReadable(home) || Files.isReadable(working)) {
            // Nothing to assert against on this machine: the default already covers it.
            assertEquals("llama3.2", OllamaConfig.model());
            return;
        }
        final Properties fromHome = new Properties();
        try (InputStream in = Files.newInputStream(home)) {
            fromHome.load(in);
        } catch (final IOException unreadable) {
            return;
        }
        final String configured = fromHome.getProperty(OllamaConfig.MODEL);
        assertEquals(configured != null ? configured : "llama3.2", OllamaConfig.model());
    }

    /** A trailing slash would double up against the path, so it is trimmed. */
    @Test
    public void trailingSlashIsTrimmedFromTheUrl() {
        System.setProperty(OllamaConfig.URL, "http://elsewhere:9999/");
        assertEquals("http://elsewhere:9999", OllamaConfig.url());
    }

    /** An unparseable timeout falls back rather than failing every call that reads it. */
    @Test
    public void anUnparseableTimeoutFallsBack() {
        System.setProperty(OllamaConfig.TIMEOUT_MS, "soon");
        assertEquals(60000, OllamaConfig.timeoutMs());
    }

    // ── The reply readers ─────────────────────────────────────────────────────────

    @Test
    public void stripRemovesQuotesWhitespaceAndAFullStop() {
        assertEquals("blue", CortexText.strip("  blue.  "));
        assertEquals("blue", CortexText.strip("\"blue\""));
        assertEquals("blue", CortexText.strip("'blue'"));
        assertEquals("blue", CortexText.strip("\"blue.\""));
        assertEquals("", CortexText.strip(null));
    }

    /** A reasoning model opens with its thinking; the answer is what follows. */
    @Test
    public void stripDropsALeadingThinkingBlock() {
        assertEquals("blue", CortexText.strip("<think>weighing it up</think>\nblue"));
        assertEquals("<think>unclosed and so kept",
            CortexText.strip("<think>unclosed and so kept"));
    }

    @Test
    public void firstNumberReadsTheNumberOutOfAChattyReply() {
        assertEquals(0.9, CortexText.firstNumber("0.9", 0.0), 0.0001);
        assertEquals(-0.75, CortexText.firstNumber("-0.75", 0.0), 0.0001);
        assertEquals(1.0, CortexText.firstNumber("1.0 (very positive)", 0.0), 0.0001);
        assertEquals(0.0, CortexText.firstNumber("no number here", 0.0), 0.0001);
        assertEquals(0.0, CortexText.firstNumber(null, 0.0), 0.0001);
    }

    // ── The category-list argument, in whichever shape it arrives ─────────────────

    @Test
    public void categoriesAcceptAListOrJsonText() {
        final List<String> fromList = CortexCategories.of(Arrays.asList("a", "b"));
        assertEquals(Arrays.asList("a", "b"), fromList);
        assertEquals(Arrays.asList("a", "b"), CortexCategories.of("[\"a\",\"b\"]"));
        assertTrue(CortexCategories.of(null).isEmpty());
    }

    @Test
    public void cosineSimilarityIsOneForIdenticalVectors() {
        final List<Double> vector = Arrays.asList(
            Double.valueOf(1.0), Double.valueOf(2.0), Double.valueOf(3.0));
        assertEquals(1.0, CosineSimilarity.of(vector, vector), 0.0001);
        assertEquals(0.0, CosineSimilarity.of(vector,
            Arrays.asList(Double.valueOf(0.0), Double.valueOf(0.0), Double.valueOf(0.0))), 0.0001);
        assertEquals(-1.0, CosineSimilarity.of(vector,
            Arrays.asList(Double.valueOf(-1.0), Double.valueOf(-2.0), Double.valueOf(-3.0))), 0.0001);
    }
}
