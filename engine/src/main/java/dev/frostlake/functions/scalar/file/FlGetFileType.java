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
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * {@code FL_GET_FILE_TYPE(file)} — the file's single category name: {@code image}, {@code video},
 * {@code audio}, {@code document}, {@code compressed} or {@code unknown}.
 *
 * <p>Live-verified: the category is decided by the descriptor's {@code CONTENT_TYPE} ALONE,
 * by exact case-sensitive match against a closed set (see {@link FileContentTypes}) — not the path,
 * not the bytes. The sets overlap, so a predicate is not the same question: {@code video/x-msvideo}
 * (an {@code .avi}) makes {@code FL_IS_AUDIO} TRUE while this function answers {@code video}.
 *
 * <p>This function does NOT propagate NULL: live, {@code FL_GET_FILE_TYPE(NULL)} is the string
 * {@code 'unknown'}, never NULL. The one-liner below is already exactly that —
 * {@link FileFunctionHelper#contentTypeOf} answers null for a NULL argument and
 * {@link FileContentTypes#fileType} maps a null content type to {@code unknown}.
 */
public class FlGetFileType extends BuiltInFunction {

    public FlGetFileType() {
        super("FL_GET_FILE_TYPE", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return FileContentTypes.fileType(FileFunctionHelper.contentTypeOf(args.get(0)));
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
