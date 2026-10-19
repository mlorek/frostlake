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
 * What an S3, GCS or Azure stage location must spell before a stage may stand on it, as CREATE STAGE and ALTER
 * STAGE ... SET URL judge it (live-verified). The scheme, in any case, is followed by {@code //}, or the location is
 * no URL at all: {@code invalid URL: s3:x/}. The bucket, everything up to the next slash, is not empty and holds only
 * letters, digits, hyphens, periods and underscores; any other character, a space or a {@code ?} included, is refused
 * in the account's own sentence, which names the bucket as written. An Azure location names a storage account's host
 * and a container (see requireAzure).
 */
final class CloudStageUrls {

    /** The schemes whose location starts with a bucket. */
    private static final String[] BUCKET_SCHEMES = {"s3", "s3gov", "s3china", "gcs"};

    /** The one Azure endpoint a stage stands on: the blob service of the public cloud. */
    private static final String AZURE_BLOB = "blob.core.windows.net";

    /** The Data Lake endpoint, which the account knows and refuses by its name. */
    private static final String AZURE_DATA_LAKE = "dfs.core.windows.net";

    /** The blob endpoints of the Government and China clouds, refused for this deployment. */
    private static final String AZURE_GOVERNMENT_BLOB = "blob.core.usgovcloudapi.net";
    private static final String AZURE_CHINA_BLOB = "blob.core.chinacloudapi.cn";

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
        final boolean azure = colon > 0 && "azure".equalsIgnoreCase(url.substring(0, colon));
        if (colon <= 0 || !azure && !isBucketScheme(url.substring(0, colon))) {
            return;
        }
        if (!url.startsWith("//", colon + 1)) {
            throw new RuntimeException(SqlCompilationError.of("invalid URL: " + url));
        }
        final int start = colon + 3;
        if (azure) {
            requireAzure(url, start);
            return;
        }
        final int slash = url.indexOf('/', start);
        final String bucket = slash < 0 ? url.substring(start) : url.substring(start, slash);
        if (!isBucketName(bucket)) {
            throw new RuntimeException("Bucket name '" + bucket + "' in the stage location is not supported. Valid"
                + " bucket names must consist of lowercase letters, digits, hyphens '-', and periods '.'.");
        }
    }

    /**
     * An Azure location is {@code azure://<account>.<endpoint>/<container>}, a path after it optional: a location
     * without a storage account, an endpoint or a container is "Invalid location format or value '…'.". The endpoint,
     * in any case, is then the public cloud's blob service: the Data Lake endpoint is "Provided Azure storage endpoint
     * 'dfs.core.windows.net' is currently not supported.", a Government or China cloud's blob service is refused for
     * this deployment, and any other host is named whole as an unsupported endpoint (live-verified).
     *
     * @param url   the location as written
     * @param start where its host starts, after the {@code //}
     */
    private static void requireAzure(final String url, final int start) {
        final int slash = url.indexOf('/', start);
        final String host = slash < 0 ? url.substring(start) : url.substring(start, slash);
        final int containerEnd = slash < 0 ? -1 : url.indexOf('/', slash + 1);
        final String container = slash < 0 ? ""
            : containerEnd < 0 ? url.substring(slash + 1) : url.substring(slash + 1, containerEnd);
        final int dot = host.indexOf('.');
        if (dot <= 0 || dot == host.length() - 1 || container.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of("Invalid location format or value '" + url + "'."));
        }
        final String endpoint = host.substring(dot + 1);
        final String lower = endpoint.toLowerCase(Locale.ROOT);
        if (AZURE_BLOB.equals(lower)) {
            return;
        }
        if (AZURE_DATA_LAKE.equals(lower)) {
            throw new RuntimeException(SqlCompilationError.of("Provided Azure storage endpoint '" + endpoint
                + "' is currently not supported."));
        }
        if (AZURE_GOVERNMENT_BLOB.equals(lower)) {
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " on non-Government deployments, using an Azure Government storage location is not allowed.");
        }
        if (AZURE_CHINA_BLOB.equals(lower)) {
            throw new RuntimeException(SqlCompilationError.PREFIX
                + " on non-China deployments, using an Azure China storage location is not allowed.");
        }
        throw new RuntimeException(SqlCompilationError.of("Provided Azure storage endpoint '" + host
            + "' is currently not supported."));
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
