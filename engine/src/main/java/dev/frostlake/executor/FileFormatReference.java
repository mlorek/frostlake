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

package dev.frostlake.executor;

import dev.frostlake.metastore.Catalog;

/**
 * A FILE_FORMAT that names a file format. Every spelling that gives a NAME rather than a TYPE reaches
 * the same refusal on a real account when nothing holds the name - a bare word, a string literal and
 * {@code FORMAT_NAME = …} alike, on CREATE STAGE, ALTER STAGE and COPY INTO:
 *
 * <pre>
 *   SQL compilation error:
 *   File format 'NOSUCHFORMAT' does not exist or not authorized.
 * </pre>
 *
 * <p>A bare word is a NAME, not a type: {@code FILE_FORMAT = CSV} is refused unless a file format
 * called CSV exists, where {@code FILE_FORMAT = (TYPE = CSV)} declares the type and needs nothing.
 * A qualified name is echoed in full. The check comes before existence is answered, so
 * {@code CREATE STAGE IF NOT EXISTS} over a stage that already stands still refuses (live-verified).
 */
public final class FileFormatReference {

    private FileFormatReference() {
    }

    /**
     * Refuse a name no file format holds.
     *
     * @param catalog     the catalog to resolve against
     * @param writtenName the name as the statement spells it, or null when a TYPE was declared instead
     */
    public static void require(final Catalog catalog, final String writtenName) {
        if (catalog == null || writtenName == null || writtenName.isEmpty()) {
            return;
        }
        try {
            if (catalog.hasFileFormat(writtenName)) {
                return;
            }
        } catch (final RuntimeException unresolvable) {
            // A database or schema part that resolves to nothing: the format is not there either, and
            // the account reports the FORMAT, naming it as the statement wrote it.
        }
        throw new RuntimeException(SqlCompilationError.doesNotExist("File format", writtenName));
    }
}
