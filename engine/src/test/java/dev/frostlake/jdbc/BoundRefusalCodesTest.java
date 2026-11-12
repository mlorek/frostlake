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

package dev.frostlake.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseJdbcTest;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * A prepared statement's bound value the compile cannot read carries the account driver's code and state
 * (live-verified): a value no conversion takes to the type its place expects is 1038 / 22023, a row count bound as a
 * FLOAT or a text is 2010 / 2201W in a LIMIT or a FETCH and 2011 / 2201X in an OFFSET. An OFFSET standing alone is
 * no clause, so its statement is the parse's syntax error at its end, whatever is bound.
 */
public class BoundRefusalCodesTest extends BaseJdbcTest {

    /** The code, the state and the sentence a prepared query is refused with, its one value set by {@code kind}. */
    private String refusal(final String sql, final int kind) {
        final SQLException refused = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                try (PreparedStatement prepared = connection.prepareStatement(sql)) {
                    if (kind == 1) {
                        prepared.setBytes(1, new byte[] {1, 2});
                    } else if (kind == 2) {
                        prepared.setDate(1, Date.valueOf("2024-01-02"));
                    } else if (kind == 3) {
                        prepared.setDouble(1, 1.5);
                    } else {
                        prepared.setString(1, "abc");
                    }
                    prepared.executeQuery().close();
                }
            }
        });
        return refused.getErrorCode() + "|" + refused.getSQLState() + "|" + refused.getMessage();
    }

    @Test
    public void aBoundValueNoConversionTakes() {
        assertEquals("1038|22023|SQL compilation error:\nCan not convert parameter ''x'' of type [VARCHAR(1)] into "
            + "expected type [BINARY(2)]", refusal("SELECT SYSTEM$TYPEOF(NVL(?, 'x'))", 1));
        assertEquals("1038|22023|SQL compilation error:\nCan not convert parameter '1' of type [NUMBER(1,0)] into "
            + "expected type [DATE]", refusal("SELECT SYSTEM$TYPEOF(IFF(TRUE, ?, 1))", 2));
    }

    @Test
    public void aBoundRowCount() {
        assertEquals("2010|2201W|SQL compilation error:\nInvalid row count '?' in limit clause",
            refusal("SELECT 1 LIMIT ?", 3));
        assertEquals("2010|2201W|SQL compilation error:\nInvalid row count '?' in limit clause",
            refusal("SELECT 1 LIMIT ?", 4));
        assertEquals("2011|2201X|SQL compilation error:\nInvalid row count '?' in result offset clause",
            refusal("SELECT 1 LIMIT 1 OFFSET ?", 4));
        assertEquals("2010|2201W|SQL compilation error:\nInvalid row count '?' in limit clause",
            refusal("SELECT 1 FETCH FIRST ? ROWS ONLY", 3));
        assertEquals("1003|42000|SQL compilation error:\nsyntax error line 1 at position 17 unexpected '<EOF>'.",
            refusal("SELECT 1 OFFSET ?", 3));
    }
}
