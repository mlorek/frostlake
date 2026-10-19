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

package dev.frostlake.metastore;

/**
 * The kinds of policy an attachment can name, each recorded in its own place: a column holds its masking and
 * projection policies, a table its row access, aggregation and join policies, and a view its row access policy.
 */
public enum PolicyKind {
    MASKING, PROJECTION, ROW_ACCESS, AGGREGATION, JOIN
}
