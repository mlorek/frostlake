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
import dev.frostlake.values.VariantValue;

import java.util.List;

/**
 * {@code TO_FILE(stage_and_path)} / {@code TO_FILE(stage, relative_path)} / {@code TO_FILE(metadata)} —
 * builds the file-metadata object that is a FILE value.
 *
 * <p>Live-verified:
 * <ul>
 *   <li>The stage-path form RESOLVES AND VALIDATES: {@code TO_FILE('@sse/hello.txt')} returns the
 *       descriptor read from the real file, and {@code TO_FILE('@sse/missing.txt')} fails "Remote file
 *       '@sse/missing.txt' was not found. …". {@code TRY_TO_FILE} returning NULL there is the ONLY
 *       difference between the two functions.</li>
 *   <li>The two-argument form is a plain join: {@code TO_FILE('@sse', '/hello.txt')} fails naming
 *       {@code '@sse//hello.txt'}, and {@code TO_FILE('sse', 'hello.txt')} fails naming
 *       {@code 'sse/hello.txt'} — no {@code @} is added and no separator is collapsed.</li>
 *   <li>A directory is not a file: {@code TO_FILE('@sse/sub')} and {@code TO_FILE('@sse/')} both fail
 *       "was not found".</li>
 *   <li>User and table stages resolve too — {@code TO_FILE('@~/p135/hello.txt')} and
 *       {@code TO_FILE('@%tstg/hello.txt')} both return descriptors.</li>
 *   <li>NULL in, NULL out, for both functions and with no error.</li>
 *   <li>The metadata-object form validates structure but NOT existence, so it is the one way to build a
 *       FILE value for a file that is not there.</li>
 * </ul>
 *
 * <p>The engine registers this twice: once from {@code FunctionRegistry} with no locator (so the name
 * resolves and {@code SHOW FUNCTIONS} lists it) and again from {@code QueryExecutor} with a locator
 * that can reach stages — the same two-step {@code GET_DDL} uses.
 */
public class ToFile extends BuiltInFunction {

    private final StageFileLocator locator;

    public ToFile() {
        this(null);
    }

    public ToFile(final StageFileLocator locator) {
        super("TO_FILE", FileType.FILE);
        this.locator = locator;
    }

    /**
     * The shared body of {@code TO_FILE} and {@code TRY_TO_FILE}.
     *
     * @param args    the call arguments (one location/metadata, or a stage and a relative path)
     * @param locator resolves a stage reference to a real file
     * @param tryMode true for {@code TRY_TO_FILE}: every failure becomes NULL
     * @return the FILE descriptor, or null
     */
    public static VariantValue convert(final List<Object> args, final StageFileLocator locator,
                                       final boolean tryMode) {
        for (final Object arg : args) {
            if (arg == null) {
                return null;
            }
        }
        if (args.size() == 2) {
            return fromLocation(args.get(0) + "/" + args.get(1), locator, tryMode);
        }
        final Object single = args.get(0);
        if (FileDescriptor.descriptorNode(single) != null) {
            return FileDescriptor.fromMetadataObject(single, tryMode);
        }
        return fromLocation(single.toString(), locator, tryMode);
    }

    private static VariantValue fromLocation(final String location, final StageFileLocator locator,
                                             final boolean tryMode) {
        if (tryMode && locator == null) {
            return null;
        }
        final VariantValue descriptor = FileDescriptor.fromLocation(location, locator);
        if (descriptor == null && !tryMode) {
            throw new RuntimeException(FileDescriptor.notFoundMessage(location));
        }
        return descriptor;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return convert(args, locator, false);
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
