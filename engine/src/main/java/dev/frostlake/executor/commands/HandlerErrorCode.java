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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.CallDepth;
import dev.frostlake.executor.SqlAccessControlError;
import dev.frostlake.executor.StatementCountMismatch;
import dev.frostlake.metastore.HybridKeys;
import dev.frostlake.transaction.TransactionManager;

/**
 * The SQLCODE and SQLSTATE a block's handler reads for a refusal the engine raised, by the refusal's sentence
 * (live-verified): a syntax error — one in the text EXECUTE IMMEDIATE runs, a string LIMIT, a routine's body — is
 * 1003 / 42000 and a SQL function's or a policy's body that does not compile 90203 / 42601; an invalid identifier is
 * 904 / 42000, a missing object 2003 / 42S02 and a schema USE cannot enter 2043 / 02000; a name already taken is
 * 2002 / 42710, a column's 1430 / 42601 and an overload that would compete with another 949 / 42723; an unknown
 * function or procedure 2139 / 02000 and a wrong argument count 938 or 939 / 22023; a text that is no number
 * 100038 / 22018 and no date 100040 / 22007; a division by zero 100051 / 22012; a scalar subquery of several rows
 * 90150 / 22000; a column outside the grouping 1104 / 42601; a SELECT … INTO of several rows 92219 / 22000, an
 * ambiguous column 2028 / 42601 and an infinity a statement binds 2086 / 22000; a statement refused because the
 * session's transaction was aborted 624 / 57014 and the COMMIT of such a transaction 670 / 57014; a statement that
 * waited for another transaction's table lock until its LOCK_TIMEOUT passed 627 / 57014, and one that could not wait
 * 626 / 57014; a statement that would end another scope's transaction 90239 / 25000 and a block or a procedure that
 * left its own open 90232 / 25000; a body ended past the depth bodies run to 90235 / 42601; an expression a VALUES
 * clause does not take 2014 / 22000, an INSERT whose values do not match its columns 2020 / 21S01, a result asked of a
 * statement that failed 711 / 02000 and a text that is no JSON 100069 / 22P02; an OPEN of an open cursor 92215 / P0000
 * and a CLOSE of one that is not open 92201 / P0000; a RESULTSET read before anything was given to it 92212 / P0000;
 * the report of an exception a Java or a Scala procedure's handler raised, a statement it ran that failed included,
 * 100315 / P0000, and of one a Python handler raised 100357 / P0000. A refusal of no known family reads 100351 / P0000.
 * A hybrid table's primary key already held is 200001 / 22000 and a foreign key it breaks 200009 / 22000, and a
 * privilege refusal - an access-control error, {@code Insufficient privileges to operate on …} - 3001 / 42501
 * (documented).
 *
 * <p>A client's driver reads the same pairs, and more families besides (live-verified): a missing object
 * 2003 / 42S02 where the sentence calls it an object, a table or a view, 2003 / 02000 where it names any other kind
 * - a schema, a database, a stage, a sequence, a routine, a file format, a stream, a task, a pipe, a materialized
 * view, a policy, a tag - and 2001 / 02000 for a routine named by its signature; a missing column 1431 / 42601 and
 * a dropped table no UNDROP finds 90025 / 02000; a text that is no timestamp 100035 / 22007, no time 100108 /
 * 22007, no BOOLEAN 100037 / 22018, a number out of its column's range 100039 or 100046 / 22003, a text too long
 * for its column 100078 / 22000, a VARIANT no cast takes 100071 / 22000, no hex 100115 and no base64 100114 /
 * 22000, no XML 100100 / 22P02 and no regular expression 100048 / 2201B, a text no format parses 100096 as a
 * timestamp and 100097 as a date / 22007; a MERGE matching a row twice 100090 / 42P18; an unknown table function
 * 2142 / 42601; an expression no GROUP BY or ORDER BY takes 979 or 2024 / 42601, a duplicate column 2025 / 42S21,
 * an unsupported type 2040 / 42601, an unknown parameter 1006 and a value it does not take 1008 / 22023, a type a
 * call or a cast does not take 1065, 1007 / 22023 and 1044 / 42P13, a set operation's column counts 1789 / 42601,
 * a parameter out of range 1817 / 42601, time travel past what is kept 707 / 02000, a date part no function knows
 * 2151 / 22023, a predicate of no BOOLEAN 2034 / 22000, a ranking window with no ORDER BY 2061 / 42601, a table
 * built of no named column 2022 / 42601, a table's last column dropped 1432 / 42601 and a column's type changed
 * to one it cannot hold 2108 / 22000, an INSERT into no table 1759 / 42601, nested aggregates 2082 / 42601, a
 * USING column no side holds 2004 / 42601 and a statement no ID names 709 / 02000. A NULL in a NOT NULL column is
 * 22000 to the client where the handler reads 00000.
 */
final class HandlerErrorCode {

    private static final String UNINITIALIZED_RESULTSET = "Stored procedure execution error: uninitialized resultSet '";

    /** How the report of an exception a Java or a Scala handler raised starts. */
    private static final String USER_ERROR_REPORT = "User Error Report: ";

    /** How this engine's report of an exception a Python handler raised starts. */
    private static final String PYTHON_REPORT = "Error executing Python ";

    /** How the account's report of an exception a Python handler raised starts. */
    private static final String PYTHON_INTERPRETER_ERROR = "Python Interpreter Error:";

    private static final HandlerErrorCode OTHER = new HandlerErrorCode(100351, "P0000");

    /** How a privilege refusal's sentence starts after its access-control prefix. */
    private static final String INSUFFICIENT_PRIVILEGES = "Insufficient privileges to operate on ";

    /** How a missing object's sentence goes on after its name. */
    private static final String NOT_AUTHORIZED = "' does not exist or not authorized";

    private final int code;
    private final String state;
    private final String clientState;

    private HandlerErrorCode(final int code, final String state) {
        this(code, state, state);
    }

    private HandlerErrorCode(final int code, final String state, final String clientState) {
        this.code = code;
        this.state = state;
        this.clientState = clientState;
    }

    /**
     * The code and state of a refusal's sentence.
     *
     * @param message the refusal as the handler's SQLERRM reads it
     * @return the pair
     */
    static HandlerErrorCode of(final String message) {
        final HandlerErrorCode known = known(message);
        return known != null ? known : OTHER;
    }

    /**
     * The code and state of a refusal's sentence, or null for a sentence of no known family.
     *
     * @param message the refusal's sentence
     * @return the pair, or null
     */
    static HandlerErrorCode known(final String message) {
        if (message == null) {
            return null;
        }
        if (message.startsWith(USER_ERROR_REPORT)) {
            return new HandlerErrorCode(100315, "P0000");
        }
        if (message.startsWith(PYTHON_REPORT) || message.startsWith(PYTHON_INTERPRETER_ERROR)) {
            return new HandlerErrorCode(100357, "P0000");
        }
        if (message.startsWith("Compilation of SQL UDF failed")) {
            return new HandlerErrorCode(90203, "42601");
        }
        if (message.equals(HybridKeys.primaryKeyExists())) {
            return new HandlerErrorCode(200001, "22000");
        }
        if (message.startsWith("Foreign key constraint \"SYS_INDEX_") && message.endsWith("\" was violated.")) {
            return new HandlerErrorCode(200009, "22000");
        }
        if (message.startsWith("Cannot apply projection policy on '")
                && message.endsWith("' because it is used as an argument by one or more policies.")) {
            return new HandlerErrorCode(506002, "23001");
        }
        if (message.contains(SqlAccessControlError.PREFIX) && message.contains(INSUFFICIENT_PRIVILEGES)) {
            return new HandlerErrorCode(3001, "42501");
        }
        if (message.contains("syntax error line ")) {
            return new HandlerErrorCode(1003, "42000");
        }
        if (message.contains("invalid identifier '")) {
            return new HandlerErrorCode(904, "42000");
        }
        if (message.contains("Invalid identifier ")) {
            return new HandlerErrorCode(2004, "42601");
        }
        if (message.contains("Division by zero")) {
            return new HandlerErrorCode(100051, "22012");
        }
        if (message.contains("This session does not have a current schema.")) {
            // Every statement refused for want of a current schema (live-verified).
            return new HandlerErrorCode(90106, "22000");
        }
        if (message.contains("NULL result in a non-nullable column")) {
            return new HandlerErrorCode(100072, "00000", "22000");
        }
        final HandlerErrorCode value = unreadValue(message);
        if (value != null) {
            return value;
        }
        if (message.contains("Unknown table function ")) {
            return new HandlerErrorCode(2142, "42601");
        }
        if (message.contains("Unknown function ") || message.contains("Unknown user-defined function ")) {
            return new HandlerErrorCode(2139, "02000");
        }
        if (message.contains("Cannot overload ") && message.contains(" as it would cause ambiguous ")) {
            return new HandlerErrorCode(949, "42723");
        }
        if (message.contains("column '") && message.contains("already exists")) {
            return new HandlerErrorCode(1430, "42601");
        }
        if (message.contains("already exists")) {
            return new HandlerErrorCode(2002, "42710");
        }
        if (message.contains("Object does not exist, or operation cannot be performed")) {
            return new HandlerErrorCode(2043, "02000");
        }
        if (message.contains("column '") && message.contains("' does not exist")) {
            return new HandlerErrorCode(1431, "42601");
        }
        final HandlerErrorCode missing = missingObject(message);
        if (missing != null) {
            return missing;
        }
        if (message.contains("does not exist")) {
            return new HandlerErrorCode(2003, "42S02");
        }
        if (message.contains(" did not exist or was purged.")) {
            return new HandlerErrorCode(90025, "02000");
        }
        if (message.contains("not enough arguments for function")) {
            return new HandlerErrorCode(938, "22023");
        }
        if (message.contains("too many arguments for function")) {
            return new HandlerErrorCode(939, "22023");
        }
        if (message.contains("Single-row subquery returns more than one row")) {
            return new HandlerErrorCode(90150, "22000");
        }
        if (message.contains("is neither an aggregate nor in the group by clause")) {
            return new HandlerErrorCode(1104, "42601");
        }
        if (message.contains("A SELECT INTO statement expects exactly 1 returned row")) {
            return new HandlerErrorCode(92219, "22000");
        }
        if (message.contains("ambiguous column name '")) {
            return new HandlerErrorCode(2028, "42601");
        }
        if (message.contains("Invalid bind value (")) {
            return new HandlerErrorCode(2086, "22000");
        }
        if (message.contains("Your transaction was aborted.")) {
            return new HandlerErrorCode(624, "57014");
        }
        if (message.contains("COMMIT failed. Your transaction '")) {
            return new HandlerErrorCode(670, "57014");
        }
        if (message.contains("' was aborted because waiting for this lock is ")) {
            return new HandlerErrorCode(message.endsWith("currently not allowed.") ? 626 : 627, "57014");
        }
        if (message.contains(TransactionManager.OTHER_SCOPE)) {
            return new HandlerErrorCode(90239, "25000");
        }
        if (message.contains(CallDepth.REFUSAL)) {
            return new HandlerErrorCode(90235, "42601");
        }
        if (message.contains("Stored procedure execution error: CURSOR '") && message.contains("' is already open.")) {
            return new HandlerErrorCode(92215, "P0000");
        }
        if (message.contains("CURSOR ") && message.contains(" is not open")) {
            return new HandlerErrorCode(92201, "P0000");
        }
        if (message.contains(UNINITIALIZED_RESULTSET)) {
            return new HandlerErrorCode(92212, "P0000");
        }
        if (message.contains("Invalid expression [") && message.contains("] in VALUES clause")) {
            return new HandlerErrorCode(2014, "22000");
        }
        if (message.contains("Insert value list does not match column list")) {
            return new HandlerErrorCode(2020, "21S01");
        }
        if (message.contains(" has no result because it failed")) {
            return new HandlerErrorCode(711, "02000");
        }
        if (message.contains("Error parsing JSON: ")) {
            return new HandlerErrorCode(100069, "22P02");
        }
        if (message.contains(TransactionManager.SCOPE_LEFT_OPEN)) {
            return new HandlerErrorCode(90232, "25000");
        }
        if (message.startsWith("Unknown compression method")) {
            return new HandlerErrorCode(100194, "42P19");
        }
        if (message.startsWith("Can't compress data")) {
            return new HandlerErrorCode(100195, "22000");
        }
        return refusedShape(message);
    }

    /** The pair of a value no conversion reads, or null for none. */
    private static HandlerErrorCode unreadValue(final String message) {
        if (message.contains("Numeric value '") && message.contains("is not recognized")) {
            return new HandlerErrorCode(100038, "22018");
        }
        if (message.contains("Numeric value '") && message.contains("' is out of range")) {
            return new HandlerErrorCode(100039, "22003");
        }
        if (message.contains("Date '") && message.contains("is not recognized")) {
            return new HandlerErrorCode(100040, "22007");
        }
        if (message.contains("Timestamp '") && message.contains("' is not recognized")) {
            return new HandlerErrorCode(100035, "22007");
        }
        if (message.contains("Time '") && message.contains("' is not recognized")) {
            return new HandlerErrorCode(100108, "22007");
        }
        if (message.contains("Boolean value '") && message.contains("' is not recognized")) {
            return new HandlerErrorCode(100037, "22018");
        }
        if (message.contains("Can't parse '") && message.contains("' as timestamp with format '")) {
            return new HandlerErrorCode(100096, "22007");
        }
        if (message.contains("Can't parse '") && message.contains("' as date with format '")) {
            return new HandlerErrorCode(100097, "22007");
        }
        if (message.contains("Number out of representable range")) {
            return new HandlerErrorCode(100046, "22003");
        }
        if (message.contains("String '") && message.contains("' is too long and would be truncated")) {
            return new HandlerErrorCode(100078, "22000");
        }
        if (message.contains("Failed to cast variant value ")) {
            return new HandlerErrorCode(100071, "22000");
        }
        if (message.contains("The following string is not a legal hex-encoded value")) {
            return new HandlerErrorCode(100115, "22000");
        }
        if (message.contains("The following string is not a legal base64-encoded value")) {
            return new HandlerErrorCode(100114, "22000");
        }
        if (message.contains("Error parsing XML: ")) {
            return new HandlerErrorCode(100100, "22P02");
        }
        if (message.contains("Invalid regular expression: ")) {
            return new HandlerErrorCode(100048, "2201B");
        }
        if (message.contains("Duplicate row detected during DML action")) {
            return new HandlerErrorCode(100090, "42P18");
        }
        return null;
    }

    /**
     * The pair of a missing object's sentence by the kind it names - {@code Schema 'S' does not exist or not
     * authorized.} - or null for no such sentence.
     */
    private static HandlerErrorCode missingObject(final String message) {
        final int end = message.indexOf(NOT_AUTHORIZED);
        final int open = end < 0 ? -1 : message.lastIndexOf(" '", end);
        if (open < 0) {
            return null;
        }
        int from = message.lastIndexOf('\n', open) + 1;
        final int colon = message.lastIndexOf(": ", open);
        if (colon + 2 > from) {
            from = colon + 2;
        }
        final String kind = message.substring(Math.min(from, open), open).trim();
        if ("Object".equals(kind) && message.substring(open + 2, end).endsWith(")")) {
            // A routine named by its signature, as GET_DDL names one.
            return new HandlerErrorCode(2001, "02000");
        }
        return "Object".equals(kind) || "Table".equals(kind) || "View".equals(kind)
            ? new HandlerErrorCode(2003, "42S02") : new HandlerErrorCode(2003, "02000");
    }

    /** The pair of a statement refused for its shape, or null for a sentence of no known family. */
    private static HandlerErrorCode refusedShape(final String message) {
        if (message.contains("is not a valid group by expression")) {
            return new HandlerErrorCode(979, "42601");
        }
        if (message.contains("is not a valid order by expression")) {
            return new HandlerErrorCode(2024, "42601");
        }
        if (message.contains("duplicate column name '")) {
            return new HandlerErrorCode(2025, "42S21");
        }
        if (message.contains("Unsupported data type '")) {
            return new HandlerErrorCode(2040, "42601");
        }
        if (message.contains(" cannot be used with arguments of types ")) {
            return new HandlerErrorCode(1065, "22023");
        }
        if (message.contains("invalid parameter '")) {
            return new HandlerErrorCode(1006, "22023");
        }
        if (message.contains("invalid value [") && message.contains("] for parameter '")) {
            return new HandlerErrorCode(1008, "22023");
        }
        if (message.contains("invalid type [") && message.contains("] for parameter '")) {
            return new HandlerErrorCode(1007, "22023");
        }
        if (message.contains("Invalid argument types for function '")) {
            return new HandlerErrorCode(1044, "42P13");
        }
        if (message.contains("invalid number of result columns for set operator input branches")) {
            return new HandlerErrorCode(1789, "42601");
        }
        if (message.contains("parameter value out of range: ")) {
            return new HandlerErrorCode(1817, "42601");
        }
        if (message.contains("Time travel data is not available for ")) {
            return new HandlerErrorCode(707, "02000");
        }
        if (message.contains("is not a valid date/time component for function ")) {
            return new HandlerErrorCode(2151, "22023");
        }
        if (message.contains("Invalid data type [") && message.contains("] for predicate [")) {
            return new HandlerErrorCode(2034, "22000");
        }
        if (message.contains("requires ORDER BY in window specification")) {
            return new HandlerErrorCode(2061, "42601");
        }
        if (message.contains("Missing column specification")) {
            return new HandlerErrorCode(2022, "42601");
        }
        if (message.contains("cannot drop all the columns of a table")) {
            return new HandlerErrorCode(1432, "42601");
        }
        if (message.contains("cannot change column ") && message.contains(" from type ")) {
            return new HandlerErrorCode(2108, "22000");
        }
        if (message.contains("INSERT statement's target must be a table")) {
            return new HandlerErrorCode(1759, "42601");
        }
        if (message.contains("Aggregate functions cannot be nested: ")) {
            return new HandlerErrorCode(2082, "42601");
        }
        if (message.startsWith("Statement ") && message.endsWith(" not found")) {
            return new HandlerErrorCode(709, "02000");
        }
        if (message.startsWith("Actual statement count ")
                && message.contains(" did not match the desired statement count ")) {
            return new HandlerErrorCode(StatementCountMismatch.CODE, StatementCountMismatch.STATE);
        }
        return null;
    }

    int code() {
        return code;
    }

    String state() {
        return state;
    }

    /** The state a client's driver reads, where it differs from the handler's. */
    String clientState() {
        return clientState;
    }
}
