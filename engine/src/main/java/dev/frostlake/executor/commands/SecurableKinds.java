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
 * The kind of object a GRANT, a REVOKE or a SHOW GRANTS ON names, spelled as grants record it and SHOW GRANTS reports
 * it: {@code FILE FORMAT} is {@code FILE_FORMAT}, {@code ROW ACCESS POLICY} is {@code ROW_ACCESS_POLICY}. Live reads a
 * multi-word kind in words only; written as one word it is not a kind at all (live-verified).
 */
final class SecurableKinds {

    private SecurableKinds() {
    }

    /**
     * A kind as written in words.
     *
     * @param kind the kind's words
     * @return the words upper-cased and joined by underscores
     */
    static String of(final FrostlakeParser.SecurableKindContext kind) {
        final StringBuilder name = new StringBuilder();
        for (int i = 0; i < kind.getChildCount(); i++) {
            if (i > 0) {
                name.append('_');
            }
            name.append(kind.getChild(i).getText().toUpperCase());
        }
        return name.toString();
    }

    /**
     * The kind of a GRANT or a REVOKE. A multi-word kind written as one word is refused ahead of everything else the
     * statement names, the object and the grantee included: {@code Object type or Class 'FILE_FORMAT' does not exist
     * or not authorized.}
     *
     * @param type the kind as written
     * @return the kind, spelled as grants record it
     */
    static String of(final FrostlakeParser.ObjectTypeContext type) {
        if (type.securableKind() == null) {
            throw new RuntimeException(SqlCompilationError.inline("Object type or Class '"
                + type.getText().toUpperCase() + "' does not exist or not authorized."));
        }
        return of(type.securableKind());
    }
}
