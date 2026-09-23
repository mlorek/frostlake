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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The stage options a CREATE TABLE writes — {@code STAGE_FILE_FORMAT = (TYPE = JSON)} and
 * {@code STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE)} — kept on the table, because
 * {@code DESCRIBE TABLE … TYPE = STAGE} reports them: the written value stands in the value column
 * while the default column keeps the format's own default (live-verified).
 */
final class TableStageOptions {

    private TableStageOptions() {
    }

    /**
     * Read the stage options written among a CREATE TABLE's tail options onto the table.
     *
     * @param table the table being created
     * @param tailOptions the statement's tail options
     */
    static void applyFrom(final Table table, final List<? extends ParseTree> tailOptions) {
        if (tailOptions == null) {
            return;
        }
        for (final ParseTree option : tailOptions) {
            if (!(option instanceof FrostlakeParser.TableTailOptionContext)) {
                continue;
            }
            final FrostlakeParser.TableTailOptionContext tail = (FrostlakeParser.TableTailOptionContext) option;
            if (tail.optionKey() == null || tail.parenOptionList() == null) {
                continue;
            }
            final String key = tail.optionKey().getText().toUpperCase();
            if ("STAGE_FILE_FORMAT".equals(key)) {
                readGroup(tail.parenOptionList(), table.getStageFileFormat());
            } else if ("STAGE_COPY_OPTIONS".equals(key)) {
                readGroup(tail.parenOptionList(), table.getStageCopyOptions());
            }
        }
    }

    /** Each {@code NAME = value} of one group, the value unquoted as it was written. */
    private static void readGroup(final FrostlakeParser.ParenOptionListContext group,
                                  final java.util.Map<String, String> into) {
        for (final FrostlakeParser.ParenOptionContext option : group.parenOption()) {
            if (option.optionKey() == null) {
                continue;
            }
            into.put(option.optionKey().getText().toUpperCase(), writtenValue(option));
        }
    }

    /** One option's value: a string literal without its quotes, a list as written, anything else verbatim. */
    private static String writtenValue(final FrostlakeParser.ParenOptionContext option) {
        if (option.copyOptionValue() == null) {
            final StringBuilder list = new StringBuilder("[");
            if (option.stringLiteralList() != null) {
                for (final org.antlr.v4.runtime.tree.TerminalNode item : option.stringLiteralList().STRING_LITERAL()) {
                    list.append(list.length() > 1 ? ", " : "").append(unquoted(item.getText()));
                }
            }
            return list.append(']').toString();
        }
        return unquoted(option.copyOptionValue().getText());
    }

    private static String unquoted(final String written) {
        return written.length() > 1 && written.charAt(0) == '\'' && written.charAt(written.length() - 1) == '\''
            ? written.substring(1, written.length() - 1).replace("''", "'") : written;
    }
}
