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

package dev.frostlake.demo;

import dev.frostlake.metastore.QueryExecution;
import dev.frostlake.metastore.model.ScalingPolicy;
import dev.frostlake.metastore.model.Warehouse;
import dev.frostlake.storage.ResultSet;
import java.time.LocalDateTime;

/**
 * Demonstration of Compute WAREHOUSES - Virtual compute clusters for query execution
 */
public class WarehousesDemo extends AbstractDemo {

    public static void main(final String[] args) {
        new WarehousesDemo().execute();
    }

    @Override
    protected String getDemoTitle() {
        return "COMPUTE WAREHOUSES Demo: Scalable Query Execution";
    }

    @Override
    protected void runDemo() throws Exception {
        // Setup - no database needed for this demo

            // ==================== DEFAULT WAREHOUSE ====================
            printSectionHeader("DEFAULT WAREHOUSE");

            printSubsection("1. Default Warehouse (COMPUTE_WH)");
            Warehouse defaultWh = engine.getCatalog().getWarehouse("COMPUTE_WH");
            printWarehouseInfo(defaultWh);

            // ==================== CREATE WAREHOUSES ====================
            printSectionHeader("CREATE WAREHOUSES - Various Sizes");

            printSubsection("2. Create Small Warehouse for Development");
            engine.execute("CREATE WAREHOUSE dev_wh WITH WAREHOUSE_SIZE = 'SMALL'");
            printSuccess("Created DEV_WH (Small - 2 servers, 16 credits/hour)");

            printSubsection("3. Create Medium Warehouse for Production");
            engine.execute(
                "CREATE WAREHOUSE prod_wh " +
                "WITH WAREHOUSE_SIZE = 'MEDIUM' " +
                "AUTO_SUSPEND = 300 " +
                "AUTO_RESUME = true"
            );
            printSuccess("Created PROD_WH (Medium - 4 servers, 32 credits/hour)");
            System.out.println("  Auto-suspend: 300 seconds (5 minutes)");
            System.out.println("  Auto-resume: Enabled");

            printSubsection("4. Create Large Warehouse for Analytics");
            engine.execute(
                "CREATE WAREHOUSE analytics_wh " +
                "WITH WAREHOUSE_SIZE = 'LARGE' " +
                "AUTO_SUSPEND = 600"
            );
            printSuccess("Created ANALYTICS_WH (Large - 8 servers, 64 credits/hour)");

            printSubsection("5. Create X-Large Warehouse for Heavy ETL");
            engine.execute("CREATE WAREHOUSE etl_wh WITH WAREHOUSE_SIZE = 'X-LARGE'");
            printSuccess("Created ETL_WH (X-Large - 16 servers, 128 credits/hour)");

            // ==================== WAREHOUSE SIZES COMPARISON ====================
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║          WAREHOUSE SIZES COMPARISON                ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("   Size         Servers    Credits/Hour    Use Case");
            System.out.println("   --------------------------------------------------------");
            System.out.println("   X-Small      1          8               Development, Testing");
            System.out.println("   Small        2          16              Light queries, BI dashboards");
            System.out.println("   Medium       4          32              Standard workloads");
            System.out.println("   Large        8          64              Heavy analytics");
            System.out.println("   X-Large      16         128             Complex ETL");
            System.out.println("   2X-Large     32         256             Data science, ML");
            System.out.println("   3X-Large     64         512             Very large datasets");
            System.out.println("   4X-Large     128        1024            Extreme workloads");

            // ==================== WAREHOUSE STATE MANAGEMENT ====================
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║        WAREHOUSE STATE MANAGEMENT                  ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("=== 6. Resume Warehouse (Start Compute) ===");
            engine.execute("ALTER WAREHOUSE prod_wh RESUME");
            Warehouse prodWh = engine.getCatalog().getWarehouse("prod_wh");
            System.out.println("✓ Warehouse PROD_WH resumed");
            System.out.println("  State: " + prodWh.getState());
            System.out.println("  Can execute queries: " + prodWh.canExecuteQueries());

            System.out.println("\n=== 7. Suspend Warehouse (Stop Compute) ===");
            engine.execute("ALTER WAREHOUSE prod_wh SUSPEND");
            System.out.println("✓ Warehouse PROD_WH suspended");
            System.out.println("  State: " + prodWh.getState());
            System.out.println("  Can execute queries: " + prodWh.canExecuteQueries());
            System.out.println("  (No credits consumed while suspended)");

            // ==================== ALTER WAREHOUSE ====================
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║             ALTER WAREHOUSE                        ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("=== 8. Resize Warehouse (Scale Up) ===");
            Warehouse devWh = engine.getCatalog().getWarehouse("dev_wh");
            System.out.println("Before resize:");
            System.out.println("  Size: " + devWh.getSize().getDisplayName());
            System.out.println("  Servers: " + devWh.getSize().getServers());
            System.out.println("  Credits/hour: " + devWh.getSize().getCreditsPerHour());

            engine.execute("ALTER WAREHOUSE dev_wh SET WAREHOUSE_SIZE = 'LARGE'");
            System.out.println("\nAfter resize:");
            System.out.println("  Size: " + devWh.getSize().getDisplayName());
            System.out.println("  Servers: " + devWh.getSize().getServers());
            System.out.println("  Credits/hour: " + devWh.getSize().getCreditsPerHour());
            System.out.println("✓ Warehouse resized from Small to Large");

            System.out.println("\n=== 9. Adjust Auto-Suspend ===");
            engine.execute("ALTER WAREHOUSE analytics_wh SET AUTO_SUSPEND = 120");
            Warehouse analyticsWh = engine.getCatalog().getWarehouse("analytics_wh");
            System.out.println("✓ Set auto-suspend to 120 seconds (2 minutes)");
            System.out.println("  Warehouse will auto-suspend after 2 minutes of inactivity");

            // ==================== USE WAREHOUSE ====================
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║          USE WAREHOUSE (Context Switching)         ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("=== 10. Switch Between Warehouses ===");
            engine.execute("USE WAREHOUSE dev_wh");
            System.out.println("✓ Using DEV_WH for development queries");
            System.out.println("  Current warehouse: " + engine.getCatalog().getCurrentWarehouse());

            engine.execute("USE WAREHOUSE analytics_wh");
            System.out.println("\n✓ Switched to ANALYTICS_WH for analytical queries");
            System.out.println("  Current warehouse: " + engine.getCatalog().getCurrentWarehouse());

            // ==================== QUERY EXECUTION WITH WAREHOUSES ====================
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║        QUERY EXECUTION WITH WAREHOUSES             ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("=== 11. Execute Queries on Different Warehouses ===");

            // Use existing database or create if needed
            engine.execute("USE DATABASE DEMO_DB");
            engine.execute("USE SCHEMA PUBLIC");

            engine.execute(
                "CREATE TABLE sales (" +
                "sale_id INTEGER, " +
                "product VARCHAR, " +
                "amount INTEGER, " +
                "region VARCHAR" +
                ")"
            );

            engine.execute(
                "INSERT INTO sales VALUES " +
                "(1, 'Widget', 1000, 'East'), " +
                "(2, 'Gadget', 1500, 'West'), " +
                "(3, 'Widget', 1200, 'East'), " +
                "(4, 'Doohickey', 800, 'South')"
            );

            System.out.println("✓ Created sample sales data\n");

            // Use different warehouses for different workloads
            engine.execute("USE WAREHOUSE dev_wh");
            engine.execute("ALTER WAREHOUSE dev_wh RESUME");
            System.out.println("Using DEV_WH for simple query:");
            ResultSet result1 = engine.executeQuery("SELECT * FROM sales WHERE region = 'East'");
            System.out.println("  Found " + result1.getRowCount() + " sales in East region");

            engine.execute("USE WAREHOUSE analytics_wh");
            engine.execute("ALTER WAREHOUSE analytics_wh RESUME");
            System.out.println("\nUsing ANALYTICS_WH for analytical query:");
            ResultSet result2 = engine.executeQuery(
                "SELECT region, COUNT(*), SUM(amount) " +
                "FROM sales " +
                "GROUP BY region " +
                "ORDER BY SUM(amount) DESC"
            );
            System.out.println("  Aggregated sales by region: " + result2.getRowCount() + " regions");

            // ==================== QUERY HISTORY ====================
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║          QUERY EXECUTION TRACKING                  ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("=== 12. Track Query Execution ===");

            // Record some query executions
            Warehouse trackWh = engine.getCatalog().getWarehouse("analytics_wh");
            LocalDateTime now = LocalDateTime.now();

            QueryExecution exec1 = new QueryExecution(
                "query-001",
                "SELECT * FROM sales",
                now,
                now.plusSeconds(2),
                2000,
                4,
                "SUCCESS"
            );
            trackWh.recordQueryExecution(exec1);

            QueryExecution exec2 = new QueryExecution(
                "query-002",
                "SELECT region, SUM(amount) FROM sales GROUP BY region",
                now.plusSeconds(5),
                now.plusSeconds(8),
                3000,
                3,
                "SUCCESS"
            );
            trackWh.recordQueryExecution(exec2);

            System.out.println("Query execution history:");
            System.out.println("  Total queries executed: " + trackWh.getTotalQueriesExecuted());
            System.out.println("  Total credits used: " + trackWh.getTotalCreditsUsed());
            System.out.println("  Query history entries: " + trackWh.getQueryHistory().size());

            // ==================== MULTI-CLUSTER CONFIGURATION ====================
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║        MULTI-CLUSTER WAREHOUSES                    ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("=== 13. Configure Multi-Cluster Scaling ===");
            Warehouse multiClusterWh = engine.getCatalog().getWarehouse("prod_wh");

            multiClusterWh.setMinClusterCount(2);
            multiClusterWh.setMaxClusterCount(10);
            multiClusterWh.setScalingPolicy(ScalingPolicy.STANDARD);

            System.out.println("✓ Configured PROD_WH for multi-cluster:");
            System.out.println("  Min clusters: " + multiClusterWh.getMinClusterCount());
            System.out.println("  Max clusters: " + multiClusterWh.getMaxClusterCount());
            System.out.println("  Scaling policy: " + multiClusterWh.getScalingPolicy());
            System.out.println("\nBenefits:");
            System.out.println("  • Auto-scales based on query load");
            System.out.println("  • Maintains performance during peak times");
            System.out.println("  • Reduces costs during low activity");

            // ==================== WAREHOUSE SUMMARY ====================
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║            ALL WAREHOUSES SUMMARY                  ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("=== 14. List All Warehouses ===");
            System.out.println(String.format("%-20s %-12s %-12s %-10s",
                "Name", "Size", "State", "Credits/Hr"));
            System.out.println("------------------------------------------------------------");

            for (final Warehouse wh : engine.getCatalog().getAllWarehouses()) {
                System.out.println(String.format("%-20s %-12s %-12s %-10d",
                    wh.getName(),
                    wh.getSize().getDisplayName(),
                    wh.getState(),
                    wh.getSize().getCreditsPerHour()
                ));
            }

            // ==================== BEST PRACTICES ====================
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║           WAREHOUSE BEST PRACTICES                 ║");
            System.out.println("╚════════════════════════════════════════════════════╝\n");

            System.out.println("1. RIGHT-SIZING:");
            System.out.println("   • Start small, monitor performance");
            System.out.println("   • Scale up if queries are slow");
            System.out.println("   • Scale down during off-peak hours\n");

            System.out.println("2. AUTO-SUSPEND:");
            System.out.println("   • Set aggressive auto-suspend for dev (60-120s)");
            System.out.println("   • Moderate for prod (300-600s)");
            System.out.println("   • Saves credits during idle periods\n");

            System.out.println("3. DEDICATED WAREHOUSES:");
            System.out.println("   • dev_wh → Development & testing");
            System.out.println("   • prod_wh → Production queries");
            System.out.println("   • analytics_wh → Complex analytics");
            System.out.println("   • etl_wh → Data loading & transformation\n");

            System.out.println("4. MULTI-CLUSTER:");
            System.out.println("   • Use for high-concurrency workloads");
            System.out.println("   • Set appropriate min/max clusters");
            System.out.println("   • Choose STANDARD vs ECONOMY policy\n");

            System.out.println("5. MONITORING:");
            System.out.println("   • Track query execution time");
            System.out.println("   • Monitor credit consumption");
            System.out.println("   • Review warehouse utilization");
            System.out.println("   • Optimize warehouse size based on metrics");
    }

    private void printWarehouseInfo(final Warehouse wh) {
        System.out.println("Warehouse: " + wh.getName());
        System.out.println("  Size: " + wh.getSize().getDisplayName());
        System.out.println("  Servers: " + wh.getSize().getServers());
        System.out.println("  Credits per hour: " + wh.getSize().getCreditsPerHour());
        System.out.println("  State: " + wh.getState());
        System.out.println("  Auto-suspend: " + wh.getAutoSuspendSeconds() + " seconds");
        System.out.println("  Auto-resume: " + wh.isAutoResume());
    }
}
