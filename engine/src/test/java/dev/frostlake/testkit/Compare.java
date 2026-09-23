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

package dev.frostlake.testkit;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Checks one step's {@code expect} block against a backend's {@link ExecResult}. Values compare after
 * normalization — numbers to 10 significant digits, booleans case-insensitively, NULL and the empty
 * string alike — so equivalent spellings from different transports ({@code 2} and {@code 2.000000},
 * {@code true} and {@code TRUE}) compare equal.
 */
final class Compare {

    private static final char CELL_SEPARATOR = (char) 0x1f;

    private Compare() {
    }

    static Outcome check(final Map<String, Object> expect, final ExecResult result,
                         final Set<Capability> capabilities) {
        final Map<String, Object> error = Json.getObj(expect, "error");
        if (error != null) {
            return checkRefusal(error, result, capabilities);
        }
        if (result.failed()) {
            return Outcome.failure("unexpected error: " + result.getErrorMessage());
        }
        final Outcome outcome = Outcome.success();
        if (expect == null) {
            return outcome;
        }
        if (expect.containsKey("value")) {
            final String value = asString(expect.get("value"));
            final String actual = firstCell(result.getRows());
            if (!norm(value).equals(norm(actual))) {
                return Outcome.failure("value [" + actual + "] != expected [" + value + "]");
            }
        }
        final List<Object> rows = Json.getArr(expect, "rows");
        if (rows != null) {
            final String diff = gridDiff(expectedGrid(rows), result.getRows(),
                Boolean.TRUE.equals(Json.getBool(expect, "ordered")));
            if (diff != null) {
                return Outcome.failure(diff);
            }
        }
        final Integer rowCount = Json.getInt(expect, "rowCount");
        if (rowCount != null) {
            final int got = result.getRows() == null ? 0 : result.getRows().size();
            if (got != rowCount) {
                return Outcome.failure("rowCount " + got + " != expected " + rowCount);
            }
        }
        final List<Object> columns = Json.getArr(expect, "columns");
        if (columns != null) {
            if (!capabilities.contains(Capability.COLUMN_NAMES)) {
                outcome.skipCheck("COLUMN_NAMES: cannot check column names");
            } else {
                final String mismatch = columnMismatch(columns, result.getColumns());
                if (mismatch != null) {
                    return Outcome.failure(mismatch);
                }
            }
        }
        final Integer updateCount = Json.getInt(expect, "updateCount");
        if (updateCount != null) {
            if (!capabilities.contains(Capability.UPDATE_COUNT)) {
                outcome.skipCheck("UPDATE_COUNT: cannot check update count (not reported by this transport)");
            } else if (result.getUpdateCount() != updateCount) {
                return Outcome.failure("updateCount " + result.getUpdateCount() + " != expected " + updateCount);
            }
        }
        return outcome;
    }

    /** The statement is EXPECTED to fail: check it did, and with the named message, code and state. */
    private static Outcome checkRefusal(final Map<String, Object> error, final ExecResult result,
                                        final Set<Capability> capabilities) {
        if (!result.failed()) {
            return Outcome.failure("expected an error, statement succeeded");
        }
        final String want = Json.getStr(error, "messageContains");
        if (want != null && !result.getErrorMessage().toLowerCase(Locale.ROOT)
                .contains(want.toLowerCase(Locale.ROOT))) {
            return Outcome.failure("error message [" + result.getErrorMessage() + "] does not contain [" + want + "]");
        }
        final Outcome outcome = Outcome.success();
        final String code = Json.getStr(error, "code");
        final String state = Json.getStr(error, "sqlState");
        if (code == null && state == null) {
            return outcome;
        }
        if (!capabilities.contains(Capability.ERROR_CODE)) {
            outcome.skipCheck("ERROR_CODE: cannot check error code/sqlState (backend reports message only)");
            return outcome;
        }
        if (code != null && !sameErrorCode(code, result.getErrorCode())) {
            return Outcome.failure("error code [" + result.getErrorCode() + "] != expected [" + code + "]");
        }
        if (state != null && !state.equals(result.getSqlState())) {
            return Outcome.failure("sqlState [" + result.getSqlState() + "] != expected [" + state + "]");
        }
        return outcome;
    }

    /**
     * Whether a reported error code is the expected one. A corpus spells a code as the SQL REST API does,
     * six zero-padded digits ({@code 001003}), while a JDBC driver reports the vendor code as an int
     * ({@code 1003}), so two all-digit codes compare by their value; anything else compares exactly.
     */
    static boolean sameErrorCode(final String expected, final String actual) {
        if (actual == null) {
            return false;
        }
        if (allDigits(expected) && allDigits(actual)) {
            return withoutLeadingZeros(expected).equals(withoutLeadingZeros(actual));
        }
        return expected.equals(actual);
    }

    private static boolean allDigits(final String text) {
        if (text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String withoutLeadingZeros(final String digits) {
        int start = 0;
        while (start < digits.length() - 1 && digits.charAt(start) == '0') {
            start++;
        }
        return digits.substring(start);
    }

    private static List<List<String>> expectedGrid(final List<Object> rows) {
        final List<List<String>> grid = new ArrayList<>();
        for (final Object row : rows) {
            final List<String> cells = new ArrayList<>();
            for (final Object cell : Json.arr(row)) {
                cells.add(cell == null ? null : asString(cell));
            }
            grid.add(cells);
        }
        return grid;
    }

    private static String firstCell(final List<List<String>> rows) {
        if (rows == null || rows.isEmpty() || rows.get(0).isEmpty()) {
            return null;
        }
        return rows.get(0).get(0);
    }

    private static String columnMismatch(final List<Object> expected, final List<String> actual) {
        final List<String> got = actual == null ? new ArrayList<String>() : actual;
        if (got.size() != expected.size()) {
            return "column count " + got.size() + " != expected " + expected.size() + " " + got;
        }
        for (int i = 0; i < expected.size(); i++) {
            if (!asString(expected.get(i)).equalsIgnoreCase(got.get(i))) {
                return "column[" + i + "] [" + got.get(i) + "] != expected [" + expected.get(i) + "]";
            }
        }
        return null;
    }

    private static String gridDiff(final List<List<String>> want, final List<List<String>> got,
                                   final boolean ordered) {
        final List<String> expected = canon(want);
        final List<String> actual = canon(got == null ? new ArrayList<List<String>>() : got);
        if (!ordered) {
            Collections.sort(expected);
            Collections.sort(actual);
        }
        if (expected.equals(actual)) {
            return null;
        }
        return "rows differ: expected " + expected + " got " + actual;
    }

    private static List<String> canon(final List<List<String>> grid) {
        final List<String> out = new ArrayList<>();
        for (final List<String> row : grid) {
            final StringBuilder line = new StringBuilder();
            for (final String cell : row) {
                line.append(norm(cell)).append(CELL_SEPARATOR);
            }
            out.add(line.toString());
        }
        return out;
    }

    /**
     * The shared value normalization, applied to both sides before they compare.
     *
     * @param raw a cell or an expected value
     * @return its canonical spelling
     */
    static String norm(final String raw) {
        if (raw == null) {
            return "NULL";
        }
        final String value = raw.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("null")) {
            return "NULL";
        }
        if (value.equalsIgnoreCase("true")) {
            return "TRUE";
        }
        if (value.equalsIgnoreCase("false")) {
            return "FALSE";
        }
        try {
            final BigDecimal number = new BigDecimal(value);
            if (number.compareTo(BigDecimal.ZERO) == 0) {
                return "0";
            }
            return number.round(new MathContext(10)).stripTrailingZeros().toPlainString();
        } catch (final NumberFormatException notANumber) {
            return value;
        }
    }

    private static String asString(final Object value) {
        return value instanceof BigDecimal ? ((BigDecimal) value).toPlainString() : String.valueOf(value);
    }
}
