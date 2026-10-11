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
 * Names, keywords and texts read the same whatever the default locale of the JVM the engine runs in. The
 * engine's tests run here under a Turkish one, where a lower-case {@code i} folds to a dotted capital and a
 * capital {@code I} to a dotless small letter: functions, table functions and named arguments written in
 * lower case resolve, identifiers are stored and found in upper case, and UPPER, LOWER, INITCAP, ILIKE, the
 * date and number formats and the type names answer what they answer everywhere. Every answer is
 * live-verified.
 */
public class DefaultLocaleCaseFoldTest extends MeasuredCellsTestSupport {

    /** The default locale the JVM had before this class changed it. */
    private static Locale before;

    /**
     * Runs the class under a Turkish default locale. An account's answers do not depend on its client's
     * locale, and the driver that reaches one is not this class's to judge, so a run against an account keeps
     * the locale it has.
     */
    @BeforeAll
    public static void foldAsATurkishHostDoes() {
        before = Locale.getDefault();
        if (!LiveSnowflake.enabled()) {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        }
    }

    /** Puts the JVM's default locale back for the classes that run after this one. */
    @AfterAll
    public static void foldAsBefore() {
        Locale.setDefault(before);
    }

    @Test
    public void namesAndTextsFoldTheSameEverywhere() {
        assertAnswer(
            "1|a|Hi",
            "SELECT iff(TRUE, 1, 2) a, split_part('a,b', ',', 1) b, initcap('hi') c");
        assertAnswer(
            "1|1|a ;; 1|2|b",
            "SELECT * FROM TABLE(split_to_table('a,b', ',')) ORDER BY 2");
        assertAnswer(
            "1",
            "SELECT value FROM TABLE(flatten(input => parse_json('[1]')))");
        assertAnswer(
            "Table TI successfully created.",
            "CREATE OR REPLACE TABLE ti (id INT, title VARCHAR)");
        assertAnswer(
            "1",
            "INSERT INTO ti VALUES (1, 'istanbul')");
        assertAnswer(
            "1",
            "SELECT id FROM ti");
        assertAnswer(
            "1|1",
            "SELECT min(id), listagg(id) FROM ti");
        assertShape(
            "ID:NUMBER(38,0), TITLE:VARCHAR(16777216)",
            "SELECT * FROM ti");
        assertAnswer(
            "1|istanbul|istanbul",
            "SELECT ti.id, Ti.TITLE, tI.title FROM ti");
        assertAnswer(
            "1|istanbul",
            "SELECT \"ID\", \"TITLE\" FROM \"TI\"");
        assertAnswer(
            "I|i|ISTANBUL|Istanbul|Ii Ii",
            "SELECT upper('i'), lower('I'), upper(title), initcap(title), initcap('iI ii') FROM ti");
        assertAnswer(
            "true|true|true|true",
            "SELECT 'i' ILIKE 'I', 'I' ILIKE 'i', 'title' ILIKE 'TITLE', CONTAINS(UPPER('title'), 'TIT')");
        assertAnswer(
            "I|false|2|SS|true",
            "SELECT UPPER('\u0131'), LOWER('\u0130') = 'i', LENGTH(LOWER('\u0130')), UPPER('\u00df'), LOWER('I') "
                + "= 'i'");
        assertAnswer(
            "1234.5|1234.5|0.5|   1,234,567.89|2.6",
            "SELECT TO_CHAR(1234.5), 1234.5::VARCHAR, TO_VARCHAR(0.5::FLOAT), TO_CHAR(1234567.891, '999,999,"
                + "999.99'), ROUND(2.567, 1)::VARCHAR");
        assertAnswer(
            "Mar Tue March|Tue|Mar|Tue, 05 Mar 2024 01:04 PM",
            "SELECT TO_CHAR('2024-03-05'::DATE, 'MON DY MMMM'), DAYNAME('2024-03-05'::DATE), "
                + "MONTHNAME('2024-03-05'::DATE), TO_CHAR('2024-03-05 13:04:05'::TIMESTAMP_NTZ, 'DY, DD MON YYYY "
                + "HH12:MI AM')");
        assertAnswer(
            "REFUSED Can't parse '5 mart 2024' as date with format 'DD MMMM YYYY'",
            "SELECT TO_DATE('05-MAR-2024', 'DD-MON-YYYY'), TO_DATE('5 mart 2024', 'DD MMMM YYYY')");
        answer(
            "SELECT TRY_TO_BOOLEAN('TRUE'), TRY_TO_BOOLEAN('yes'), 'x'::VARIANT, PARSE_JSON('{\"i\":1}'):i, "
                + "OBJECT_CONSTRUCT('id', 1):ID");
        assertAnswer(
            "INTEGER|true|NUMBER(1,0)[SB1]|null|0|0.000000",
            "SELECT typeof(1::VARIANT), is_integer(1::VARIANT), system$typeof(1), nullif(1, 1), zeroifnull(NULL), "
                + "div0(1, 0)");
        assertAnswer(
            "1|istanbul|istanbul|[1]|2|8",
            "SELECT COUNT(DISTINCT id), ANY_VALUE(title), MAX_BY(title, id), ARRAY_AGG(id), BITAND(6, 3), "
                + "BIT_LENGTH('i') FROM ti");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value [millisecond] for parameter 'EXTRACT date/time part'",
            "SELECT DATE_PART(minute, '2024-03-05 13:04:05'::TIMESTAMP_NTZ), DATEDIFF(minute, '2024-03-05 "
                + "13:04:05'::TIMESTAMP_NTZ, '2024-03-05 13:14:05'::TIMESTAMP_NTZ), DATE_TRUNC('minute', '2024-03-05 "
                + "13:04:05'::TIMESTAMP_NTZ)::VARCHAR, EXTRACT(millisecond FROM '13:04:05.123'::TIME)");
        assertAnswer(
            "i||2|ibc|ii|true|true",
            "SELECT TRIM('  i  '), LTRIM('ii', 'i'), POSITION('i' IN 'hi'), INSERT('abc', 1, 1, 'i'), REPEAT('i', "
                + "2), RLIKE('i', 'I', 'i'), REGEXP_LIKE('I', 'i', 'i')");
        assertAnswer(
            "View VI successfully created.",
            "CREATE OR REPLACE VIEW vi AS SELECT id AS ident, title FROM ti");
        assertAnswer(
            "1",
            "SELECT ident FROM vi");
        answer(
            "SHOW TABLES LIKE 'ti'");
        assertAnswer(
            "TI",
            "SELECT \"name\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "ID|NUMBER(38,0)|COLUMN|Y|null|N|N|null|null|null|null|null|null ;; "
                + "TITLE|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|null|null|null|null",
            "DESCRIBE TABLE ti");
        assertAnswer(
            "ID|NUMBER(38,0) ;; TITLE|VARCHAR(16777216)",
            "SELECT \"name\", \"type\" FROM TABLE(RESULT_SCAN(LAST_QUERY_ID()))");
        assertAnswer(
            "Function INC_I successfully created.",
            "CREATE OR REPLACE FUNCTION inc_i(i NUMBER) RETURNS NUMBER AS $$ i + 1 $$");
        assertAnswer(
            "2|3",
            "SELECT inc_i(1), INC_I(i => 2)");
        assertAnswer(
            "ID ;; TITLE",
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'TI' AND table_schema = "
                + "CURRENT_SCHEMA() ORDER BY ordinal_position");
        assertAnswer(
            "1",
            "SELECT id FROM ti WHERE title IN ('istanbul') AND title LIKE 'i%' AND title BETWEEN 'i' AND 'j' "
                + "ORDER BY id LIMIT 1");
        assertAnswer(
            "REFUSED The following string is not a legal hex-encoded value: 'i'",
            "SELECT TRY_CAST('1' AS INTEGER), CAST(1 AS string), 1::int, 1::bigint, TRUE::boolean, 'i'::binary, "
                + "1.5::decimal(3,1)");
        assertAnswer(
            "3|1,2",
            "WITH it AS (SELECT 1 AS i UNION ALL SELECT 2) SELECT SUM(i), listagg(i, ',') WITHIN GROUP (ORDER BY "
                + "i) FROM it");
        assertAnswer(
            "1",
            "SELECT i FROM (SELECT 1 AS i) QUALIFY ROW_NUMBER() OVER (PARTITION BY i ORDER BY i) = 1");
        assertAnswer(
            "2",
            "BEGIN LET i := 1; LET item := i + 1; RETURN item; END");
    }
}
