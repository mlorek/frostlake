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
import dev.frostlake.metastore.HybridKeys;
import dev.frostlake.transaction.TransactionManager;

/**
 * The SQLCODE and SQLSTATE a block's handler reads for a refusal the engine raised, by the refusal's sentence
 * (live-verified): a syntax error — one in the text EXECUTE IMMEDIATE runs, a string LIMIT, a routine's body — is
 * 1003 / 42000 and a SQL function's or a policy's body that does not compile 90203 / 42601; an invalid identifier is
 * 904 / 42000, a missing object 2003 / 42S02 and a schema USE cannot enter 2043 / 02000; a name already taken is
 * 2002 / 42710, a column's 1430 / 42601 and an overload that would compete with another 949 / 42723; an unknown
 * function or procedure 2139 / 02000 and a wrong argument count
 * 938 or 939 / 22023; a text that is no number 100038 / 22018 and no date 100040 / 22007; a division by zero
 * 100051 / 22012; a scalar subquery of several rows 90150 / 22000; a column outside the grouping 1104 / 42601; a
 * SELECT … INTO of several rows 92219 / 22000, an ambiguous column 2028 / 42601 and an infinity a statement binds
 * 2086 / 22000; a statement refused because the session's transaction was aborted 624 / 57014 and the COMMIT of
 * such a transaction 670 / 57014; a statement that would end another scope's transaction 90239 / 25000 and a
 * block or a procedure that left its own open 90232 / 25000; a body ended past the depth bodies run to 90235 /
 * 42601; an expression a VALUES clause does not take 2014 / 22000, an INSERT whose values do not match its columns
 * 2020 / 21S01, a result asked of a statement that failed 711 / 02000 and a text that is no JSON 100069 / 22P02; an
 * OPEN of an open cursor 92215 / P0000 and a CLOSE of one that is not open 92201 / P0000; a RESULTSET read before
 * anything was given to it 92212 / P0000. A refusal of no known family reads 100351 / P0000. A hybrid table's
 * primary key already held is 200001 / 22000 and a foreign key it breaks 200009 / 22000 (documented).
 */
final class HandlerErrorCode {

    private static final String UNINITIALIZED_RESULTSET = "Stored procedure execution error: uninitialized resultSet '";

    private static final HandlerErrorCode OTHER = new HandlerErrorCode(100351, "P0000");

    private final int code;
    private final String state;

    private HandlerErrorCode(final int code, final String state) {
        this.code = code;
        this.state = state;
    }

    /**
     * The code and state of a refusal's sentence.
     *
     * @param message the refusal as the handler's SQLERRM reads it
     * @return the pair
     */
    static HandlerErrorCode of(final String message) {
        if (message == null) {
            return OTHER;
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
        if (message.contains("syntax error line ")) {
            return new HandlerErrorCode(1003, "42000");
        }
        if (message.contains("invalid identifier '")) {
            return new HandlerErrorCode(904, "42000");
        }
        if (message.contains("Division by zero")) {
            return new HandlerErrorCode(100051, "22012");
        }
        if (message.contains("NULL result in a non-nullable column")) {
            return new HandlerErrorCode(100072, "00000");
        }
        if (message.contains("Numeric value '") && message.contains("is not recognized")) {
            return new HandlerErrorCode(100038, "22018");
        }
        if (message.contains("Date '") && message.contains("is not recognized")) {
            return new HandlerErrorCode(100040, "22007");
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
        if (message.contains("does not exist")) {
            return new HandlerErrorCode(2003, "42S02");
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
        return OTHER;
    }

    int code() {
        return code;
    }

    String state() {
        return state;
    }
}
