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

package dev.frostlake.metastore.model;

/**
 * A compute pool's lifecycle state as SHOW COMPUTE POOLS spells it. This engine runs no nodes, so a
 * pool only ever reports the two states a real account shows before any node has come up: SUSPENDED,
 * and STARTING for a pool whose nodes have been asked for (created unsuspended, or resumed).
 */
public enum ComputePoolState {
    SUSPENDED,
    STARTING
}
