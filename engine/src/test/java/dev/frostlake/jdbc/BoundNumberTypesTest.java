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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Prepared-statement values bound as Snowflake's JDBC driver binds them (all live-verified): a double or a float is a
 * FLOAT — {@code SYSTEM$TYPEOF(?)} over setDouble(0.1) is FLOAT[DOUBLE], setDouble(1.255) written into a NUMBER(10,2)
 * is 1.25 and setDouble(0.1) into a DECFLOAT column 0.100000000000000006 — and refused where a row count stands; a
 * BigDecimal is the NUMBER its digits spell, trailing zeros dropped, refused where its fraction takes it past 38
 * digits: setBigDecimal(1E-50) is {@code Invalid intermediate datatype: NUMBER(51,50).}
 */
public class BoundNumberTypesTest extends BaseJdbcTest {

    private final StringBuilder text = new StringBuilder();

    private static String shape(final String value) {
        return value == null ? "null" : value.replace("\n", " | ");
    }

    private static void set(final PreparedStatement ps, final int index, final String setter, final Object value)
            throws SQLException {
        switch (setter) {
            case "double":
                ps.setDouble(index, ((Double) value).doubleValue());
                break;
            case "float":
                ps.setFloat(index, ((Float) value).floatValue());
                break;
            case "decimal":
                ps.setBigDecimal(index, (BigDecimal) value);
                break;
            case "int":
                ps.setInt(index, ((Integer) value).intValue());
                break;
            case "long":
                ps.setLong(index, ((Long) value).longValue());
                break;
            case "string":
                ps.setString(index, (String) value);
                break;
            case "object":
                ps.setObject(index, value);
                break;
            case "nullDouble":
                ps.setNull(index, Types.DOUBLE);
                break;
            default:
                ps.setNull(index, Types.DECIMAL);
                break;
        }
    }

    private void rows(final String label, final ResultSet rs) throws SQLException {
        final ResultSetMetaData md = rs.getMetaData();
        int row = 0;
        while (rs.next()) {
            row++;
            final StringBuilder values = new StringBuilder();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                values.append(i > 1 ? "|" : "").append(shape(rs.getString(i)));
            }
            text.append(label).append(row > 1 ? ".row" + row : "").append(" => ").append(values).append('\n');
        }
    }

    private void query(final String label, final String sql, final String setter, final Object value) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            final int count = sql.length() - sql.replace("?", "").length();
            for (int i = 1; i <= count; i++) {
                set(ps, i, setter, value);
            }
            try (ResultSet rs = ps.executeQuery()) {
                rows(label, rs);
            }
        } catch (final SQLException refused) {
            text.append(label).append(" => REFUSED ").append(shape(refused.getMessage())).append('\n');
        }
    }

    private void typed(final String label, final String setter, final Object value) {
        // A text of its own per cell, so no cached plan or result of another cell answers it.
        query(label, "SELECT SYSTEM$TYPEOF(?) AS t_" + label.replace('.', '_') + ", (?)::VARCHAR AS v", setter, value);
    }

    private void uncached() {
        try {
            statement.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        } catch (final SQLException refused) {
            text.append("uncached => REFUSED ").append(shape(refused.getMessage())).append('\n');
        }
    }

    private void insert(final String label, final String column, final String setter, final Object value) {
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO jb (k, " + column + ") VALUES (?, ?)")) {
            ps.setString(1, label);
            set(ps, 2, setter, value);
            ps.executeUpdate();
            text.append(label).append(" => bound\n");
        } catch (final SQLException refused) {
            text.append(label).append(" => REFUSED ").append(shape(refused.getMessage())).append('\n');
        }
    }

    @Test
    public void boundNumbers() throws SQLException {
        uncached();
        typed("jb.001", "double", Double.valueOf(0.1));
        typed("jb.002", "double", Double.valueOf(1.5));
        typed("jb.003", "double", Double.valueOf(1e20));
        typed("jb.004", "double", Double.valueOf(1e-7));
        typed("jb.005", "double", Double.valueOf(123456789.125));
        typed("jb.006", "float", Float.valueOf(0.1f));
        typed("jb.007", "decimal", new BigDecimal("1.5"));
        typed("jb.008", "decimal", new BigDecimal("1.50"));
        typed("jb.009", "decimal", new BigDecimal("1E-50"));
        typed("jb.010", "decimal", new BigDecimal("1E+40"));
        typed("jb.011", "decimal", new BigDecimal("12345678901234567890123456789012345678"));
        typed("jb.012", "decimal", new BigDecimal("-0.001"));
        typed("jb.013", "int", Integer.valueOf(5));
        typed("jb.014", "long", Long.valueOf(7));
        typed("jb.015", "string", "1.5");
        typed("jb.016", "object", Double.valueOf(0.1));
        typed("jb.017", "object", new BigDecimal("1.5"));
        typed("jb.018", "nullDouble", null);
        typed("jb.019", "nullDecimal", null);
        typed("jb.020", "double", Double.valueOf(Double.NaN));
        typed("jb.021", "double", Double.valueOf(Double.POSITIVE_INFINITY));
        typed("jb.022", "decimal", new BigDecimal("0.1000000000000000000000000000000000000000"));
        typed("jb.023", "double", Double.valueOf(-0.0));
        typed("jb.024", "decimal", new BigDecimal("1E+3"));
        query("jb.025", "SELECT ? + 1 AS p, SYSTEM$TYPEOF(? + 1) AS t", "double", Double.valueOf(0.1));
        query("jb.026", "SELECT ? / 3 AS p, SYSTEM$TYPEOF(? / 3) AS t", "decimal", new BigDecimal("1"));
        query("jb.027", "SELECT ? = 0.1 AS e", "double", Double.valueOf(0.1));
        query("jb.028", "SELECT 1 FROM (SELECT 1) WHERE 0.1 = ?", "double", Double.valueOf(0.1));
        query("jb.029", "SELECT TO_VARCHAR(?) AS v", "double", Double.valueOf(1e20));
        statement.execute("CREATE OR REPLACE TABLE jb (k VARCHAR, d DECFLOAT, n NUMBER(10,2), f FLOAT, v VARCHAR)");
        insert("jb.030", "d", "double", Double.valueOf(0.1));
        insert("jb.031", "d", "decimal", new BigDecimal("1E-50"));
        insert("jb.032", "n", "double", Double.valueOf(1.255));
        insert("jb.033", "v", "double", Double.valueOf(0.1));
        insert("jb.034", "v", "decimal", new BigDecimal("1.50"));
        insert("jb.035", "f", "decimal", new BigDecimal("1.5"));
        insert("jb.036", "v", "double", Double.valueOf(1e20));
        insert("jb.037", "n", "double", Double.valueOf(1e20));
        insert("jb.038", "v", "float", Float.valueOf(0.1f));
        insert("jb.039", "d", "decimal", new BigDecimal("1.50"));
        query("jb.040", "SELECT k, d::VARCHAR, n, f, v FROM jb WHERE k <> ? ORDER BY k", "string", "x");
        assertEquals("""
            jb.001 => FLOAT[DOUBLE]|0.1
            jb.002 => FLOAT[DOUBLE]|1.5
            jb.003 => FLOAT[DOUBLE]|1e+20
            jb.004 => FLOAT[DOUBLE]|1e-07
            jb.005 => FLOAT[DOUBLE]|123456789.125
            jb.006 => FLOAT[DOUBLE]|0.1
            jb.007 => NUMBER(2,1)[SB1]|1.5
            jb.008 => NUMBER(2,1)[SB1]|1.5
            jb.009 => REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(51,50).
            jb.011 => NUMBER(38,0)[SB16]|12345678901234567890123456789012345678
            jb.012 => NUMBER(4,3)[SB1]|-0.001
            jb.013 => NUMBER(1,0)[SB1]|5
            jb.014 => NUMBER(1,0)[SB1]|7
            jb.015 => VARCHAR(3)[LOB]|1.5
            jb.016 => FLOAT[DOUBLE]|0.1
            jb.017 => NUMBER(2,1)[SB1]|1.5
            jb.020 => FLOAT[DOUBLE]|NaN
            jb.021 => FLOAT[DOUBLE]|inf
            jb.022 => NUMBER(2,1)[SB1]|0.1
            jb.023 => FLOAT[DOUBLE]|-0
            jb.024 => NUMBER(4,0)[SB2]|1000
            jb.025 => 1.1|FLOAT[DOUBLE]
            jb.026 => 0.333333|NUMBER(7,6)[SB4]
            jb.027 => TRUE
            jb.028 => 1
            jb.029 => 1e+20
            jb.030 => bound
            jb.031 => REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(51,50).
            jb.032 => bound
            jb.033 => bound
            jb.034 => bound
            jb.035 => bound
            jb.036 => bound
            jb.037 => REFUSED DML operation to table JB failed on column N with error: Number out of representable \
            range: type FIXED[SB8](10,2){nullable}, value 1e+20
            jb.038 => bound
            jb.039 => bound
            jb.040 => jb.030|0.100000000000000006|null|null|null
            jb.040.row2 => jb.032|null|1.25|null|null
            jb.040.row3 => jb.033|null|null|null|0.1
            jb.040.row4 => jb.034|null|null|null|1.5
            jb.040.row5 => jb.035|null|null|1.5|null
            jb.040.row6 => jb.036|null|null|null|1e+20
            jb.040.row7 => jb.038|null|null|null|0.1
            jb.040.row8 => jb.039|1.5|null|null|null
            """, held("jb.010", "jb.018", "jb.019"));
    }

    @Test
    public void boundDecimals() throws SQLException {
        uncached();
        final String[] decimals = {"-1.5", "0.5", "-0.5", "123.456", "1234567890.123456789",
            "-1234567890.123456789", "1.5E+3", "0.0", "100", "-100", "1E+1", "12345678901234567890123456789.5",
            "0.001", "10.5", "-10.5",
            "1.0", "99999999999999999999999999999999999999", "999999999999999999999999999999999999999", "-1E-5",
            "1E-7", "-1E-7", "0.00001", "5E-1", "1.25", "3.14159"};
        for (int i = 0; i < decimals.length; i++) {
            typed(String.format(Locale.ROOT, "jc.%03d", i + 1), "decimal", new BigDecimal(decimals[i]));
        }
        typed("jc.026", "double", Double.valueOf(-1.5));
        typed("jc.027", "double", Double.valueOf(100.0));
        typed("jc.028", "double", Double.valueOf(Double.MAX_VALUE));
        typed("jc.029", "float", Float.valueOf(1.1f));
        typed("jc.030", "float", Float.valueOf(1e10f));
        query("jc.031", "SELECT ?::NUMBER(10,2) AS n", "double", Double.valueOf(1.255));
        query("jc.032", "SELECT ? AS v", "double", Double.valueOf(0.1));
        query("jc.033", "SELECT ? AS v", "decimal", new BigDecimal("1.5"));
        query("jc.034", "SELECT ? AS v", "decimal", new BigDecimal("-0.001"));
        query("jc.035", "SELECT ?::VARCHAR AS v", "nullDouble", null);
        query("jc.036", "SELECT ? + 1 AS v", "nullDouble", null);
        query("jc.037", "SELECT COALESCE(?, 1) AS v, SYSTEM$TYPEOF(COALESCE(?, 1)) AS t", "nullDecimal", null);
        query("jc.038", "SELECT IFF(? IS NULL, 'n', 'v') AS v", "nullDouble", null);
        query("jc.039", "SELECT 1 AS v FROM (SELECT 1) LIMIT ?", "double", Double.valueOf(2.0));
        query("jc.040", "SELECT 1 AS v FROM (SELECT 1) LIMIT ?", "decimal", new BigDecimal("2"));
        query("jc.041", "SELECT ? = 1.5 AS e, ? = 1.5 AS f", "decimal", new BigDecimal("1.5"));
        query("jc.042", "SELECT ROUND(?, 1) AS r, SYSTEM$TYPEOF(ROUND(?, 1)) AS t", "decimal",
            new BigDecimal("2.25"));
        assertEquals("""
            jc.001 => NUMBER(2,1)[SB1]|-1.5
            jc.002 => NUMBER(2,1)[SB1]|0.5
            jc.003 => NUMBER(2,1)[SB1]|-0.5
            jc.004 => NUMBER(6,3)[SB4]|123.456
            jc.005 => NUMBER(19,9)[SB8]|1234567890.123456789
            jc.006 => NUMBER(19,9)[SB8]|-1234567890.123456789
            jc.007 => NUMBER(4,0)[SB2]|1500
            jc.008 => NUMBER(1,0)[SB1]|0
            jc.009 => NUMBER(3,0)[SB1]|100
            jc.010 => NUMBER(3,0)[SB1]|-100
            jc.011 => NUMBER(2,0)[SB1]|10
            jc.012 => NUMBER(30,1)[SB16]|12345678901234567890123456789.5
            jc.013 => NUMBER(4,3)[SB1]|0.001
            jc.014 => NUMBER(3,1)[SB1]|10.5
            jc.015 => NUMBER(3,1)[SB1]|-10.5
            jc.016 => NUMBER(1,0)[SB1]|1
            jc.017 => NUMBER(38,0)[SB16]|99999999999999999999999999999999999999
            jc.019 => NUMBER(6,5)[SB1]|-0.00001
            jc.020 => NUMBER(8,7)[SB1]|0.0000001
            jc.021 => NUMBER(8,7)[SB1]|-0.0000001
            jc.022 => NUMBER(6,5)[SB1]|0.00001
            jc.023 => NUMBER(2,1)[SB1]|0.5
            jc.024 => NUMBER(3,2)[SB1]|1.25
            jc.025 => NUMBER(6,5)[SB4]|3.14159
            jc.026 => FLOAT[DOUBLE]|-1.5
            jc.027 => FLOAT[DOUBLE]|100
            jc.028 => FLOAT[DOUBLE]|1.79769313486232e+308
            jc.029 => FLOAT[DOUBLE]|1.1
            jc.030 => FLOAT[DOUBLE]|10000000000
            jc.031 => 1.25
            jc.032 => 0.1
            jc.033 => 1.5
            jc.034 => -0.001
            jc.035 => null
            jc.036 => null
            jc.037 => 1|NUMBER(1,0)[SB1]
            jc.038 => n
            jc.039 => REFUSED SQL compilation error: | Invalid row count '?' in limit clause
            jc.040 => 1
            jc.041 => TRUE|TRUE
            jc.042 => 2.3|NUMBER(4,1)[SB2]
            """, held("jc.018"));
    }

    private void labels(final String label, final String sql, final String setter, final Object value) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            final int count = sql.length() - sql.replace("?", "").length();
            for (int i = 1; i <= count; i++) {
                set(ps, i, setter, value);
            }
            try (ResultSet rs = ps.executeQuery()) {
                final ResultSetMetaData md = rs.getMetaData();
                final StringBuilder out = new StringBuilder();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    out.append(i > 1 ? " ;; " : "").append(md.getColumnLabel(i)).append(' ')
                        .append(md.getColumnTypeName(i));
                }
                text.append(label).append(" => ").append(out).append('\n');
            }
        } catch (final SQLException refused) {
            text.append(label).append(" => REFUSED ").append(shape(refused.getMessage())).append('\n');
        }
    }

    @Test
    public void boundPlaces() throws SQLException {
        uncached();
        labels("jd.001", "SELECT ?", "double", Double.valueOf(0.1));
        labels("jd.002", "SELECT ? /* d */", "decimal", new BigDecimal("1.5"));
        labels("jd.003", "SELECT ? /* i */", "int", Integer.valueOf(5));
        labels("jd.004", "SELECT ?, ?", "string", "a");
        labels("jd.005", "SELECT ? + 1", "double", Double.valueOf(0.1));
        query("jd.006", "SELECT 3 - ? AS d", "double", Double.valueOf(-1.5));
        query("jd.007", "SELECT ROUND(1.2345, ?) AS r", "double", Double.valueOf(2.0));
        query("jd.008", "SELECT COUNT(*) AS n FROM TABLE(GENERATOR(ROWCOUNT => ?))", "double", Double.valueOf(2.0));
        query("jd.009", "SELECT ARRAY_CONSTRUCT(?)::VARCHAR AS a, TYPEOF(ARRAY_CONSTRUCT(?)[0]) AS t", "double",
            Double.valueOf(0.1));
        query("jd.010", "SELECT TO_JSON(OBJECT_CONSTRUCT('a', ?)) AS j", "double", Double.valueOf(1e20));
        query("jd.011", "SELECT TO_JSON(OBJECT_CONSTRUCT('a', ?)) AS j", "double", Double.valueOf(0.5));
        query("jd.012", "SELECT 1 AS v FROM (SELECT 1) LIMIT 1 OFFSET ?", "double", Double.valueOf(0.0));
        query("jd.013", "SELECT IFF(? > 1, 'big', 'small') AS v", "double", Double.valueOf(1.5));
        query("jd.014", "SELECT ? IN (1.5, 2) AS v", "double", Double.valueOf(1.5));
        query("jd.015", "SELECT CASE WHEN ? = 0.1 THEN 'eq' ELSE 'ne' END AS v", "double", Double.valueOf(0.1));
        query("jd.016", "SELECT SYSTEM$TYPEOF(-?) AS t", "double", Double.valueOf(2.5));
        query("jd.017", "SELECT ?::VARIANT AS v, TYPEOF(?::VARIANT) AS t", "double", Double.valueOf(2.5));
        query("jd.018", "SELECT SYSTEM$TYPEOF(?) AS t", "decimal", new BigDecimal("0E-10"));
        query("jd.019", "SELECT SYSTEM$TYPEOF(?) AS t, ?::VARCHAR AS v", "decimal", new BigDecimal("-1E+40"));
        query("jd.020", "SELECT SYSTEM$TYPEOF(?) AS t, ?::VARCHAR AS v", "decimal",
            new BigDecimal("0.123456789012345678901234567890123456789"));
        assertEquals("""
            jd.006 => 4.5
            jd.008 => 2
            jd.009 => [1.000000000000000e-01]|DOUBLE
            jd.010 => {"a":1.000000000000000e+20}
            jd.011 => {"a":5.000000000000000e-01}
            jd.012 => REFUSED SQL compilation error: | Invalid row count '?' in result offset clause
            jd.013 => big
            jd.014 => TRUE
            jd.015 => eq
            jd.016 => FLOAT[DOUBLE]
            jd.017 => 2.500000000000000e+00|DOUBLE
            jd.018 => NUMBER(1,0)[SB1]
            jd.020 => REFUSED SQL compilation error:  | Invalid intermediate datatype: NUMBER(40,39).
            """, held("jd.001", "jd.002", "jd.003", "jd.004", "jd.005", "jd.007", "jd.019"));
    }

    /** The answers of the cells run so far, each a line of its label and answer, the ones dropped left out. */
    private String held(final String... dropped) {
        final StringBuilder out = new StringBuilder();
        for (final String line : text.toString().split("\n")) {
            final String label = line.split(" => ")[0].split("\\.row")[0];
            boolean drop = label.equals("uncached");
            for (final String name : dropped) {
                drop |= label.equals(name);
            }
            if (!drop) {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }
}
