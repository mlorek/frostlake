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
 * GET_RELATIVE_PATH(@stage, 'absolute path'): the absolute path with the stage's location (see
 * {@link GetStageLocation}) taken off its front, compared case-sensitively and character by character — so a slash
 * the location lacks stays on the result ({@code /a/b.csv}); the location itself and the empty string answer the
 * empty string. A path
 * that does not start with the location is refused while the row is read, the location cut to its first fifty
 * characters:
 *
 * <pre>
 *   Absolute file path 'x' does not belong to stage '@"DB"."PUBLIC"."ST"' whose location is 's3://…/stage...'
 * </pre>
 *
 * <p>A NULL path answers NULL.
 */
public class GetRelativePath extends StageUrlFunction {

    /** How much of the location the refusal quotes before cutting it short. */
    private static final int QUOTED_LOCATION = 50;

    public GetRelativePath(final NamedStageLocator stages, final EngineConfig config) {
        super("GET_RELATIVE_PATH", StringType.VARCHAR, stages, config);
    }

    @Override
    public Object call(final List<Object> args, final boolean pathFolded) {
        final NamedStage stage = requireStage(args.get(0));
        if (args.get(1) == null) {
            return null;
        }
        final String absolute = args.get(1).toString();
        final String location = stage.getLocation();
        if (absolute.isEmpty()) {
            return "";
        }
        if (absolute.startsWith(location)) {
            return absolute.substring(location.length());
        }
        final String quoted = location.length() > QUOTED_LOCATION
            ? location.substring(0, QUOTED_LOCATION) + "..." : location;
        throw new RuntimeException("Absolute file path '" + absolute + "' does not belong to stage '"
            + stage.quotedReference() + "' whose location is '" + quoted + "'");
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
