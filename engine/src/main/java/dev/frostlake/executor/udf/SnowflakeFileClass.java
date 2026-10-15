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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;

/**
 * The class an account's machine keeps in Snowpark's packages for a routine that does not name Snowpark,
 * {@code com.snowflake.snowpark_java.types.SnowflakeFile}, as a compiler reads it: a class of that name and no
 * members. It is there so that a body compiled without Snowpark finds that package as an account's machine finds
 * it — {@code import com.snowflake.snowpark_java.types.*} compiles, and a missing class of the package is a symbol
 * the compiler cannot find, not a package that does not exist. Nothing loads it.
 */
public final class SnowflakeFileClass extends SimpleJavaFileObject {

    /** The class's binary name. */
    static final String NAME = "com.snowflake.snowpark_java.types.SnowflakeFile";

    /** The package it is listed in. */
    static final String PACKAGE = "com.snowflake.snowpark_java.types";

    private static final String SOURCE = """
        package com.snowflake.snowpark_java.types;

        public final class SnowflakeFile {
            private SnowflakeFile() {
            }
        }
        """;

    private static byte[] compiled;

    SnowflakeFileClass() {
        super(URI.create("string:///com/snowflake/snowpark_java/types/SnowflakeFile.class"), Kind.CLASS);
    }

    @Override
    public InputStream openInputStream() {
        return new ByteArrayInputStream(bytes());
    }

    /** The class file, compiled on first use. */
    private static synchronized byte[] bytes() {
        if (compiled == null) {
            final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            final InMemoryJavaFileManager files = new InMemoryJavaFileManager(
                compiler.getStandardFileManager(null, null, null));
            final JavaFileObject source = new SimpleJavaFileObject(
                    URI.create("string:///com/snowflake/snowpark_java/types/SnowflakeFile.java"), Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
                    return SOURCE;
                }
            };
            final StringWriter report = new StringWriter();
            if (!compiler.getTask(report, files, null, Arrays.asList("--release", "11"), null,
                    Arrays.asList(source)).call()) {
                throw new IllegalStateException("SnowflakeFile did not compile: " + report);
            }
            final Map<String, byte[]> classes = files.getAllClassBytes();
            compiled = classes.get(NAME);
        }
        return compiled;
    }
}
