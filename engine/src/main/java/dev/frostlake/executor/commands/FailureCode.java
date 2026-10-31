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

import dev.frostlake.executor.BlockExpressionTypeError;
import dev.frostlake.executor.GetDdlMissingObject;
import dev.frostlake.executor.StatementCanceledException;
import dev.frostlake.executor.StatementCountMismatch;
import dev.frostlake.executor.procedural.ProceduralException;
import dev.frostlake.parser.SqlSyntaxException;

/**
 * The code and the state a failed statement carries: what a block's handler reads as SQLCODE and SQLSTATE, and
 * what the error a JavaScript procedure catches holds as {@code code} and {@code state} (live-verified). A
 * user-defined exception carries its declared code and the state P0001; a syntax error is 1003 / 42000, and a
 * refusal the parser raises of another family that family's pair; argument
 * types an operator or a call does not take are 1044 / 42P13, a parameter that cannot be converted to the type it
 * is compared with 1038 / 22023 and a cast the matrix refuses 1007 / 22023, and an object GET_DDL does not find
 * 2003 / 02000; every other refusal reads the code and the state of its sentence (see {@link HandlerErrorCode}).
 */
public final class FailureCode {

    private final int code;
    private final String state;

    private FailureCode(final int code, final String state) {
        this.code = code;
        this.state = state;
    }

    /**
     * The code and the state of a failure.
     *
     * @param failure what the statement failed with
     * @return the pair
     */
    public static FailureCode of(final Throwable failure) {
        final FailureCode typed = typed(failure);
        if (typed != null) {
            return typed;
        }
        final HandlerErrorCode read = HandlerErrorCode.of(messageOf(failure));
        return new FailureCode(read.code(), read.state());
    }

    /**
     * The code and the state a client's driver reads for a failure (live-verified): the pair a handler reads, but
     * 22000 for a NULL in a NOT NULL column, where a handler reads 00000, and SQLSTATE 57014 with its own code for
     * a statement a timeout or a cancel ended.
     *
     * @param failure what the statement failed with
     * @return the pair, or null for a refusal of no known family
     */
    public static FailureCode ofClient(final Throwable failure) {
        if (failure instanceof StatementCanceledException) {
            return new FailureCode(((StatementCanceledException) failure).getCode(),
                StatementCanceledException.SQL_STATE);
        }
        final FailureCode typed = typed(failure);
        return typed != null ? typed : ofClientMessage(messageOf(failure));
    }

    /**
     * The code and the state a client's driver reads for a refusal it has only the sentence of.
     *
     * @param message the refusal's sentence
     * @return the pair, or null for a sentence of no known family
     */
    public static FailureCode ofClientMessage(final String message) {
        final Integer canceled = StatementCanceledException.codeOf(message);
        if (canceled != null) {
            return new FailureCode(canceled.intValue(), StatementCanceledException.SQL_STATE);
        }
        final HandlerErrorCode read = HandlerErrorCode.known(message);
        return read == null ? null : new FailureCode(read.code(), read.clientState());
    }

    /** The pair a failure's kind carries whatever its sentence, or null. */
    private static FailureCode typed(final Throwable failure) {
        final String message = messageOf(failure);
        if (failure instanceof ProceduralException) {
            return new FailureCode(((ProceduralException) failure).getErrorCode(), "P0001");
        }
        if (failure instanceof SqlSyntaxException) {
            // A refusal the parser raises that is no syntax error reads its sentence's family: an unsupported
            // data type is 2040 / 42601 (live-verified).
            final HandlerErrorCode read = HandlerErrorCode.known(message);
            return read != null ? new FailureCode(read.code(), read.state()) : new FailureCode(1003, "42000");
        }
        if (failure instanceof BlockExpressionTypeError) {
            if (message.contains("Can not convert parameter")) {
                return new FailureCode(1038, "22023");
            }
            if (message.contains("invalid type [CAST(")) {
                return new FailureCode(1007, "22023");
            }
            return new FailureCode(1044, "42P13");
        }
        if (failure instanceof StatementCountMismatch) {
            return new FailureCode(StatementCountMismatch.CODE, StatementCountMismatch.STATE);
        }
        if (failure instanceof GetDdlMissingObject) {
            return new FailureCode(GetDdlMissingObject.CODE, GetDdlMissingObject.STATE);
        }
        return null;
    }

    /**
     * The sentence a failure reads as: its message, or its kind where it has none.
     *
     * @param failure what the statement failed with
     * @return the sentence
     */
    public static String messageOf(final Throwable failure) {
        return failure.getMessage() != null ? failure.getMessage() : failure.getClass().getSimpleName();
    }

    public int code() {
        return code;
    }

    public String state() {
        return state;
    }
}
