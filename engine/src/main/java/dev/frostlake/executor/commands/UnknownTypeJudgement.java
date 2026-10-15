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
import dev.frostlake.parser.SqlSyntaxException;
import dev.frostlake.parser.SyntaxErrorListener;
import dev.frostlake.parser.UnknownTypeReading;
import dev.frostlake.parser.UnknownTypeWord;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * What a statement says of its types besides a word that names none, judged before the word is refused. The
 * account resolves such a word once the statement has parsed, so the widths of the types it does know come first:
 * {@code CREATE TABLE t (n nosuch, v VARCHAR(0))} is refused for the length. An Iceberg table's column list is
 * read column by column instead, the word and every earlier column's own refusal in the order written:
 * {@code (v VARCHAR(10), n nosuch)} is refused for the length the format does not take, {@code (n nosuch, v
 * VARCHAR(10))} for the word (live-verified).
 */
public final class UnknownTypeJudgement {

    private UnknownTypeJudgement() {
    }

    /**
     * The refusal to raise for a statement refused for a word that names no type.
     *
     * @param refused the refusal the parse raised
     * @param parser  the parser a written type is read with
     * @return a refusal of the statement that comes before the word's, else {@code refused} itself
     */
    public static RuntimeException judged(final RuntimeException refused, final ColumnDefinitionParser parser) {
        if (!(refused instanceof SqlSyntaxException)) {
            return refused;
        }
        final UnknownTypeReading reading = ((SqlSyntaxException) refused).getUnknownTypes();
        if (reading == null) {
            return refused;
        }
        final FrostlakeParser.SqlScriptContext tree = SyntaxErrorListener.cleanParse(reading.getSubstituted());
        if (tree == null) {
            return refused;
        }
        try {
            if (reading.isIceberg()) {
                judgeColumns(tree, reading, parser);
            } else {
                judgeWidths(tree, reading, parser);
            }
        } catch (final RuntimeException ahead) {
            return SqlCompilationError.isCompilationError(ahead.getMessage())
                || ahead.getMessage() != null && ahead.getMessage().startsWith("SQL Compilation error:")
                ? ahead : refused;
        }
        return refused;
    }

    /** Every type the statement names that is no stand-in for a word, read in the order written. */
    private static void judgeWidths(final ParseTree tree, final UnknownTypeReading reading,
                                    final ColumnDefinitionParser parser) {
        final List<FrostlakeParser.DataTypeNameContext> types = new ArrayList<>();
        collect(tree, FrostlakeParser.DataTypeNameContext.class, types, true);
        for (final FrostlakeParser.DataTypeNameContext type : types) {
            final UnknownTypeWord word = wordIn(reading, type.getStart().getStartIndex(),
                type.getStop().getStopIndex());
            if (word == null) {
                parser.parseDataType(type, parametersOf(type));
            } else if (word.isParameterized()) {
                // A word written with parameters is a type the account builds as it reads the statement, in its
                // turn among the widths: nothing written after it is judged first.
                return;
            }
        }
    }

    /** An Iceberg table's columns in the order written: a word refused where it stands, a known type read as written. */
    private static void judgeColumns(final ParseTree tree, final UnknownTypeReading reading,
                                     final ColumnDefinitionParser parser) {
        final List<FrostlakeParser.ColumnDefContext> columns = new ArrayList<>();
        collect(tree, FrostlakeParser.ColumnDefContext.class, columns, false);
        for (final FrostlakeParser.ColumnDefContext column : columns) {
            final FrostlakeParser.DataTypeNameContext type = column.dataTypeName();
            if (type == null) {
                continue;
            }
            final int stop = column.typeParameters() != null ? column.typeParameters().getStop().getStopIndex()
                : type.getStop().getStopIndex();
            final UnknownTypeWord word = wordIn(reading, type.getStart().getStartIndex(), stop);
            if (word != null) {
                throw new RuntimeException(word.isRefusedAlike() ? SqlCompilationError.of(word.icebergSentence())
                    : "SQL Compilation error:\n" + word.icebergSentence());
            }
            IcebergColumnTypes.judgeAsWritten(column, parser);
        }
    }

    /** The first word standing within a stretch of the text, or null. */
    private static UnknownTypeWord wordIn(final UnknownTypeReading reading, final int from, final int to) {
        for (final UnknownTypeWord word : reading.getWords()) {
            if (word.getStart() >= from && word.getStart() <= to) {
                return word;
            }
        }
        return null;
    }

    /** The parameters written after a type's name, or null: the node that follows it under the same parent. */
    private static FrostlakeParser.TypeParametersContext parametersOf(final FrostlakeParser.DataTypeNameContext type) {
        final ParserRuleContext parent = type.getParent();
        if (parent == null) {
            return null;
        }
        for (int i = 0; i + 1 < parent.getChildCount(); i++) {
            if (parent.getChild(i) == type) {
                return parent.getChild(i + 1) instanceof FrostlakeParser.TypeParametersContext
                    ? (FrostlakeParser.TypeParametersContext) parent.getChild(i + 1) : null;
            }
        }
        return null;
    }

    /** The nodes of a kind under a tree, in the order written; with {@code outermost}, none that lies within another. */
    private static <T extends ParseTree> void collect(final ParseTree node, final Class<T> kind, final List<T> into,
                                                      final boolean outermost) {
        if (kind.isInstance(node)) {
            into.add(kind.cast(node));
            if (outermost) {
                return;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), kind, into, outermost);
        }
    }
}
