package com.life1000;

import com.fasterxml.jackson.databind.*;
import com.life1000.goal.*;
import com.life1000.entity.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real mysql profile, committed transactions and actual temporary disk files. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named="DB_USERNAME", matches=".+")
class Phase3MysqlIntegrationTest {
    @TempDir static Path directory;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired LifeGoalService goals;
    @Autowired GoalDetailService details;
    @Autowired LocalFiles files;
    String token;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        TestCredentials.configure(properties);
        properties.add("life1000.upload-directory",()->directory.toString());
    }
    JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var response=mvc.perform(request.header("Authorization",token)).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsByteArray().length==0?json.nullNode():json.readTree(response.getContentAsByteArray());
    }
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(value));
    }
    @Test void detailRecordsAttachmentsCoverAndDeletionPersistAcrossRequests() throws Exception {
        for(int slot:new int[]{998,999})
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_goal WHERE slot_no=?",Integer.class,slot))
                    .as("Use empty test slots 998 and 999; existing goals are never cleared").isZero();
        token="Bearer "+json.readTree(mvc.perform(body(post("/api/auth/login"),Map.of(
                "username",TestCredentials.USERNAME,"password",TestCredentials.PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("accessToken").asText();
        boolean firstCreated=false, secondCreated=false;
        Long category=null;
        try {
            category=call(body(post("/api/categories"),Map.of("name","Phase 3 test","sortOrder",0)),201).get("id").asLong();
            call(body(post("/api/goals/998"),Map.of("title","Phase 3 test")),201); firstCreated=true;
            call(body(post("/api/goals/999"),Map.of("title","Another test")),201); secondCreated=true;
            call(body(put("/api/goals/998"),Map.of("title","修改的标题","categoryId",category,
                    "reason","较长的缘由\n第二段","status","IN_PROGRESS")),200);
            var goal=call(get("/api/goals/998"),200);
            assertThat(goal.get("reason").asText()).contains("第二段");
            assertThat(goal.get("categoryId").asLong()).isEqualTo(category);
            long goalId=goal.get("id").asLong();
            long check=call(body(post("/api/goals/998/check-items"),Map.of("content","第一条","completed",false)),201).get("id").asLong();
            long second=call(body(post("/api/goals/998/check-items"),Map.of("content","第二条","completed",false)),201).get("id").asLong();
            call(body(put("/api/check-items/"+check),Map.of("content","修改条件","completed",true)),200);
            assertThat(call(get("/api/goals/998/check-items"),200).get(0).get("completed").asBoolean()).isTrue();
            call(body(put("/api/check-items/"+check),Map.of("content","修改条件","completed",false)),200);
            call(delete("/api/check-items/"+check),204);
            assertThat(call(get("/api/goals/998/check-items"),200).get(0).get("id").asLong()).isEqualTo(second);
            long record=call(body(post("/api/goals/998/records"),Map.of("recordDate","2026-09-01","content","最初的念头")),201).get("id").asLong();
            call(body(put("/api/records/"+record),Map.of("recordDate","2026-09-02","content","研究路线")),200);
            assertThat(call(get("/api/goals/998/records"),200).get(0).get("content").asText()).isEqualTo("研究路线");
            var bytes=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
            var photo=new MockMultipartFile("file","../影像.png","image/png",bytes.toByteArray());
            var first=call(multipart("/api/goals/998/attachments").file(photo).param("stage","GENERAL"),201);
            var latest=call(multipart("/api/goals/998/attachments").file(photo).param("stage","PROCESS").param("recordId",String.valueOf(record)),201);
            long imageId=first.get("id").asLong(), recentId=latest.get("id").asLong();
            var document=call(multipart("/api/goals/998/attachments").file(new MockMultipartFile(
                    "file","行程.txt","text/plain","route document".getBytes())).param("stage","GENERAL"),201);
            long documentId=document.get("id").asLong();
            Path firstPath=files.resolve(first.get("filePath").asText()), recentPath=files.resolve(latest.get("filePath").asText());
            Path documentPath=files.resolve(document.get("filePath").asText());
            assertThat(firstPath).exists(); assertThat(recentPath).exists(); assertThat(documentPath).exists();
            assertThat(call(get("/api/goals/998/attachments"),200).size()).isEqualTo(3);
            assertThat(call(get("/api/goals/998/cover"),200).get("id").asLong()).isEqualTo(recentId);
            call(put("/api/goals/998/cover/"+imageId),204);
            assertThat(call(get("/api/goals/998/cover"),200).get("id").asLong()).isEqualTo(imageId);
            call(put("/api/goals/999/cover/"+imageId),400);
            call(put("/api/goals/998/cover/"+documentId),400);
            call(body(put("/api/attachments/"+imageId+"/home-background"),Map.of("allowed",true)),200);
            assertThat(call(get("/api/goals/998/attachments"),200).get(0).get("allowHomeBackground").asBoolean()).isTrue();
            call(body(put("/api/attachments/"+imageId+"/home-background"),Map.of("allowed",false)),200);
            mvc.perform(get("/api/attachments/"+imageId+"/content").header("Authorization",token))
                    .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(bytes.toByteArray()));
            mvc.perform(get("/api/attachments/"+documentId+"/content?download=true").header("Authorization",token))
                    .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")))
                    .andExpect(content().string("route document"));
            mvc.perform(get("/api/attachments/"+documentId+"/content")).andExpect(status().isUnauthorized());
            call(multipart("/api/goals/999/attachments").file(photo).param("stage","PROCESS").param("recordId",String.valueOf(record)),400);
            call(multipart("/api/goals/998/attachments").file(photo).param("stage","COMPLETION"),400);
            call(delete("/api/attachments/"+imageId),204);
            assertThat(firstPath).doesNotExist();
            assertThat(call(get("/api/goals/998"),200).get("coverAttachmentId").isNull()).isTrue();
            assertThat(call(get("/api/goals/998/cover"),200).get("id").asLong()).isEqualTo(recentId);
            call(put("/api/goals/998/cover/"+recentId),204);
            call(delete("/api/records/"+record),204);
            assertThat(recentPath).doesNotExist();
            assertThat(call(get("/api/goals/998/records"),200).isEmpty()).isTrue();
            call(get("/api/goals/998/cover"),204);
            // Recreate a process attachment, then verify whole-goal cascading cleanup.
            long remainingRecord=call(body(post("/api/goals/998/records"),Map.of("recordDate","2026-09-03","content","后来")),201).get("id").asLong();
            var remaining=call(multipart("/api/goals/998/attachments").file(photo).param("stage","PROCESS")
                    .param("recordId",String.valueOf(remainingRecord)),201);
            Path remainingPath=files.resolve(remaining.get("filePath").asText());
            call(put("/api/goals/998/cover/"+remaining.get("id").asLong()),204);
            call(delete("/api/goals/998"),204); firstCreated=false;
            assertThat(documentPath).doesNotExist(); assertThat(remainingPath).doesNotExist();
            for(String table:new String[]{"goal_check_item","goal_record","goal_attachment"})
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE goal_id=?",Integer.class,goalId)).isZero();
            call(get("/api/goals/998"),404);
        } finally {
            if(firstCreated) goals.delete(998);
            if(secondCreated) goals.delete(999);
            if(category!=null) call(delete("/api/categories/"+category),204);
        }
    }
}
