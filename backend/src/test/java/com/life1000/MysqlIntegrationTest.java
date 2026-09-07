package com.life1000;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real MySQL only. Each test rolls back its data; Flyway DDL remains.
 * Set DB_USERNAME / DB_PASSWORD / DB_URL before running Maven.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named = "DB_USERNAME", matches = ".+")
@Transactional
class MysqlIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) { TestCredentials.configure(registry); }

    private String login() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "username", TestCredentials.USERNAME, "password", TestCredentials.PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    private void requireBlank(int slot) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_goal WHERE slot_no = ?", Integer.class, slot))
                .as("编号 %s 必须为空白；测试不会清空已有事项，请使用空测试数据库", slot).isZero();
    }

    private JsonNode createCategory(String token, String name, int sortOrder) throws Exception {
        return json.readTree(mvc.perform(post("/api/categories").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                                Map.of("name", name, "sortOrder", sortOrder))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void slot001CreateReadUpdateDeleteReturnsToBlank() throws Exception {
        requireBlank(1);
        requireBlank(2);
        String token = login();
        mvc.perform(get("/api/health/db").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/goals/001").header("Authorization", token)).andExpect(status().isNotFound());
        long categoryId = createCategory(token, "Phase 1 测试分类", 5).get("id").asLong();

        mvc.perform(post("/api/goals/001").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", "独自看一次日出", "categoryId", categoryId,
                                "reason", "留下一段经历"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.slotNo").value(1))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        String first = mvc.perform(get("/api/goals/001").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("独自看一次日出"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(first).get("id").asLong();
        mvc.perform(post("/api/goals/2").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"相邻编号保持原样\"}")).andExpect(status().isCreated());

        mvc.perform(put("/api/goals/001").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"去海边看一次日出\",\"categoryId\":null,\"reason\":null,\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.slotNo").value(1)).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.categoryId").isEmpty()).andExpect(jsonPath("$.reason").isEmpty());

        mvc.perform(get("/api/goals/001").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("去海边看一次日出"));
        mvc.perform(delete("/api/goals/001").header("Authorization", token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/goals/001").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/goals/range?fromSlot=1&toSlot=1").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_goal WHERE slot_no = 1", Integer.class)).isZero();
        mvc.perform(get("/api/goals/2").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.slotNo").value(2));
        mvc.perform(post("/api/goals/001").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"同一编号可以重新写下\"}")).andExpect(status().isCreated());
        mvc.perform(delete("/api/goals/001").header("Authorization", token)).andExpect(status().isNoContent());
    }

    @Test
    void databaseAndApiEnforceSlotConstraints() throws Exception {
        requireBlank(1);
        requireBlank(1000);
        String token = login();
        for (int slot : new int[]{0, 1001}) {
            mvc.perform(post("/api/goals/" + slot).header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"invalid\"}"))
                    .andExpect(status().isBadRequest());
            // MySQL CHECK errors may be translated as UncategorizedSQLException (3819 / HY000).
            assertThatThrownBy(() -> jdbc.update("INSERT INTO life_goal(slot_no, title) VALUES (?, 'invalid')", slot))
                    .isInstanceOf(DataAccessException.class)
                    .rootCause()
                    .hasMessageContaining("Check constraint 'ck_life_goal_slot' is violated");
        }
        mvc.perform(post("/api/goals/1000").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"上边界\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.slotNo").value(1000));
        mvc.perform(post("/api/goals/1").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"first\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/goals/001").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"duplicate\"}"))
                .andExpect(status().isConflict());
        assertThatThrownBy(() -> jdbc.update("INSERT INTO life_goal(slot_no, title) VALUES (1, 'duplicate')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        mvc.perform(get("/api/goals/1").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("first"));
    }

    @Test
    void categoryCrudPreservesGoalAndSearchPreservesSlotNumber() throws Exception {
        requireBlank(1);
        String token = login();
        long categoryId = createCategory(token, "测试分类", 10).get("id").asLong();
        mvc.perform(put("/api/categories/" + categoryId).header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"旅行\",\"sortOrder\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("旅行"))
                .andExpect(jsonPath("$.sortOrder").value(2));
        mvc.perform(get("/api/categories").header("Authorization", token)).andExpect(status().isOk());
        mvc.perform(post("/api/goals/1").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                                Map.of("title", "旅行_100%", "categoryId", categoryId))))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/goals/search").param("keyword", "_100%").param("categoryId", "" + categoryId)
                        .header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].slotNo").value(1));
        mvc.perform(delete("/api/categories/" + categoryId).header("Authorization", token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/goals/1").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.categoryId").isEmpty());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM category WHERE id = ?", Integer.class, categoryId)).isZero();
        mvc.perform(delete("/api/categories/" + categoryId).header("Authorization", token))
                .andExpect(status().isNotFound());
    }
}
