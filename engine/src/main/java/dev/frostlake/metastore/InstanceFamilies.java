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

import java.util.ArrayList;
import java.util.List;

/**
 * The compute-pool instance-family catalog, exactly as a real account lists it — the same rows
 * SHOW COMPUTE POOL INSTANCE FAMILIES answers with, in the same (non-alphabetical) order, and the
 * set CREATE COMPUTE POOL validates INSTANCE_FAMILY against. A name outside this set is rejected
 * with the account's own wording.
 *
 * <p>Row columns, in listing order: name, description, vcpu, memory_gib, storage_gib, gpu,
 * gpu_count, gpu_memory_gib, current_node_usage, message.
 */
public final class InstanceFamilies {

    private static final String GEN2_X86 = "Current gen x86. Best price-performance for general workloads.";
    private static final String GEN2_MEM = "Current gen x86. Optimized for memory-intensive workloads.";
    private static final String GEN1_ARM = "Current gen ARM. Best cost-efficiency for scale-out workloads.";
    private static final String RESERVATION = "Instance family available by reservation only.";
    private static final String STORAGE = "93.13";

    private static final String[][] ROWS = {
        {"CPU_X64_XS", "Smallest instance available for Snowpark Containers. Ideal for cost-savings and getting started.", "1", "6", STORAGE, "", "0", "0", "0", ""},
        {"CPU_X64_S", "Ideal for hosting multiple services/jobs while saving cost.", "3", "13", STORAGE, "", "0", "0", "0", ""},
        {"CPU_X64_M", "Ideal for having a full stack application or multiple services.", "6", "28", STORAGE, "", "0", "0", "0", ""},
        {"CPU_X64_L", "For applications which need an unusually large number of CPUs, memory and Storage.", "28", "116", STORAGE, "", "0", "0", "0", ""},
        {"CPU_X64_SL", GEN2_X86, "14", "58", STORAGE, "", "0", "0", "0", ""},
        {"HIGHMEM_X64_S", "For memory intensive applications.", "6", "58", STORAGE, "", "0", "0", "0", ""},
        {"HIGHMEM_X64_M", "For hosting multiple memory intensive applications on a single machine.", "28", "240", STORAGE, "", "0", "0", "0", ""},
        {"HIGHMEM_X64_L", "Previous gen x86. Memory Optimized. Use MEM_X64_G2 for new apps.", "124", "984", STORAGE, "", "0", "0", "0", ""},
        {"GPU_NV_S", "Standard mid-range GPU for ML development and data science.", "6", "27", STORAGE, "NVIDIA A10G", "1", "24", "0", ""},
        {"GPU_NV_M", "Standard mid-range GPU for ML development and data science.", "44", "178", STORAGE, "NVIDIA A10G", "4", "24", "0", ""},
        {"GPU_NV_L", "Previous gen AI. High-throughput for large datasets and modeling.", "92", "1112", STORAGE, "NVIDIA A100", "8", "40", "0", ""},
        {"GPU_NV_XL", "", "188", "1843", STORAGE, "NVIDIA H100", "8", "80", "0", RESERVATION},
        {"GPU_NV_2XL", "", "188", "1843", STORAGE, "NVIDIA H200", "8", "141", "0", RESERVATION},
        {"GEN_X64_G2_2", GEN2_X86, "1", "6", STORAGE, "", "0", "0", "0", ""},
        {"GEN_X64_G2_4", GEN2_X86, "3", "13", STORAGE, "", "0", "0", "0", ""},
        {"GEN_X64_G2_8", GEN2_X86, "6", "28", STORAGE, "", "0", "0", "0", ""},
        {"GEN_X64_G2_32", GEN2_X86, "28", "116", STORAGE, "", "0", "0", "0", ""},
        {"MEM_X64_G2_8", GEN2_MEM, "6", "58", STORAGE, "", "0", "0", "0", ""},
        {"MEM_X64_G2_32", GEN2_MEM, "28", "240", STORAGE, "", "0", "0", "0", ""},
        {"MEM_X64_G2_64", GEN2_MEM, "60", "492", STORAGE, "", "0", "0", "0", ""},
        {"MEM_X64_G2_192", GEN2_MEM, "188", "1436", STORAGE, "", "0", "0", "0", ""},
        {"GEN_ARM_G1_2", GEN1_ARM, "1", "6", STORAGE, "", "0", "0", "0", ""},
        {"GEN_ARM_G1_4", GEN1_ARM, "3", "13", STORAGE, "", "0", "0", "0", ""},
        {"GEN_ARM_G1_8", GEN1_ARM, "6", "28", STORAGE, "", "0", "0", "0", ""},
        {"GEN_ARM_G1_16", GEN1_ARM, "14", "58", STORAGE, "", "0", "0", "0", ""},
        {"GEN_ARM_G1_32", GEN1_ARM, "28", "116", STORAGE, "", "0", "0", "0", ""},
    };

    private InstanceFamilies() {
    }

    /** Whether CREATE COMPUTE POOL accepts this INSTANCE_FAMILY name (case-insensitive). */
    public static boolean isValid(final String name) {
        if (name == null) {
            return false;
        }
        for (final String[] row : ROWS) {
            if (row[0].equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** The catalog rows in listing order; each row is a fresh copy. */
    public static List<String[]> rows() {
        final List<String[]> copy = new ArrayList<>();
        for (final String[] row : ROWS) {
            copy.add(row.clone());
        }
        return copy;
    }
}
