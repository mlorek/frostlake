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


package dev.frostlake.persistence;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * The values a write-ahead log record keeps by Java serialization, as the state snapshots keep theirs: a session's
 * variables and parameters and the results a statement read by RESULT_SCAN. A value serialization cannot write is
 * not kept, and the record says so instead of failing the commit that writes it.
 */
final class WalValues {

    private WalValues() {
    }

    /**
     * The bytes a value serializes to.
     *
     * @param value the value, null included
     * @return its bytes, or null when it does not serialize
     */
    static byte[] bytesOf(final Object value) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        } catch (final IOException | RuntimeException unwritable) {
            return null;
        }
        return bytes.toByteArray();
    }

    /**
     * The value bytes hold.
     *
     * @param bytes what {@link #bytesOf} wrote
     * @return the value, null included
     * @throws IOException when the bytes do not read back as a value
     */
    static Object valueOf(final byte[] bytes) throws IOException {
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return in.readObject();
        } catch (final ClassNotFoundException unknown) {
            throw new IOException("unknown class in a write-ahead log value: " + unknown.getMessage(), unknown);
        } catch (final RuntimeException unreadable) {
            throw new IOException("unreadable write-ahead log value: " + unreadable.getMessage(), unreadable);
        }
    }
}
