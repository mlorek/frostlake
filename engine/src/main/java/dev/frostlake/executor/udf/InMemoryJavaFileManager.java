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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

/**
 * In-memory file manager. A compilation for a routine that does not name Snowpark reads the class path without
 * Snowpark's classes, as an account's machine has it (see {@link SnowparkPackage}).
 */
public class InMemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final Map<String, ByteArrayOutputStream> classBytes;
    /** Whether Snowpark's classes are on the class path. */
    private final boolean snowpark;

    public InMemoryJavaFileManager(final StandardJavaFileManager fileManager) {
        this(fileManager, true);
    }

    /**
     * @param fileManager the platform's file manager
     * @param snowpark    whether Snowpark's classes are on the class path
     */
    public InMemoryJavaFileManager(final StandardJavaFileManager fileManager, final boolean snowpark) {
        super(fileManager);
        this.classBytes = new HashMap<>();
        this.snowpark = snowpark;
    }

    @Override
    public Iterable<JavaFileObject> list(final Location location, final String packageName,
                                         final Set<JavaFileObject.Kind> kinds, final boolean recurse)
            throws IOException {
        if (snowpark || location != StandardLocation.CLASS_PATH || !SnowparkPackage.holds(packageName)) {
            return super.list(location, packageName, kinds, recurse);
        }
        if (!SnowflakeFileClass.PACKAGE.equals(packageName) || !kinds.contains(JavaFileObject.Kind.CLASS)) {
            return Collections.emptyList();
        }
        final List<JavaFileObject> kept = new ArrayList<JavaFileObject>();
        kept.add(new SnowflakeFileClass());
        return kept;
    }

    @Override
    public String inferBinaryName(final Location location, final JavaFileObject file) {
        return file instanceof SnowflakeFileClass ? SnowflakeFileClass.NAME : super.inferBinaryName(location, file);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
            final Location location,
            final String className,
            final JavaFileObject.Kind kind,
            final FileObject sibling) {

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        classBytes.put(className, baos);

        return new SimpleJavaFileObject(URI.create("string:///" + className), kind) {
            @Override
            public OutputStream openOutputStream() {
                return baos;
            }
        };
    }

    public byte[] getClassBytes(final String className) {
        final ByteArrayOutputStream baos = classBytes.get(className);
        return baos != null ? baos.toByteArray() : null;
    }

    /** Every class the compilation produced, by its binary name, in name order. */
    public Map<String, byte[]> getAllClassBytes() {
        final Map<String, byte[]> all = new TreeMap<>();
        for (final Map.Entry<String, ByteArrayOutputStream> entry : classBytes.entrySet()) {
            all.put(entry.getKey(), entry.getValue().toByteArray());
        }
        return all;
    }
}
