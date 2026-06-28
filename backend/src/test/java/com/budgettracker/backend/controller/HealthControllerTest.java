package com.budgettracker.backend.controller;

import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.exchange-rate.api-enabled=false")
@AutoConfigureMockMvc
public class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DSLContext dsl;

    @Test
    public void testHealthCheck_AllUp() throws Exception {
        Mockito.when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(1);
        
        var dslSelectStep = Mockito.mock(org.jooq.SelectSelectStep.class);
        var dslWhereStep = Mockito.mock(org.jooq.SelectWhereStep.class);
        Mockito.when(dsl.selectOne()).thenReturn(dslSelectStep);
        Mockito.when(dslSelectStep.fetchOneInto(Integer.class)).thenReturn(1);

        Mockito.when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(Collections.singletonList("accounts"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.databaseJdbc", is("CONNECTED")))
                .andExpect(jsonPath("$.databaseJooq", is("CONNECTED")));
    }

    @Test
    public void testHealthCheck_AllDown() throws Exception {
        Mockito.when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class)))
                .thenThrow(new RuntimeException("JDBC connection failed"));

        Mockito.when(dsl.selectOne())
                .thenThrow(new RuntimeException("JOOQ connection failed"));

        Mockito.when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Flyway check failed"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.databaseJdbc", is("DISCONNECTED")))
                .andExpect(jsonPath("$.errorJdbc", is("JDBC connection failed")))
                .andExpect(jsonPath("$.databaseJooq", is("DISCONNECTED")))
                .andExpect(jsonPath("$.errorJooq", is("JOOQ connection failed")))
                .andExpect(jsonPath("$.tablesError", is("Flyway check failed")));
    }

    @Test
    public void testHealthCheck_NullResponses() throws Exception {
        Mockito.when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(null);

        var dslSelectStep = Mockito.mock(org.jooq.SelectSelectStep.class);
        Mockito.when(dsl.selectOne()).thenReturn(dslSelectStep);
        Mockito.when(dslSelectStep.fetchOneInto(Integer.class)).thenReturn(null);

        Mockito.when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.databaseJdbc", is("UNEXPECTED_RESPONSE")))
                .andExpect(jsonPath("$.databaseJooq", is("UNEXPECTED_RESPONSE")));
    }

    @Test
    public void testHealthCheck_UnexpectedValues() throws Exception {
        Mockito.when(jdbcTemplate.queryForObject(eq("SELECT 1"), eq(Integer.class))).thenReturn(999);

        var dslSelectStep = Mockito.mock(org.jooq.SelectSelectStep.class);
        Mockito.when(dsl.selectOne()).thenReturn(dslSelectStep);
        Mockito.when(dslSelectStep.fetchOneInto(Integer.class)).thenReturn(999);

        Mockito.when(jdbcTemplate.queryForList(anyString(), eq(String.class)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")))
                .andExpect(jsonPath("$.databaseJdbc", is("UNEXPECTED_RESPONSE")))
                .andExpect(jsonPath("$.databaseJooq", is("UNEXPECTED_RESPONSE")));
    }
}
