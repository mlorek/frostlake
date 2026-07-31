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

import java.net.URL;
import java.net.URLClassLoader;

/**
 * Classloader for IMPORTS handler jars that prefers the jar (and its stage-sibling dependency jars)
 * over the engine's classpath — the equivalent of Snowflake running a UDF against the dependency set
 * its PACKAGES clause supplies, not against whatever the warehouse happens to have loaded. Without
 * this, a handler built against one library version silently ran against the engine's copy: the
 * vendor plugin-output parsers (generated with ANTLR 4.11) picked up the engine's ANTLR 4.13 runtime,
 * warning on every call and mis-recovering on inputs their own runtime parses cleanly.
 *
 * <p>Classes the handler must SHARE with the engine stay parent-first: JDK classes, the engine's own
 * {@code dev.frostlake} types, the {@code com.snowflake} Snowpark bridge (a procedure's Session
 * argument must be the engine's class, not a copy from the jar), and SLF4J so logging binds once.
 */
public final class ChildFirstUdfClassLoader extends URLClassLoader {

    public ChildFirstUdfClassLoader(final URL[] urls, final ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            final Class<?> alreadyLoaded = findLoadedClass(name);
            if (alreadyLoaded != null) {
                if (resolve) {
                    resolveClass(alreadyLoaded);
                }
                return alreadyLoaded;
            }
            if (isSharedWithEngine(name)) {
                return super.loadClass(name, resolve);
            }
            try {
                final Class<?> fromJars = findClass(name);
                if (resolve) {
                    resolveClass(fromJars);
                }
                return fromJars;
            } catch (final ClassNotFoundException notInJars) {
                return super.loadClass(name, resolve);
            }
        }
    }

    private static boolean isSharedWithEngine(final String name) {
        return name.startsWith("java.")
            || name.startsWith("javax.")
            || name.startsWith("jdk.")
            || name.startsWith("sun.")
            || name.startsWith("com.sun.")
            || name.startsWith("dev.frostlake.")
            || name.startsWith("com.snowflake.")
            || name.startsWith("org.slf4j.");
    }
}
