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

package dev.frostlake.executor.copy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.TreeSet;

/**
 * Selects the {@link StageFileReader} for a record-based FILE_FORMAT TYPE. Readers are discovered once, at
 * class load, as a {@link ServiceLoader} SPI: the engine ships the JSON and XML providers, and the optional
 * {@code frostlake-formats} module contributes Avro/Parquet/ORC when it is on the classpath. Returns null
 * for CSV (which the loader reads on its own positional-text path) and for a type no provider handles (which
 * the loader rejects). The readers are stateless and shared.
 */
public final class StageReaderFactory {

    private static final Map<String, StageFileReader> READERS = discoverReaders();

    private StageReaderFactory() {
    }

    private static Map<String, StageFileReader> discoverReaders() {
        final Map<String, StageFileReader> byType = new LinkedHashMap<>();
        final ServiceLoader<StageFileReader> providers =
            ServiceLoader.load(StageFileReader.class, StageReaderFactory.class.getClassLoader());
        for (final StageFileReader reader : providers) {
            byType.put(reader.formatType().toUpperCase(), reader);
        }
        return byType;
    }

    /** The reader for {@code type}, or null for CSV / a positional or unregistered format. */
    public static StageFileReader forType(final String type) {
        if (type == null) {
            return null;
        }
        return READERS.get(type.toUpperCase());
    }

    /** CSV plus every discovered record type, sorted — for the loader's "unsupported format" message. */
    public static String supportedTypesDisplay() {
        final StringBuilder display = new StringBuilder("CSV");
        for (final String type : new TreeSet<>(READERS.keySet())) {
            display.append(", ").append(type);
        }
        return display.toString();
    }
}
