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

import java.net.URI;
import javax.tools.SimpleJavaFileObject;

/**
 * An inline body as the compiler's source: one compilation unit named {@code InlineCode.java} whatever class
 * it declares, which is the name the compiler's report gives a fault's place by (live-verified).
 */
public class InMemoryJavaFile extends SimpleJavaFileObject {
    private final String sourceCode;

    /**
     * @param sourceCode the body
     */
    public InMemoryJavaFile(final String sourceCode) {
        super(URI.create("string:///InlineCode" + Kind.SOURCE.extension), Kind.SOURCE);
        this.sourceCode = sourceCode;
    }

    @Override
    public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
        return sourceCode;
    }

    /** A public class of any name may stand in the unit: the body is not a file named for its class. */
    @Override
    public boolean isNameCompatible(final String simpleName, final Kind kind) {
        return kind == Kind.SOURCE;
    }
}
