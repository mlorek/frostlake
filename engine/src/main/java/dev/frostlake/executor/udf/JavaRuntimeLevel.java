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

/**
 * The Java language level an inline body is compiled at: the one its routine's RUNTIME_VERSION names, 11 when
 * it names none (live-verified) - so a body written for a later Java than its routine declares is refused when
 * the routine is created, in its syntax and in the classes and methods it calls. A version above the JDK the
 * engine runs on cannot be held to: such a body is compiled at the JDK's own level.
 */
public final class JavaRuntimeLevel {

    /** The level of a routine that declares no RUNTIME_VERSION. */
    private static final int DEFAULT = 11;

    /** Static helpers only — never instantiated. */
    private JavaRuntimeLevel() {
    }

    /**
     * @param runtimeVersion a routine's RUNTIME_VERSION, or null when it declares none
     * @return the release to compile at, or 0 for the JDK's own level
     */
    public static int of(final String runtimeVersion) {
        int level = DEFAULT;
        if (runtimeVersion != null && !runtimeVersion.trim().isEmpty()) {
            final String written = runtimeVersion.trim();
            int end = 0;
            while (end < written.length() && Character.isDigit(written.charAt(end))) {
                end++;
            }
            if (end == 0 || end > 3) {
                return 0;
            }
            level = Integer.parseInt(written.substring(0, end));
        }
        return level > Runtime.version().feature() ? 0 : level;
    }
}
