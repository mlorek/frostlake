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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The account's driver hands back every VARIANT, OBJECT and ARRAY cell from getObject as the String getString reads
 * (live-verified), as getColumnClassName promises: a VARIANT holding a BINARY, a DATE, a TIME or a timestamp included,
 * which the in-process driver handed back as a byte[] or a java.time value. A binary reads as the JSON string of its
 * hex digits, as a temporal reads as the JSON string of its text.
 */
public class VariantCellObjectTest {

    private static final Logger logger = LoggerFactory.getLogger(VariantCellObjectTest.class);

    private Connection connection;

    @AfterEach
    public void tearDown() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    public void everyVariantCellIsItsText() throws SQLException {
        connection = DriverManager.getConnection("jdbc:frostlake:direct:variant_cell_object");
        try (Statement s = connection.createStatement()) {
            s.execute("CREATE OR REPLACE DATABASE vco_db");
            s.execute("CREATE TABLE vt (k INT, v VARIANT)");
            s.execute("INSERT INTO vt SELECT 1, TO_VARIANT(TO_BINARY('AB')) UNION ALL "
                + "SELECT 2, TO_VARIANT('2024-01-01'::DATE) UNION ALL "
                + "SELECT 3, TO_VARIANT('2024-01-01 10:00:00'::TIMESTAMP_NTZ) UNION ALL "
                + "SELECT 4, TO_VARIANT('10:00:00'::TIME) UNION ALL SELECT 5, TO_VARIANT(1.5)");
            final StringBuilder texts = new StringBuilder();
            try (ResultSet r = s.executeQuery("SELECT k, v FROM vt ORDER BY k")) {
                assertEquals("java.lang.String", r.getMetaData().getColumnClassName(2));
                while (r.next()) {
                    final Object value = r.getObject(2);
                    logger.info("row {}: getObject {} {}, getString {}", r.getInt(1), value.getClass().getName(),
                        value, r.getString(2));
                    assertEquals(String.class, value.getClass(), "row " + r.getInt(1));
                    assertEquals(r.getString(2), value, "row " + r.getInt(1));
                    texts.append(texts.length() > 0 ? " ;; " : "").append(value);
                }
                // Each is its JSON text: a binary and a temporal a JSON string, a number bare.
                assertEquals("\"AB\" ;; \"2024-01-01\" ;; \"2024-01-01 10:00:00.000\" ;; \"10:00:00\" ;; 1.5",
                    texts.toString());
            }
        }
    }
}
