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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Where the AI pack finds its model server, read once from {@code frostlake.properties}, with every
 * value overridable by a system property of the same name so a test or a one-off run can point
 * somewhere else without editing a file.
 *
 * <p>The file is looked for in the same three places the engine looks, and in the same order — the
 * working directory, then {@code ~/.frostlake/frostlake.properties}, then the classpath. The home
 * directory matters most for an embedded engine inside somebody else's process: a SQL client's working
 * directory is its own install directory, which is no place to put your configuration.
 *
 * <pre>
 * ai.ollama.url          base URL of the Ollama server        (default http://localhost:11434)
 * ai.ollama.model        model for the text functions          (default llama3.2)
 * ai.ollama.embedModel   model for the EMBED_TEXT_* functions  (default nomic-embed-text)
 * ai.ollama.timeoutMs    per-request timeout in milliseconds   (default 60000)
 * </pre>
 *
 * <p>Nothing here contacts the server: a pack whose Ollama is not running still loads, and only a
 * CALL of one of its functions fails — the same shape as the language runtimes, where an absent
 * module is a call-time error rather than a startup one.
 */
public final class OllamaConfig {

    public static final String URL = "ai.ollama.url";
    public static final String MODEL = "ai.ollama.model";
    public static final String EMBED_MODEL = "ai.ollama.embedModel";
    public static final String TIMEOUT_MS = "ai.ollama.timeoutMs";

    private static final String CONFIG_FILE = "frostlake.properties";
    private static final String DEFAULT_URL = "http://localhost:11434";
    private static final String DEFAULT_MODEL = "llama3.2";
    private static final String DEFAULT_EMBED_MODEL = "nomic-embed-text";
    private static final String DEFAULT_TIMEOUT_MS = "60000";

    /** Loaded lazily and held: the file is configuration, not state. */
    private static Properties fileProperties;

    private OllamaConfig() {
    }

    public static String url() {
        return trimTrailingSlash(value(URL, DEFAULT_URL));
    }

    public static String model() {
        return value(MODEL, DEFAULT_MODEL);
    }

    public static String embedModel() {
        return value(EMBED_MODEL, DEFAULT_EMBED_MODEL);
    }

    public static int timeoutMs() {
        try {
            return Integer.parseInt(value(TIMEOUT_MS, DEFAULT_TIMEOUT_MS).trim());
        } catch (final NumberFormatException notANumber) {
            return Integer.parseInt(DEFAULT_TIMEOUT_MS);
        }
    }

    /** A system property wins over the file, and the file over the default. */
    private static String value(final String key, final String fallback) {
        final String override = System.getProperty(key);
        if (override != null && !override.isEmpty()) {
            return override;
        }
        final String configured = properties().getProperty(key);
        return configured != null && !configured.isEmpty() ? configured : fallback;
    }

    private static synchronized Properties properties() {
        if (fileProperties == null) {
            fileProperties = load();
        }
        return fileProperties;
    }

    private static Properties load() {
        final Properties loaded = new Properties();
        if (loadFrom(loaded, Path.of(CONFIG_FILE))) {
            return loaded;
        }
        if (loadFrom(loaded, Path.of(System.getProperty("user.home"), ".frostlake", CONFIG_FILE))) {
            return loaded;
        }
        try (InputStream in = OllamaConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in != null) {
                loaded.load(in);
            }
        } catch (final IOException unreadable) {
            loaded.clear();
        }
        return loaded;
    }

    /** Whether that file was there and readable; an unreadable one is not worth failing over. */
    private static boolean loadFrom(final Properties loaded, final Path path) {
        if (!Files.isReadable(path)) {
            return false;
        }
        try (InputStream in = Files.newInputStream(path)) {
            loaded.load(in);
            return true;
        } catch (final IOException unreadable) {
            loaded.clear();
            return false;
        }
    }

    private static String trimTrailingSlash(final String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
