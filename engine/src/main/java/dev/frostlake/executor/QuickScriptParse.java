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

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.atn.PredictionMode;

/**
 * A script parsed with SLL prediction, the first of the grammar's two stages: several times faster than full LL
 * prediction, and where it reads a text without a fault it reads it exactly as LL does. It answers only for a text
 * it reads cleanly - no lexical fault, no syntax error, nothing an action of the grammar reports - and gives up on
 * everything else, which the full parse then reads and words. So no statement is refused, accepted or read
 * differently for the quicker stage; a text it gives up on is parsed twice.
 */
final class QuickScriptParse {

    private final CommonTokenStream tokens;
    private final FrostlakeParser.SqlScriptContext tree;

    private QuickScriptParse(final CommonTokenStream tokens, final FrostlakeParser.SqlScriptContext tree) {
        this.tokens = tokens;
        this.tree = tree;
    }

    /**
     * The script as SLL prediction reads it.
     *
     * @param sql the script's text
     * @return the parse, or null where this stage does not read the text cleanly
     */
    static QuickScriptParse of(final String sql) {
        final boolean[] faulted = {false};
        final BaseErrorListener anyFault = new BaseErrorListener() {
            @Override
            public void syntaxError(final Recognizer<?, ?> recognizer, final Object offendingSymbol, final int line,
                                    final int charPositionInLine, final String msg, final RecognitionException e) {
                faulted[0] = true;
            }
        };
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(sql));
        lexer.removeErrorListeners();
        lexer.addErrorListener(anyFault);
        final CommonTokenStream tokens = new CommonTokenStream(lexer);
        final FrostlakeParser parser = new FrostlakeParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(anyFault);
        parser.setErrorHandler(new BailErrorStrategy());
        parser.getInterpreter().setPredictionMode(PredictionMode.SLL);
        try {
            final FrostlakeParser.SqlScriptContext tree = parser.sqlScript();
            return faulted[0] ? null : new QuickScriptParse(tokens, tree);
        } catch (final RuntimeException gaveUp) {
            return null;
        }
    }

    CommonTokenStream tokens() {
        return tokens;
    }

    FrostlakeParser.SqlScriptContext tree() {
        return tree;
    }
}
