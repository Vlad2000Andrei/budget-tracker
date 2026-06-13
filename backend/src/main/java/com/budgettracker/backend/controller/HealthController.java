package com.budgettracker.backend.controller;

import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final DSLContext dsl;

    @Autowired
    public HealthController(JdbcTemplate jdbcTemplate, DSLContext dsl) {
        this.jdbcTemplate = jdbcTemplate;
        this.dsl = dsl;
    }

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        
        try {
            // Query the database via JDBC to verify active connection
            Integer dbResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (dbResult != null && dbResult == 1) {
                response.put("databaseJdbc", "CONNECTED");
            } else {
                response.put("databaseJdbc", "UNEXPECTED_RESPONSE");
            }
        } catch (Exception e) {
            response.put("databaseJdbc", "DISCONNECTED");
            response.put("errorJdbc", e.getMessage());
        }

        try {
            // Query the database via JOOQ to verify connection and configuration
            Integer jooqResult = dsl.selectOne().fetchOneInto(Integer.class);
            if (jooqResult != null && jooqResult == 1) {
                response.put("databaseJooq", "CONNECTED");
            } else {
                response.put("databaseJooq", "UNEXPECTED_RESPONSE");
            }
        } catch (Exception e) {
            response.put("databaseJooq", "DISCONNECTED");
            response.put("errorJooq", e.getMessage());
        }

        try {
            // Fetch names of created tables to verify Flyway migrations
            var tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE LOWER(table_schema)='public'", 
                String.class
            );
            response.put("tables", tables);
        } catch (Exception e) {
            response.put("tablesError", e.getMessage());
        }
        
        return response;
    }
}
