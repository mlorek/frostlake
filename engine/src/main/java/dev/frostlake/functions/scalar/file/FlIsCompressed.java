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
 * {@code FL_IS_COMPRESSED(file)} — whether the file's {@code CONTENT_TYPE} is one of the archive types.
 *
 * <p>Live-verified: the test is on {@code CONTENT_TYPE} alone, by exact case-sensitive
 * match against a closed set (see {@link FileContentTypes}) — not the path, not the bytes.
 *
 * <p>The set is arbitrary and must not be guessed from the family: TRUE for {@code application/zip},
 * {@code application/gzip} — which is what a staged {@code .gz} file gets, live-verified — plus
 * {@code application/x-tar}, {@code application/vnd.rar} and {@code application/x-bzip2}, but FALSE
 * for the {@code application/x-gzip} spelling and for {@code application/x-7z-compressed}, what a
 * {@code .7z} gets. {@code FL_IS_COMPRESSED(NULL)} is FALSE, never NULL.
 */
public class FlIsCompressed extends BuiltInFunction {

    public FlIsCompressed() {
        super("FL_IS_COMPRESSED", BooleanType.BOOLEAN);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return Boolean.valueOf(FileContentTypes.isCompressed(FileFunctionHelper.contentTypeOf(args.get(0))));
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
