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

package dev.frostlake.examples;

import dev.frostlake.DatabaseEngine;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IntervalExampleTest {
    private static final Logger logger = LoggerFactory.getLogger(IntervalExampleTest.class);

    @Test
    public void demonstrateIntervalExpressions() {
        final DatabaseEngine engine = new DatabaseEngine();

        try {
            engine.execute("CREATE DATABASE demo_db");
            engine.execute("USE DATABASE demo_db");

            logger.info("=== INTERVAL Expressions ===");

            // Example 1: Basic INTERVAL with string value
            logger.info("\n1. Basic INTERVAL with string value:");
            logger.info("SELECT INTERVAL '10' DAYS");
            logger.info("Returns: INTERVAL 10 DAYS");
            logger.info("Syntax: INTERVAL '<value>' <UNIT>");
            logger.info("The value is enclosed in single quotes");

            // Example 2: INTERVAL with numeric value
            logger.info("\n2. INTERVAL with numeric value:");
            logger.info("SELECT INTERVAL 5 HOURS");
            logger.info("Returns: INTERVAL 5 HOURS");
            logger.info("The value can also be a number without quotes");

            // Example 3: Different time units - DAYS
            logger.info("\n3. Time unit: DAYS (or DAY for singular):");
            logger.info("INTERVAL '7' DAYS - 7 days");
            logger.info("INTERVAL '1' DAY - 1 day (singular form)");
            logger.info("Used for day-level durations");

            // Example 4: Different time units - HOURS
            logger.info("\n4. Time unit: HOURS (or HOUR for singular):");
            logger.info("INTERVAL '24' HOURS - 24 hours");
            logger.info("INTERVAL '1' HOUR - 1 hour");
            logger.info("Used for hour-level durations");

            // Example 5: Different time units - MINUTES
            logger.info("\n5. Time unit: MINUTES (or MINUTE for singular):");
            logger.info("INTERVAL '30' MINUTES - 30 minutes");
            logger.info("INTERVAL '1' MINUTE - 1 minute");
            logger.info("Used for minute-level durations");

            // Example 6: Different time units - SECONDS
            logger.info("\n6. Time unit: SECONDS (or SECOND for singular):");
            logger.info("INTERVAL '45' SECONDS - 45 seconds");
            logger.info("INTERVAL '1' SECOND - 1 second");
            logger.info("Used for second-level durations");

            // Example 7: Different time units - MONTHS
            logger.info("\n7. Time unit: MONTHS (or MONTH for singular):");
            logger.info("INTERVAL '3' MONTHS - 3 months");
            logger.info("INTERVAL '1' MONTH - 1 month");
            logger.info("Used for month-level durations");

            // Example 8: Different time units - YEARS
            logger.info("\n8. Time unit: YEARS (or YEAR for singular):");
            logger.info("INTERVAL '2' YEARS - 2 years");
            logger.info("INTERVAL '1' YEAR - 1 year");
            logger.info("Used for year-level durations");

            // Example 9: INTERVAL in table queries
            logger.info("\n9. Using INTERVAL with table data:");
            engine.execute("CREATE TABLE work_items (id INTEGER, task_name STRING, duration_hours INTEGER)");
            engine.execute("INSERT INTO work_items VALUES (1, 'Development', 40)");
            engine.execute("INSERT INTO work_items VALUES (2, 'Testing', 20)");
            engine.execute("INSERT INTO work_items VALUES (3, 'Deployment', 5)");

            logger.info("Created table: work_items(id, task_name, duration_hours)");
            logger.info("Query: SELECT task_name, INTERVAL duration_hours HOURS FROM work_items");
            logger.info("Combines column values with INTERVAL expressions");
            logger.info("Result shows task names with their duration as intervals");

            // Example 10: INTERVAL with expressions
            logger.info("\n10. INTERVAL with computed values:");
            engine.execute("CREATE TABLE events (id INTEGER, start_day INTEGER, end_day INTEGER)");
            engine.execute("INSERT INTO events VALUES (1, 1, 5)");
            engine.execute("INSERT INTO events VALUES (2, 10, 15)");

            logger.info("Created table: events(id, start_day, end_day)");
            logger.info("Can compute intervals: INTERVAL (end_day - start_day) DAYS");
            logger.info("This would calculate the duration between start and end");

            logger.info("\n=== Summary ===");
            logger.info("INTERVAL expressions provide:");
            logger.info("  - Time and date duration representation");
            logger.info("  - Support for multiple units:");
            logger.info("    * YEAR/YEARS - yearly durations");
            logger.info("    * MONTH/MONTHS - monthly durations");
            logger.info("    * DAY/DAYS - daily durations");
            logger.info("    * HOUR/HOURS - hourly durations");
            logger.info("    * MINUTE/MINUTES - minute-level durations");
            logger.info("    * SECOND/SECONDS - second-level durations");
            logger.info("  - Both singular and plural forms accepted");
            logger.info("  - Values can be strings or numeric expressions");
            logger.info("  - Can be used with column values");
            logger.info("  - Foundation for date/time arithmetic operations");
            logger.info("");
            logger.info("Common use cases:");
            logger.info("  - Task duration representation");
            logger.info("  - Date arithmetic (future feature)");
            logger.info("  - Time-based calculations");
            logger.info("  - Scheduling and planning queries");

        } finally {
            engine.shutdown();
        }
    }
}
