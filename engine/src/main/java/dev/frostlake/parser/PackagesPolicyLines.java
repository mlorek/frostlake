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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.antlr.v4.runtime.Token;

/**
 * The lines the account reports for a packages policy written out of its order, LANGUAGE first, and for DESCRIBE
 * DATASET's words after its name (all live-verified):
 *
 * <pre>
 *   CREATE PACKAGES POLICY p COMMENT = 'c' LANGUAGE PYTHON    'COMMENT', then the word after LANGUAGE
 *   CREATE PACKAGES POLICY p COMMENT = 'c'                    'COMMENT' alone
 *   CREATE PACKAGES POLICY p ALLOWLIST = ('a') LANGUAGE …     'ALLOWLIST', '=' and nothing more
 *   CREATE PACKAGES POLICY p LANGUAGE PYTHON, ALLOWLIST = …    ',' alone
 *   DESCRIBE DATASET d x 'y' z                                ''y'', then the end of the input
 * </pre>
 */
final class PackagesPolicyLines {

    private PackagesPolicyLines() {
    }

    /**
     * The lines for a statement of one of the shapes above, or the lines worked out so far for any other.
     *
     * @param spoken the text's default-channel tokens, end of input included
     * @param faults the token indexes of the faults, in order, -1 for the end of input
     * @param lines  the lines worked out so far
     * @return the lines
     */
    static List<String> lines(final List<Token> spoken, final List<Integer> faults, final List<String> lines) {
        if (faults.isEmpty() || lines == null || lines.isEmpty()) {
            return lines;
        }
        final int fault = spokenIndex(spoken, faults.get(0).intValue());
        if (fault < 0) {
            return lines;
        }
        final int name = policyName(spoken);
        if (name >= 0 && fault == name + 1) {
            final Token at = spoken.get(fault);
            if (at.getType() == FrostlakeLexer.COMMENT) {
                final List<String> told = new ArrayList<String>();
                told.add(SyntaxErrorListener.sentence(at));
                // COMMENT = '…' is read as the policy's comment; a LANGUAGE after it names the word it holds.
                if (type(spoken, fault + 3) == FrostlakeLexer.LANGUAGE && fault + 4 < spoken.size()
                        && spoken.get(fault + 4).getType() != Token.EOF) {
                    told.add(SyntaxErrorListener.sentence(spoken.get(fault + 4)));
                }
                return told;
            }
            if (SyntaxErrorListener.isNameLike(at) && type(spoken, fault + 1) == FrostlakeLexer.EQ) {
                final List<String> told = new ArrayList<String>();
                told.add(SyntaxErrorListener.sentence(at));
                told.add(SyntaxErrorListener.sentence(spoken.get(fault + 1)));
                return told;
            }
        }
        if (name >= 0 && fault == name + 3 && spoken.get(fault).getType() == FrostlakeLexer.COMMA
                && type(spoken, name + 1) == FrostlakeLexer.LANGUAGE) {
            return Collections.singletonList(SyntaxErrorListener.sentence(spoken.get(fault)));
        }
        if (describesDataset(spoken) && fault == 4 && spoken.get(4).getType() == FrostlakeLexer.STRING_LITERAL
                && SyntaxErrorListener.isNameLike(spoken.get(3)) && SyntaxErrorListener.isNameLike(spoken.get(5))
                && type(spoken, 6) == Token.EOF) {
            final List<String> told = new ArrayList<String>();
            told.add(SyntaxErrorListener.sentence(spoken.get(4)));
            told.add(SyntaxErrorListener.sentence(spoken.get(6)));
            return told;
        }
        return lines;
    }

    /** The index of the policy's name in a CREATE [OR REPLACE] PACKAGES POLICY, or -1 for another statement. */
    private static int policyName(final List<Token> spoken) {
        if (type(spoken, 0) != FrostlakeLexer.CREATE) {
            return -1;
        }
        int at = 1;
        if (type(spoken, at) == FrostlakeLexer.OR && type(spoken, at + 1) == FrostlakeLexer.REPLACE) {
            at += 2;
        }
        return type(spoken, at) == FrostlakeLexer.PACKAGES && type(spoken, at + 1) == FrostlakeLexer.POLICY
            && at + 2 < spoken.size() && SyntaxErrorListener.isNameLike(spoken.get(at + 2)) ? at + 2 : -1;
    }

    /** Whether the statement is a DESCRIBE DATASET of a one-part name. */
    private static boolean describesDataset(final List<Token> spoken) {
        return (type(spoken, 0) == FrostlakeLexer.DESCRIBE || type(spoken, 0) == FrostlakeLexer.DESC)
            && spoken.size() > 2 && "DATASET".equalsIgnoreCase(spoken.get(1).getText())
            && SyntaxErrorListener.isNameLike(spoken.get(2));
    }

    private static int type(final List<Token> spoken, final int at) {
        return at >= 0 && at < spoken.size() ? spoken.get(at).getType() : Token.EOF;
    }

    /** The index in {@code spoken} of the token with the stream index {@code tokenIndex}; -1 for the end of input. */
    private static int spokenIndex(final List<Token> spoken, final int tokenIndex) {
        if (tokenIndex < 0) {
            return -1;
        }
        for (int i = 0; i < spoken.size(); i++) {
            if (spoken.get(i).getTokenIndex() == tokenIndex) {
                return i;
            }
        }
        return -1;
    }
}
