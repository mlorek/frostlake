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

package dev.frostlake.functions.scalar.string;

import dev.frostlake.functions.SemiStructuredRejection;
import dev.frostlake.functions.TextArgumentFunction;
import dev.frostlake.types.StringType;

import java.util.List;

/**
 * SOUNDEX(text) — the American Soundex code of a text: its first character exactly as it was written, then the codes
 * of up to three of the letters after it, padded with zeros.
 *
 * <p>Only the ASCII letters carry a code, in either case: B F P V are 1, C G J K Q S X Z are 2, D T are 3, L is 4, M N
 * are 5 and R is 6. A letter whose code equals the one written before it is not written again. The vowels A E I O U
 * and Y write nothing but separate two equal codes, so {@code SOUNDEX('bab')} is {@code b100}; H, W and every other
 * character, accented letters, digits, punctuation and spaces included, are passed over as if absent, so
 * {@code SOUNDEX('Ashcraft')} is {@code A261}. The first character is kept whatever it is, and when it is a letter
 * its own code counts as written: {@code SOUNDEX('Pfister')} is {@code P236}. The empty text is {@code 0000}
 * (live-verified).
 */
public class Soundex extends TextArgumentFunction {

    /** The digit count after the first character. */
    private static final int DIGITS = 3;

    /** What a vowel writes: nothing, though it separates two equal codes. */
    private static final char VOWEL = '0';

    /** What a character that is passed over carries. */
    private static final char SILENT = ' ';

    /** Whether the first letter's own code is left unwritten, so a letter after it that shares it is coded. */
    private final boolean codesSharedSecondLetter;

    public Soundex() {
        this("SOUNDEX", false);
    }

    /**
     * @param name                    the function name
     * @param codesSharedSecondLetter whether a letter sharing the first letter's code is written after it
     */
    protected Soundex(final String name, final boolean codesSharedSecondLetter) {
        super(name, StringType.VARCHAR);
        this.codesSharedSecondLetter = codesSharedSecondLetter;
    }

    @Override
    public Object evaluate(final List<Object> args) {
        if (args.get(0) == null) return null;
        final String text = args.get(0).toString();
        if (text.isEmpty()) return "0000";
        final int first = text.codePointAt(0);
        final StringBuilder code = new StringBuilder().appendCodePoint(first);
        char previous = codesSharedSecondLetter ? VOWEL : codeOf(first);
        int written = 0;
        for (int i = Character.charCount(first); i < text.length() && written < DIGITS; i++) {
            final char letterCode = codeOf(text.charAt(i));
            if (letterCode == SILENT) continue;
            if (letterCode != VOWEL && letterCode != previous) {
                code.append(letterCode);
                written++;
            }
            previous = letterCode;
        }
        for (; written < DIGITS; written++) {
            code.append('0');
        }
        return code.toString();
    }

    /** The code of one character: a digit for a consonant, {@link #VOWEL}, or {@link #SILENT}. */
    private static char codeOf(final int character) {
        switch (character) {
            case 'B': case 'F': case 'P': case 'V':
            case 'b': case 'f': case 'p': case 'v':
                return '1';
            case 'C': case 'G': case 'J': case 'K': case 'Q': case 'S': case 'X': case 'Z':
            case 'c': case 'g': case 'j': case 'k': case 'q': case 's': case 'x': case 'z':
                return '2';
            case 'D': case 'T':
            case 'd': case 't':
                return '3';
            case 'L':
            case 'l':
                return '4';
            case 'M': case 'N':
            case 'm': case 'n':
                return '5';
            case 'R':
            case 'r':
                return '6';
            case 'A': case 'E': case 'I': case 'O': case 'U': case 'Y':
            case 'a': case 'e': case 'i': case 'o': case 'u': case 'y':
                return VOWEL;
            default:
                return SILENT;
        }
    }

    @Override
    public int getMinArgCount() { return 1; }
    @Override
    public int getMaxArgCount() { return 1; }

    /** A BINARY is no text here: the account refuses it by the argument types (live-verified). */
    @Override
    public SemiStructuredRejection binaryRejection(final int position) {
        return SemiStructuredRejection.ARGUMENT_TYPES;
    }
}
