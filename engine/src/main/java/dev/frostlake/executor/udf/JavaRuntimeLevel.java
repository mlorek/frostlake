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

    /** A class file's major version is its Java level and this: Java 11 writes 55. */
    private static final int CLASS_FILE_VERSION_OF_JAVA_ZERO = 44;

    /** Static helpers only — never instantiated. */
    private JavaRuntimeLevel() {
    }

    /**
     * @param runtimeVersion a routine's RUNTIME_VERSION, or null when it declares none
     * @return the release to compile at, or 0 for the JDK's own level
     */
    public static int of(final String runtimeVersion) {
        final int level = declared(runtimeVersion);
        return level > Runtime.version().feature() ? 0 : level;
    }

    /**
     * The Java level a routine runs at on an account, whatever JDK the engine itself runs on.
     *
     * @param runtimeVersion a routine's RUNTIME_VERSION, or null when it declares none
     * @return the level, or 0 for a version that names none
     */
    public static int declared(final String runtimeVersion) {
        if (runtimeVersion == null || runtimeVersion.trim().isEmpty()) {
            return DEFAULT;
        }
        final String written = runtimeVersion.trim();
        int end = 0;
        while (end < written.length() && Character.isDigit(written.charAt(end))) {
            end++;
        }
        return end == 0 || end > 3 ? 0 : Integer.parseInt(written.substring(0, end));
    }

    /**
     * The newest class-file version a routine's runtime reads: 55 for Java 11, 61 for 17, 65 for 21.
     *
     * @param runtimeVersion a routine's RUNTIME_VERSION, or null when it declares none
     * @return the class-file version, or {@code Integer.MAX_VALUE} for a version that names no level
     */
    public static int newestClassFile(final String runtimeVersion) {
        final int level = declared(runtimeVersion);
        return level == 0 ? Integer.MAX_VALUE : CLASS_FILE_VERSION_OF_JAVA_ZERO + level;
    }
}
