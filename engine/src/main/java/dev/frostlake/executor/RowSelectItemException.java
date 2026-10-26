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

/**
 * A select item that is a ROW, refused as {@code Unsupported: SELECT list element with multiple columns.}: an INSERT
 * reads it as such, to judge its source as a write first (see {@code InsertExecutor}).
 */
public class RowSelectItemException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * @param message the refusal
     */
    public RowSelectItemException(final String message) {
        super(message);
    }
}
