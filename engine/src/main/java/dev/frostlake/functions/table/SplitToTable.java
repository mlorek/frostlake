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
import dev.frostlake.types.LengthlessStringType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * SPLIT_TO_TABLE — splits a string into one row per part, as SEQ, INDEX and VALUE.
 *
 * <p><b>Positional only.</b> Snowflake documents the two parameters as STRING and DELIMITER but does
 * not accept them by name: {@code SPLIT_TO_TABLE(STRING =&gt; 'a,b', DELIMITER =&gt; ',')} is refused
 * with {@code unexpected argument [STRING] at position 1}. Both arguments are required — there is no
 * default delimiter. Those refusals are raised at compile time before this runs.
 *
 * <p>The measured row rules:
 * <ul>
 *   <li>SEQ numbers the INPUT RECORD, not the part: every row of one call carries the same SEQ, and it
 *       starts at 1. INDEX is the 1-based position of the part.</li>
 *   <li>A NULL string or a NULL delimiter yields NO rows.</li>
 *   <li>An EMPTY delimiter does not split at all — the whole string comes back as one row.</li>
 *   <li>An empty string yields one row whose VALUE is empty, and empty parts between consecutive
 *       delimiters are kept, leading and trailing ones included.</li>
 * </ul>
 */
public class SplitToTable extends TableFunction {

    /**
     * Snowflake's SEQ is a sequence number for the INPUT RECORD, so one call numbers all its rows
     * alike. Live increments it across the rows of a scanned column; a single call always reports 1.
     */
    private static final long INPUT_SEQUENCE = 1L;

    public SplitToTable() {
        super("SPLIT_TO_TABLE");
    }

    /** The positional form {@code SPLIT_TO_TABLE(string, delimiter)} — the only one Snowflake takes. */
    @Override
    public ResultSet execute(final List<Object> positionalArgs) {
        return split(positionalArgs.isEmpty() ? null : positionalArgs.get(0),
            positionalArgs.size() > 1 ? positionalArgs.get(1) : null);
    }

    /**
     * The named-argument entry point of the {@link TableFunction} contract. SQL never reaches it —
     * a named call is refused while the statement is compiled — so it serves callers inside the engine,
     * which pass the two parameters under their documented names.
     */
    @Override
    public ResultSet execute(final Map<String, Object> namedArgs) {
        return split(namedArgs.get("STRING"), namedArgs.get("DELIMITER"));
    }

    private ResultSet split(final Object stringValue, final Object delimiterValue) {
        // Declared as the account declares them: SEQ and INDEX NUMBER(38,0), VALUE a VARCHAR the
        // plan spells bare (live-verified).
        final List<ResultSetColumn> columns = columns();

        final List<Row> rows = new ArrayList<>();
        // NULL in either parameter contributes nothing at all — not an empty-string row.
        if (stringValue == null || delimiterValue == null) {
            return new ResultSet(columns, rows);
        }

        final String input = stringValue.toString();
        final String delimiter = delimiterValue.toString();
        final List<String> parts = new ArrayList<>();
        if (delimiter.isEmpty()) {
            // An empty delimiter splits nowhere: live answers a single row holding the whole string.
            parts.add(input);
        } else {
            // -1 keeps trailing empty parts, which live also keeps.
            for (final String part : input.split(Pattern.quote(delimiter), -1)) {
                parts.add(part);
            }
        }

        for (int i = 0; i < parts.size(); i++) {
            final List<Object> values = new ArrayList<>();
            values.add(INPUT_SEQUENCE);
            values.add((long) (i + 1));
            values.add(parts.get(i));
            rows.add(new Row(values));
        }
        return new ResultSet(columns, rows);
    }

    @Override
    public void validateArgs(final Map<String, Object> namedArgs) {
        // Argument names, count and types are all settled while the statement is compiled — see
        // TableFunctionArguments. Nothing is left to check once a value is in hand.
    }

    /** The three columns every SPLIT_TO_TABLE answers. */
    public static List<ResultSetColumn> columns() {
        final List<ResultSetColumn> columns = new ArrayList<>();
        columns.add(new ResultSetColumn("SEQ", NumericType.INTEGER, null, new NumericType("NUMBER", 38, 0)));
        columns.add(new ResultSetColumn("INDEX", NumericType.INTEGER, null, new NumericType("NUMBER", 38, 0)));
        columns.add(new ResultSetColumn("VALUE", StringType.VARCHAR, null, new LengthlessStringType()));
        return columns;
    }

    @Override
    public List<ResultSetColumn> outputColumns(final Map<String, Object> namedArgs) {
        return columns();
    }
}
