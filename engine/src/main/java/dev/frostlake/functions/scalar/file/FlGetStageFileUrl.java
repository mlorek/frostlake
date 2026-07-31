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
 * {@code FL_GET_STAGE_FILE_URL(file)} — the descriptor's {@code STAGE_FILE_URL} field.
 *
 * <p>Live-verified: this is a plain descriptor FIELD, nothing is derived from the stage. A
 * descriptor that {@code TO_FILE} built from a stage path does not carry it, so live this returns NULL
 * — even on a stage with {@code DIRECTORY = (ENABLE = TRUE)}. It answers a value only when a
 * caller-supplied metadata object set the field, verified by round-tripping one through
 * {@code TRY_TO_FILE}. The NULL is the measured Snowflake answer, not an engine shortfall. NULL in,
 * NULL out.
 */
public class FlGetStageFileUrl extends BuiltInFunction {

    public FlGetStageFileUrl() {
        super("FL_GET_STAGE_FILE_URL", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return FileFunctionHelper.textField(args.get(0), FileDescriptor.STAGE_FILE_URL);
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
