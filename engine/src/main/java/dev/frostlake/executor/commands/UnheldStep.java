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

/**
 * The steps an Iceberg table's column list is judged in once every type is read as written, each over every
 * column before the next: the semi-structured containers and the zoned timestamp, the scale of a time, and the
 * values the format holds none of (live-verified).
 */
enum UnheldStep {

    /** An OBJECT or an ARRAY of no declared shape, and a TIMESTAMP_TZ. */
    CONTAINER,

    /** A TIME or a TIMESTAMP of a scale the format does not keep. */
    SCALE,

    /** A VARIANT, a GEOGRAPHY, a GEOMETRY and every other value the format holds none of. */
    VALUE
}
