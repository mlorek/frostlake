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
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;

/**
 * In-memory file manager
 */
public class InMemoryJavaFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    private final Map<String, ByteArrayOutputStream> classBytes;

    public InMemoryJavaFileManager(final StandardJavaFileManager fileManager) {
        super(fileManager);
        this.classBytes = new HashMap<>();
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
}
