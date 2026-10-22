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

import dev.frostlake.LiveSnowflake;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Locale;

/**
 * Dates, times and numbers are written in ASCII digits whatever the default locale of the JVM the engine runs in:
 * the engine's tests run here under one whose digits are Arabic-Indic, where formatting by the default locale
 * writes those digits instead. Every answer is live-verified.
 */
public class DefaultLocaleDigitsTest extends MeasuredCellsTestSupport {

    /** The default locale the JVM had before this class changed it. */
    private static Locale before;

    /**
     * Runs the class under a default locale whose digits are Arabic-Indic. An account's answers do not depend on its
     * client's locale, so a run against an account keeps the locale it has.
     */
    @BeforeAll
    public static void formatAsAnArabicHostDoes() {
        before = Locale.getDefault();
        if (!LiveSnowflake.enabled()) {
            Locale.setDefault(Locale.forLanguageTag("ar-SA-u-nu-arab"));
        }
    }

    /** Puts the JVM's default locale back for the classes that run after this one. */
    @AfterAll
    public static void formatAsBefore() {
        Locale.setDefault(before);
    }

    @Test
    public void textsOfTemporalsAndNumbers() {
        assertAnswer("2024-01-02", "SELECT TO_VARCHAR(TO_DATE('2024-01-02'))");
        answer("SELECT '2024-01-02 03:04:05.123'::TIMESTAMP_NTZ::VARCHAR");
        assertAnswer("03:04:05", "SELECT TO_VARCHAR('03:04:05'::TIME)");
        assertAnswer("2024-01-02", "SELECT TO_VARIANT(TO_DATE('2024-01-02'))::VARCHAR");
        answer("SELECT '2024-01-02 03:04:05 +01:30'::TIMESTAMP_TZ::VARCHAR");
        assertAnswer("2024-01-03", "SELECT DATEADD(day, 1, '2024-01-02'::DATE)::VARCHAR");
        assertAnswer("{\"d\":\"2024-01-02\"}", "SELECT TO_JSON(OBJECT_CONSTRUCT('d', '2024-01-02'::DATE))");
        assertAnswer("[\"2024-01-02\",\"03:04:05\"]",
            "SELECT ARRAY_CONSTRUCT('2024-01-02'::DATE, '03:04:05'::TIME)::VARCHAR");
        answer("SELECT TO_VARCHAR('2024-01-02 03:04:05'::TIMESTAMP_NTZ, 'YYYY-MM-DD HH24:MI:SS')");
        assertAnswer("0001-01-02", "SELECT '0001-01-02'::DATE::VARCHAR");
        assertAnswer("12345.678|1.5|12345",
            "SELECT TO_VARCHAR(12345.678::NUMBER(10,3)), TO_VARCHAR(1.5::FLOAT), 12345::VARCHAR");
        assertAnswer("true", "SELECT TO_VARCHAR('2024-01-02 03:04:05.123456789'::TIMESTAMP_LTZ(9) IS NOT NULL)");
    }
}
