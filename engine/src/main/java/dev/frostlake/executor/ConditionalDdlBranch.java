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

/**
 * Which conditional branch a DDL statement took — the two outcomes whose status sentence depends on
 * what the catalog held BEFORE the statement ran, which the parse tree alone cannot say.
 */
public enum ConditionalDdlBranch {
    /** CREATE … IF NOT EXISTS found the object and created nothing. */
    CREATE_SKIPPED,
    /** DROP … IF EXISTS found nothing and dropped nothing. */
    DROP_SKIPPED,
    /** CREATE OR ALTER TABLE found the table and altered it where it stands. */
    ALTERED_IN_PLACE
}
