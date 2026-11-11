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

package dev.frostlake.parser;

import java.util.List;

import org.antlr.v4.runtime.Token;

/** The shape of what follows a star modifier's keyword in its call (see {@link StarModifierContextLines}). */
final class StarModifierShape {

    /** What follows the keyword. */
    final StarModifierRemainder kind;

    /** The index of the bracket that closes a bracketed remainder, or -1. */
    final int group;

    private StarModifierShape(final StarModifierRemainder kind, final int group) {
        this.kind = kind;
        this.group = group;
    }

    /**
     * The shape after a keyword, or null for one none of the measured shapes is.
     *
     * @param spoken  the text's default-channel tokens
     * @param keyword the keyword's index, its call's bracket two before it
     * @return the shape, or null
     */
    static StarModifierShape of(final List<Token> spoken, final int keyword) {
        final int call = StarModifierContextLines.closing(spoken, keyword - 2);
        if (call == keyword + 1) {
            return new StarModifierShape(StarModifierRemainder.BARE, -1);
        }
        if (call == keyword + 2 && name(spoken, keyword + 1)
                || call == keyword + 3 && name(spoken, keyword + 1) && name(spoken, keyword + 2)
                || call == keyword + 4 && name(spoken, keyword + 1) && type(spoken, keyword + 2) == FrostlakeLexer.AS
                    && name(spoken, keyword + 3)) {
            return new StarModifierShape(StarModifierRemainder.WORDS, -1);
        }
        if (type(spoken, keyword + 1) != FrostlakeLexer.LPAREN) {
            return null;
        }
        final int group = StarModifierContextLines.closing(spoken, keyword + 1);
        final int after = type(spoken, group + 1);
        if (group == keyword + 5 && type(spoken, keyword + 3) == FrostlakeLexer.AS && name(spoken, keyword + 4)
                && (after == FrostlakeLexer.RPAREN || after == FrostlakeLexer.COMMA)) {
            return new StarModifierShape(StarModifierRemainder.ALIASED, group);
        }
        if (group == keyword + 3 && name(spoken, keyword + 2) && after == FrostlakeLexer.RPAREN) {
            return new StarModifierShape(StarModifierRemainder.NAMED, group);
        }
        return null;
    }

    private static boolean name(final List<Token> spoken, final int at) {
        return at < spoken.size() && SyntaxErrorListener.isNameLike(spoken.get(at));
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }
}
