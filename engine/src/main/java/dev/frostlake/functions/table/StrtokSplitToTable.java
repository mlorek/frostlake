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

package dev.frostlake.functions.table;

import dev.frostlake.functions.TableFunction;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * STRTOK_SPLIT_TO_TABLE — tokenizes a string into one row per token, as SEQ, INDEX and VALUE.
 *
 * <p>The sibling of SPLIT_TO_TABLE, and it splits differently: its second argument is a SET of
 * delimiter CHARACTERS rather than one delimiter string, each character splitting on its own, and an
 * empty token is never produced — consecutive, leading and trailing delimiters all collapse. It is the
 * table-valued form of STRTOK / STRTOK_TO_ARRAY, and shares their tokenizer.
 *
 * <p>The second argument may be left out, and the delimiter set is then a single space.
 *
 * <p>The row rules it shares with SPLIT_TO_TABLE: SEQ numbers the INPUT RECORD, so every row of one
 * call carries the same SEQ and it starts at 1, while INDEX is the 1-based position of the token; a
 * NULL string or a NULL delimiter set yields no rows at all.
 */
public class StrtokSplitToTable extends TableFunction {

    /** The delimiter set a call that leaves the argument out splits on. */
    private static final String DEFAULT_DELIMITERS = " ";

    /**
     * Snowflake's SEQ is a sequence number for the INPUT RECORD, so one call numbers all its rows
     * alike.
     */
    private static final long INPUT_SEQUENCE = 1L;

    public StrtokSplitToTable() {
        super("STRTOK_SPLIT_TO_TABLE");
    }

    /** The positional form {@code STRTOK_SPLIT_TO_TABLE(string [, delimiters])}. */
    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        return tokenize(positionalArgs.isEmpty() ? null : positionalArgs.get(0),
            positionalArgs.size() > 1 ? positionalArgs.get(1) : DEFAULT_DELIMITERS);
    }

    /**
     * The named-argument entry point of the {@link TableFunction} contract. SQL never reaches it — a
     * named call is refused while the statement is compiled — so it serves callers inside the engine.
     */
    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        return tokenize(namedArgs.get("STRING"),
            namedArgs.containsKey("DELIMITER") ? namedArgs.get("DELIMITER") : DEFAULT_DELIMITERS);
    }

    private ResultSet tokenize(final Object stringValue, final Object delimiterValue) {
        final List<ResultSetColumn> columns = SplitToTable.columns();
        final List<Row> rows = new ArrayList<>();
        // NULL in either parameter contributes nothing at all — not an empty-string row.
        if (stringValue == null || delimiterValue == null) {
            return new ResultSet(columns, rows);
        }
        final String input = stringValue.toString();
        final String delimiters = delimiterValue.toString();
        final List<String> tokens = new ArrayList<>();
        if (delimiters.isEmpty()) {
            // No character splits anything: the whole string is one token, and an empty one is no token.
            if (!input.isEmpty()) {
                tokens.add(input);
            }
        } else {
            // Any run of delimiter characters separates, so no empty token is ever produced.
            for (final String token : input.split("[" + Pattern.quote(delimiters) + "]+", -1)) {
                if (!token.isEmpty()) {
                    tokens.add(token);
                }
            }
        }
        for (int i = 0; i < tokens.size(); i++) {
            final List<Object> values = new ArrayList<>();
            values.add(Long.valueOf(INPUT_SEQUENCE));
            values.add(Long.valueOf(i + 1));
            values.add(tokens.get(i));
            rows.add(new Row(values));
        }
        return new ResultSet(columns, rows);
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Argument names, count and types are settled while the statement is compiled — see
        // TableFunctionArguments.
    }

    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return SplitToTable.columns();
    }
}
