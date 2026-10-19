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
package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The values ALTER SESSION SET takes (live-verified). A parameter takes a value of its own kind — a STRING
 * takes text, a NUMBER a whole number of 32 bits, a BOOLEAN TRUE or FALSE — and NULL, which puts it back on
 * its default; a value of another kind is refused as written. A value of the right kind is then weighed against
 * the parameter's range or vocabulary. The list is judged whole before anything applies, its names taken in the
 * order of their text, and the assignments need no comma between them.
 */
public class SessionParameterValueTest extends BaseDatabaseTest {

    private static final String PREFIX = "SQL compilation error:\n";

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    private void assertInvalid(final String assignment, final String echoed, final String parameter) {
        assertEquals(PREFIX + "invalid value [" + echoed + "] for parameter '" + parameter + "'",
            refusal("ALTER SESSION SET " + assignment), assignment);
    }

    /** The value and the level SHOW PARAMETERS reports for a session parameter, a bar between them. */
    private String held(final String name) {
        final ResultSet rs = engine.executeQuery("SHOW PARAMETERS LIKE '" + name + "' IN SESSION");
        assertEquals(1, rs.getRowCount(), name);
        return rs.getRows().get(0).getValue(1) + "|" + rs.getRows().get(0).getValue(3);
    }

    @Test
    public void aStringParameterRefusesANumberOrABoolean() {
        assertInvalid("QUERY_TAG = 2147483647", "2147483647", "QUERY_TAG");
        assertInvalid("QUERY_TAG = 1.5", "1.5", "QUERY_TAG");
        assertInvalid("QUERY_TAG = 12345678901234567890.5", "12345678901234567890.5", "QUERY_TAG");
        assertInvalid("QUERY_TAG = 00000000000000000000001", "00000000000000000000001", "QUERY_TAG");
        assertInvalid("QUERY_TAG = 1e2", "1e2", "QUERY_TAG");
        assertInvalid("QUERY_TAG = -2147483648", "-2147483648", "QUERY_TAG");
        // The sign joins its number in the echo.
        assertInvalid("QUERY_TAG = - 5", "-5", "QUERY_TAG");
        assertInvalid("QUERY_TAG = TRUE", "TRUE", "QUERY_TAG");
        assertInvalid("QUERY_TAG = False", "False", "QUERY_TAG");
        assertInvalid("TIMEZONE = 5", "5", "TIMEZONE");
        assertInvalid("DATE_OUTPUT_FORMAT = 5", "5", "DATE_OUTPUT_FORMAT");
        assertInvalid("BINARY_INPUT_FORMAT = 1", "1", "BINARY_INPUT_FORMAT");
        assertInvalid("query_tag = 5", "5", "QUERY_TAG");
        assertEquals("|", held("QUERY_TAG"));
    }

    @Test
    public void aNumberParameterTakesAWholeNumberOfThirtyTwoBits() {
        assertInvalid("LOCK_TIMEOUT = 'x'", "'x'", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = '5'", "'5'", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = 1.5", "1.5", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = 5.0", "5.0", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = -1.5", "-1.5", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = 1e2", "1e2", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = TRUE", "TRUE", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = abc", "abc", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = \"5\"", "\"5\"", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = x'41'", "x'41'", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = $$5$$", "$$5$$", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = 2147483648", "2147483648", "LOCK_TIMEOUT");
        assertInvalid("LOCK_TIMEOUT = -2147483649", "-2147483649", "LOCK_TIMEOUT");
        assertInvalid("WEEK_START = monday", "monday", "WEEK_START");
        assertInvalid("JSON_INDENT = '4'", "'4'", "JSON_INDENT");
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = -1");
        assertEquals("-1|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = 05");
        assertEquals("5|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = - 5");
        assertEquals("-5|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = -0");
        assertEquals("0|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = 2147483647");
        assertEquals("2147483647|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = -2147483648");
        assertEquals("-2147483648|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION UNSET LOCK_TIMEOUT");
        assertEquals(PREFIX + "syntax error line 1 at position 33 unexpected '+'.",
            refusal("ALTER SESSION SET LOCK_TIMEOUT = +5"));
    }

    @Test
    public void aBooleanParameterTakesOnlyTrueOrFalse() {
        assertInvalid("AUTOCOMMIT = 1", "1", "AUTOCOMMIT");
        assertInvalid("AUTOCOMMIT = 0", "0", "AUTOCOMMIT");
        assertInvalid("AUTOCOMMIT = -1", "-1", "AUTOCOMMIT");
        assertInvalid("AUTOCOMMIT = 1.5", "1.5", "AUTOCOMMIT");
        assertInvalid("AUTOCOMMIT = 'true'", "'true'", "AUTOCOMMIT");
        assertInvalid("AUTOCOMMIT = yes", "yes", "AUTOCOMMIT");
        assertInvalid("AUTOCOMMIT = \"TRUE\"", "\"TRUE\"", "AUTOCOMMIT");
        assertInvalid("AUTOCOMMIT = $$true$$", "$$true$$", "AUTOCOMMIT");
        assertInvalid("ERROR_ON_NONDETERMINISTIC_UPDATE = 'no'", "'no'", "ERROR_ON_NONDETERMINISTIC_UPDATE");
        engine.execute("ALTER SESSION SET ABORT_DETACHED_QUERY = true");
        assertEquals("true|SESSION", held("ABORT_DETACHED_QUERY"));
        engine.execute("ALTER SESSION SET ABORT_DETACHED_QUERY = False");
        assertEquals("false|SESSION", held("ABORT_DETACHED_QUERY"));
        engine.execute("ALTER SESSION UNSET ABORT_DETACHED_QUERY");
    }

    /** An unquoted word upper-folds, a quoted one keeps its content, and a name of several parts stays as written. */
    @Test
    public void aStringParameterTakesAWord() {
        final String[][] words = {{"abc", "ABC"}, {"\"abc\"", "abc"}, {"\"a b\"", "a b"}, {"a.B", "a.B"},
            {"\"a\".\"B\"", "\"a\".\"B\""}, {"current_date", "CURRENT_DATE"}, {"$$dq$$", "dq"},
            {"x'41'", "x'41'"}, {"'it''s'", "it's"}, {"''", ""}};
        for (final String[] word : words) {
            engine.execute("ALTER SESSION SET QUERY_TAG = " + word[0]);
            assertEquals(word[1] + "|SESSION", held("QUERY_TAG"), word[0]);
        }
        engine.execute("ALTER SESSION UNSET QUERY_TAG");
        engine.execute("ALTER SESSION SET TIMESTAMP_TYPE_MAPPING = timestamp_ltz");
        assertEquals("timestamp_ltz|SESSION", held("TIMESTAMP_TYPE_MAPPING"));
        engine.execute("ALTER SESSION UNSET TIMESTAMP_TYPE_MAPPING");
        engine.execute("ALTER SESSION SET DATE_INPUT_FORMAT = auto");
        assertEquals("auto|SESSION", held("DATE_INPUT_FORMAT"));
        engine.execute("ALTER SESSION UNSET DATE_INPUT_FORMAT");
        engine.execute("ALTER SESSION SET TRACE_LEVEL = on_event");
        assertEquals("ON_EVENT|SESSION", held("TRACE_LEVEL"));
        engine.execute("ALTER SESSION UNSET TRACE_LEVEL");
        // A reserved word is no value.
        assertEquals(PREFIX + "syntax error line 1 at position 30 unexpected 'select'.",
            refusal("ALTER SESSION SET QUERY_TAG = select"));
    }

    @Test
    public void nullPutsAParameterBackOnItsDefault() {
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = 77");
        assertEquals("77|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = NULL");
        assertEquals("43200|", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET QUERY_TAG = 'keep'");
        engine.execute("ALTER SESSION SET QUERY_TAG = null");
        assertEquals("|", held("QUERY_TAG"));
        engine.execute("ALTER SESSION SET ABORT_DETACHED_QUERY = NULL");
        assertEquals("false|", held("ABORT_DETACHED_QUERY"));
    }

    @Test
    public void aListAppliesNothingWhenOneAssignmentIsRefused() {
        assertInvalid("LOCK_TIMEOUT = 1, QUERY_TAG = 2147483648", "2147483648", "QUERY_TAG");
        assertEquals("43200|", held("LOCK_TIMEOUT"));
        assertEquals(PREFIX + "invalid parameter 'NOSUCH_PARAM'",
            refusal("ALTER SESSION SET LOCK_TIMEOUT = 3, NOSUCH_PARAM = 5"));
        assertEquals("43200|", held("LOCK_TIMEOUT"));
        assertInvalid("LOCK_TIMEOUT = 11, WEEK_START = 9", "9", "WEEK_START");
        assertEquals("43200|", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = 9, QUERY_TAG = NULL");
        assertInvalid("LOCK_TIMEOUT = NULL, QUERY_TAG = 5", "5", "QUERY_TAG");
        assertEquals("9|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION UNSET LOCK_TIMEOUT");
        assertEquals(PREFIX + "invalid parameter 'NOSUCH'", refusal("ALTER SESSION UNSET LOCK_TIMEOUT, NOSUCH"));
    }

    @Test
    public void assignmentsNeedNoCommaBetweenThem() {
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = 4 QUERY_TAG = 't'");
        assertEquals("4|SESSION", held("LOCK_TIMEOUT"));
        assertEquals("t|SESSION", held("QUERY_TAG"));
        engine.execute("ALTER SESSION SET QUERY_TAG = abc LOCK_TIMEOUT = 6");
        assertEquals("ABC|SESSION", held("QUERY_TAG"));
        assertEquals("6|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = -8 QUERY_TAG = a.b");
        assertEquals("-8|SESSION", held("LOCK_TIMEOUT"));
        assertEquals("a.b|SESSION", held("QUERY_TAG"));
        assertInvalid("LOCK_TIMEOUT = 4 QUERY_TAG = 5", "5", "QUERY_TAG");
        assertEquals(PREFIX + "syntax error line 1 at position 53 unexpected '<EOF>'.",
            refusal("ALTER SESSION SET LOCK_TIMEOUT = 5, QUERY_TAG = 't2',"));
        // A word after a value reads as the next parameter's name, so the input ends before its '='.
        assertEquals(PREFIX + "syntax error line 1 at position 37 unexpected '<EOF>'.",
            refusal("ALTER SESSION SET QUERY_TAG = abc def"));
        // UNSET's names need their commas.
        assertEquals(PREFIX + "syntax error line 1 at position 33 unexpected 'QUERY_TAG'.",
            refusal("ALTER SESSION UNSET LOCK_TIMEOUT QUERY_TAG"));
        engine.execute("ALTER SESSION UNSET LOCK_TIMEOUT, QUERY_TAG");
        assertEquals("43200|", held("LOCK_TIMEOUT"));
    }

    /**
     * With two faults the refusal follows the names, not the order written: a value of the wrong kind before a
     * value out of range, the NUMBER parameters before the STRING ones and those before the BOOLEAN ones, and
     * within one step the names by their text.
     */
    @Test
    public void theRefusalOfTwoFaultsFollowsTheNames() {
        final String[][] lists = {
            {"QUERY_TAG = 5, LOCK_TIMEOUT = 'x'", "'x'", "LOCK_TIMEOUT"},
            {"LOCK_TIMEOUT = 'x', QUERY_TAG = 5", "'x'", "LOCK_TIMEOUT"},
            {"QUERY_TAG = 5, WEEK_START = 'x'", "'x'", "WEEK_START"},
            {"AUTOCOMMIT = 1, LOCK_TIMEOUT = 'x'", "'x'", "LOCK_TIMEOUT"},
            {"AUTOCOMMIT = 1, BINARY_INPUT_FORMAT = 5", "5", "BINARY_INPUT_FORMAT"},
            {"BINARY_INPUT_FORMAT = 5, AUTOCOMMIT = 1", "5", "BINARY_INPUT_FORMAT"},
            {"TIMEZONE = 5, QUERY_TAG = 5", "5", "QUERY_TAG"},
            {"QUERY_TAG = 5, DATE_OUTPUT_FORMAT = 5", "5", "DATE_OUTPUT_FORMAT"},
            {"WEEK_START = 'z', LOCK_TIMEOUT = 'y', JSON_INDENT = 'x'", "'x'", "JSON_INDENT"},
            {"AUTOCOMMIT = 1, ABORT_DETACHED_QUERY = 2", "2", "ABORT_DETACHED_QUERY"},
            {"CLIENT_PREFETCH_THREADS = 0, QUERY_TAG = 5", "5", "QUERY_TAG"},
            {"TIMEZONE = 'Nowhere/Land', QUERY_TAG = 5", "5", "QUERY_TAG"},
            {"STATEMENT_TIMEOUT_IN_SECONDS = -1, QUERY_TAG = 5", "5", "QUERY_TAG"},
            {"LOCK_TIMEOUT = 2147483648, QUERY_TAG = 5", "2147483648", "LOCK_TIMEOUT"},
            {"QUERY_TAG = 5, LOCK_TIMEOUT = 2147483648", "2147483648", "LOCK_TIMEOUT"},
            {"WEEK_START = 9, TIMEZONE = 'Nowhere/Land'", "Nowhere/Land", "TIMEZONE"},
            {"TIMEZONE = 'Nowhere/Land', WEEK_START = 9", "Nowhere/Land", "TIMEZONE"},
            {"WEEK_START = 9, CLIENT_PREFETCH_THREADS = 0", "0", "CLIENT_PREFETCH_THREADS"},
            {"STATEMENT_TIMEOUT_IN_SECONDS = -1, CLIENT_PREFETCH_THREADS = 0", "0", "CLIENT_PREFETCH_THREADS"}};
        for (final String[] list : lists) {
            assertInvalid(list[0], list[1], list[2]);
        }
        assertEquals("SQL compilation error: \nparameter value out of range: -1. Must be between 0 and 604,800.",
            refusal("ALTER SESSION SET WEEK_START = 9, STATEMENT_TIMEOUT_IN_SECONDS = -1"));
        assertEquals(PREFIX + "Invalid binary format string 'nope': Must be 'HEX' or 'BASE64'",
            refusal("ALTER SESSION SET TIMEZONE = 'Nowhere/Land', BINARY_OUTPUT_FORMAT = 'nope'"));
        assertEquals(PREFIX + "invalid parameter 'NOSUCH'", refusal("ALTER SESSION SET QUERY_TAG = 5, NOSUCH = 1"));
        assertEquals(PREFIX + "invalid parameter 'NOSUCH0'",
            refusal("ALTER SESSION SET NOSUCH1 = 1, NOSUCH0 = 2"));
        assertEquals(PREFIX + "invalid parameter 'NOSUCH0'", refusal("ALTER SESSION UNSET NOSUCH1, NOSUCH0"));
    }

    @Test
    public void aNameWrittenTwiceIsADuplicateProperty() {
        assertEquals(PREFIX + "duplicate property 'LOCK_TIMEOUT';",
            refusal("ALTER SESSION SET LOCK_TIMEOUT = 5, LOCK_TIMEOUT = 6"));
        assertEquals(PREFIX + "duplicate property 'LOCK_TIMEOUT';",
            refusal("ALTER SESSION SET LOCK_TIMEOUT = 7 LOCK_TIMEOUT = 'x'"));
        assertEquals(PREFIX + "duplicate property 'lock_timeout';",
            refusal("ALTER SESSION SET lock_timeout = 1, lock_timeout = 2"));
        assertEquals(PREFIX + "duplicate property 'LOCK_TIMEOUT';",
            refusal("ALTER SESSION SET LOCK_TIMEOUT = 5, LOCK_TIMEOUT = 6, NOSUCH = 1"));
        assertEquals(PREFIX + "duplicate property 'NOSUCH';", refusal("ALTER SESSION SET NOSUCH = 1, NOSUCH = 2"));
        assertEquals(PREFIX + "duplicate property 'LOCK_TIMEOUT';",
            refusal("ALTER SESSION UNSET LOCK_TIMEOUT, LOCK_TIMEOUT"));
        assertEquals("43200|", held("LOCK_TIMEOUT"));
        // Two spellings of one name are no duplicate: both apply in the order of their text, so the later wins.
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = 1, lock_timeout = 2");
        assertEquals("2|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET lock_timeout = 3, LOCK_TIMEOUT = 4");
        assertEquals("3|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION UNSET LOCK_TIMEOUT, lock_timeout");
        assertEquals("43200|", held("LOCK_TIMEOUT"));
    }

    @Test
    public void anUnknownNameIsEchoedAsWrittenAndAQuotedNameIsNoParameter() {
        assertEquals(PREFIX + "invalid parameter 'nosuch'", refusal("ALTER SESSION SET nosuch = 5"));
        assertEquals(PREFIX + "invalid parameter 'nosuch'", refusal("ALTER SESSION UNSET nosuch"));
        assertEquals(PREFIX + "invalid parameter '\"LOCK_TIMEOUT\"'",
            refusal("ALTER SESSION SET \"LOCK_TIMEOUT\" = 5"));
        assertEquals(PREFIX + "invalid parameter '\"lock_timeout\"'",
            refusal("ALTER SESSION SET \"lock_timeout\" = 5"));
        assertEquals(PREFIX + "invalid parameter '\"LOCK_TIMEOUT\"'", refusal("ALTER SESSION UNSET \"LOCK_TIMEOUT\""));
        assertEquals(PREFIX + "invalid parameter 'NOSUCH'",
            refusal("ALTER SESSION SET \"QUERY_TAG\" = 'x', NOSUCH = 1"));
        assertEquals(PREFIX + "invalid parameter '\"QUERY_TAG\"'",
            refusal("ALTER SESSION SET nosuch = 1, \"QUERY_TAG\" = 'x'"));
    }

    @Test
    public void aNumberOutsideItsParametersRangeIsRefused() {
        assertInvalid("WEEK_START = 8", "8", "WEEK_START");
        assertInvalid("WEEK_START = -1", "-1", "WEEK_START");
        assertInvalid("WEEK_OF_YEAR_POLICY = 2", "2", "WEEK_OF_YEAR_POLICY");
        // A number is echoed with its thousands grouped once it is weighed against the range.
        assertInvalid("TWO_DIGIT_CENTURY_START = 1899", "1,899", "TWO_DIGIT_CENTURY_START");
        assertInvalid("TWO_DIGIT_CENTURY_START = 2101", "2,101", "TWO_DIGIT_CENTURY_START");
        assertInvalid("CLIENT_PREFETCH_THREADS = 0", "0", "CLIENT_PREFETCH_THREADS");
        assertInvalid("CLIENT_PREFETCH_THREADS = 2147483647", "2,147,483,647", "CLIENT_PREFETCH_THREADS");
        assertInvalid("CLIENT_RESULT_CHUNK_SIZE = 15", "15", "CLIENT_RESULT_CHUNK_SIZE");
        assertInvalid("CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY = 3601", "3,601",
            "CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY");
        assertInvalid("ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 7", "7", "ICEBERG_TIMESTAMP_DEFAULT_PRECISION");
        assertInvalid("SNOWPARK_REQUEST_TIMEOUT_IN_SECONDS = 604800", "604,800",
            "SNOWPARK_REQUEST_TIMEOUT_IN_SECONDS");
        assertInvalid("HYBRID_TABLE_LOCK_TIMEOUT = -1", "-1", "HYBRID_TABLE_LOCK_TIMEOUT");
        assertInvalid("STATEMENT_QUEUED_TIMEOUT_IN_SECONDS = -1", "-1", "STATEMENT_QUEUED_TIMEOUT_IN_SECONDS");
        assertEquals("SQL compilation error: \nparameter value out of range: -1. Must be between 0 and 604,800.",
            refusal("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = -1"));
        assertEquals("SQL compilation error: \nparameter value out of range: 604,801. Must be between 0 and 604,800.",
            refusal("ALTER SESSION SET STATEMENT_TIMEOUT_IN_SECONDS = 604801"));
        for (final String taken : new String[] {"WEEK_START = 7", "WEEK_OF_YEAR_POLICY = 1",
            "TWO_DIGIT_CENTURY_START = 1900", "TWO_DIGIT_CENTURY_START = 2100", "CLIENT_PREFETCH_THREADS = 10",
            "CLIENT_RESULT_CHUNK_SIZE = 16", "ICEBERG_TIMESTAMP_DEFAULT_PRECISION = 9",
            "SNOWPARK_REQUEST_TIMEOUT_IN_SECONDS = 604799", "ROWS_PER_RESULTSET = -5", "CLIENT_MEMORY_LIMIT = -1"}) {
            engine.execute("ALTER SESSION SET " + taken);
        }
        assertEquals("7|SESSION", held("WEEK_START"));
        engine.execute("ALTER SESSION UNSET WEEK_START, WEEK_OF_YEAR_POLICY, TWO_DIGIT_CENTURY_START,"
            + " CLIENT_PREFETCH_THREADS, CLIENT_RESULT_CHUNK_SIZE, ICEBERG_TIMESTAMP_DEFAULT_PRECISION,"
            + " SNOWPARK_REQUEST_TIMEOUT_IN_SECONDS, ROWS_PER_RESULTSET, CLIENT_MEMORY_LIMIT");
    }

    @Test
    public void aWordOutsideItsParametersVocabularyIsRefused() {
        assertEquals(PREFIX + "Invalid binary format string 'nope': Must be 'HEX' or 'BASE64'",
            refusal("ALTER SESSION SET BINARY_OUTPUT_FORMAT = 'nope'"));
        assertEquals(PREFIX + "Invalid binary format string 'NOPE': Must be 'HEX' or 'BASE64'",
            refusal("ALTER SESSION SET BINARY_OUTPUT_FORMAT = nope"));
        assertEquals(PREFIX + "Invalid binary format string 'nope': Must be 'HEX', 'BASE64', or 'UTF-8'",
            refusal("ALTER SESSION SET BINARY_INPUT_FORMAT = 'nope'"));
        assertInvalid("TIMESTAMP_TYPE_MAPPING = nope", "NOPE", "TIMESTAMP_TYPE_MAPPING");
        assertInvalid("TIMESTAMP_NTZ_OUTPUT_FORMAT = auto", "auto", "TIMESTAMP_NTZ_OUTPUT_FORMAT");
        assertInvalid("TIMESTAMP_OUTPUT_FORMAT = 'AUTO'", "AUTO", "TIMESTAMP_OUTPUT_FORMAT");
        assertInvalid("UNSUPPORTED_DDL_ACTION = 'nope'", "nope", "UNSUPPORTED_DDL_ACTION");
        assertEquals("Invalid value for log level. Allowed values are TRACE,DEBUG,INFO,WARN,ERROR,FATAL,OFF.",
            refusal("ALTER SESSION SET LOG_LEVEL = 'nope'"));
        assertEquals("Invalid value for trace level. Allowed values are ALWAYS,ON_EVENT,PROPAGATE,OFF.",
            refusal("ALTER SESSION SET TRACE_LEVEL = 'nope'"));
        assertEquals(PREFIX + "invalid value [nope] for parameter 'DEFAULT_NULL_ORDERING'. Accepted values: 'LAST',"
            + " 'FIRST'.'", refusal("ALTER SESSION SET DEFAULT_NULL_ORDERING = 'nope'"));
        assertEquals(PREFIX + "invalid value [EN] for parameter 'Snowflake only supports [ja, en, fr-FR]'",
            refusal("ALTER SESSION SET LANGUAGE = en"));
        assertEquals(PREFIX + "invalid value [Each checkpoint alias must start with 'CP$', but found: NOPE] for"
            + " parameter 'VERSIONED_STAGE_ACTIVE_CHECKPOINT_ALIASES'",
            refusal("ALTER SESSION SET VERSIONED_STAGE_ACTIVE_CHECKPOINT_ALIASES = 'CP$a, nope'"));
        assertEquals("Unsupported feature 'transaction isolation level SERIALIZABLE'.",
            refusal("ALTER SESSION SET TRANSACTION_DEFAULT_ISOLATION_LEVEL = 'SERIALIZABLE'"));
        // A QUERY_TAG holds two thousand characters.
        engine.execute("ALTER SESSION SET QUERY_TAG = '" + "x".repeat(2000) + "'");
        assertInvalid("QUERY_TAG = '" + "x".repeat(2001) + "'", "x".repeat(2001), "QUERY_TAG");
        engine.execute("ALTER SESSION UNSET QUERY_TAG");
        for (final String taken : new String[] {"BINARY_INPUT_FORMAT = utf8", "LOG_LEVEL = 'info'",
            "DEFAULT_NULL_ORDERING = 'first'", "LANGUAGE = 'fr-FR'", "GEOGRAPHY_OUTPUT_FORMAT = 'EWKT'",
            "VERSIONED_STAGE_ACTIVE_CHECKPOINT_ALIASES = 'CP$a,cp$b'", "CLIENT_TIMESTAMP_TYPE_MAPPING = 'TIMESTAMP_TZ'",
            "TRANSACTION_DEFAULT_ISOLATION_LEVEL = 'read committed'"}) {
            engine.execute("ALTER SESSION SET " + taken);
        }
        assertEquals("UTF8|SESSION", held("BINARY_INPUT_FORMAT"));
        engine.execute("ALTER SESSION UNSET BINARY_INPUT_FORMAT, LOG_LEVEL, DEFAULT_NULL_ORDERING, LANGUAGE,"
            + " GEOGRAPHY_OUTPUT_FORMAT, VERSIONED_STAGE_ACTIVE_CHECKPOINT_ALIASES, CLIENT_TIMESTAMP_TYPE_MAPPING,"
            + " TRANSACTION_DEFAULT_ISOLATION_LEVEL");
    }

    /** A time zone is one the zone database names, spelled exactly; an offset and most abbreviations are none. */
    @Test
    public void aTimeZoneIsAZoneDatabaseNameSpelledExactly() {
        for (final String unknown : new String[] {"utc", "europe/warsaw", "+02:00", "GMT+2", "UTC+1", "PST", "Z",
            "SystemV/PST8", " UTC", "", "Nowhere/Land"}) {
            assertInvalid("TIMEZONE = '" + unknown + "'", unknown, "TIMEZONE");
        }
        assertInvalid("TIMEZONE = Nowhere", "NOWHERE", "TIMEZONE");
        assertInvalid("TIMEZONE = Europe.Warsaw", "Europe.Warsaw", "TIMEZONE");
        for (final String known : new String[] {"'EST'", "'MST'", "'HST'", "'ROC'", "'Factory'", "'Etc/GMT+2'", "'GMT'",
            "'US/Pacific'", "'Asia/Calcutta'", "'Zulu'", "'Europe/Kyiv'", "\"America/Chicago\"", "utc", "$$UTC$$"}) {
            engine.execute("ALTER SESSION SET TIMEZONE = " + known);
        }
        assertEquals("UTC|SESSION", held("TIMEZONE"));
        engine.execute("ALTER SESSION SET TIMEZONE = 'EST'");
        assertEquals("2024-01-01 07:00:00.000 -0500", engine.executeQuery(
            "SELECT TO_VARCHAR('2024-01-01 12:00:00 +00:00'::TIMESTAMP_TZ::TIMESTAMP_LTZ,"
                + " 'YYYY-MM-DD HH24:MI:SS.FF3 TZHTZM')").getRows().get(0).getValue(0));
        engine.execute("ALTER SESSION SET TIMEZONE = 'ROC'");
        assertEquals("2024-01-01 20:00:00.000 +0800", engine.executeQuery(
            "SELECT TO_VARCHAR('2024-01-01 12:00:00 +00:00'::TIMESTAMP_TZ::TIMESTAMP_LTZ,"
                + " 'YYYY-MM-DD HH24:MI:SS.FF3 TZHTZM')").getRows().get(0).getValue(0));
        engine.execute("ALTER SESSION UNSET TIMEZONE");
    }

    /** A session variable stands for the value it holds, and is weighed as that value's kind. */
    @Test
    public void aSessionVariableStandsForItsValue() {
        engine.execute("SET spv_tag = 'fromvar'");
        engine.execute("SET spv_num = 5");
        engine.execute("SET spv_flag = FALSE");
        engine.execute("ALTER SESSION SET QUERY_TAG = $spv_tag");
        assertEquals("fromvar|SESSION", held("QUERY_TAG"));
        engine.execute("ALTER SESSION SET LOCK_TIMEOUT = $spv_num");
        assertEquals("5|SESSION", held("LOCK_TIMEOUT"));
        engine.execute("ALTER SESSION SET ABORT_DETACHED_QUERY = $spv_flag");
        assertEquals("false|SESSION", held("ABORT_DETACHED_QUERY"));
        assertInvalid("QUERY_TAG = $spv_num", "5", "QUERY_TAG");
        assertInvalid("LOCK_TIMEOUT = $spv_tag", "fromvar", "LOCK_TIMEOUT");
        assertInvalid("ABORT_DETACHED_QUERY = $spv_tag", "fromvar", "ABORT_DETACHED_QUERY");
        assertInvalid("QUERY_TAG = $spv_flag", "FALSE", "QUERY_TAG");
        assertEquals("SQL compilation error: error line -1 at position -1\nSession variable '$SPV_NONE' does not"
            + " exist", refusal("ALTER SESSION SET QUERY_TAG = $spv_none"));
        engine.execute("ALTER SESSION UNSET QUERY_TAG, LOCK_TIMEOUT, ABORT_DETACHED_QUERY");
        engine.execute("UNSET spv_tag");
        engine.execute("UNSET spv_num");
        engine.execute("UNSET spv_flag");
    }
}
