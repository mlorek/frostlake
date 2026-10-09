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

import dev.frostlake.executor.SessionParameterCatalog;
import dev.frostlake.executor.SessionParameterRow;
import dev.frostlake.parser.FrostlakeParser;

/**
 * One name ALTER SESSION SET or UNSET writes, with the value SET gives it. The name is kept as written, since
 * that is what a refusal of it echoes, beside the parameter it names.
 */
final class SessionParameterSetting {

    private final String written;
    private final String key;
    private final String type;
    private final FrostlakeParser.SessionValueContext valueContext;
    private SessionParameterValue value;
    private Object stored;

    SessionParameterSetting(final FrostlakeParser.SessionParameterContext name,
                            final FrostlakeParser.SessionValueContext valueContext) {
        this.written = name.MULTI_STATEMENT_COUNT() != null
            ? name.MULTI_STATEMENT_COUNT().getText() : name.identifier().getText();
        this.valueContext = valueContext;
        // A quoted name is no parameter's, whatever it spells.
        final String folded = written.startsWith("\"") ? null : ParameterRegistry.canonical(written);
        final SessionParameterRow row = folded != null && ParameterRegistry.isSessionParameter(folded)
            ? SessionParameterCatalog.find(folded) : null;
        this.key = row == null ? null : folded;
        this.type = row == null ? null : row.getType();
    }

    /** The name as the statement spells it, quotes included. */
    String written() {
        return written;
    }

    /** The name without its quotes, which is what orders the names of one statement. */
    String ordering() {
        return written.length() >= 2 && written.startsWith("\"") && written.endsWith("\"")
            ? written.substring(1, written.length() - 1) : written;
    }

    /** The parameter's name, upper case; null when the name is no session parameter's. */
    String key() {
        return key;
    }

    /** The parameter's kind — STRING, NUMBER or BOOLEAN; null when the name is no session parameter's. */
    String type() {
        return type;
    }

    /** The value as parsed; null for an UNSET. */
    FrostlakeParser.SessionValueContext valueContext() {
        return valueContext;
    }

    /** The value as read; null until the statement's values are judged, and for an UNSET. */
    SessionParameterValue value() {
        return value;
    }

    void read(final SessionParameterValue read) {
        this.value = read;
    }

    /** The value the session will hold, once judged; null puts the parameter back on its default. */
    Object stored() {
        return stored;
    }

    void store(final Object value) {
        this.stored = value;
    }
}
