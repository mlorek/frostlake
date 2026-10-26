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

package dev.frostlake.jdbc;

import java.time.ZoneId;

/**
 * Where a connection's result sets learn the zone its session's TIMEZONE names — the session's own, its user's or
 * the account's — which the account's driver reads a DATE's midnight and a wall clock of no zone in (see {@link
 * TemporalCellReads}).
 */
interface SessionZoneSource {

    /**
     * @return the zone the session's TIMEZONE named when its last statement ran
     */
    ZoneId sessionZone();
}
