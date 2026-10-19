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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * SHOW COMPUTE POOL INSTANCE FAMILIES lists the account's catalog. The engine's table lacked one family and told two
 * GPU memory sizes wrong.
 *
 * <p>The account lists 27 families in an order of its own, the reservation-only NVIDIA B300 family before the ARM
 * ones, and 22 GiB of GPU memory for the two A10G families. Every cell is live-verified.
 */
public class InstanceFamilyCatalogTest extends BaseDatabaseTest {

    @Test
    public void theCatalogIsTheAccounts() {
        final ResultSet rs = engine.executeQuery("SHOW COMPUTE POOL INSTANCE FAMILIES");
        final List<String> names = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            names.add(cell(rs, row, "name"));
        }
        final List<String> expected = new ArrayList<>();
        for (final String name : new String[] {"CPU_X64_XS", "CPU_X64_S", "CPU_X64_M", "CPU_X64_L", "CPU_X64_SL",
            "HIGHMEM_X64_S", "HIGHMEM_X64_M", "HIGHMEM_X64_L", "GPU_NV_S", "GPU_NV_M", "GPU_NV_L", "GPU_NV_XL",
            "GPU_NV_2XL", "GEN_X64_G2_2", "GEN_X64_G2_4", "GEN_X64_G2_8", "GEN_X64_G2_32", "MEM_X64_G2_8",
            "MEM_X64_G2_32", "MEM_X64_G2_64", "MEM_X64_G2_192", "GPU_B300_G1_192", "GEN_ARM_G1_2", "GEN_ARM_G1_4",
            "GEN_ARM_G1_8", "GEN_ARM_G1_16", "GEN_ARM_G1_32"}) {
            expected.add(name);
        }
        assertEquals(expected, names);
        final Row small = soleRowWhere(rs, "name", "GPU_NV_S");
        assertEquals("NVIDIA A10G|1|22", cell(rs, small, "gpu") + "|" + cell(rs, small, "gpu_count") + "|"
            + cell(rs, small, "gpu_memory_gib"));
        final Row medium = soleRowWhere(rs, "name", "GPU_NV_M");
        assertEquals("NVIDIA A10G|4|22", cell(rs, medium, "gpu") + "|" + cell(rs, medium, "gpu_count") + "|"
            + cell(rs, medium, "gpu_memory_gib"));
        final Row b300 = soleRowWhere(rs, "name", "GPU_B300_G1_192");
        assertEquals("Next-gen training and inference for the largest AI workloads.|188|3969|93.13|NVIDIA B300|8|268|"
            + "Instance family available by reservation only.", cell(rs, b300, "description") + "|"
            + cell(rs, b300, "vcpu") + "|" + cell(rs, b300, "memory_gib") + "|" + cell(rs, b300, "storage_gib") + "|"
            + cell(rs, b300, "gpu") + "|" + cell(rs, b300, "gpu_count") + "|" + cell(rs, b300, "gpu_memory_gib") + "|"
            + cell(rs, b300, "message"));
    }
}
