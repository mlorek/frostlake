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

import java.util.Map;

/**
 * Loads the classes one compilation of an inline source produced: the handler's class and every class beside
 * it — a nested or an inner class, an enum, an anonymous class, another top-level class of the same source.
 * Each is defined when the handler first needs it.
 */
public class InMemoryClassLoader extends ClassLoader {

    private final Map<String, byte[]> classes;

    /**
     * @param classes the compilation's classes, by binary name
     */
    public InMemoryClassLoader(final Map<String, byte[]> classes) {
        this.classes = classes;
    }

    /**
     * @param classes the compilation's classes, by binary name
     * @param parent  the loader every other class comes from
     */
    public InMemoryClassLoader(final Map<String, byte[]> classes, final ClassLoader parent) {
        super(parent);
        this.classes = classes;
    }

    /**
     * @param name a class's binary name
     * @return whether the compilation produced it
     */
    public boolean defines(final String name) {
        return classes.containsKey(name);
    }

    @Override
    protected Class<?> findClass(final String name) throws ClassNotFoundException {
        final byte[] bytes = classes.get(name);
        if (bytes != null) {
            return defineClass(name, bytes, 0, bytes.length);
        }
        return super.findClass(name);
    }
}
