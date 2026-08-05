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

package dev.frostlake.functions.scalar.encoding;

/**
 * The compression algorithms {@code COMPRESS} / {@code DECOMPRESS_STRING} / {@code DECOMPRESS_BINARY}
 * accept. This is the complete set — live-verified, every other spelling (including
 * {@code deflate}, {@code raw_deflate}, {@code gzip}, {@code bzip2}, {@code zstandard} and
 * {@code lz4}) is rejected by the account with "Unknown compression method '…'".
 */
public enum CompressionAlgorithm {
    SNAPPY,
    ZLIB,
    ZSTD,
    BZ2
}
