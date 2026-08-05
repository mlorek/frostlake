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

package dev.frostlake.types;

/**
 * The GEOSPATIAL type predicate shared by the strict-argument rules: {@link GeographyType} and
 * {@link GeometryType} are ONE family for every rejection measured on the account.
 *
 * <p>That the two agree was measured rather than assumed. Live over a table carrying both
 * a GEOGRAPHY and a GEOMETRY column, every rejection reproduced with the type name swapped and
 * nothing else changed: {@code GROUP BY} (SQLSTATE 42804, vendor 92102), {@code ORDER BY} (92103),
 * {@code PARTITION BY} (92104), {@code MAX} / {@code MIN}, {@code SUM}, {@code MEDIAN},
 * {@code STDDEV}, {@code UPPER}, {@code ABS}, {@code ||}, {@code =} and {@code CAST(… AS VARCHAR)}.
 *
 * <p>Both types live in the ENGINE even though the {@code ST_} function pack ships in the optional
 * {@code frostlake-geo} module: a GEOGRAPHY column can be declared with the module absent, so these
 * rejections must not depend on it being on the classpath.
 *
 * <p>This is deliberately NOT folded into {@code TypeCategory.SEMI_STRUCTURED}, which both geo types
 * happen to carry: the semi-structured rules key on the OBJECT / ARRAY / MAP classes, and live treats
 * geo differently from all three in both directions — {@code GROUP BY o} and {@code o = o} are legal
 * where the geo spellings are not, while {@code TO_JSON(o)}, {@code GET(o, 'k')} and
 * {@code ARRAY_AGG(o)} all work where the geo spellings are refused.
 */
public final class GeoTypes {

    private GeoTypes() {
    }

    /** Whether {@code type} is GEOGRAPHY or GEOMETRY. */
    public static boolean isGeo(final DataType type) {
        return type instanceof GeographyType || type instanceof GeometryType;
    }
}
