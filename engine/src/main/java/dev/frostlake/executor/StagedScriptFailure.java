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

import dev.frostlake.parser.FrostlakeParser;

/**
 * How EXECUTE IMMEDIATE FROM reports its staged script's failure, the file named by its stage's name alone. A statement
 * that failed is "Uncaught exception of type 'STATEMENT_ERROR' in file @ST/s.sql on line 2 at position 0: " and,
 * on a line of its own, what it failed with - a statement a block of the script ran is placed where it stands, inside
 * the block - and a script that does not parse is "Uncaught exception of type 'STATEMENT_ERROR' in file @ST/s.sql: "
 * and the syntax error, placed in the file (live-verified).
 */
final class StagedScriptFailure {

    private StagedScriptFailure() {
    }

    /**
     * A statement of the script that failed.
     *
     * @param file      the file, as the refusal names it
     * @param statement the statement of the script that failed
     * @param failed    its failure
     * @return the script's refusal
     */
    static RuntimeException inStatement(final String file, final FrostlakeParser.StatementContext statement,
                                        final RuntimeException failed) {
        if (failed instanceof UncaughtStatementFailure && AnonymousBlockResult.isBlock(statement)) {
            final UncaughtStatementFailure inBlock = (UncaughtStatementFailure) failed;
            return new RuntimeException("Uncaught exception of type '" + inBlock.getKind() + "' in file " + file
                + " on line " + inBlock.getLine() + " at position " + inBlock.getPosition() + ": \n"
                + inBlock.getError(), failed);
        }
        return new RuntimeException("Uncaught exception of type 'STATEMENT_ERROR' in file " + file + " on line "
            + statement.getStart().getLine() + " at position " + statement.getStart().getCharPositionInLine() + ": \n"
            + failed.getMessage(), failed);
    }

    /**
     * A script that does not parse.
     *
     * @param file   the file, as the refusal names it
     * @param failed the syntax error
     * @return the script's refusal
     */
    static RuntimeException unparsed(final String file, final RuntimeException failed) {
        return new RuntimeException("Uncaught exception of type 'STATEMENT_ERROR' in file " + file + ": \n"
            + failed.getMessage(), failed);
    }
}
