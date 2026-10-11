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

package dev.frostlake.executor.udf;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The classes inline Java sources compiled to, kept so that a source is compiled once: creating a routine
 * compiles its body, and every later call loads the classes that compilation produced. The engines of one JVM
 * share it, and it holds bytes only — a class and its loader live as long as the statement or the CALL that
 * loaded them. Bounded: the source read longest ago goes first.
 */
public final class JavaCompiledSources {

    private static final int MAX_SOURCES = 256;

    private static final Map<JavaSourceKey, Map<String, byte[]>> SOURCES = Collections.synchronizedMap(
        new LinkedHashMap<JavaSourceKey, Map<String, byte[]>>(MAX_SOURCES + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(final Map.Entry<JavaSourceKey, Map<String, byte[]>> eldest) {
                return size() > MAX_SOURCES;
            }
        });

    /** Static helpers only — never instantiated. */
    private JavaCompiledSources() {
    }

    /**
     * @param key a source
     * @return the classes it compiled to, by binary name, or null when it has not been compiled
     */
    public static Map<String, byte[]> get(final JavaSourceKey key) {
        return SOURCES.get(key);
    }

    /**
     * @param key     a source
     * @param classes the classes it compiled to, by binary name
     */
    public static void put(final JavaSourceKey key, final Map<String, byte[]> classes) {
        SOURCES.put(key, classes);
    }
}
