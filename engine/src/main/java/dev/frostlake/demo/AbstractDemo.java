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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;

/**
 * Abstract base class for demo applications
 * Provides common functionality for engine management, output formatting, and database operations
 */
public abstract class AbstractDemo {

    protected DatabaseEngine engine;

    /**
     * Main entry point - subclasses should implement this to define their demo logic
     */
    protected abstract void runDemo() throws Exception;

    /**
     * Get the demo title to display at the start
     */
    protected abstract String getDemoTitle();

    /**
     * Execute the demo with proper setup and cleanup
     */
    public final void execute() {
        try {
            setup();
            printTitle(getDemoTitle());
            runDemo();
            System.out.println("\n=== Demo Complete ===");
        } catch (final Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    /**
     * Initialize the Frostlake engine
     */
    protected void setup() {
        engine = new DatabaseEngine();
    }

    /**
     * Shutdown the engine and release resources
     */
    protected void shutdown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    /**
     * Setup a database - create if doesn't exist and use it
     */
    protected void setupDatabase(final String databaseName) {
        try {
            engine.execute("CREATE DATABASE " + databaseName);
        } catch (final RuntimeException e) {
            // Database might already exist, that's OK
        }
        engine.execute("USE DATABASE " + databaseName);
        engine.execute("USE SCHEMA PUBLIC");
    }

    /**
     * Print demo title with formatting
     */
    protected void printTitle(final String title) {
        System.out.println("=== " + title + " ===\n");
    }

    /**
     * Print a section header with box formatting
     */
    protected void printSectionHeader(final String title) {
        int width = Math.max(title.length() + 4, 50);
        String border = "╔" + "═".repeat(width) + "╗";
        int padding = (width - title.length()) / 2;
        String line = "║" + " ".repeat(padding) + title + " ".repeat(width - padding - title.length()) + "║";

        System.out.println("\n" + border);
        System.out.println(line);
        System.out.println("╚" + "═".repeat(width) + "╝\n");
    }

    /**
     * Print a subsection header
     */
    protected void printSubsection(final String title) {
        System.out.println("\n--- " + title + " ---");
    }

    /**
     * Print an info line with a checkmark
     */
    protected void printSuccess(final String message) {
        System.out.println("✓ " + message);
    }

    /**
     * Query a table and print its contents
     */
    protected void printTable(final String tableName) {
        ResultSet result = engine.executeQuery("SELECT * FROM " + tableName);
        printResultSet(result);
    }

    /**
     * Execute a query and print its results
     */
    protected void printQuery(final String sql) {
        ResultSet result = engine.executeQuery(sql);
        printResultSet(result);
    }

    /**
     * Print a result set with formatted output
     * Subclasses can override for custom formatting
     */
    protected void printResultSet(final ResultSet result) {
        printResultSet(result, 20); // Default column width
    }

    /**
     * Print a result set with specified column width
     */
    protected void printResultSet(final ResultSet result, final int columnWidth) {
        if (result.getRowCount() == 0) {
            System.out.println("   (No rows)");
            return;
        }

        // Print column headers
        System.out.print("   ");
        for (final ResultSetColumn col : result.getColumns()) {
            System.out.printf("%-" + columnWidth + "s", col.getName());
        }
        System.out.println();

        // Print separator
        System.out.print("   ");
        for (int i = 0; i < result.getColumnCount(); i++) {
            System.out.print("-".repeat(columnWidth));
        }
        System.out.println();

        // Print rows
        result.reset();
        int displayCount = 0;
        int maxDisplayRows = getMaxDisplayRows();

        while (result.next() && displayCount < maxDisplayRows) {
            System.out.print("   ");
            for (int i = 0; i < result.getColumnCount(); i++) {
                Object value = result.getValue(i);
                String displayValue = value != null ? value.toString() : "NULL";

                // Truncate long values
                if (displayValue.length() > columnWidth - 2) {
                    displayValue = displayValue.substring(0, columnWidth - 5) + "...";
                }

                System.out.printf("%-" + columnWidth + "s", displayValue);
            }
            System.out.println();
            displayCount++;
        }

        // Show row count
        if (result.getRowCount() > maxDisplayRows) {
            System.out.println("   ... (" + (result.getRowCount() - maxDisplayRows) + " more rows)");
        }
        System.out.println("   (" + result.getRowCount() + " rows)");
    }

    /**
     * Get maximum number of rows to display in result sets
     * Subclasses can override to show more or fewer rows
     */
    protected int getMaxDisplayRows() {
        return 100; // Default: show up to 100 rows
    }

    /**
     * Get the row count from a table
     */
    protected int getRowCount(final String tableName) {
        ResultSet result = engine.executeQuery("SELECT * FROM " + tableName);
        return result.getRowCount();
    }

    /**
     * Print a horizontal line separator
     */
    protected void printSeparator() {
        System.out.println();
    }
}
