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
import dev.frostlake.executor.StagePathSegments;
import dev.frostlake.executor.StatementClock;
import dev.frostlake.types.StringType;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * BUILD_SCOPED_FILE_URL(@stage, 'relative/path' [, use_privatelink_host]): a scoped URL of a staged file,
 * {@code <server>/api/files/<query id>/<account number>/<encoded token>}, valid for 24 hours. The account encrypts the
 * file's path into the token and names its own host; this engine signs the file's local path the way
 * GET_PRESIGNED_URL does and names its HTTP server, which serves the file at that URL (see
 * {@code StageFileUrlHandler}) until the token expires. Every call makes a different URL, as on the account; the calls
 * of one statement share its query-id segment. The third argument is accepted only as the literal TRUE or FALSE and
 * changes nothing here, since the engine has no private-link host. A NULL path answers NULL.
 */
public class BuildScopedFileUrl extends StageUrlFunction {

    /** How long a scoped URL stays valid, in seconds: the account's persisted-result period. */
    public static final long VALIDITY_SECONDS = 86400L;

    /** Keeps the query-id segments of two engine processes apart. */
    private static final String PROCESS_SALT = Long.toHexString(new SecureRandom().nextLong());

    /** The random characters that open every token, so no two calls make the same URL. */
    private static final int NONCE_LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    public BuildScopedFileUrl(final NamedStageLocator stages, final EngineConfig config) {
        super("BUILD_SCOPED_FILE_URL", StringType.VARCHAR, stages, config);
    }

    @Override
    public Object call(final List<Object> args, final boolean pathFolded) {
        final NamedStage stage = requireStage(args.get(0));
        if (args.get(1) == null) {
            return null;
        }
        final Path path = stage.fileAt(args.get(1).toString());
        // The token signs the path the file's name spells, as GET_PRESIGNED_URL's does.
        final Path signed = path != null ? StagePathSegments.namedPath(path)
            : Path.of(PresignedUrls.CONTEXT, "unreachable").toAbsolutePath();
        final String token = PresignedUrls.sign(signed, Instant.now().getEpochSecond() + VALIDITY_SECONDS);
        final String sealed = nonce() + token;
        return baseUrl() + BuildStageFileUrl.CONTEXT + statementQueryId() + "/" + accountNumber() + "/"
            + urlEncoded(Base64.getEncoder().encodeToString(sealed.getBytes(StandardCharsets.UTF_8)), true);
    }

    /**
     * The presigned-URL token a scoped URL's last segment carries, or null when the segment is not one.
     *
     * @param segment the segment, still percent-encoded
     * @return the token
     */
    public static String tokenOf(final String segment) {
        try {
            final String decoded = URLDecoder.decode(segment, StandardCharsets.UTF_8);
            final String sealed = new String(Base64.getDecoder().decode(decoded), StandardCharsets.UTF_8);
            return sealed.length() > NONCE_LENGTH ? sealed.substring(NONCE_LENGTH) : null;
        } catch (final IllegalArgumentException malformed) {
            return null;
        }
    }

    /** Sixteen random hex digits. */
    private static String nonce() {
        final StringBuilder digits = new StringBuilder(Long.toHexString(RANDOM.nextLong()));
        while (digits.length() < NONCE_LENGTH) {
            digits.insert(0, '0');
        }
        return digits.toString();
    }

    /** A query-id-shaped segment, the same for every call of the statement running. */
    private static String statementQueryId() {
        final Instant start = StatementClock.statementStart();
        if (start == null) {
            return UUID.randomUUID().toString();
        }
        return UUID.nameUUIDFromBytes((PROCESS_SALT + ":" + Thread.currentThread().getId() + ":"
            + start.getEpochSecond() + ":" + start.getNano()).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** An account number for the middle segment, fixed for the configured account. */
    private String accountNumber() {
        final String account = config() == null ? "" : String.valueOf(config().getAccountId());
        final long spread = Math.floorMod(account.toUpperCase(Locale.ROOT).hashCode() * 31L + 17L, 90000000000L);
        return Long.toString(10000000000L + spread);
    }

    @Override
    public int getMinArgCount() {
        return 2;
    }

    @Override
    public int getMaxArgCount() {
        return 3;
    }
}
