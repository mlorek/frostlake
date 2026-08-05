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
 * {@code FL_IS_VIDEO(file)} — whether the file's {@code CONTENT_TYPE} is one of the video types.
 *
 * <p>Live-verified: the test is on {@code CONTENT_TYPE} alone, by exact case-sensitive
 * match against a closed set (see {@link FileContentTypes}) — not the path, not the bytes.
 *
 * <p>Each predicate is its own membership test, so {@code FL_IS_VIDEO(f)} is NOT
 * {@code FL_GET_FILE_TYPE(f) = 'video'}: {@code video/x-msvideo} (an {@code .avi}) makes both
 * {@code FL_IS_VIDEO} and {@code FL_IS_AUDIO} TRUE, while {@code FL_GET_FILE_TYPE} calls it
 * {@code video}. {@code FL_IS_VIDEO(NULL)} is FALSE, never NULL.
 */
public class FlIsVideo extends BuiltInFunction {

    public FlIsVideo() {
        super("FL_IS_VIDEO", BooleanType.BOOLEAN);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return Boolean.valueOf(FileContentTypes.isVideo(FileFunctionHelper.contentTypeOf(args.get(0))));
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
