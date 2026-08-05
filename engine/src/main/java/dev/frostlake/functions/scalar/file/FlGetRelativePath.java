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
 * {@code FL_GET_RELATIVE_PATH(file)} — the file's path within its stage.
 *
 * <p>Live-verified: {@code TO_FILE('@sse/sub/nested.txt')} gives {@code sub/nested.txt}, so
 * sub-directories are kept and there is no leading slash. NULL in, NULL out.
 */
public class FlGetRelativePath extends BuiltInFunction {

    public FlGetRelativePath() {
        super("FL_GET_RELATIVE_PATH", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return FileFunctionHelper.textField(args.get(0), FileDescriptor.RELATIVE_PATH);
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
