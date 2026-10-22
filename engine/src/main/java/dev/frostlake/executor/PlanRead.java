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
 * A column an EXPLAIN plan reads from one of its scans, recorded where a name resolves to it and listed by the scan
 * once the statement is planned.
 */
final class PlanRead {

    private final PlanNode scan;
    private final String column;

    /**
     * @param scan   the scan of the table the column belongs to
     * @param column the column's canonical name
     */
    PlanRead(final PlanNode scan, final String column) {
        this.scan = scan;
        this.column = column;
    }

    /** Records the read on its scan. */
    void mark() {
        scan.reads(column);
    }
}
