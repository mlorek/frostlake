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

package dev.frostlake.executor;

import dev.frostlake.executor.expressions.CollationSpec;

/**
 * How Snowflake lays out a compilation-error message.
 *
 * <p>The detail always begins on a SECOND line — {@code "SQL compilation error:"}, a newline, then the
 * detail. When the error carries a source position, that clause and only that clause stays on the first
 * line, and the newline comes after it. Measured on a real account, with the newlines shown
 * escaped exactly as the driver returns them:
 *
 * <pre>
 * SELECT MAX(OBJECT_CONSTRUCT('k',1))  -&gt; SQL compilation error:\nFunction MAX does not support OBJECT …
 * SELECT 1::NOSUCHTYPE                 -&gt; SQL compilation error:\nUnsupported data type 'NOSUCHTYPE'.
 * SELECT 1::FILE                       -&gt; SQL compilation error:\ninvalid type [CAST(1 AS FILE)] for …
 * DROP VIEW nosuchview                 -&gt; SQL compilation error:\nView '…' does not exist or not …
 * SELCT 1                              -&gt; SQL compilation error:\nsyntax error line 1 at position 0 …
 * SELECT nosuchcolumn FROM …           -&gt; SQL compilation error: error line 1 at position 7\ninvalid …
 * SELECT a, b FROM t GROUP BY a        -&gt; SQL compilation error: error line 1 at position 10\n'T.B' in …
 * </pre>
 *
 * <p>Note where the two forms differ: a SYNTAX error spells its position inside the detail, on the second
 * line, while a positioned SEMANTIC error spells it on the first. Runtime (non-compilation) failures such
 * as {@code Date 'a' is not recognized} carry no prefix at all and do not belong here.
 */
public final class SqlCompilationError {

    /** The prefix, without the separator that follows it. */
    public static final String PREFIX = "SQL compilation error:";

    private SqlCompilationError() {
    }

    /** A compilation error whose detail starts on the second line. */
    public static String of(final String detail) {
        return PREFIX + "\n" + detail;
    }

    /**
     * A compilation error whose detail follows the prefix ON THE SAME LINE, after a space, with no
     * newline anywhere — the layout the date-part slot family keeps: live spells "SQL compilation
     * error: Date/time component [TO_CHAR(1) ]for function TRUNC needs to be an identifier or a
     * string literal." as one line, where the argument-type and conversion sentences break after
     * the prefix.
     *
     * @param detail the sentence
     * @return the message as the driver hands it to a client
     */
    public static String inline(final String detail) {
        return PREFIX + " " + detail;
    }

    /**
     * The ONE sentence family that lays itself out differently: the detail follows the prefix on its
     * OWN line, after a space, and a newline closes the message instead of separating it.
     *
     * <pre>
     * ALTER TABLE t ALTER COLUMN f SET DATA TYPE NUMBER(10,2)
     *     SQL compilation error: cannot change column F from type FLOAT to NUMBER(10,2)\n
     * </pre>
     *
     * <p>This is a property of the SENTENCE, not of the statement and not of un-positioned errors in
     * general — which is worth stating plainly, because it looks like both. Twenty-one other
     * un-positioned refusals were measured in one run and every one of them uses {@link #of}'s layout,
     * including three raised by ALTER TABLE itself ({@code column 'I' already exists},
     * {@code column 'X' does not exist}, {@code Object 'T.C' already exists.}). Only
     * "cannot change column …" reads this way, in all eight retype directions.
     *
     * @param detail the sentence, which supplies its own full stop where it has one
     * @return the message as the driver hands it to a client
     */
    public static String trailing(final String detail) {
        return PREFIX + " " + detail + "\n";
    }

    /**
     * Whether a message is one of these — a COMPILE-time refusal rather than a failure that only
     * showed up while rows were being produced. The distinction matters wherever Frostlake reaches an
     * answer by executing something Snowflake merely plans.
     *
     * <p>One compile-time refusal is worded WITHOUT the prefix: an invalid collation specification
     * ({@code Invalid value 'xx-yy' for COLLATION. Reason: Unknown option: yy}), which live raises
     * while the statement compiles all the same — over an empty table, and in a CREATE TABLE. It
     * counts here, so every site that lets a compile-time refusal through lets it through too.
     *
     * @param message the exception message, which may be null
     * @return true when the message carries this prefix, or is the collation refusal
     */
    public static boolean isCompilationError(final String message) {
        return message != null && (message.startsWith(PREFIX) || CollationSpec.isRefusal(message));
    }

    /** A compilation error that reports a source position, which stays on the FIRST line. */
    /**
     * The positioned form spelled with a CAPITAL E — {@code SQL compilation error: Error line 1 at
     * position 7}. Live uses it for the literal READER's refusals and the lower-case {@link #at} for
     * the identifier and syntax families; the difference is measured, not a typo, and it is one of the
     * few places the word is capitalised at all.
     */
    public static String atCapitalised(final int line, final int position, final String detail) {
        final int[] shown = LeadingCommentOffset.rebase(line, position);
        return PREFIX + " Error line " + shown[0] + " at position " + shown[1] + "\n" + detail;
    }

    /**
     * The POSITIONED layout with an EMPTY position — the separating space survives where the "error
     * line N at position M" would go, so the message opens "SQL compilation error: " with a trailing
     * space before its newline.
     *
     * <p>That trailing space is live's, not a stray character: measured in one run beside the
     * window-inside-an-aggregate refusal, which uses {@link #of} and has no trailing space. The two
     * shapes sit side by side in the same family, so the difference is real.
     */
    public static String withoutPosition(final String detail) {
        return PREFIX + " \n" + detail;
    }

    public static String at(final int line, final int position, final String detail) {
        // Past any LEADING comment: a statement that opens with one reports from its first real token,
        // so the place computed against the raw text is shifted back here — see LeadingCommentOffset.
        final int[] shown = LeadingCommentOffset.rebase(line, position);
        return PREFIX + " error line " + shown[0] + " at position " + shown[1] + "\n" + detail;
    }


    /**
     * A property given twice in one statement. The trailing semicolon is a real account's, not a
     * typo, and the name is the property's INTERNAL spelling where the two differ (a sequence's
     * {@code START} reports as {@code SEQUENCE_START}).
     */
    public static String duplicateProperty(final String name) {
        return of("duplicate property '" + name + "';");
    }

    /**
     * A file-format parameter given twice. A different shape from {@link #duplicateProperty}, and
     * measured: the detail stays on the FIRST line, after a space, with no closing punctuation.
     */
    public static String conflictingFileFormatParameter(final String name) {
        return PREFIX + " conflicting values file format parameter '" + name + "'";
    }

    /** A copy option given twice — first line like the file-format twin, then a trailing newline. */
    public static String conflictingCopyOption(final String name) {
        return PREFIX + " conflicting values for copy option '" + name + "'\n";
    }

    /**
     * A column carrying more than one DEFAULT / AUTOINCREMENT / IDENTITY. Its shape is a third one
     * again — a space AFTER the colon, then the newline, and the sentence ends in a period.
     */
    public static String multipleDefaultOrAutoincrement(final String column) {
        return PREFIX + " \nMultiple DEFAULT or AUTOINCREMENT expressions declared for column "
            + column + ".";
    }

    /** A second PRIMARY KEY on one table, inline or table-level. */
    public static String primaryKeyAlreadyExists(final String table) {
        return of("primary key already exists for table '" + table + "'");
    }

    /** A second constraint over the same columns — live names neither constraint. */
    public static String duplicateConstraintSignature(final String table) {
        return of("constraint with the same signature already exists on table '" + table + "'");
    }

    /**
     * The invalid-VALUE refusal for a parameter, brackets around the rendered value. What sits in
     * the brackets follows the surface that raised it: format and copy options echo a quoted
     * string WITH its quotes, session parameters strip them, integers and barewords stay bare.
     */
    public static String invalidValueForParameter(final String rendered, final String parameter) {
        return of("invalid value [" + rendered + "] for parameter '" + parameter + "'");
    }

    /** The property-flavored twin, single-quoted — warehouse numerics and the sequence increment. */
    public static String invalidValueForProperty(final String value, final String property) {
        return of("invalid value '" + value + "' for property '" + property + "'");
    }

    /**
     * The THIRD phrasing an enumerated warehouse property uses for a value outside its vocabulary.
     * The three are not interchangeable, and each belongs to one property (live-verified):
     *
     * <pre>
     *   WAREHOUSE_SIZE   invalid type of property 'HUGE' for 'WAREHOUSE_SIZE'
     *   SCALING_POLICY   invalid value 'NOSUCH' for property 'SCALING_POLICY'      {@link #invalidValueForProperty}
     *   WAREHOUSE_TYPE   invalid property 'NOSUCH' for 'WAREHOUSE_TYPE'            this one
     * </pre>
     *
     * <p>Same mistake, three sentences — so the wording is per property and cannot be shared.
     */
    public static String invalidPropertyFor(final String value, final String property) {
        return of("invalid property '" + value + "' for '" + property + "'");
    }

    /**
     * The message for a name that resolves to nothing. Snowflake never says WHY — a name you may not see
     * and a name that is not there are reported identically, so the wording is always "does not exist or
     * not authorized". Measured across the whole family: {@code DROP VIEW nosuch} answers
     * {@code View 'DB.SCHEMA.NOSUCH' does not exist or not authorized.}, and Table / Schema / Database /
     * Stage / Stream / Task / Sequence / Function / Procedure all answer in that same shape.
     *
     * <p>How much of the name is spelled depends on the statement, and callers pass whichever they hold:
     * a DDL statement (DROP, ALTER, TRUNCATE) reports the FULLY QUALIFIED name, while a query or DML
     * statement reports the bare one — {@code INSERT INTO nosuch} and {@code DESCRIBE TABLE nosuch} both
     * answer {@code Table 'NOSUCH' …}. A table missing from a FROM clause is the one that also changes
     * kind: it is reported as {@code Object 'NOSUCH' …}, not {@code Table}.
     */
    /**
     * The sentence live gives when a SHOW cannot reach its scope — for the kinds that do not name what
     * was missing. Measured across all three scope forms ({@code IN SCHEMA db.missing},
     * {@code IN SCHEMA missing}, {@code IN DATABASE missing}): TABLES, VIEWS, STAGES, FILE FORMATS and
     * DYNAMIC TABLES answer exactly this, while PIPES, STREAMS, TASKS, SEQUENCES, TAGS, the two policy
     * kinds, CORTEX SEARCH SERVICES, PROCEDURES, FUNCTIONS and COLUMNS name the missing schema or
     * database instead. Which one a kind uses is a property of the kind, not of the scope form. Every
     * USE that resolves nothing — a database, a schema, a role, a quoted name in the wrong case — answers
     * it too.
     */
    public static String objectDoesNotExist() {
        return of("Object does not exist, or operation cannot be performed.");
    }

    /**
     * The sentence live gives for a kind-less {@code USE} of a name with more than two parts, verbatim —
     * the account's own template, with its line and position placeholders left unfilled.
     */
    public static String useNameForm() {
        return "SQL compilation error: error line USE <identifier> is of the form USE <db.schema> or USE <db>"
            + " at position {1}\ninvalid identifier '{2}'";
    }

    public static String doesNotExist(final String kind, final String name) {
        // The name is spelled the way every refusal spells one: quoted only where it has to be, part
        // by part. Live reads Object '"kw"' for a lower-case relation and TEST_DB.TEST_SCHEMA."kw"
        // when the whole path is named, where an ordinary upper-case name stays bare.
        return of(kind + " '" + SqlIdentifiers.spellAlreadyCanonicalPath(name)
            + "' does not exist or not authorized.");
    }

    /**
     * A name that resolves to no COLUMN. Snowflake does not phrase this like a missing object at all —
     * measured, a column nobody can resolve is an {@code invalid identifier}, in the select
     * list, a WHERE, an ORDER BY, a GROUP BY, an INSERT column list, an UPDATE SET, a view body, an
     * aggregate argument, and every ALTER COLUMN form:
     *
     * <pre>
     * SELECT nosuchcol FROM t              -&gt; …error line 1 at position 7\ninvalid identifier 'NOSUCHCOL'
     * SELECT t.nosuchcol FROM t            -&gt; …position 7\ninvalid identifier 'T.NOSUCHCOL'
     * ALTER TABLE t ADD PRIMARY KEY (nsc)  -&gt; …position 31\ninvalid identifier 'NSC'
     * </pre>
     *
     * <p>Live always carries the source position on these. Frostlake resolves columns from an expression
     * AST that does not record one ({@code ColumnReferenceExpression} keeps only the names), so the
     * positioned overload is used where a parse context is at hand and this one elsewhere — the wording
     * matches, the position clause is absent. Threading positions through the AST is a separate change.
     */
    public static String invalidIdentifier(final String name) {
        return of("invalid identifier '" + name + "'");
    }

    /** An invalid identifier whose source position IS known. */
    public static String invalidIdentifier(final int line, final int position, final String name) {
        return at(line, position, "invalid identifier '" + name + "'");
    }

    /**
     * The one column phrasing that is NOT "invalid identifier": {@code ALTER TABLE … DROP COLUMN nosuch}
     * answers {@code column 'NOSUCH' does not exist} — lower-case kind, no position, and no "or not
     * authorized" tail. ({@code RENAME COLUMN} is different again and reports an {@code Object}; it uses
     * {@link #doesNotExist}.)
     */
    public static String columnDoesNotExist(final String name) {
        return of("column '" + name + "' does not exist");
    }

    /**
     * A DROP that names the wrong kind. The five relation kinds share one name space, so the object IS
     * found - just not as the statement spells it - and the sentence names both kinds on one line, as
     * {@code Object found is of type 'VIEW', not specified type 'TABLE'.} IF EXISTS does not forgive it
     * (live-verified).
     *
     * @param found     the kind holding the name
     * @param specified the kind the statement named
     * @return the message as the driver hands it to a client
     */
    public static String objectOfOtherType(final String found, final String specified) {
        return inline("Object found is of type '" + found + "', not specified type '" + specified + "'.");
    }

    /** Whether a message is the wrong-kind refusal above, which IF EXISTS must not swallow. */
    public static boolean isWrongObjectType(final String message) {
        return message != null && message.contains("not specified type '");
    }

}
