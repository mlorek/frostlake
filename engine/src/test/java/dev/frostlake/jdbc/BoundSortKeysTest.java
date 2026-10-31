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

import dev.frostlake.BaseJdbcTest;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A value bound as a whole ORDER BY or GROUP BY key is a constant, as the account's server-side binding makes it (all
 * live-verified): {@code ORDER BY ?, a} bound to 2 orders by {@code a}, not by the second column; {@code GROUP BY ?}
 * bound to 1 makes one group; a bound 0, -1 or 9 is no position out of range; and so for a bind in parentheses or
 * under a sign, of any number type, in ROLLUP, CUBE and GROUPING SETS and in a set operation's ORDER BY. A bind inside
 * a larger key is a value as it stands, and a key written as a number is still a position.
 */
public class BoundSortKeysTest extends BaseJdbcTest {

    private final StringBuilder text = new StringBuilder();

    private void query(final String label, final String sql, final Object... binds) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < binds.length; i++) {
                ps.setObject(i + 1, binds[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                final int width = rs.getMetaData().getColumnCount();
                final StringBuilder rows = new StringBuilder();
                while (rs.next()) {
                    rows.append(rows.length() > 0 ? " ;; " : "");
                    for (int c = 1; c <= width; c++) {
                        rows.append(c > 1 ? "|" : "").append(rs.getString(c));
                    }
                }
                text.append(label).append(" => ").append(rows).append('\n');
            }
        } catch (final SQLException refused) {
            text.append(label).append(" => REFUSED ").append(String.valueOf(refused.getMessage()).replace("\n", " | "))
                .append('\n');
        }
    }

    @Test
    public void aBoundKeyIsAConstant() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE bk (a INT, b VARCHAR)");
        statement.execute("INSERT INTO bk VALUES (1, 'z'), (2, 'y'), (3, 'x')");
        query("bk.001", "SELECT a, b FROM bk ORDER BY ?, a", Integer.valueOf(2));
        query("bk.002", "SELECT a, b FROM bk ORDER BY ? DESC, a", Integer.valueOf(1));
        query("bk.003", "SELECT COUNT(*) FROM bk GROUP BY ?", Integer.valueOf(1));
        query("bk.004", "SELECT a, b, a + ? FROM bk GROUP BY ?", Integer.valueOf(2), Integer.valueOf(1));
        query("bk.005", "SELECT a FROM bk ORDER BY ?, a DESC", Integer.valueOf(1));
        query("bk.006", "SELECT a, COUNT(*) FROM bk GROUP BY a, ? ORDER BY a", Integer.valueOf(2));
        query("bk.007", "SELECT a FROM bk ORDER BY ?, a", Integer.valueOf(9));
        query("bk.008", "SELECT a FROM bk ORDER BY -?, a", Integer.valueOf(1));
        query("bk.009", "SELECT a, b FROM bk ORDER BY (?), a", Integer.valueOf(2));
        query("bk.010", "SELECT a FROM bk ORDER BY -(?), a DESC", Integer.valueOf(1));
        query("bk.011", "SELECT a FROM bk ORDER BY ((?)), a DESC", Integer.valueOf(1));
        query("bk.012", "SELECT a FROM bk ORDER BY ?, a DESC", Integer.valueOf(-1));
        query("bk.013", "SELECT a FROM bk ORDER BY ? DESC, a DESC", Integer.valueOf(0));
        query("bk.014", "SELECT a FROM bk ORDER BY ?, a DESC", Long.valueOf(1L));
        query("bk.015", "SELECT a FROM bk ORDER BY ?, a DESC", new BigDecimal("1"));
        query("bk.016", "SELECT a FROM bk ORDER BY ?, a DESC", new BigDecimal("1.5"));
        query("bk.017", "SELECT a FROM bk ORDER BY ?, a DESC", Short.valueOf((short) 1));
        query("bk.018", "SELECT a FROM bk ORDER BY ?, a DESC", Double.valueOf(1.0));
        query("bk.019", "SELECT a FROM bk ORDER BY ?, a DESC", "1");
        query("bk.020", "SELECT COUNT(*) FROM bk GROUP BY ROLLUP (?)", Integer.valueOf(1));
        query("bk.021", "SELECT COUNT(*) FROM bk GROUP BY GROUPING SETS ((?), ())", Integer.valueOf(1));
        query("bk.022", "SELECT COUNT(*) FROM bk GROUP BY CUBE (?)", Integer.valueOf(1));
        query("bk.023", "SELECT COUNT(*) FROM bk GROUP BY ROLLUP (?, a) ORDER BY 1", Integer.valueOf(1));
        query("bk.024", "SELECT a, COUNT(*) FROM bk GROUP BY a, (?) ORDER BY a", Integer.valueOf(2));
        query("bk.025", "SELECT a FROM bk UNION ALL SELECT 4 ORDER BY ?, 1 DESC", Integer.valueOf(1));
        query("bk.026", "SELECT a FROM bk ORDER BY ? NULLS FIRST, a DESC", Integer.valueOf(1));
        query("bk.027", "SELECT a FROM bk ORDER BY ?, ?, a DESC", Integer.valueOf(1), Integer.valueOf(2));
        query("bk.028", "SELECT a FROM bk GROUP BY a ORDER BY ?, a DESC", Integer.valueOf(1));
        query("bk.029", "SELECT a FROM (SELECT a FROM bk ORDER BY ?, a DESC LIMIT 2) ORDER BY a", Integer.valueOf(1));
        query("bk.030", "SELECT a, ROW_NUMBER() OVER (ORDER BY ?, a DESC) rn FROM bk ORDER BY a", Integer.valueOf(1));
        query("bk.031", "SELECT ?, a FROM bk ORDER BY ?, a DESC", Integer.valueOf(7), Integer.valueOf(1));
        query("bk.032", "SELECT a + ? FROM bk GROUP BY a + ? ORDER BY 1", Integer.valueOf(1), Integer.valueOf(1));
        query("bk.033", "SELECT a FROM bk ORDER BY ? + 0, a", Integer.valueOf(2));
        query("bk.034", "SELECT a FROM bk ORDER BY ?::INT, a", Integer.valueOf(2));
        query("bk.035", "SELECT a FROM bk ORDER BY a LIMIT ?", Integer.valueOf(2));
        query("bk.036", "SELECT ? FROM bk ORDER BY 1", Integer.valueOf(5));
        query("bk.037", "SELECT a, b FROM bk ORDER BY 2, a");
        statement.execute("DROP TABLE IF EXISTS bk");
        assertEquals("""
            bk.001 => 1|z ;; 2|y ;; 3|x
            bk.002 => 1|z ;; 2|y ;; 3|x
            bk.003 => 3
            bk.004 => REFUSED SQL compilation error: error line 1 at position 7 | 'BK.A' in select clause is neither \
            an aggregate nor in the group by clause.
            bk.005 => 3 ;; 2 ;; 1
            bk.006 => 1|1 ;; 2|1 ;; 3|1
            bk.007 => 1 ;; 2 ;; 3
            bk.008 => 1 ;; 2 ;; 3
            bk.009 => 1|z ;; 2|y ;; 3|x
            bk.010 => 3 ;; 2 ;; 1
            bk.011 => 3 ;; 2 ;; 1
            bk.012 => 3 ;; 2 ;; 1
            bk.013 => 3 ;; 2 ;; 1
            bk.014 => 3 ;; 2 ;; 1
            bk.015 => 3 ;; 2 ;; 1
            bk.016 => 3 ;; 2 ;; 1
            bk.017 => 3 ;; 2 ;; 1
            bk.018 => 3 ;; 2 ;; 1
            bk.019 => 3 ;; 2 ;; 1
            bk.020 => 3 ;; 3
            bk.021 => 3 ;; 3
            bk.022 => 3 ;; 3
            bk.023 => 1 ;; 1 ;; 1 ;; 3 ;; 3
            bk.024 => 1|1 ;; 2|1 ;; 3|1
            bk.025 => 4 ;; 3 ;; 2 ;; 1
            bk.026 => 3 ;; 2 ;; 1
            bk.027 => 3 ;; 2 ;; 1
            bk.028 => 3 ;; 2 ;; 1
            bk.029 => 2 ;; 3
            bk.030 => 1|3 ;; 2|2 ;; 3|1
            bk.031 => 7|3 ;; 7|2 ;; 7|1
            bk.032 => 2 ;; 3 ;; 4
            bk.033 => 1 ;; 2 ;; 3
            bk.034 => 1 ;; 2 ;; 3
            bk.035 => 1 ;; 2
            bk.036 => 5 ;; 5 ;; 5
            bk.037 => 3|x ;; 2|y ;; 1|z
            """, text.toString());
    }
}
