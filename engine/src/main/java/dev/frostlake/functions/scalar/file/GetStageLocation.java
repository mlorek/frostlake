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
 * GET_STAGE_LOCATION(@stage): where a named stage keeps its files. An external stage answers the URL it was created
 * with, exactly as written ({@code s3://bucket/some/path} gains no slash); an internal stage answers its storage
 * location, which on the account is a cloud URL ending in a slash and here is the stage's local directory as a
 * {@code file://} URL ending in one.
 */
public class GetStageLocation extends StageUrlFunction {

    public GetStageLocation(final NamedStageLocator stages, final EngineConfig config) {
        super("GET_STAGE_LOCATION", StringType.VARCHAR, stages, config);
    }

    @Override
    public Object call(final List<Object> args, final boolean pathFolded) {
        return requireStage(args.get(0)).getLocation();
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
