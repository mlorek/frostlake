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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.metastore.model.Stage;
import dev.frostlake.parser.FrostlakeParser;
import java.util.Locale;
import java.util.Set;

/**
 * A stage's {@code DIRECTORY = ( … )} property list, judged as CREATE STAGE and ALTER STAGE … SET judge it
 * (live-verified):
 *
 * <pre>
 *   DIRECTORY = ()  |  'x'  |  TRUE     Parameter for directory requires property list. …
 *   (ENABLE = MAYBE | 'TRUE' | 1)       invalid value [MAYBE] for parameter 'ENABLE'   the value as written;
 *                                       AUTO_REFRESH and REFRESH_ON_CREATE are judged alike
 *   (FOO = 1)                           invalid parameter 'FOO'
 *   ALTER … (REFRESH_ON_CREATE = TRUE)  invalid property 'true' for 'REFRESH_ON_CREATE'   a CREATE takes it
 * </pre>
 *
 * A list names what it changes and no more: one without ENABLE leaves the directory table as it is, on a stage
 * that already has one. The last of a repeated option counts.
 */
final class StageDirectoryOptions {

    /** The options a notification-driven refresh takes, read and kept inert. */
    private static final Set<String> INERT = Set.of("NOTIFICATION_INTEGRATION", "AWS_SNS_TOPIC");

    private Boolean enable;
    private Boolean autoRefresh;
    private Boolean refreshOnCreate;

    private StageDirectoryOptions() {
    }

    /**
     * Reads a DIRECTORY property's value.
     *
     * @param list     the parenthesized list, or null when the value is not one
     * @param creating whether a CREATE STAGE wrote it, which alone takes REFRESH_ON_CREATE
     * @return the options the list names
     */
    static StageDirectoryOptions read(final FrostlakeParser.ParenOptionListContext list, final boolean creating) {
        if (list == null || list.parenOption().isEmpty()) {
            throw new RuntimeException("Parameter for directory requires property list."
                + " Example: DIRECTORY=(ENABLE=TRUE AUTO_REFRESH=FALSE).");
        }
        final StageDirectoryOptions options = new StageDirectoryOptions();
        for (final FrostlakeParser.ParenOptionContext option : list.parenOption()) {
            final String key = option.optionKey().getText().toUpperCase(Locale.ROOT);
            if ("ENABLE".equals(key)) {
                options.enable = flag(option, key);
            } else if ("AUTO_REFRESH".equals(key)) {
                options.autoRefresh = flag(option, key);
            } else if ("REFRESH_ON_CREATE".equals(key)) {
                final boolean value = flag(option, key);
                if (!creating) {
                    throw new RuntimeException(SqlCompilationError.invalidPropertyFor(String.valueOf(value), key));
                }
                options.refreshOnCreate = value;
            } else if (!INERT.contains(key)) {
                throw new RuntimeException(SqlCompilationError.of("invalid parameter '" + key + "'"));
            }
        }
        return options;
    }

    /** An option's TRUE or FALSE, written bare; anything else is refused as written. */
    private static boolean flag(final FrostlakeParser.ParenOptionContext option, final String key) {
        final FrostlakeParser.CopyOptionValueContext value = option.copyOptionValue();
        if (value != null && value.booleanValue() != null) {
            return value.booleanValue().TRUE() != null;
        }
        final String written;
        if (value != null) {
            written = ParseTreeText.getOriginalText(value);
        } else {
            written = "(" + (option.stringLiteralList() != null
                ? ParseTreeText.getOriginalText(option.stringLiteralList()) : "") + ")";
        }
        throw new RuntimeException(SqlCompilationError.invalidValueForParameter(written, key));
    }

    /** Whether the list turns the directory table on. */
    boolean enables() {
        return Boolean.TRUE.equals(enable);
    }

    /** Whether a created stage registers its files right away: REFRESH_ON_CREATE is on unless written off. */
    boolean refreshesOnCreate() {
        return !Boolean.FALSE.equals(refreshOnCreate);
    }

    /**
     * Applies the list to a stage: what it names changes, the rest stays.
     *
     * @param stage the stage the list was written for
     */
    void applyTo(final Stage stage) {
        stage.setDirectoryDeclared(true);
        if (enable != null) {
            stage.switchDirectory(enable);
        }
        if (autoRefresh != null) {
            stage.setDirectoryAutoRefresh(autoRefresh);
        }
    }
}
