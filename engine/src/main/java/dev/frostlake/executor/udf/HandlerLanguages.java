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

/**
 * The handler languages whose routines run on this thread, counted as the runtimes enter and leave them: a statement
 * a Python procedure runs through its session runs on the procedure's thread, beneath whatever handler it calls. A
 * runtime asks it to know which other language's frames lie below its own.
 */
public final class HandlerLanguages {

    private static final ThreadLocal<Map<UdfLanguage, int[]>> RUNNING = new ThreadLocal<Map<UdfLanguage, int[]>>() {
        @Override
        protected Map<UdfLanguage, int[]> initialValue() {
            return new EnumMap<>(UdfLanguage.class);
        }
    };

    private HandlerLanguages() {
    }

    /**
     * Records that a handler of the language starts on this thread.
     *
     * @param language the handler's language
     */
    static void enter(final UdfLanguage language) {
        final Map<UdfLanguage, int[]> running = RUNNING.get();
        int[] count = running.get(language);
        if (count == null) {
            count = new int[1];
            running.put(language, count);
        }
        count[0]++;
    }

    /**
     * Records that a handler of the language has ended on this thread.
     *
     * @param language the handler's language
     */
    static void exit(final UdfLanguage language) {
        final int[] count = RUNNING.get().get(language);
        if (count != null && count[0] > 0) {
            count[0]--;
        }
    }

    /**
     * Whether a handler of the language runs on this thread.
     *
     * @param language the language
     * @return whether one does
     */
    public static boolean isRunning(final UdfLanguage language) {
        final int[] count = RUNNING.get().get(language);
        return count != null && count[0] > 0;
    }
}
