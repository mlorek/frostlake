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
import dev.frostlake.parser.FrostlakeParser;

/**
 * A parenthesized list written where one value goes, read as the account reads it: as the parser's own word for it
 * (live-verified). A list of properties, {@code (a = 'b')}, is {@code TOK_PROPERTY_LIST}; a list of constants,
 * {@code ('a', 'b')} or {@code ()}, is {@code TOK_CONSTANT_LIST}, and {@code TOK_LIST} when one of the constants is a
 * list of properties itself, {@code ((a = 'b'))}.
 */
final class ListValues {

    /** The word for a list of properties. */
    static final String PROPERTY_LIST = "TOK_PROPERTY_LIST";

    /** The word for a list of constants. */
    static final String CONSTANT_LIST = "TOK_CONSTANT_LIST";

    /** The word for a list of constants that holds a list of properties. */
    static final String NESTED_LIST = "TOK_LIST";

    private ListValues() {
    }

    /** Whether the list is one of properties. */
    static boolean isPropertyList(final FrostlakeParser.ListValueContext list) {
        return !list.listedProperty().isEmpty();
    }

    /** The parser's word for the list. */
    static String word(final FrostlakeParser.ListValueContext list) {
        if (isPropertyList(list)) {
            return PROPERTY_LIST;
        }
        for (final FrostlakeParser.ListedConstantContext constant : list.listedConstant()) {
            if (constant.LPAREN() != null) {
                return NESTED_LIST;
            }
        }
        return CONSTANT_LIST;
    }

    /**
     * A contact's purpose written as a list is none: a list of properties is "Purpose type TOK_PROPERTY_LIST is not
     * valid, please use access_approval, steward and support.", and a list of constants an invalid value of the
     * parameter {@code purposeValue}.
     */
    static RuntimeException noPurpose(final FrostlakeParser.ListValueContext list) {
        if (isPropertyList(list)) {
            return new RuntimeException(SqlCompilationError.inline("Purpose type " + PROPERTY_LIST
                + " is not valid, please use access_approval, steward and support."));
        }
        return new RuntimeException(SqlCompilationError.invalidValueForParameter(word(list), "purposeValue"));
    }
}
