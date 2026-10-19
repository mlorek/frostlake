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

import java.util.List;

/**
 * One option of a written FILE_FORMAT list, whichever shape the list takes (see {@link FileFormatClause}): its name
 * in upper case, and either a scalar value - as the statement spells it, for a refusal's echo, and decoded - or a
 * parenthesized list of decoded strings.
 */
final class WrittenFormatOption {

    final String name;

    /** The scalar value as written, quotes and all; null for a list. */
    final String written;

    /** The scalar value decoded; null for a list. */
    final String value;

    /** Whether the scalar value was a string literal. */
    final boolean string;

    /** The list's decoded values; null for a scalar. */
    final List<String> listed;

    WrittenFormatOption(final String name, final String written, final String value, final boolean string,
                        final List<String> listed) {
        this.name = name;
        this.written = written;
        this.value = value;
        this.string = string;
        this.listed = listed;
    }
}
