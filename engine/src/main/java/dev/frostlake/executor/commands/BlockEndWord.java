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
import dev.frostlake.parser.ClassNameWords;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;

/**
 * The word written after the END of a body's top block. The account reads one there, as it reads a loop's label
 * after END LOOP: {@code BEGIN RETURN 1; END IF;} answers 1, and so it does ended by LOOP, CASE, DO, UNTIL, a quoted
 * name or any keyword that may be a name. A plain word names nothing a block may end with and is refused where it
 * stands, once the text has parsed: a second word after it is the one the parser stops at (all live-verified). What
 * the word does to a nested block is UnrunBlocks'.
 */
final class BlockEndWord {

    private BlockEndWord() {
    }

    /**
     * Refuse a plain word written after a block's END.
     *
     * @param block the top block of a body
     */
    static void requireKnown(final FrostlakeParser.BeginEndBlockContext block) {
        final FrostlakeParser.BlockEndWordContext word = block.blockEndWord();
        if (word == null || word.IDENTIFIER() == null) {
            return;
        }
        final Token plain = word.IDENTIFIER().getSymbol();
        if (ClassNameWords.isClassWord(plain)) {
            throw new RuntimeException(SqlCompilationError.of(ShowScopeRefusal.syntaxErrorAt(plain, plain.getText())));
        }
    }
}
