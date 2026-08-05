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

package dev.frostlake.functions.scalar.file;

import dev.frostlake.functions.BuiltInFunction;
import dev.frostlake.types.BooleanType;

import java.util.List;

/**
 * {@code FL_IS_DOCUMENT(file)} — whether the file's {@code CONTENT_TYPE} is one of the document types.
 *
 * <p>Live-verified: the test is on {@code CONTENT_TYPE} alone, by exact case-sensitive
 * match against a closed set (see {@link FileContentTypes}) — not the path, not the bytes.
 *
 * <p>Membership is measured, not guessed: TRUE for {@code text/plain}, {@code text/csv},
 * {@code text/html}, {@code text/markdown}, {@code application/json}, {@code application/pdf} and the
 * Office types, but FALSE for {@code text/tab-separated-values} and {@code text/x-python} — so a
 * staged {@code .tsv} or {@code .py} is NOT a document. {@code FL_IS_DOCUMENT(NULL)} is FALSE, never
 * NULL.
 */
public class FlIsDocument extends BuiltInFunction {

    public FlIsDocument() {
        super("FL_IS_DOCUMENT", BooleanType.BOOLEAN);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return Boolean.valueOf(FileContentTypes.isDocument(FileFunctionHelper.contentTypeOf(args.get(0))));
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 1;
    }
}
