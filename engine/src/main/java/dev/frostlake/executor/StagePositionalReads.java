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

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Whether the query block that reads a staged-file source reads one of the files' positional columns — $1, $2, …,
 * qualified or not, in any clause. The account names the column being read in a staged file's refusal only then, and
 * decodes the values of a binary record format only then: under {@code SELECT COUNT(*)}, a constant or METADATA$
 * columns alone the refusal names no column, and a Parquet or ORC file's string that is no UTF-8 is not refused
 * (live-verified).
 */
final class StagePositionalReads {

    private StagePositionalReads() {
    }

    /**
     * Whether the block reading {@code source} reads a positional column.
     *
     * @param source the staged-file source
     * @return true when the block reads one, and where no block is found
     */
    static boolean readBy(final ParseTree source) {
        ParseTree block = source.getParent();
        while (block != null && !(block instanceof FrostlakeParser.SelectClauseContext)) {
            block = block.getParent();
        }
        return block == null || readsPosition(block, source);
    }

    private static boolean readsPosition(final ParseTree node, final ParseTree source) {
        if (node == source || node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof TerminalNode) {
            return ((TerminalNode) node).getSymbol().getType() == FrostlakeParser.POSITIONAL_PARAMETER;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsPosition(node.getChild(i), source)) {
                return true;
            }
        }
        return false;
    }
}
