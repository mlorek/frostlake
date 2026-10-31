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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Reads a FLOAT column holding 1.5, NaN, an infinity each way and NULL through a driver: getObject's class and value,
 * getDouble and getString, as the account's driver answers them on its default results (live-verified) — shared by
 * the in-process and the HTTP driver's tests.
 */
final class FloatNonFiniteSupport {

    /** What every driver reads, the account's included. */
    static final String EXPECTED = "1:Double=1.5,1.5,1.5 2:Double=NaN,NaN,NaN 3:Double=Infinity,Infinity,Infinity"
        + " 4:Double=-Infinity,-Infinity,-Infinity 5:null,0.0,null";

    private FloatNonFiniteSupport() {
    }

    /**
     * The reads, one line per row.
     *
     * @param connection the connection, its current schema set
     * @return what each row read as
     * @throws SQLException when a statement fails
     */
    static String read(final Connection connection) throws SQLException {
        final Statement st = connection.createStatement();
        try {
            st.execute("CREATE OR REPLACE TABLE fnan (k INT, v FLOAT)");
            st.execute("INSERT INTO fnan VALUES (1, 1.5)");
            st.execute("INSERT INTO fnan VALUES (2, 'NaN')");
            st.execute("INSERT INTO fnan VALUES (3, 'inf')");
            st.execute("INSERT INTO fnan VALUES (4, '-inf')");
            st.execute("INSERT INTO fnan VALUES (5, NULL)");
            final ResultSet rs = st.executeQuery("SELECT k, v FROM fnan ORDER BY k");
            final StringBuilder out = new StringBuilder();
            while (rs.next()) {
                final Object o = rs.getObject(2);
                out.append(rs.getInt(1)).append(':')
                    .append(o == null ? "null" : o.getClass().getSimpleName() + "=" + o)
                    .append(',').append(rs.getDouble(2)).append(',').append(rs.getString(2)).append(' ');
            }
            return out.toString().trim();
        } finally {
            st.execute("DROP TABLE IF EXISTS fnan");
            st.close();
        }
    }
}
