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

import java.util.Locale;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.TokenStream;

/**
 * The two-word scope kinds a SHOW listing reads after its IN — {@code APPLICATION PACKAGE}, {@code COMPUTE POOL},
 * {@code FAILOVER GROUP} and {@code REPLICATION GROUP} — each written as the scope's first word straight after the
 * IN and the kind's second word after it, and which single words read a name after them at all (live-verified).
 */
public final class ShowScopeWords {

    /** The single-word scope kinds that read a name after them, each a keyword to live's lexer. */
    private static final String[] NAMING_KINDS = {
        "VIEW", "PIPE", "SERVICE", "APPLICATION", "CLASS", "ORGANIZATION", "CONNECTION"
    };

    private ShowScopeWords() {
    }

    /**
     * Whether the scope a SHOW listing names right after its IN reads a name after it: the instance of the class
     * the scope names, or the object a scope kind names. A single unquoted word that live's lexer keeps as a
     * keyword ({@link ClassNameWords#isClassWord}) reads none unless it is such a kind — MODEL is one for the plain
     * {@code SHOW FUNCTIONS} alone — so the word names a schema on its own, and whatever follows it that does not
     * open the listing's modifiers is a syntax error where it stands: a name, a quoted or qualified one, or another
     * keyword, as in
     * {@code SHOW TABLES IN MATERIALIZED VIEW v} and {@code SHOW TABLES IN DATASET d} (live-verified). An
     * IDENTIFIER() reference is read as the name all the same, so that it is not taken for the opener of another
     * statement: the statement then refuses it at its IDENTIFIER word (ShowScopeRefusal).
     *
     * @param input the tokens, read from just after the scope's name
     * @return whether a name may follow the scope's name
     */
    public static boolean takesInstance(final TokenStream input) {
        return takesInstance(new Token[] {input.LT(-1), input.LT(-2), input.LT(-3), input.LT(-4), input.LT(-5),
            input.LT(-6)}, input.LA(1));
    }

    /**
     * Whether the parse refused {@code refused} because the scope written just before it reads no name: the token
     * after a keyword written alone as a SHOW listing's scope ({@link #takesInstance}).
     *
     * @param stream  the parse's tokens
     * @param refused the refused token
     * @return whether the word before it is such a scope
     */
    public static boolean refusedAfterScopeWord(final TokenStream stream, final Token refused) {
        final Token[] before = new Token[6];
        int index = refused.getTokenIndex();
        for (int k = 0; k < before.length && index > 0; k++) {
            index--;
            while (index > 0 && stream.get(index).getChannel() != Token.DEFAULT_CHANNEL) {
                index--;
            }
            before[k] = stream.get(index).getChannel() == Token.DEFAULT_CHANNEL ? stream.get(index) : null;
        }
        return before[1] != null && before[1].getType() == FrostlakeLexer.IN
            && !takesInstance(before, refused.getType());
    }

    /**
     * {@link #takesInstance} over the six tokens before the one that follows the scope's name, nearest first — the
     * scope's word, the IN, and the four before it — and the type of that following token.
     */
    private static boolean takesInstance(final Token[] before, final int next) {
        final Token word = before[0];
        final Token in = before[1];
        if (in == null || in.getType() != FrostlakeLexer.IN || word == null
                || word.getType() == FrostlakeLexer.QUOTED_IDENTIFIER || ClassNameWords.isClassWord(word)
                || next == FrostlakeParser.KW_IDENTIFIER_REF || next == FrostlakeParser.KW_IDENTIFIER_OPEN) {
            return true;
        }
        final String upper = word.getText().toUpperCase(Locale.ROOT);
        for (final String kind : NAMING_KINDS) {
            if (kind.equals(upper)) {
                return true;
            }
        }
        return "MODEL".equals(upper) && listsFunctions(before);
    }

    /**
     * Whether the IN before the scope's name closes the head of the plain function listing,
     * {@code SHOW FUNCTIONS [LIKE '…'] IN}: a USER, BUILTIN or TERSE listing reads MODEL as no kind (live-verified).
     */
    private static boolean listsFunctions(final Token[] before) {
        final int listing = before[2] != null && before[2].getType() == FrostlakeLexer.STRING_LITERAL ? 4 : 2;
        if (listing == 4 && (before[3] == null || before[3].getType() != FrostlakeLexer.LIKE)) {
            return false;
        }
        return before[listing] != null && before[listing].getType() == FrostlakeLexer.FUNCTIONS
            && before[listing + 1] != null && before[listing + 1].getType() == FrostlakeLexer.SHOW;
    }

    /**
     * Whether a scope's first word, written straight after IN, and the token after it spell a two-word kind.
     *
     * @param in     the token before the scope's first word, or null
     * @param first  the scope's first word, or null
     * @param second the token after it, or null
     * @return whether the words spell a two-word kind
     */
    public static boolean opensTwoWordKind(final Token in, final Token first, final Token second) {
        if (in == null || in.getType() != FrostlakeLexer.IN || first == null || second == null) {
            return false;
        }
        if (first.getType() == FrostlakeLexer.APPLICATION) {
            return second.getType() == FrostlakeLexer.PACKAGE;
        }
        if (first.getType() == FrostlakeLexer.COMPUTE) {
            return second.getType() == FrostlakeLexer.POOL;
        }
        return first.getType() == FrostlakeLexer.IDENTIFIER && second.getType() == FrostlakeLexer.GROUP
            && ("FAILOVER".equalsIgnoreCase(first.getText()) || "REPLICATION".equalsIgnoreCase(first.getText()));
    }
}
