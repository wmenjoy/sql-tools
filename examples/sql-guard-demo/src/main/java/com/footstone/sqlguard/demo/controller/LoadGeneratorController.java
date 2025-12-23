package com.footstone.sqlguard.demo.controller;

import com.footstone.sqlguard.demo.load.LoadGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * REST Controller for managing the load generator.
 *
 * <p>This controller provides endpoints to start, stop, and monitor the
 * load generator for producing audit logs.</p>
 *
 * <p><strong>Endpoints:</strong></p>
 * <ul>
 *   <li>POST /api/load-generator/start - Start load generator (5 minutes default)</li>
 *   <li>POST /api/load-generator/start/{durationMinutes} - Start with custom duration</li>
 *   <li>POST /api/load-generator/stop - Stop load generator</li>
 *   <li>GET /api/load-generator/status - Get current status and statistics</li>
 * </ul>
 *
 * @see LoadGenerator
 */
@RestController
@RequestMapping("/api/load-generator")
public class LoadGeneratorController {

    private static final Logger log = LoggerFactory.getLogger(LoadGeneratorController.class);

    // Executor for running load generator in background
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Latest statistics from completed run
    private volatile LoadGenerator.LoadStatistics latestStatistics;

    @Autowired
    private LoadGenerator loadGenerator;

    /**
     * Start the load generator with default duration (5 minutes).
     *
     * <p>The load generator runs in the background and produces queries with
     * the following distribution:</p>
     * <ul>
     *   <li>80% Fast queries (&lt;100ms)</li>
     *   <li>15% Slow queries (&gt;1s)</li>
     *   <li>5% Error queries</li>
     * </ul>
     *
     * @return response indicating start status
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start() {
        return startWithDuration(5);
    }

    /**
     * Start the load generator with custom duration.
     *
     * @param durationMinutes duration in minutes
     * @return response indicating start status
     */
    @PostMapping("/start/{durationMinutes}")
    public ResponseEntity<Map<String, Object>> startWithDuration(@PathVariable int durationMinutes) {
        if (loadGenerator.isRunning()) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Load generator is already running",
                "currentStats", loadGenerator.getStatistics()
            ));
        }

        if (durationMinutes < 1 || durationMinutes > 60) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Duration must be between 1 and 60 minutes"
            ));
        }

        long durationMs = durationMinutes * 60L * 1000L;

        log.info("Starting load generator for {} minutes", durationMinutes);

        // Run load generator in background
        executor.submit(() -> {
            try {
                latestStatistics = loadGenerator.runFor(durationMs);
                log.info("Load generator completed: {}", latestStatistics);
            } catch (Exception e) {
                log.error("Load generator failed", e);
            }
        });

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "Load generator started for " + durationMinutes + " minutes");
        response.put("durationMinutes", durationMinutes);
        response.put("expectedQueries", Map.of(
            "total", "~" + (durationMinutes * 60 * 100),
            "fast", "~" + (int)(durationMinutes * 60 * 100 * 0.80) + " (80%)",
            "slow", "~" + (int)(durationMinutes * 60 * 100 * 0.15) + " (15%)",
            "error", "~" + (int)(durationMinutes * 60 * 100 * 0.05) + " (5%)"
        ));
        response.put("monitorEndpoint", "/api/load-generator/status");
        response.put("stopEndpoint", "/api/load-generator/stop");

        return ResponseEntity.ok(response);
    }

    /**
     * Stop the load generator if it's running.
     *
     * @return response indicating stop status
     */
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stop() {
        if (!loadGenerator.isRunning()) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Load generator is not running",
                "latestStatistics", latestStatistics != null ? latestStatistics.toString() : "No data"
            ));
        }

        log.info("Stopping load generator...");
        loadGenerator.stop();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "stopped");
        response.put("message", "Load generator stopped");
        response.put("currentStatistics", loadGenerator.getStatistics());

        return ResponseEntity.ok(response);
    }

    /**
     * Get current status and statistics of the load generator.
     *
     * @return current status and statistics
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> response = new HashMap<>();
        response.put("running", loadGenerator.isRunning());
        
        if (loadGenerator.isRunning()) {
            LoadGenerator.LoadStatistics currentStats = loadGenerator.getStatistics();
            response.put("status", "running");
            response.put("currentStatistics", Map.of(
                "totalQueries", currentStats.getTotalQueries(),
                "fastQueries", currentStats.getFastQueryCount(),
                "fastQueryPercent", String.format("%.1f%%", currentStats.getFastQueryPercent()),
                "slowQueries", currentStats.getSlowQueryCount(),
                "slowQueryPercent", String.format("%.1f%%", currentStats.getSlowQueryPercent()),
                "errorQueries", currentStats.getErrorQueryCount(),
                "errorQueryPercent", String.format("%.1f%%", currentStats.getErrorQueryPercent()),
                "averageExecutionTimeMs", String.format("%.2f", currentStats.getAverageExecutionTimeMs())
            ));
        } else {
            response.put("status", "idle");
            if (latestStatistics != null) {
                response.put("latestStatistics", Map.of(
                    "totalQueries", latestStatistics.getTotalQueries(),
                    "fastQueries", latestStatistics.getFastQueryCount(),
                    "fastQueryPercent", String.format("%.1f%%", latestStatistics.getFastQueryPercent()),
                    "slowQueries", latestStatistics.getSlowQueryCount(),
                    "slowQueryPercent", String.format("%.1f%%", latestStatistics.getSlowQueryPercent()),
                    "errorQueries", latestStatistics.getErrorQueryCount(),
                    "errorQueryPercent", String.format("%.1f%%", latestStatistics.getErrorQueryPercent()),
                    "actualDurationMs", latestStatistics.getActualDurationMs(),
                    "averageExecutionTimeMs", String.format("%.2f", latestStatistics.getAverageExecutionTimeMs())
                ));
            } else {
                response.put("message", "No load generation run completed yet");
            }
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Get information about load generator endpoints.
     *
     * @return API documentation
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> response = new HashMap<>();
        response.put("description", "Load Generator API for SQL Audit Platform Demo");
        response.put("endpoints", Map.of(
            "POST /api/load-generator/start", "Start load generator (5 minutes)",
            "POST /api/load-generator/start/{minutes}", "Start with custom duration (1-60 minutes)",
            "POST /api/load-generator/stop", "Stop running load generator",
            "GET /api/load-generator/status", "Get current status and statistics"
        ));
        response.put("distribution", Map.of(
            "fast", "80% - Fast queries (<100ms)",
            "slow", "15% - Slow/deep pagination queries",
            "error", "5% - Error queries"
        ));
        response.put("targetQPS", 100);
        response.put("running", loadGenerator.isRunning());

        return ResponseEntity.ok(response);
    }
}

