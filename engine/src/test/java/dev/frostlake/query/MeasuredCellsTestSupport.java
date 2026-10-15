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
package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Shared helpers for the tests that hold one measured answer per statement: an answered statement reads as
 * its rows, cells joined by a bar and rows by {@code " ;; "}, and a refused one as {@code REFUSED} and its
 * message on one line. A statement's shape reads as its columns, each a name and its type.
 */
public abstract class MeasuredCellsTestSupport extends BaseDatabaseTest {

    /** How the refusal of a correlated subquery the account cannot evaluate reads with no place given. */
    protected static final String UNSUPPORTED =
        "REFUSED SQL compilation error: | Unsupported subquery type cannot be evaluated";

    /** The refusal placed at a character position of the statement's first line. */
    protected static String unsupportedAt(final int position) {
        return UNSUPPORTED + " at line 1, position " + position;
    }

    /** A statement's answer: its rows, or its refusal on one line. */
    protected String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            final StringBuilder out = new StringBuilder();
            boolean firstRow = true;
            while (rs.next()) {
                if (!firstRow) {
                    out.append(" ;; ");
                }
                firstRow = false;
                for (int c = 0; c < rs.getColumnCount(); c++) {
                    // A value of several lines reads on one, as a refusal does.
                    out.append(c == 0 ? "" : "|").append(String.valueOf(rs.getValue(c)).replace("\n", " | "));
                }
            }
            // A value may carry a refusal too: a handler that returns its SQLERRM.
            return masked(out.toString());
        } catch (final RuntimeException refused) {
            return "REFUSED " + masked(String.valueOf(refused.getMessage()).replace("\n", " | "));
        }
    }

    /** A statement's shape: its columns, each as {@code NAME:TYPE}, or its refusal on one line. */
    protected String shape(final String sql) {
        try {
            final StringBuilder out = new StringBuilder();
            for (final ResultSetColumn column : engine.executeQuery(sql).getColumns()) {
                if (out.length() > 0) {
                    out.append(", ");
                }
                out.append(column.getName()).append(':').append(spelled(column.getDataType()));
            }
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + masked(String.valueOf(refused.getMessage()).replace("\n", " | "));
        }
    }

    /**
     * A refusal with what differs by the account it ran on masked: the role a privilege hint names — the session's
     * own, or the owner a procedure runs as — and the account's locator, which a hint on the account and a share's
     * name carry. Neither is the same on every engine the suite runs against.
     */
    private static String masked(final String message) {
        return message.replaceAll("Your primary role \\S+ (or one of your secondary roles )?must",
                "Your primary role <ROLE> $1must")
            .replaceAll("The owner role \\S+ must", "The owner role <ROLE> must")
            .replaceAll("granted on ACCOUNT \\S+\\.", "granted on ACCOUNT <ACCOUNT>.")
            .replaceAll("(Share '\"|granted on SHARE \")[A-Za-z0-9_]+\\.", "$1<ACCOUNT>.");
    }

    private static String spelled(final DataType type) {
        if (type == null) {
            return "?";
        }
        if (type instanceof NumericType) {
            final NumericType numeric = (NumericType) type;
            return numeric.getName() + "(" + numeric.getPrecision() + "," + numeric.getScale() + ")";
        }
        if (type instanceof StringType) {
            return type.getName() + "(" + ((StringType) type).getMaxLength() + ")";
        }
        return type.getName();
    }

    /** Asserts each statement of {@code cells} — a statement and its answer — reads as measured. */
    protected void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    /** Asserts one statement reads as measured. */
    protected void assertAnswer(final String expected, final String sql) {
        assertEquals(expected, answer(sql), sql);
    }

    /**
     * Asserts one statement reads as measured once what every run generates anew is masked: a 19-digit id — a
     * transaction's — as {@code <ID19>} and a UUID as {@code <QID>}.
     */
    protected void assertShapedAnswer(final String expected, final String sql) {
        assertEquals(expected, answer(sql).replaceAll("\\b\\d{19}\\b", "<ID19>")
            .replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "<QID>"), sql);
    }

    /**
     * Asserts one statement reads as measured once the day it ran on is masked: a quoted date, as the echo of a
     * CURRENT_DATE() holds it, reads {@code '<DATE>'}.
     */
    protected void assertDatedAnswer(final String expected, final String sql) {
        assertEquals(expected, answer(sql).replaceAll("'\\d{4}-\\d{2}-\\d{2}'", "'<DATE>'"), sql);
    }

    /** Asserts one statement's columns are the measured ones. */
    protected void assertShape(final String expected, final String sql) {
        assertEquals(expected, shape(sql), sql);
    }

    /** Asserts each of the statements has the measured columns, the same for all of them. */
    protected void assertShapes(final String expected, final String... statements) {
        for (final String sql : statements) {
            assertEquals(expected, shape(sql), sql);
        }
    }
}
