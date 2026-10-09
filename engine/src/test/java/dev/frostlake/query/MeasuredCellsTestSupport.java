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
            return out.toString();
        } catch (final RuntimeException refused) {
            return "REFUSED " + roleMasked(String.valueOf(refused.getMessage()).replace("\n", " | "));
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
            return "REFUSED " + roleMasked(String.valueOf(refused.getMessage()).replace("\n", " | "));
        }
    }

    /**
     * A refusal with the role it names masked: the privilege hint of a missing object names the session's own
     * role, which is not the same on every engine the suite runs against.
     */
    private static String roleMasked(final String message) {
        return message.replaceAll("Your primary role \\S+ must", "Your primary role <ROLE> must");
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

    /** Asserts one statement's columns are the measured ones. */
    protected void assertShape(final String expected, final String sql) {
        assertEquals(expected, shape(sql), sql);
    }
}
