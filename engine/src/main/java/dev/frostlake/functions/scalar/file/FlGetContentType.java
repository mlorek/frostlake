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
 * {@code FL_GET_CONTENT_TYPE(file)} — the file's MIME content type.
 *
 * <p>Live-verified: this is the field the whole classification side of the family keys off.
 * {@code FL_GET_FILE_TYPE} and all five {@code FL_IS_*} predicates read {@code CONTENT_TYPE} ONLY, by
 * exact case-sensitive match against a closed set (see {@link FileContentTypes}) — not the path, not
 * the bytes. NULL in, NULL out.
 */
public class FlGetContentType extends BuiltInFunction {

    public FlGetContentType() {
        super("FL_GET_CONTENT_TYPE", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return FileFunctionHelper.textField(args.get(0), FileDescriptor.CONTENT_TYPE);
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
