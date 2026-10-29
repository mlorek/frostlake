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
 * What names one compilation of an inline Java source: the class the handler names, the source's whole text
 * and the language level it is compiled at. Two sources are the same compilation only when all are equal, so
 * two bodies that merely share a hash never run each other's code.
 */
public final class JavaSourceKey {

    private final String className;
    private final String sourceCode;
    private final int release;
    /** Whether Snowpark's classes were on the class path (see {@link SnowparkPackage}). */
    private final boolean snowpark;
    /** The jars on the class path as they stood, each path with its length and time; empty for none. */
    private final String jars;

    /**
     * @param className  the class the handler names
     * @param sourceCode the source's whole text
     */
    public JavaSourceKey(final String className, final String sourceCode) {
        this(className, sourceCode, 0);
    }

    /**
     * @param className  the class the handler names
     * @param sourceCode the source's whole text
     * @param release    the language level, or 0 for the JDK's own
     */
    public JavaSourceKey(final String className, final String sourceCode, final int release) {
        this(className, sourceCode, release, true);
    }

    /**
     * @param className  the class the handler names
     * @param sourceCode the source's whole text
     * @param release    the language level, or 0 for the JDK's own
     * @param snowpark   whether Snowpark's classes are on the class path
     */
    public JavaSourceKey(final String className, final String sourceCode, final int release, final boolean snowpark) {
        this(className, sourceCode, release, snowpark, "");
    }

    /**
     * @param className  the class the handler names
     * @param sourceCode the source's whole text
     * @param release    the language level, or 0 for the JDK's own
     * @param snowpark   whether Snowpark's classes are on the class path
     * @param jars       the jars on the class path, each path with its length and time; empty for none
     */
    public JavaSourceKey(final String className, final String sourceCode, final int release, final boolean snowpark,
                         final String jars) {
        this.className = className;
        this.sourceCode = sourceCode;
        this.release = release;
        this.snowpark = snowpark;
        this.jars = jars;
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof JavaSourceKey)) {
            return false;
        }
        final JavaSourceKey that = (JavaSourceKey) other;
        return release == that.release && snowpark == that.snowpark && className.equals(that.className)
            && sourceCode.equals(that.sourceCode) && jars.equals(that.jars);
    }

    @Override
    public int hashCode() {
        return 31 * (2 * (31 * (31 * className.hashCode() + sourceCode.hashCode()) + release) + (snowpark ? 1 : 0))
            + jars.hashCode();
    }
}
