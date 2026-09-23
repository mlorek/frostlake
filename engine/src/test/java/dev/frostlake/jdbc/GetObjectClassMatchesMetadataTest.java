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

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@code getObject} hands back is the class {@code getColumnClassName} NAMES — a client reading the
 * metadata is entitled to cast to it. A temporal cell used to come back as the engine's own
 * {@code java.time} value, and a small whole number as an Integer over the wire.
 *
 * <p>The one class that is a driver's own is a zoned timestamp's: the account's driver answers a VENDOR
 * SUBCLASS of {@code java.sql.Timestamp} there, so these read the base class.
 */
public class GetObjectClassMatchesMetadataTest extends BaseJdbcTest {

    /** The value {@code getObject} answers for a one-column query. */
    private Object objectOf(final String expression) throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT " + expression + " AS c")) {
            rs.next();
            return rs.getObject(1);
        }
    }

    /** The class {@code getColumnClassName} names for a one-column query. */
    private String metaClassOf(final String expression) throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT " + expression + " AS c")) {
            rs.next();
            return rs.getMetaData().getColumnClassName(1);
        }
    }

    /** A DATE comes back as java.sql.Date, the class its metadata names. */
    @Test
    public void aDateIsASqlDate() throws SQLException {
        assertEquals("java.sql.Date", metaClassOf("'2024-01-15'::DATE"));
        final Object value = objectOf("'2024-01-15'::DATE");
        assertInstanceOf(Date.class, value);
        assertEquals("2024-01-15", value.toString());
    }

    /** A TIME comes back as java.sql.Time, whatever fraction it carries. */
    @Test
    public void aTimeIsASqlTime() throws SQLException {
        assertEquals("java.sql.Time", metaClassOf("'12:34:56'::TIME"));
        assertInstanceOf(Time.class, objectOf("'12:34:56'::TIME"));
        assertEquals("12:34:56", objectOf("'12:34:56.789'::TIME(3)").toString(),
            "a java.sql.Time spells no fraction");
    }

    /** Every timestamp flavour comes back as a java.sql.Timestamp. */
    @Test
    public void everyTimestampIsASqlTimestamp() throws SQLException {
        assertEquals("java.sql.Timestamp", metaClassOf("'2024-01-15 12:34:56'::TIMESTAMP_NTZ"));
        assertInstanceOf(Timestamp.class, objectOf("'2024-01-15 12:34:56'::TIMESTAMP_NTZ"));
        assertInstanceOf(Timestamp.class, objectOf("'2024-01-15 12:34:56'::TIMESTAMP_LTZ"));
        assertInstanceOf(Timestamp.class, objectOf("'2024-01-15 12:34:56 +0200'::TIMESTAMP_TZ"));
        assertEquals("2024-01-01 00:00:00.123456789",
            objectOf("'2024-01-01 00:00:00.123456789'::TIMESTAMP_NTZ").toString(),
            "and it keeps the whole fraction");
    }

    /** A whole number is a Long while it fits one, and a BigDecimal once it does not. */
    @Test
    public void aWholeNumberIsALongWhileItFits() throws SQLException {
        assertEquals("java.lang.Long", metaClassOf("1"));
        assertEquals(Long.valueOf(1L), objectOf("1"));
        assertEquals(Long.valueOf(9007199254740993L), objectOf("9007199254740993"));
        final Object wide = objectOf("12345678901234567890123456789012345678");
        assertInstanceOf(BigDecimal.class, wide, "past a Long the driver widens it");
        assertEquals("12345678901234567890123456789012345678", wide.toString());
    }

    /** A scaled number is a BigDecimal, as its metadata says. */
    @Test
    public void aScaledNumberIsABigDecimal() throws SQLException {
        assertEquals("java.math.BigDecimal", metaClassOf("1.25"));
        assertInstanceOf(BigDecimal.class, objectOf("1.25"));
        assertInstanceOf(BigDecimal.class, objectOf("CAST(1.25 AS NUMBER(10,2))"));
    }

    /** The classes that already agreed still do, NULL included. */
    @Test
    public void theOtherFamiliesAreUnchanged() throws SQLException {
        assertInstanceOf(Double.class, objectOf("1.5::FLOAT"));
        assertInstanceOf(String.class, objectOf("'ab'"));
        assertInstanceOf(Boolean.class, objectOf("TRUE"));
        assertTrue(objectOf("X'00AB'") instanceof byte[]);
        assertInstanceOf(String.class, objectOf("TO_VARIANT(1)"));
        assertInstanceOf(String.class, objectOf("OBJECT_CONSTRUCT('a', 1)"));
        assertInstanceOf(String.class, objectOf("ARRAY_CONSTRUCT(1, 2)"));
        assertNull(objectOf("CAST(NULL AS INT)"));
    }
}
