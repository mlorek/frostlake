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

package dev.frostlake.functions;

/**
 * Raised by a function that ORDERS its arguments when two of them belong to families it cannot bring
 * together — {@code GREATEST(<binary>, <string>)} and its kin.
 *
 * <p>It carries no user-facing sentence on purpose. The refusal the account gives names the operand
 * and both DECLARED types ("Can not convert parameter 'T.S' of type [VARCHAR(10)] into expected type
 * [BINARY(4)]"), and a function that sees only VALUES cannot spell that: the qualified column name
 * lives in the expression tree. So this type is a SIGNAL — the evaluator catches it and re-asks the
 * static type channel, which has the tree and produces the account's own wording. What must never
 * happen is the alternative it replaced: a raw ClassCastException from a Comparable pairing, quoting
 * Java class names at whoever ran the query.
 */
public class IncomparableArgumentsException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IncomparableArgumentsException(final String detail) {
        super(detail);
    }
}
