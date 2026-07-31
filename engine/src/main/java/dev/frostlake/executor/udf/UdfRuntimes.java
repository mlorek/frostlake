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

import dev.frostlake.metastore.model.UdfLanguage;

import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Registry of the {@link UdfLanguageRuntime} providers on the classpath, discovered once at first
 * use. A language whose runtime module is absent fails lazily — at CALL time, not at CREATE or
 * catalog-restore time — with an error naming the module to add, so catalogs holding e.g. Python
 * functions still load on a JavaScript-only deployment.
 */
public final class UdfRuntimes {

    private static final Map<UdfLanguage, UdfLanguageRuntime> RUNTIMES = discover();

    private static Map<UdfLanguage, UdfLanguageRuntime> discover() {
        final Map<UdfLanguage, UdfLanguageRuntime> byLanguage = new EnumMap<>(UdfLanguage.class);
        final ServiceLoader<UdfLanguageRuntime> providers =
            ServiceLoader.load(UdfLanguageRuntime.class, UdfRuntimes.class.getClassLoader());
        for (final UdfLanguageRuntime runtime : providers) {
            byLanguage.put(runtime.language(), runtime);
        }
        return byLanguage;
    }

    /** Every discovered runtime, for engine-construction configuration. */
    public static Iterable<UdfLanguageRuntime> all() {
        return RUNTIMES.values();
    }

    /** The runtime for {@code language}, or a clear error naming the module that provides it. */
    public static UdfLanguageRuntime require(final UdfLanguage language) {
        final UdfLanguageRuntime runtime = RUNTIMES.get(language);
        if (runtime == null) {
            throw new RuntimeException("LANGUAGE " + language + " is not available: add "
                + moduleFor(language) + " to the classpath");
        }
        return runtime;
    }

    private static String moduleFor(final UdfLanguage language) {
        switch (language) {
            case JAVASCRIPT:
                return "dev.frostlake:frostlake-rt-js";
            case PYTHON:
                return "dev.frostlake:frostlake-rt-py";
            case SCALA:
                return "dev.frostlake:frostlake-rt-scala";
            default:
                return "a module providing a " + language + " UdfLanguageRuntime";
        }
    }

    private UdfRuntimes() {
    }
}
