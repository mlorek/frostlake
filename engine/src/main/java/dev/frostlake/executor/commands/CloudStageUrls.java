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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.SqlCompilationError;

import java.util.Locale;

/**
 * What an S3 or GCS stage location must spell before a stage may stand on it, as CREATE STAGE and ALTER STAGE ... SET
 * URL judge it (live-verified). The scheme, in any case, is followed by {@code //}, or the location is no URL at all:
 * {@code invalid URL: s3:x/}. The bucket, everything up to the next slash, is not empty and holds only letters, digits,
 * hyphens, periods and underscores; any other character, a space or a {@code ?} included, is refused in the account's
 * own sentence, which names the bucket as written.
 */
final class CloudStageUrls {

    /** The schemes whose location starts with a bucket. */
    private static final String[] BUCKET_SCHEMES = {"s3", "s3gov", "s3china", "gcs"};

    private CloudStageUrls() {
    }

    /**
     * Refuse a stage location an S3 or GCS stage cannot stand on; any other location passes untouched.
     *
     * @param url the location as the statement writes it, or null for none
     */
    static void require(final String url) {
        if (url == null) {
            return;
        }
        final int colon = url.indexOf(':');
        if (colon <= 0 || !isBucketScheme(url.substring(0, colon))) {
            return;
        }
        if (!url.startsWith("//", colon + 1)) {
            throw new RuntimeException(SqlCompilationError.of("invalid URL: " + url));
        }
        final int start = colon + 3;
        final int slash = url.indexOf('/', start);
        final String bucket = slash < 0 ? url.substring(start) : url.substring(start, slash);
        if (!isBucketName(bucket)) {
            throw new RuntimeException("Bucket name '" + bucket + "' in the stage location is not supported. Valid"
                + " bucket names must consist of lowercase letters, digits, hyphens '-', and periods '.'.");
        }
    }

    private static boolean isBucketScheme(final String scheme) {
        final String lower = scheme.toLowerCase(Locale.ROOT);
        for (final String bucketScheme : BUCKET_SCHEMES) {
            if (bucketScheme.equals(lower)) {
                return true;
            }
        }
        return false;
    }

    /** Letters of any case and script, digits, hyphens, periods and underscores, at least one of them. */
    private static boolean isBucketName(final String bucket) {
        if (bucket.isEmpty()) {
            return false;
        }
        for (int i = 0; i < bucket.length(); i++) {
            final char c = bucket.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '-' && c != '.' && c != '_') {
                return false;
            }
        }
        return true;
    }
}
