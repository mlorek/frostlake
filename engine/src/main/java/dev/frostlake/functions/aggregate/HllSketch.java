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

package dev.frostlake.functions.aggregate;

import dev.frostlake.values.BinaryValue;

/**
 * A HyperLogLog sketch as the HLL STATE family carries it: the registers, the precision they were counted
 * at, and the two encodings the account writes them in.
 *
 * <p>★ THE BINARY STATE has no header. While few registers are set it is SPARSE — three bytes per set
 * register, the register's index as a little-endian 16-bit number followed by its value — and past that it
 * is DENSE, one byte per register and nothing else, so its length alone tells the two apart against a known
 * precision. An empty sketch is an empty BINARY (all live-verified: three distinct values read
 * {@code 2B00016E0C02E20F02}, a hundred read 297 bytes, five hundred read the dense 4096).
 *
 * <p>★ THE EXPORTED OBJECT is the same state as JSON, `{"precision": p, "sparse": {"indices": […],
 * "maxLzCounts": […]}, "version": 4}` while sparse and `{"dense": […], "precision": p, "version": 4}` when
 * dense, and IMPORT reads either back — including one the account exported, which is what makes the object
 * the interchange form. The REGISTERS themselves depend on the hash each engine uses, so a state's bytes
 * are its own engine's; an imported state is read at the precision it declares.
 */
public final class HllSketch {

    /** The version the account stamps on an exported state. */
    public static final int VERSION = 4;

    private final int precision;
    private final byte[] registers;

    /**
     * @param precision the number of index bits, so {@code 1 << precision} registers
     * @param registers one byte per register, each the largest leading-zero count seen there
     */
    public HllSketch(final int precision, final byte[] registers) {
        this.precision = precision;
        this.registers = registers;
    }

    /** The number of index bits. */
    public int precision() {
        return precision;
    }

    /** The registers, one byte each. */
    public byte[] registers() {
        return registers;
    }

    /** How many registers hold a value. */
    private int setCount() {
        int set = 0;
        for (final byte register : registers) {
            if (register != 0) {
                set++;
            }
        }
        return set;
    }

    /** Whether the sparse encoding is the smaller one, which is the encoding the state then takes. */
    public boolean isSparse() {
        return 3 * setCount() < registers.length;
    }

    /**
     * The cardinality the registers estimate: HyperLogLog's harmonic mean, with linear counting while
     * registers are still empty, which is what keeps a handful of values exact.
     *
     * @return the estimate, rounded
     */
    public long estimate() {
        final int m = registers.length;
        double harmonic = 0.0;
        int empty = 0;
        for (final byte register : registers) {
            harmonic += 1.0 / (1L << register);
            if (register == 0) {
                empty++;
            }
        }
        final double alpha = 0.7213 / (1.0 + 1.079 / m);
        double estimate = alpha * m * m / harmonic;
        if (estimate <= 2.5 * m && empty > 0) {
            estimate = m * Math.log((double) m / empty);
        }
        return Math.round(estimate);
    }

    /**
     * The state as the account writes it: sparse while that is smaller, else dense.
     *
     * @return the binary state
     */
    public BinaryValue toBinary() {
        if (!isSparse()) {
            return BinaryValue.of(registers.clone());
        }
        final byte[] sparse = new byte[3 * setCount()];
        int at = 0;
        for (int index = 0; index < registers.length; index++) {
            if (registers[index] == 0) {
                continue;
            }
            sparse[at++] = (byte) (index & 0xff);
            sparse[at++] = (byte) (index >>> 8 & 0xff);
            sparse[at++] = registers[index];
        }
        return BinaryValue.of(sparse);
    }

    /**
     * A state read back from its binary form. The length tells the encoding: as many bytes as the
     * precision has registers is the dense form, and anything else is the sparse one.
     *
     * @param bytes     the binary state
     * @param precision the precision the state was counted at
     * @return the sketch
     */
    public static HllSketch fromBinary(final byte[] bytes, final int precision) {
        final int m = 1 << precision;
        if (bytes.length == m) {
            return new HllSketch(precision, bytes.clone());
        }
        final byte[] registers = new byte[m];
        for (int at = 0; at + 2 < bytes.length; at += 3) {
            final int index = (bytes[at] & 0xff) | (bytes[at + 1] & 0xff) << 8;
            if (index < m) {
                registers[index] = bytes[at + 2];
            }
        }
        return new HllSketch(precision, registers);
    }

    /**
     * This sketch with another's registers folded in: each register keeps the larger of the two, which is
     * what makes a combined sketch the sketch of the union.
     *
     * @param other the sketch to fold in
     * @return the merged sketch
     */
    public HllSketch merge(final HllSketch other) {
        if (other.registers.length != registers.length) {
            // Different precisions cannot be merged register by register; the wider state wins, as
            // nothing else can be said about the narrower one's counts.
            return other.registers.length > registers.length ? other : this;
        }
        final byte[] merged = new byte[registers.length];
        for (int i = 0; i < registers.length; i++) {
            merged[i] = registers[i] >= other.registers[i] ? registers[i] : other.registers[i];
        }
        return new HllSketch(precision, merged);
    }
}
