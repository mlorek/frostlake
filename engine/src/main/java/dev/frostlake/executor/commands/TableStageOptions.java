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

import dev.frostlake.executor.FileFormatClause;
import dev.frostlake.metastore.model.FileFormat;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The stage options a CREATE TABLE writes — {@code STAGE_FILE_FORMAT = (TYPE = JSON)} and
 * {@code STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE)} — kept on the table, because
 * {@code DESCRIBE TABLE … TYPE = STAGE} reports them: the written value stands in the value column
 * while the default column keeps the format's own default (live-verified). The format's options are read as
 * every FILE_FORMAT list is (see {@link FileFormatClause}) and kept as a named format keeps its own.
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
            final String key = tail.optionKey().getText().toUpperCase(Locale.ROOT);
            if ("STAGE_FILE_FORMAT".equals(key)) {
                keepFormat(tail.parenOptionList(), table.getStageFileFormat());
            } else if ("STAGE_COPY_OPTIONS".equals(key)) {
                table.getStageCopyOptions().putAll(StageCopyOptions.read(tail.parenOptionList()));
            }
        }
    }

    /**
     * Keep a written format group as a stage keeps one: TYPE as written, FORMAT_NAME as the listing spells it, and
     * the options as a named format keeps them.
     *
     * @param group the group
     * @param into  the map the table keeps its stage file format in
     */
    static void keepFormat(final FrostlakeParser.ParenOptionListContext group, final Map<String, String> into) {
        final FileFormatClause written = FileFormatClause.read(group, new FileFormat("WRITTEN", "CSV"), true);
        if (written.writtenType() != null) {
            into.put("TYPE", written.writtenType());
        }
        if (written.formatNameWritten() != null) {
            into.put("FORMAT_NAME", written.formatNameWritten());
        }
        into.putAll(written.format().getOptions());
    }
}
