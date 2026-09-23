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

import dev.frostlake.config.EngineConfig;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * GET_ABSOLUTE_PATH(@stage, 'relative/path'): the stage's location (see {@link GetStageLocation}) with the relative
 * path appended as written — no slash is added, so a location without a trailing one runs straight into the path, as
 * on the account ({@code s3://b/some/path} and {@code a.csv} make {@code s3://b/some/patha.csv}). Nothing is checked
 * against the files; a NULL path answers NULL.
 */
public class GetAbsolutePath extends StageUrlFunction {

    public GetAbsolutePath(final NamedStageLocator stages, final EngineConfig config) {
        super("GET_ABSOLUTE_PATH", StringType.VARCHAR, stages, config);
    }

    @Override
    public Object call(final List<Object> args, final boolean pathFolded) {
        final NamedStage stage = requireStage(args.get(0));
        if (args.get(1) == null) {
            return null;
        }
        return stage.getLocation() + args.get(1);
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return 2;
    }
}
