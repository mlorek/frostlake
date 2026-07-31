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

package dev.frostlake.functions.file;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A FILE is not text, not a number and not something to accumulate, and Snowflake refuses one wherever
 * any of those is expected. Frostlake used to accept every case below and answer from the FILE's
 * DESCRIPTOR — {@code LENGTH(f)} counted the characters of
 * {@code {"CONTENT_TYPE":…,"ETAG":…}}, {@code UPPER(f)} upper-cased its KEYS, {@code SUBSTR(f, 1, 3)}
 * returned {@code "{"C"} and {@code SUM(f)} produced a number — output plausible enough to be carried
 * onward by a caller who never saw the descriptor.
 *
 * <p>Every expectation here was measured against a live Snowflake account on over a
 * populated {@code FILE} column, alongside an OBJECT, a VARIANT, a VARCHAR and a NUMBER column as
 * controls. FILE turns out to use the SAME four message shapes the semi-structured families use, with
 * its own type name substituted — the argument-type list for the text, numeric and summing families,
 * the incompatible-types pair for MEDIAN and the percentiles, the internal {@code '*'} for the moment
 * aggregates, and the "does not support … for keys" sentence for an OBJECT_CONSTRUCT key.
 *
 * <p>The ACCEPTED forms are pinned at least as hard as the rejections, because the text and numeric
 * functions are machinery every query shares: a FILE still compares, de-duplicates, joins, counts,
 * hashes and collects into an ARRAY, and each {@code FL_*} accessor still reads it. Those tests are
 * what would fail loudly if this rule were ever widened past what the account actually does.
 */
public class FileArgumentRejectionTest extends FileFunctionTestSupport {

    /** The argument-type list: SQLSTATE 42P13, vendor code 1044 — the shape most of the surface uses. */
    private static final String LENGTH_FILE =
        "Invalid argument types for function 'LENGTH': (FILE)";

    /** MEDIAN and the percentiles: SQLSTATE 42846, vendor code 1010 — a different sentence entirely. */
    private static final String ORDERED_FILE =
        "incompatible types: [FILE] and [NUMBER(9,0)]";

    /** The moment aggregates reach their sum of squares first, so live names the multiplication. */
    private static final String MOMENT_FILE =
        "Invalid argument types for function '*': (FILE, FILE)";

    @BeforeEach
    public void createFileTable() {
        engine.execute("CREATE TABLE fa (id INTEGER, f FILE, o OBJECT, v VARIANT, s VARCHAR, n NUMBER)");
        engine.execute("INSERT INTO fa SELECT 1, OBJECT_CONSTRUCT("
            + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'hello.txt', 'SIZE', 12,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e1'),"
            + " OBJECT_CONSTRUCT('a', 1), TO_VARIANT(1), 'x', 10");
        engine.execute("INSERT INTO fa SELECT 2, OBJECT_CONSTRUCT("
            + "'STAGE', '@D.S.ST', 'RELATIVE_PATH', 'two.txt', 'SIZE', 10,"
            + " 'LAST_MODIFIED', '" + LAST_MODIFIED + "', 'CONTENT_TYPE', 'text/plain', 'ETAG', 'e2'),"
            + " OBJECT_CONSTRUCT('a', 2), TO_VARIANT(2), 'y', 20");
    }

    /** The exact message live Snowflake produces, asserted in full — not merely that it threw. */
    private void assertFails(final String sql, final String expectedMessage) {
        final RuntimeException error = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery(sql);
            }
        });
        assertTrue(error.getMessage().contains(expectedMessage),
            "expected [" + expectedMessage + "] for [" + sql + "] but got: " + error.getMessage());
    }

    /** A projection of one FILE-taking call, rejected with the argument-type list it names. */
    private void assertArgumentTypes(final String projection, final String reported,
                                     final String argumentTypes) {
        assertFails("SELECT " + projection + " FROM fa",
            "Invalid argument types for function '" + reported + "': (" + argumentTypes + ")");
    }

    private void assertAccepted(final String sql, final int expectedRows) {
        assertEquals(expectedRows, engine.executeQuery(sql).getRows().size(),
            "expected " + expectedRows + " row(s) from [" + sql + "]");
    }

    private void assertFirstValue(final String sql, final String expected) {
        assertEquals(expected, String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0)),
            "unexpected value from [" + sql + "]");
    }

    // ── The text family ──────────────────────────────────────────────────────

    /** The three the defect report named, each naming itself over "(FILE)". */
    @Test
    public void theTextFunctionsInTheDefectReportAreRejected() {
        assertFails("SELECT LENGTH(f) FROM fa", LENGTH_FILE);
        assertArgumentTypes("UPPER(f)", "UPPER", "FILE");
        assertArgumentTypes("SUBSTR(f, 1, 3)", "SUBSTR", "FILE, NUMBER(1,0), NUMBER(1,0)");
    }

    /**
     * A spread across the text family rather than a list of the ones that were easy to guess: casing,
     * measuring, trimming, splitting, searching, padding, regex, phonetics and parsing all refuse.
     */
    @Test
    public void theWholeTextFamilyRefusesAFile() {
        assertArgumentTypes("LEN(f)", "LEN", "FILE");
        assertArgumentTypes("LOWER(f)", "LOWER", "FILE");
        assertArgumentTypes("INITCAP(f)", "INITCAP", "FILE");
        assertArgumentTypes("REVERSE(f)", "REVERSE", "FILE");
        assertArgumentTypes("TRIM(f)", "TRIM", "FILE");
        assertArgumentTypes("LTRIM(f)", "LTRIM", "FILE");
        assertArgumentTypes("RTRIM(f)", "RTRIM", "FILE");
        assertArgumentTypes("ASCII(f)", "ASCII", "FILE");
        assertArgumentTypes("UNICODE(f)", "UNICODE", "FILE");
        assertArgumentTypes("OCTET_LENGTH(f)", "OCTET_LENGTH", "FILE");
        assertArgumentTypes("SOUNDEX(f)", "SOUNDEX", "FILE");
        assertArgumentTypes("PARSE_URL(f)", "PARSE_URL", "FILE");
    }

    /** The multi-argument text functions, which list EVERY argument so the offender is visible. */
    @Test
    public void theMultiArgumentTextFunctionsListEveryArgument() {
        assertArgumentTypes("SUBSTRING(f, 1, 3)", "SUBSTRING", "FILE, NUMBER(1,0), NUMBER(1,0)");
        assertArgumentTypes("REPLACE(f, 'a', 'b')", "REPLACE", "FILE, VARCHAR(1), VARCHAR(1)");
        assertArgumentTypes("SPLIT_PART(f, ',', 1)", "SPLIT_PART", "FILE, VARCHAR(1), NUMBER(1,0)");
        assertArgumentTypes("SPLIT(f, ',')", "SPLIT", "FILE, VARCHAR(1)");
        assertArgumentTypes("TRANSLATE(f, 'a', 'b')", "TRANSLATE", "FILE, VARCHAR(1), VARCHAR(1)");
        assertArgumentTypes("LPAD(f, 20, '.')", "LPAD", "FILE, NUMBER(2,0), VARCHAR(1)");
        assertArgumentTypes("RPAD(f, 20, '.')", "RPAD", "FILE, NUMBER(2,0), VARCHAR(1)");
        assertArgumentTypes("CONTAINS(f, 'a')", "CONTAINS", "FILE, VARCHAR(1)");
        assertArgumentTypes("STARTSWITH(f, 'a')", "STARTSWITH", "FILE, VARCHAR(1)");
        assertArgumentTypes("ENDSWITH(f, 'a')", "ENDSWITH", "FILE, VARCHAR(1)");
        assertArgumentTypes("EDITDISTANCE(f, 'a')", "EDITDISTANCE", "FILE, VARCHAR(1)");
        assertArgumentTypes("JAROWINKLER_SIMILARITY(f, 'a')", "JAROWINKLER_SIMILARITY",
            "FILE, VARCHAR(1)");
        assertArgumentTypes("STRTOK(f, ',')", "STRTOK", "FILE, VARCHAR(1)");
        assertArgumentTypes("STRTOK_TO_ARRAY(f, ',')", "STRTOK_TO_ARRAY", "FILE, VARCHAR(1)");
        assertArgumentTypes("PARSE_IP(f, 'inet')", "PARSE_IP", "FILE, VARCHAR(4)");
    }

    /** The regex functions, which read their subject as text like the rest. */
    @Test
    public void theRegexFunctionsRefuseAFileSubject() {
        assertArgumentTypes("REGEXP_COUNT(f, 'a')", "REGEXP_COUNT", "FILE, VARCHAR(1)");
        assertArgumentTypes("REGEXP_LIKE(f, '.*')", "REGEXP_LIKE", "FILE, VARCHAR(2)");
        assertArgumentTypes("REGEXP_SUBSTR(f, 'a')", "REGEXP_SUBSTR", "FILE, VARCHAR(1)");
        assertArgumentTypes("REGEXP_INSTR(f, 'a')", "REGEXP_INSTR", "FILE, VARCHAR(1)");
        assertArgumentTypes("REGEXP_REPLACE(f, 'a', 'b')", "REGEXP_REPLACE",
            "FILE, VARCHAR(1), VARCHAR(1)");
    }

    /**
     * EVERY position, not merely the text-shaped ones — the same result the OBJECT rule measured, and
     * re-measured here because it is the claim most easily over-generalised. Live rejects a FILE in the
     * NUMBER argument of {@code SPLIT_PART} and {@code LPAD} just as it does in their string ones.
     */
    @Test
    public void aFileIsRefusedInEveryTextPositionIncludingNumericOnes() {
        assertArgumentTypes("SPLIT_PART(s, ',', f)", "SPLIT_PART",
            "VARCHAR(16777216), VARCHAR(1), FILE");
        assertArgumentTypes("LPAD(s, f, '.')", "LPAD", "VARCHAR(16777216), FILE, VARCHAR(1)");
        assertArgumentTypes("SUBSTR(s, f, 2)", "SUBSTR", "VARCHAR(16777216), FILE, NUMBER(1,0)");
        assertArgumentTypes("REPLACE(s, f, 'x')", "REPLACE", "VARCHAR(16777216), FILE, VARCHAR(1)");
        assertArgumentTypes("TRANSLATE(s, f, 'b')", "TRANSLATE", "VARCHAR(16777216), FILE, VARCHAR(1)");
        assertArgumentTypes("CHARINDEX('a', f)", "CHARINDEX", "VARCHAR(1), FILE");
        assertArgumentTypes("CHARINDEX(f, s)", "CHARINDEX", "FILE, VARCHAR(16777216)");
        assertArgumentTypes("POSITION('a', f)", "POSITION", "VARCHAR(1), FILE");
    }

    // ── The numeric family ───────────────────────────────────────────────────

    /** A FILE coerces to a NUMBER nowhere, so the whole numeric surface refuses it. */
    @Test
    public void theNumericFamilyRefusesAFile() {
        assertArgumentTypes("ABS(f)", "ABS", "FILE");
        assertArgumentTypes("FLOOR(f)", "FLOOR", "FILE");
        assertArgumentTypes("CEIL(f)", "CEIL", "FILE");
        assertArgumentTypes("TRUNC(f)", "TRUNC", "FILE");
        assertArgumentTypes("SIGN(f)", "SIGN", "FILE");
        assertArgumentTypes("SQRT(f)", "SQRT", "FILE");
        assertArgumentTypes("CBRT(f)", "CBRT", "FILE");
        assertArgumentTypes("SQUARE(f)", "SQUARE", "FILE");
        assertArgumentTypes("EXP(f)", "EXP", "FILE");
        assertArgumentTypes("LN(f)", "LN", "FILE");
        assertArgumentTypes("FACTORIAL(f)", "FACTORIAL", "FILE");
        assertArgumentTypes("ZEROIFNULL(f)", "ZEROIFNULL", "FILE");
        assertArgumentTypes("BITNOT(f)", "BITNOT", "FILE");
    }

    /** The trigonometric and angular functions are members of the same family. */
    @Test
    public void theTrigonometricFunctionsRefuseAFile() {
        assertArgumentTypes("SIN(f)", "SIN", "FILE");
        assertArgumentTypes("COS(f)", "COS", "FILE");
        assertArgumentTypes("TAN(f)", "TAN", "FILE");
        assertArgumentTypes("ASIN(f)", "ASIN", "FILE");
        assertArgumentTypes("ACOS(f)", "ACOS", "FILE");
        assertArgumentTypes("ATAN(f)", "ATAN", "FILE");
        assertArgumentTypes("SINH(f)", "SINH", "FILE");
        assertArgumentTypes("COSH(f)", "COSH", "FILE");
        assertArgumentTypes("TANH(f)", "TANH", "FILE");
        assertArgumentTypes("ASINH(f)", "ASINH", "FILE");
        assertArgumentTypes("ACOSH(f)", "ACOSH", "FILE");
        assertArgumentTypes("ATANH(f)", "ATANH", "FILE");
        assertArgumentTypes("DEGREES(f)", "DEGREES", "FILE");
        assertArgumentTypes("RADIANS(f)", "RADIANS", "FILE");
    }

    /** Both positions of the two-argument numeric functions, measured either way round. */
    @Test
    public void aFileIsRefusedInEveryNumericPosition() {
        assertArgumentTypes("ROUND(f, 2)", "ROUND", "FILE, NUMBER(1,0)");
        assertArgumentTypes("ROUND(n, f)", "ROUND", "NUMBER(38,0), FILE");
        assertArgumentTypes("POWER(f, 2)", "POWER", "FILE, NUMBER(1,0)");
        assertArgumentTypes("POWER(2, f)", "POWER", "NUMBER(1,0), FILE");
        assertArgumentTypes("MOD(f, 2)", "MOD", "FILE, NUMBER(1,0)");
        assertArgumentTypes("LOG(f, 2)", "LOG", "FILE, NUMBER(1,0)");
        assertArgumentTypes("ATAN2(f, 1)", "ATAN2", "FILE, NUMBER(1,0)");
        assertArgumentTypes("DIV0(f, 2)", "DIV0", "FILE, NUMBER(1,0)");
        assertArgumentTypes("BITAND(f, 1)", "BITAND", "FILE, NUMBER(1,0)");
        assertArgumentTypes("BITOR(f, 1)", "BITOR", "FILE, NUMBER(1,0)");
        assertArgumentTypes("BITXOR(f, 1)", "BITXOR", "FILE, NUMBER(1,0)");
        assertArgumentTypes("BITSHIFTLEFT(f, 1)", "BITSHIFTLEFT", "FILE, NUMBER(1,0)");
        assertArgumentTypes("BITSHIFTRIGHT(f, 1)", "BITSHIFTRIGHT", "FILE, NUMBER(1,0)");
        assertArgumentTypes("GETBIT(f, 1)", "GETBIT", "FILE, NUMBER(1,0)");
        assertArgumentTypes("HAVERSINE(f, 1, 2, 3)", "HAVERSINE",
            "FILE, NUMBER(1,0), NUMBER(1,0), NUMBER(1,0)");
        assertArgumentTypes("WIDTH_BUCKET(1, f, 10, 3)", "WIDTH_BUCKET",
            "NUMBER(1,0), FILE, NUMBER(2,0), NUMBER(1,0)");
    }

    // ── The aggregates ───────────────────────────────────────────────────────

    /**
     * The summing family names itself in the argument-type list. The family does NOT split by whether
     * an aggregate is "numeric" — six aggregates that look just as numeric accept a FILE happily, which
     * is pinned separately below.
     *
     * <p>{@code AVG} follows live's split exactly: the BARE
     * {@code AVG(f)} reports 'SUM' — live desugars before it type-checks — while
     * {@code AVG(DISTINCT f)} and {@code AVG(f) OVER ()} report 'AVG'. The text functions live
     * reports under a desugared name ({@code LEFT} as 'SUBSTR', {@code REPEAT} as 'LENGTH',
     * {@code BIT_LENGTH} as 'OCTET_LENGTH', {@code SPACE} as 'LPAD') remain reported as written.
     */
    @Test
    public void theSummingAggregatesRefuseAFile() {
        assertArgumentTypes("SUM(f)", "SUM", "FILE");
        assertArgumentTypes("AVG(f)", "SUM", "FILE");
        assertArgumentTypes("BITAND_AGG(f)", "BITAND_AGG", "FILE");
        assertArgumentTypes("BITOR_AGG(f)", "BITOR_AGG", "FILE");
        assertArgumentTypes("BITXOR_AGG(f)", "BITXOR_AGG", "FILE");
    }

    /** LISTAGG joins its input as text and refuses a FILE in either position. */
    @Test
    public void listaggRefusesAFile() {
        assertArgumentTypes("LISTAGG(f)", "LISTAGG", "FILE");
        assertArgumentTypes("LISTAGG(f, ',')", "LISTAGG", "FILE, VARCHAR(1)");
    }

    /**
     * OBJECT_AGG refuses a FILE in BOTH halves — the one place FILE and a plain OBJECT genuinely part
     * company, since the VALUE half nests an OBJECT quite happily.
     */
    @Test
    public void objectAggRefusesAFileAsKeyAndAsValue() {
        assertArgumentTypes("OBJECT_AGG(f, 1)", "OBJECT_AGG", "FILE, NUMBER(1,0)");
        assertArgumentTypes("OBJECT_AGG('k', f)", "OBJECT_AGG", "VARCHAR(1), FILE");
        assertArgumentTypes("OBJECT_AGG(s, f)", "OBJECT_AGG", "VARCHAR(16777216), FILE");
        assertAccepted("SELECT OBJECT_AGG(s, o) FROM fa", 1);
    }

    /** MEDIAN and the percentiles use a different sentence and a different SQLSTATE. */
    @Test
    public void theOrderingByValueAggregatesUseTheIncompatibleTypesSentence() {
        assertFails("SELECT MEDIAN(f) FROM fa", ORDERED_FILE);
        assertFails("SELECT PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY f) FROM fa", ORDERED_FILE);
        assertFails("SELECT PERCENTILE_DISC(0.9) WITHIN GROUP (ORDER BY f) FROM fa", ORDERED_FILE);
    }

    /**
     * The moment aggregates never name themselves: live reaches the internal sum of SQUARES first and
     * reports that multiplication, listing the one offending argument twice.
     */
    @Test
    public void theMomentAggregatesReportTheirInternalMultiplication() {
        assertFails("SELECT STDDEV(f) FROM fa", MOMENT_FILE);
        assertFails("SELECT STDDEV_POP(f) FROM fa", MOMENT_FILE);
        assertFails("SELECT STDDEV_SAMP(f) FROM fa", MOMENT_FILE);
        assertFails("SELECT VARIANCE(f) FROM fa", MOMENT_FILE);
        assertFails("SELECT VAR_POP(f) FROM fa", MOMENT_FILE);
        assertFails("SELECT VAR_SAMP(f) FROM fa", MOMENT_FILE);
        assertFails("SELECT SKEW(f) FROM fa", MOMENT_FILE);
        assertFails("SELECT KURTOSIS(f) FROM fa", MOMENT_FILE);
    }

    /** The same rejections reach a WINDOWED call, which is a different code path in the engine. */
    @Test
    public void theWindowedFormsAreRejectedToo() {
        assertArgumentTypes("SUM(f) OVER ()", "SUM", "FILE");
        assertArgumentTypes("AVG(f) OVER ()", "AVG", "FILE");
        assertFails("SELECT MEDIAN(f) OVER () FROM fa", ORDERED_FILE);
        assertFails("SELECT STDDEV(f) OVER () FROM fa", MOMENT_FILE);
    }

    // ── Arithmetic ───────────────────────────────────────────────────────────

    /**
     * Arithmetic names the operator symbol and both operand types. Frostlake used to reach the
     * evaluator and fail with "Cannot add: {"CONTENT_TYPE":…} + 1", quoting the descriptor back at a
     * caller who never wrote it — and never failing at all over an empty input.
     */
    @Test
    public void arithmeticRefusesAFileOperand() {
        assertArgumentTypes("f + 1", "+", "FILE, NUMBER(1,0)");
        assertArgumentTypes("1 + f", "+", "NUMBER(1,0), FILE");
        assertArgumentTypes("f - 1", "-", "FILE, NUMBER(1,0)");
        assertArgumentTypes("f * 2", "*", "FILE, NUMBER(1,0)");
        assertArgumentTypes("f / 2", "/", "FILE, NUMBER(1,0)");
        assertArgumentTypes("f % 2", "%", "FILE, NUMBER(1,0)");
        assertArgumentTypes("f + f", "+", "FILE, FILE");
        assertArgumentTypes("f + n", "+", "FILE, NUMBER(38,0)");
    }

    /** Unary minus reports itself as 'NEGATE' rather than '-'. */
    @Test
    public void unaryMinusOverAFileIsRejected() {
        assertArgumentTypes("-f", "NEGATE", "FILE");
    }

    // ── The constructors ─────────────────────────────────────────────────────

    /**
     * A FILE NESTS as a constructed value and is refused as a KEY — the "for keys" tail, which carries
     * its own vendor code live (2270 rather than 2016). {@code ARRAY_CONSTRUCT} takes one anywhere.
     */
    @Test
    public void objectConstructRefusesAFileKeyButNestsAFileValue() {
        assertFails("SELECT OBJECT_CONSTRUCT(f, 1) FROM fa",
            "SQL compilation error:\nFunction OBJECT_CONSTRUCT does not support FILE argument type"
                + " for keys");
        assertAccepted("SELECT OBJECT_CONSTRUCT('a', f) FROM fa", 2);
        assertAccepted("SELECT ARRAY_CONSTRUCT(f) FROM fa", 2);
        assertAccepted("SELECT ARRAY_CONSTRUCT_COMPACT(f) FROM fa", 2);
    }

    // ── How the type is read ─────────────────────────────────────────────────

    /**
     * The DECLARED type is what is read, through every wrapper that preserves it — a derived table, a
     * CTE and a conditional over FILE branches all still refuse.
     */
    @Test
    public void theRuleReadsThroughDerivedTablesCtesAndConditionals() {
        assertFails("SELECT UPPER(x) FROM (SELECT f AS x FROM fa)",
            "Invalid argument types for function 'UPPER': (FILE)");
        assertFails("WITH c AS (SELECT f AS x FROM fa) SELECT SUM(x) FROM c",
            "Invalid argument types for function 'SUM': (FILE)");
        assertFails("SELECT LENGTH(IFF(TRUE, f, f)) FROM fa", LENGTH_FILE);
    }

    /** It is a COMPILE-time rule, so it fires with nothing to evaluate — exactly as live does. */
    @Test
    public void theRuleFiresOverAnEmptyInput() {
        assertFails("SELECT LENGTH(f) FROM fa WHERE 1 = 0", LENGTH_FILE);
        assertFails("SELECT SUM(f) FROM fa WHERE 1 = 0",
            "Invalid argument types for function 'SUM': (FILE)");
        assertFails("SELECT MEDIAN(f) FROM fa WHERE 1 = 0", ORDERED_FILE);
    }

    // ── The boundary: everything that must keep working ──────────────────────

    /**
     * The aggregates that take a FILE. This is the bound that makes "aggregates reject FILE" the wrong
     * rule: six of them accept one, and would break if the summing rule were widened by family.
     */
    @Test
    public void theCollectingAggregatesStillAcceptAFile() {
        assertFirstValue("SELECT COUNT(f) FROM fa", "2");
        assertFirstValue("SELECT COUNT(DISTINCT f) FROM fa", "2");
        assertFirstValue("SELECT APPROX_COUNT_DISTINCT(f) FROM fa", "2");
        assertAccepted("SELECT ANY_VALUE(f) FROM fa", 1);
        assertAccepted("SELECT ARRAY_AGG(f) FROM fa", 1);
        assertAccepted("SELECT ARRAY_UNIQUE_AGG(f) FROM fa", 1);
        assertAccepted("SELECT HASH_AGG(f) FROM fa", 1);
        assertAccepted("SELECT MAX_BY(f, n) FROM fa", 1);
        assertAccepted("SELECT MIN_BY(n, f) FROM fa", 1);
    }

    /** A FILE compares, de-duplicates, joins and takes part in a set operation. */
    @Test
    public void comparisonsDistinctJoinsAndSetOperationsStillWork() {
        assertAccepted("SELECT id FROM fa WHERE f = f", 2);
        assertAccepted("SELECT id FROM fa WHERE f IS NULL", 0);
        assertAccepted("SELECT id FROM fa WHERE f IS NOT NULL", 2);
        assertAccepted("SELECT DISTINCT f FROM fa", 2);
        assertAccepted("SELECT a.id FROM fa a JOIN fa b ON a.f = b.f", 2);
        assertAccepted("SELECT f FROM fa UNION ALL SELECT f FROM fa", 4);
    }

    /** Every accessor still reads a FILE, and its RESULT is an ordinary value the rules leave alone. */
    @Test
    public void everyAccessorStillReadsAFile() {
        assertAccepted("SELECT FL_GET_STAGE(f), FL_GET_RELATIVE_PATH(f), FL_GET_SIZE(f),"
            + " FL_GET_ETAG(f), FL_GET_CONTENT_TYPE(f), FL_GET_LAST_MODIFIED(f) FROM fa", 2);
        assertAccepted("SELECT FL_GET_FILE_TYPE(f), FL_GET_SCOPED_FILE_URL(f),"
            + " FL_GET_STAGE_FILE_URL(f) FROM fa", 2);
        assertAccepted("SELECT FL_IS_AUDIO(f), FL_IS_COMPRESSED(f), FL_IS_DOCUMENT(f),"
            + " FL_IS_IMAGE(f), FL_IS_VIDEO(f) FROM fa", 2);
        assertFirstValue("SELECT SUM(FL_GET_SIZE(f)) FROM fa", "22");
        assertAccepted("SELECT LENGTH(FL_GET_RELATIVE_PATH(f)) FROM fa", 2);
        assertAccepted("SELECT UPPER(FL_GET_ETAG(f)) FROM fa", 2);
    }

    /** The conditionals and containers pass a FILE through untouched. */
    @Test
    public void conditionalsStillAcceptAFile() {
        assertAccepted("SELECT IFF(TRUE, f, f) FROM fa", 2);
        assertAccepted("SELECT COALESCE(f, f) FROM fa", 2);
        assertAccepted("SELECT NVL(f, f) FROM fa", 2);
        assertAccepted("SELECT GREATEST(f, f) FROM fa", 2);
        assertAccepted("SELECT LEAST(f, f) FROM fa", 2);
        assertAccepted("SELECT CASE WHEN TRUE THEN f ELSE f END FROM fa", 2);
    }

    /**
     * The other column types are untouched by every rule above — the guard that says the machinery was
     * narrowed to FILE and not simply switched on. A VARIANT is never refused even where an OBJECT is.
     */
    @Test
    public void theOtherColumnTypesAreUnaffected() {
        assertAccepted("SELECT UPPER(s), LENGTH(s), SUBSTR(s, 1, 1) FROM fa", 2);
        assertAccepted("SELECT ABS(n), ROUND(n, 1), n + 1, -n FROM fa", 2);
        assertFirstValue("SELECT SUM(n) FROM fa", "30");
        assertAccepted("SELECT MEDIAN(n), STDDEV(n) FROM fa", 1);
        assertAccepted("SELECT UPPER(v), v + 1 FROM fa", 2);
        assertAccepted("SELECT SUM(v) FROM fa", 1);
        assertAccepted("SELECT PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY n) FROM fa", 1);
    }

    /** And an OBJECT column keeps ITS answers, which differ from FILE's in both directions. */
    @Test
    public void anObjectColumnKeepsItsOwnAnswers() {
        assertFails("SELECT UPPER(o) FROM fa", "Invalid argument types for function 'UPPER': (OBJECT)");
        assertFails("SELECT SUM(o) FROM fa", "Invalid argument types for function 'SUM': (OBJECT)");
        assertFails("SELECT MEDIAN(o) FROM fa", "incompatible types: [OBJECT] and [NUMBER(9,0)]");
        assertAccepted("SELECT ARRAY_AGG(o) FROM fa", 1);
        assertAccepted("SELECT OBJECT_CONSTRUCT('a', o) FROM fa", 2);
    }
}
