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

package com.snowflake.snowpark_java.types;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * A GEOMETRY value as Snowpark hands it to a Java handler: its GeoJSON text, kept as given and read back through
 * {@link #toString}, a null text refused, {@code java.io.IOException: Cannot create geometry object from null
 * input} wrapped as unchecked. Two values are equal when their texts are (live-verified).
 */
public final class Geometry {

    private final String data;

    private Geometry(final String data) {
        if (data == null) {
            throw new UncheckedIOException(new IOException("Cannot create geometry object from null input"));
        }
        this.data = data;
    }

    /** A geometry of its GeoJSON text. */
    public static Geometry fromGeoJSON(final String g) {
        return new Geometry(g);
    }

    @Override
    public String toString() {
        return data;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof Geometry && data.equals(((Geometry) other).data);
    }

    @Override
    public int hashCode() {
        return data.hashCode();
    }
}
