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

import dev.frostlake.executor.expressions.AntlrExpressionParser;
import dev.frostlake.metastore.model.DefaultValueExpression;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A column DEFAULT that reads the columns defined before it, {@code c INT DEFAULT c2 + 1} (all live-verified): an
 * insert that leaves the column out writes the default over the row it inserts, the earlier columns' own defaults
 * applied first, so {@code (c2) VALUES (5)} writes 6; such a default given by the DEFAULT keyword in a VALUES list is
 * {@code Invalid expression [DE.C2 + 1] in VALUES clause}, and {@code UPDATE … SET c = DEFAULT} is {@code Unsupported
 * use of DEFAULT in UPDATE SET clause: C=DEFAULT needs non-updated value of column C2}, whatever rows it would reach.
 */
public final class ColumnReadingDefaults {

    /** The words a bare default names that read no column. */
    private static final Set<String> NO_COLUMN = new HashSet<String>(Arrays.asList("CURRENT_TIMESTAMP",
        "CURRENT_DATE", "CURRENT_TIME", "LOCALTIMESTAMP", "LOCALTIME", "TRUE", "FALSE", "NULL"));

    private ColumnReadingDefaults() {
    }

    /**
     * The columns a default reads, their canonical names in the order first read; none for a default that is no
     * expression or one that does not parse.
     *
     * @param defaultValue the column's recorded default
     * @return the names
     */
    public static List<String> columnsRead(final Object defaultValue) {
        final List<String> names = new ArrayList<String>();
        if (!(defaultValue instanceof DefaultValueExpression)) {
            return names;
        }
        final ParseTree tree;
        try {
            tree = AntlrExpressionParser.parseTree(((DefaultValueExpression) defaultValue).getExpressionText());
        } catch (final RuntimeException unparsed) {
            return names;
        }
        collect(tree, names);
        return names;
    }

    private static void collect(final ParseTree node, final List<String> names) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            final String last = parts[parts.length - 1];
            if (!"NEXTVAL".equals(last) && !(parts.length == 1 && NO_COLUMN.contains(last))
                    && !names.contains(last)) {
                names.add(last);
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), names);
        }
    }

    /**
     * The refusal of a DEFAULT keyword in a VALUES list for a column whose default reads columns.
     *
     * @param defaultValue the column's default
     * @return the refusal, naming the default as the account tells it back
     */
    public static RuntimeException inValues(final DefaultValueExpression defaultValue) {
        return new RuntimeException(SqlCompilationError.of(
            "Invalid expression [" + defaultValue.getToldText() + "] in VALUES clause"));
    }

    /**
     * The refusal of {@code SET c = DEFAULT} for a column whose default reads columns.
     *
     * @param column the column set, canonical
     * @param read   the first column its default reads, canonical
     * @return the refusal
     */
    public static RuntimeException inUpdate(final String column, final String read) {
        return new RuntimeException(SqlCompilationError.of("Unsupported use of DEFAULT in UPDATE SET clause: "
            + SqlIdentifiers.spellCanonical(column) + "=DEFAULT needs non-updated value of column "
            + SqlIdentifiers.spellCanonical(read)));
    }
}
