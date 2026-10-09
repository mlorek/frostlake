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
package dev.frostlake.functions.scalar.context;

import dev.frostlake.executor.SqlIdentifiers;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The role a text names for IS_ROLE_IN_SESSION and IS_DATABASE_ROLE_IN_SESSION (live-verified). A text the
 * statement writes is read as an identifier is: bare it folds to upper case, in double quotes it is kept as
 * written. A text a row carries is the name itself, spelled as the account spells it — bare when it needs no
 * quotes, in its quotes otherwise — so {@code held_dr} read from a column names no role where the same text
 * written in the statement names HELD_DR. A text that is no single identifier names nothing.
 */
final class RoleNameText {

    private static final Pattern BARE = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    private RoleNameText() {
    }

    /**
     * The name a text written in the statement names.
     *
     * @param text the text
     * @return the name, or null when the text is no single identifier
     */
    static String written(final String text) {
        if (text == null) {
            return null;
        }
        if (BARE.matcher(text).matches()) {
            return text.toUpperCase(Locale.ROOT);
        }
        if (text.length() > 2 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            final String inside = text.substring(1, text.length() - 1);
            return inside.replace("\"\"", "").indexOf('"') >= 0 ? null : inside.replace("\"\"", "\"");
        }
        return null;
    }

    /**
     * The name a text read from a row names: the text has to be the name's own spelling.
     *
     * @param text the text
     * @return the name, or null when the text spells no name as the account spells one
     */
    static String carried(final String text) {
        final String name = written(text);
        return name != null && SqlIdentifiers.spellCanonical(name).equals(text) ? name : null;
    }

    /**
     * The name an argument names, by the way it reached the call.
     *
     * @param argument the argument's value: a text the statement wrote, or a {@link CarriedRoleName}
     * @return the name, or null when it names nothing
     */
    static String of(final Object argument) {
        if (argument instanceof CarriedRoleName) {
            return carried(((CarriedRoleName) argument).getText());
        }
        return argument == null ? null : written(argument.toString());
    }

    /** Whether the argument is a NULL, which answers NULL. */
    static boolean isNull(final Object argument) {
        return argument == null
            || argument instanceof CarriedRoleName && ((CarriedRoleName) argument).getText() == null;
    }
}
