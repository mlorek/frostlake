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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inside ORDER BY a bare name that names an OUTPUT column reads as that column, and a refusal's echo prints
 * it bare: an alias, an unaliased column's own name however it was written ({@code i}, {@code eo.i},
 * {@code "I"}), and a star's column. A name the select list never produces ({@code i AS a}, {@code i + 0})
 * is the base column and prints qualified, as does a name written qualified. Every cell is live-verified.
 */
public class OrderByOutputNameEchoTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE eo (i NUMBER(5,0), bn BINARY)");
        engine.execute("INSERT INTO eo SELECT 1, TO_BINARY('00') UNION ALL SELECT 2, TO_BINARY('01')");
    }

    private void assertEcho(final String sql, final int position, final String call) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        final String expected = "error line 1 at position " + position
            + "\ntoo many arguments for function [" + call + "] expected 1, got 2";
        assertTrue(String.valueOf(refused.getMessage()).contains(expected), sql + " -> " + refused.getMessage());
    }

    @Test
    public void aNameThatIsAnOutputColumnEchoesBare() {
        assertEcho("SELECT i FROM eo ORDER BY ABS(ROW_NUMBER() OVER (ORDER BY i), 1)", 26,
            "ABS(ROW_NUMBER() OVER (ORDER BY I ASC NULLS LAST), 1)");
        assertEcho("SELECT i FROM eo ORDER BY ABS(i, 1)", 26, "ABS(I, 1)");
        assertEcho("SELECT i AS a FROM eo ORDER BY ABS(ROW_NUMBER() OVER (ORDER BY a), 1)", 31,
            "ABS(ROW_NUMBER() OVER (ORDER BY A ASC NULLS LAST), 1)");
        assertEcho("SELECT eo.i FROM eo ORDER BY ABS(i, 1)", 29, "ABS(I, 1)");
        assertEcho("SELECT i, i AS a FROM eo ORDER BY ABS(i, 1)", 34, "ABS(I, 1)");
        assertEcho("SELECT i AS i FROM eo ORDER BY ABS(i, 1)", 31, "ABS(I, 1)");
        assertEcho("SELECT \"I\" FROM eo ORDER BY ABS(i, 1)", 28, "ABS(I, 1)");
        assertEcho("SELECT * FROM eo ORDER BY ABS(i, 1)", 26, "ABS(I, 1)");
        assertEcho("SELECT e.i FROM eo e ORDER BY ABS(i, 1)", 30, "ABS(I, 1)");
        assertEcho("SELECT i, bn FROM eo ORDER BY ABS(i, 1)", 30, "ABS(I, 1)");
        assertEcho("SELECT bn AS i FROM eo ORDER BY ABS(i, 1)", 32, "ABS(I, 1)");
    }

    @Test
    public void aNameTheSelectListNeverProducesEchoesQualified() {
        assertEcho("SELECT bn FROM eo ORDER BY ABS(ROW_NUMBER() OVER (ORDER BY i), 1)", 27,
            "ABS(ROW_NUMBER() OVER (ORDER BY EO.I ASC NULLS LAST), 1)");
        assertEcho("SELECT bn FROM eo ORDER BY ABS(i, 1)", 27, "ABS(EO.I, 1)");
        assertEcho("SELECT i AS a FROM eo ORDER BY ABS(i, 1)", 31, "ABS(EO.I, 1)");
        assertEcho("SELECT i + 0 FROM eo ORDER BY ABS(i, 1)", 30, "ABS(EO.I, 1)");
        assertEcho("SELECT i FROM eo e ORDER BY ABS(e.i, 1)", 28, "ABS(E.I, 1)");
    }
}
