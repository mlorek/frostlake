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
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.StagePathSegments;
import dev.frostlake.types.StringType;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * GET_PRESIGNED_URL(@stage, 'relative/path' [, expiration_seconds]): a URL that downloads the staged file without a
 * session until it expires (3600 seconds by default, at most 604800). The stage is a named stage, written bare or as
 * a string (see {@link StageUrlFunction}); a user or table stage is refused. The URL points at this engine's HTTP
 * server ({@code http.host}/{@code http.port}), under {@link PresignedUrls#CONTEXT}, with an opaque signed token; as in
 * Snowflake it is made whether or not the file exists, and fetching a missing file answers {@code NoSuchKey}. A path
 * computed to NULL answers NULL; a path segment made of dots is a name like any other and cannot climb out of the
 * stage.
 */
public class GetPresignedUrl extends StageUrlFunction {

    /** The expiration a call leaves out, in seconds. */
    public static final long DEFAULT_EXPIRATION = 3600L;
    /** The longest expiration, in seconds. */
    public static final long MAX_EXPIRATION = 604800L;
    /** The arguments of a call that gives the expiration: the stage, the path and the seconds. */
    public static final int EXPIRY_ARGUMENTS = 3;

    public GetPresignedUrl(final NamedStageLocator stages, final EngineConfig config) {
        super("GET_PRESIGNED_URL", StringType.VARCHAR, stages, config);
    }

    @Override
    public Object call(final List<Object> args, final boolean pathFolded) {
        final NamedStage stage = requireStage(args.get(0));
        final long expiration = args.size() >= EXPIRY_ARGUMENTS ? expiration(args.get(2)) : DEFAULT_EXPIRATION;
        if (args.get(1) == null) {
            return null;
        }
        final Path path = stage.fileAt(args.get(1).toString());
        if (path == null) {
            // A stage whose location this engine cannot reach has no file to sign for.
            throw new RuntimeException("Valid credentials are required for this stage location.");
        }
        final long expiresAt = Instant.now().getEpochSecond() + expiration;
        final String name = path.getFileName() == null ? "file" : StagePathSegments.diskName(path);
        // The token signs the path the file's name spells, so the URL keeps reaching the file when later names
        // continue that name and the file moves inside the directory of its name.
        return baseUrl() + PresignedUrls.CONTEXT + PresignedUrls.sign(StagePathSegments.namedPath(path), expiresAt)
            + "/" + URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * An expiration in seconds, refused as the account refuses it while the statement compiles: a whole number from
     * 1 to 604800 — {@code 60.0} and {@code 1e2} are whole, {@code 0.5}, {@code '60'}, TRUE and NULL are not — with
     * zero refused in a sentence of its own.
     *
     * @param value the argument's value
     * @return the seconds
     */
    public static long expiration(final Object value) {
        BigDecimal seconds = null;
        if (value instanceof BigDecimal) {
            seconds = (BigDecimal) value;
        } else if (value instanceof Double || value instanceof Float) {
            final double d = ((Number) value).doubleValue();
            seconds = Double.isFinite(d) ? BigDecimal.valueOf(d) : null;
        } else if (value instanceof Number) {
            seconds = new BigDecimal(value.toString());
        }
        if (seconds == null || seconds.signum() < 0 || seconds.compareTo(BigDecimal.valueOf(MAX_EXPIRATION)) > 0
                || seconds.stripTrailingZeros().scale() > 0) {
            throw new RuntimeException(SqlCompilationError.inline(
                "GET_PRESIGNED_URL expiry in seconds is invalid. Must be between 0 and 604,800 (1 week)."));
        }
        if (seconds.signum() == 0) {
            throw new RuntimeException(SqlCompilationError.inline("Presigned URL expiry in seconds is invalid based"
                + " on the stage credential type. Must be between 1 and 604,800."));
        }
        return seconds.longValueExact();
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    /**
     * Four: the account declares a fourth argument no call may pass, so a call of four is refused "Invalid number of
     * arguments" (see StageFunctionArguments) and one of five or more by the count sentence, "expected 4".
     */
    @Override
    public int getMaxArgCount() {
        return EXPIRY_ARGUMENTS + 1;
    }
}
