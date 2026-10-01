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
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * BUILD_STAGE_FILE_URL(@stage, 'relative/path'): the file URL of a staged file, which does not expire —
 * {@code <server>/api/files/<database>/<schema>/<stage>/<path>}. The account names its own host there; this engine
 * names its HTTP server ({@code http.host}/{@code http.port}), which serves the file at that URL (see
 * {@code StageFileUrlHandler}). Each name part is spelled as SQL spells it — bare when it can be, quoted otherwise —
 * and every part, the path included, is percent-encoded in lower-case hex, a slash inside the path too
 * ({@code dir/f 1.csv} is {@code dir%2ff%201.csv}). A dot is kept when the path is a constant the statement folds, and
 * encoded ({@code %2e}) when the path is computed from a column, as the account spells them (live-verified). The URL is
 * made whether or not the file exists; a NULL path answers NULL.
 */
public class BuildStageFileUrl extends StageUrlFunction {

    /** The HTTP path the file URLs are served under. */
    public static final String CONTEXT = "/api/files/";

    public BuildStageFileUrl(final NamedStageLocator stages, final EngineConfig config) {
        super("BUILD_STAGE_FILE_URL", StringType.VARCHAR, stages, config);
    }

    @Override
    public Object call(final List<Object> args, final boolean pathFolded) {
        final NamedStage stage = requireStage(args.get(0));
        if (args.get(1) == null) {
            return null;
        }
        return fileUrl(baseUrl(), stage.getDatabase(), stage.getSchema(), stage.getName(), args.get(1).toString(),
            pathFolded);
    }

    /**
     * A staged file's URL: {@code <server>/api/files/<database>/<schema>/<stage>/<path>}, each name spelled as SQL
     * spells it and every part percent-encoded (see {@link #urlEncoded}).
     *
     * @param server   the server, {@code http://host:port}
     * @param database the stage's database, canonical
     * @param schema   its schema, canonical
     * @param stage    the stage's own name, canonical
     * @param path     the file's stage-relative name
     * @param keepDot  whether a dot in the path stays as it is, as it does in a path the statement folds
     * @return the URL
     */
    public static String fileUrl(final String server, final String database, final String schema, final String stage,
                                 final String path, final boolean keepDot) {
        return server + CONTEXT + urlEncoded(SqlIdentifiers.spellCanonicalEscaped(database), true)
            + "/" + urlEncoded(SqlIdentifiers.spellCanonicalEscaped(schema), true)
            + "/" + urlEncoded(SqlIdentifiers.spellCanonicalEscaped(stage), true)
            + "/" + urlEncoded(path, keepDot);
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
