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
import dev.frostlake.functions.scalar.SharedFunctionHelpers;
import dev.frostlake.types.DateTimeType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * {@code FL_GET_LAST_MODIFIED(file)} — when the staged file was last modified.
 *
 * <p>Live-verified: the result type is {@code TIMESTAMP_TZ(9)}, and the descriptor holds
 * the moment as an RFC-1123 string in GMT, e.g. {@code Mon, 03 Aug 2026 11:24:13 GMT}, which
 * {@link FileDescriptor#parseLastModified(String)} turns back into an {@link Instant}. NULL in, NULL
 * out.
 *
 * <p>The DECLARED type is {@link DateTimeType#TIMESTAMP_TZ}, so {@code SHOW FUNCTIONS} reports the
 * same {@code RETURN TIMESTAMP_TZ} the live catalog does. The VALUE is a {@link LocalDateTime} holding
 * the UTC wall clock, because Frostlake carries every {@code TIMESTAMP_*} — NTZ, LTZ and TZ alike — as
 * a {@code LocalDateTime}: {@code SharedFunctionHelpers.toTemporalValue} folds the zoned spellings onto
 * it, and {@code TYPEOF}, the arithmetic layer and set operations recognise nothing else. Only the
 * declared type differs from {@code CURRENT_TIMESTAMP}, which is copied here in every other respect.
 */
public class FlGetLastModified extends BuiltInFunction {

    public FlGetLastModified() {
        super("FL_GET_LAST_MODIFIED", DateTimeType.TIMESTAMP_TZ);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        final String text = FileFunctionHelper.textField(args.get(0), FileDescriptor.LAST_MODIFIED);
        if (text == null) {
            return null;
        }
        final Instant modified = FileDescriptor.parseLastModified(text);
        if (modified != null) {
            return LocalDateTime.ofInstant(modified, ZoneOffset.UTC);
        }
        // The descriptor accepts ANY LAST_MODIFIED string, and this accessor parses it leniently
        // (live): '2026-08-03 11:24:13' and epoch-second digits both yield the timestamp
        // — even though TRY_TO_TIMESTAMP itself returns NULL for the RFC-1123 shape above, whose
        // zone token live discards, keeping the wall clock — and junk text yields NULL.
        try {
            return SharedFunctionHelpers.parseTimestampWithFormatOrScale(text, null);
        } catch (final Exception e) {
            return null;
        }
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
