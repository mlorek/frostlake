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
import dev.frostlake.types.FileType;

import java.util.List;

/**
 * {@code TRY_TO_FILE(...)} — {@link ToFile} with every failure turned into NULL.
 *
 * <p>Live-verified: {@code TRY_TO_FILE('@sse/missing.txt')} is NULL where {@code TO_FILE} of
 * the same path errors, and an invalid metadata object (an unknown field, a missing required field, a
 * {@code LAST_MODIFIED} that is not an RFC-1123 date, or one carrying neither a stage-and-path nor a
 * URL) is NULL where {@code TO_FILE} raises the corresponding "Invalid file metadata…" error. Valid
 * input gives the identical descriptor.
 */
public class TryToFile extends BuiltInFunction {

    private final StageFileLocator locator;

    public TryToFile() {
        this(null);
    }

    public TryToFile(final StageFileLocator locator) {
        super("TRY_TO_FILE", FileType.FILE);
        this.locator = locator;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return ToFile.convert(args, locator, true);
    }

    @Override
    public int getMinArgCount() {
        return 1;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }
}
