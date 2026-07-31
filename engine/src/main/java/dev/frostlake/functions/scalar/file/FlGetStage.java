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
 * {@code FL_GET_STAGE(file)} — the stage the file lives on.
 *
 * <p>Live-verified: a named stage renders fully qualified and upper-case, e.g.
 * {@code @PROBE_DB.S.SSE}. The user stage renders {@code @"~"} and a table stage renders just its
 * table name, {@code @TSTG}. NULL in, NULL out.
 */
public class FlGetStage extends BuiltInFunction {

    public FlGetStage() {
        super("FL_GET_STAGE", StringType.VARCHAR);
    }

    @Override
    public Object evaluate(final List<Object> args) {
        return FileFunctionHelper.textField(args.get(0), FileDescriptor.STAGE);
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
