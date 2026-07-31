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

package dev.frostlake.types;

/**
 * The FILE column type: a reference to a file on a stage. The DECLARED type is {@code FILE} — live
 * {@code DESCRIBE TABLE}, {@code SHOW COLUMNS}, {@code GET_DDL} and
 * {@code INFORMATION_SCHEMA.COLUMNS.DATA_TYPE} all report {@code FILE} for such a column — but a FILE
 * VALUE is not a distinct runtime type: it is an object of file metadata
 * ({@code CONTENT_TYPE / ETAG / LAST_MODIFIED / RELATIVE_PATH / SIZE / STAGE / ...}) and
 * {@code SYSTEM$TYPEOF} of a FILE column reports {@code OBJECT[LOB]}. Hence
 * {@link TypeCategory#SEMI_STRUCTURED} and no dedicated value class.
 *
 * <p>FILE is deliberately NOT a cast target: {@code NULL::FILE} and {@code CAST(NULL AS FILE)} both fail
 * live with "invalid type [...] for parameter 'TO_FILE'", so the cast is rejected where the target type
 * is read (see {@code ExpressionAstBuilder}).
 *
 * <p>The metadata object itself is produced by {@code TO_FILE}, which resolves a stage path
 * against the stage and reads the real file's size, mtime, MD5 and extension-derived content type. A
 * write to a FILE column goes through it, so a stage-path string is resolved on write and fails when it
 * names no file, while a metadata object is validated structurally but not resolved.
 */
public class FileType extends DataType {

    public static final FileType FILE = new FileType();

    public FileType() {
        super("FILE", TypeCategory.SEMI_STRUCTURED);
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        return value;
    }

    @Override
    public String formatValue(final Object value) {
        return value == null ? "NULL" : value.toString();
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other instanceof FileType;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        return this;
    }

    @Override
    public int getSize() {
        return Integer.MAX_VALUE;
    }
}
