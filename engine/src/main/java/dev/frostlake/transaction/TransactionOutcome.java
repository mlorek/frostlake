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

package dev.frostlake.transaction;

/**
 * Work a statement leaves for its transaction's end, beside the rows its write set holds: what a load records and
 * purges once it is committed, and what it takes back when it is not (see {@link Transaction#onOutcome}).
 */
public interface TransactionOutcome {

    /** The transaction committed, its writes applied. */
    void committed();

    /** The transaction rolled back, or the statement that registered this failed inside it. */
    void rolledBack();
}
