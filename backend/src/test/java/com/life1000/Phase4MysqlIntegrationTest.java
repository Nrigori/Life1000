package com.life1000;

import com.fasterxml.jackson.databind.*;
import com.life1000.goal.*;
import com.life1000.entity.*;
import java.nio.file.*;
import java.io.ByteArrayOutputStream;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.util.*;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataAccessException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named="DB_USERNAME", matches=".+")
class Phase4MysqlIntegrationTest {
    @TempDir static Path directory;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired LifeGoalService goals;
    @Autowired CompletionService completions;
    @Autowired GoalDetailService details;
    @Autowired PlatformTransactionManager transactions;
    @Autowired LocalFiles files;
    String token;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        TestCredentials.configure(properties);
        properties.add("life1000.upload-directory", () -> directory.toString());
    }
    JsonNode call(MockHttpServletRequestBuilder request, int expected) throws Exception {
        var response=mvc.perform(request.header("Authorization",token)).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsByteArray().length==0?json.nullNode():json.readTree(response.getContentAsByteArray());
    }
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object input) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input));
    }
    @Test void completionUndoRecompletionTimelineAndFilesRemainConsistent() throws Exception {
        for (int slot:new int[]{996,997}) assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_goal WHERE slot_no=?",Integer.class,slot))
                .as("Use empty test slots 996 and 997; existing data is never deleted").isZero();
        token="Bearer "+json.readTree(mvc.perform(body(post("/api/auth/login"),Map.of("username",TestCredentials.USERNAME,"password",TestCredentials.PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("accessToken").asText();
        boolean first=false,second=false;
        try {
            goals.create(996,new GoalCreateRequest("Phase 4 first",null,null)); first=true;
            goals.create(997,new GoalCreateRequest("Phase 4 second",null,null)); second=true;
            goals.updateStatus(996,GoalStatus.IN_PROGRESS);
            call(body(post("/api/goals/996/complete"),Map.of()),400);
            for (int rating:new int[]{0,6}) call(body(post("/api/goals/996/complete"),Map.of("completedDate","2028-06-17","rating",rating)),400);
            var buffer=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",buffer);
            var image=new MockMultipartFile("files","proof.png","image/png",buffer.toByteArray());
            var document=new MockMultipartFile("files","proof.txt","text/plain","proof".getBytes());
            var input=new MockMultipartFile("completion","","application/json",json.writeValueAsBytes(Map.of("completedDate","2028-06-17")));
            var archive=call(multipart("/api/goals/996/complete").file(input).file(image).file(document),200);
            long archiveId=archive.get("id").asLong(), goalId=archive.get("goalId").asLong();
            assertThat(archive.get("rating").isNull()).isTrue(); assertThat(archive.get("completionNote").isNull()).isTrue();
            assertThat(archive.get("statusBeforeCompletion").asText()).isEqualTo("IN_PROGRESS");
            assertThat(goals.get(996).getStatus()).isEqualTo(GoalStatus.COMPLETED);
            assertThat(goals.get(996).getCompletedDate()).isEqualTo(LocalDate.of(2028,6,17));
            var attachments=details.attachments(996);
            assertThat(attachments).hasSize(2).allSatisfy(file -> {
                assertThat(file.getStage()).isEqualTo("COMPLETION"); assertThat(file.getRecordId()).isNull();
                assertThat(file.getGoalId()).isEqualTo(goalId);
            });
            Path imagePath=files.resolve(attachments.get(0).getFilePath()), documentPath=files.resolve(attachments.get(1).getFilePath());
            assertThat(imagePath).exists(); assertThat(documentPath).exists();
            long imageId=attachments.get(0).getId(), documentId=attachments.get(1).getId();
            assertThat(details.cover(996).getId()).isEqualTo(imageId);
            details.setCover(996,imageId); details.homeBackground(imageId,true);
            mvc.perform(get("/api/attachments/"+imageId+"/content").header("Authorization",token)).andExpect(status().isOk()).andExpect(content().bytes(buffer.toByteArray()));
            mvc.perform(get("/api/attachments/"+documentId+"/content?download=true").header("Authorization",token)).andExpect(status().isOk()).andExpect(content().string("proof"));
            call(post("/api/goals/996/uncomplete"),204);
            assertThat(goals.get(996).getStatus()).isEqualTo(GoalStatus.IN_PROGRESS);
            assertThat(goals.get(996).getCompletedDate()).isNull();
            assertThat(completions.get(996).getId()).isEqualTo(archiveId);
            assertThat(details.attachments(996)).hasSize(2);
            assertThat(imagePath).exists(); assertThat(documentPath).exists();
            assertThat(call(get("/api/timeline/2028"),200).toString()).doesNotContain("Phase 4 first");
            call(body(post("/api/goals/996/complete"),Map.of("completedDate","2028-06-17","completionNote","留作回忆","rating",5)),200);
            call(body(post("/api/goals/996/complete"),Map.of("completedDate","2028-06-17")),409);
            assertThat(completions.get(996).getId()).isEqualTo(archiveId);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goal_completion WHERE goal_id=?",Integer.class,goalId)).isEqualTo(1);
            call(body(post("/api/goals/997/complete"),Map.of("completedDate","2028-06-17")),200);
            var entries=call(get("/api/timeline/2028"),200);
            var testSlots=new ArrayList<Integer>();
            for(var entry:entries) if(entry.get("slotNo").asInt()>=996 && entry.get("slotNo").asInt()<=997) testSlots.add(entry.get("slotNo").asInt());
            assertThat(testSlots).containsExactly(996,997);
            for(var year:call(get("/api/timeline"),200))
                assertThat(year.get("count").asInt()).isEqualTo(call(get("/api/timeline/"+year.get("year").asInt()),200).size());
            call(body(put("/api/goals/996/completion"),Map.of("completedDate","2029-01-02")),200);
            assertThat(completions.get(996).getRating()).isNull();
            assertThat(completions.get(996).getCompletionNote()).isNull();
            assertThat(call(get("/api/timeline/2028"),200).toString()).doesNotContain("Phase 4 first");
            assertThat(call(get("/api/timeline/2029"),200).toString()).contains("Phase 4 first");
            // Completed goals still support basic editing and process/general records.
            goals.update(996,new GoalUpdateRequest("Phase 4 edited",null,"文字仍可编辑",null));
            details.addRecord(996,new GoalDetailService.RecordInput(LocalDate.now(),"继续记录"));
            call(delete("/api/attachments/"+imageId),204);
            assertThat(imagePath).doesNotExist(); assertThat(goals.get(996).getCoverAttachmentId()).isNull();
            call(delete("/api/goals/996"),204); first=false;
            assertThat(documentPath).doesNotExist();
            assertThat(call(get("/api/timeline/2029"),200).toString()).doesNotContain("Phase 4 edited");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goal_completion WHERE goal_id=?",Integer.class,goalId)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goal_attachment WHERE goal_id=?",Integer.class,goalId)).isZero();
            // Force a real DB constraint failure after the completion and file writes, before commit.
            call(post("/api/goals/997/uncomplete"),204);
            var prior=completions.get(997);
            var paths=new ArrayList<Path>();
            assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                try {
                    completions.save(997,new CompletionService.Input(LocalDate.of(2030,1,1),"must roll back",4),List.of(image),false);
                    for(var file:details.attachments(997)) paths.add(files.resolve(file.getFilePath()));
                    jdbc.update("UPDATE life_goal SET slot_no=0 WHERE slot_no=997");
                } catch(java.io.IOException failure) { throw new RuntimeException(failure); }
            })).isInstanceOf(DataAccessException.class);
            assertThat(goals.get(997).getStatus()).isEqualTo(GoalStatus.NOT_STARTED);
            assertThat(completions.get(997).getCompletedDate()).isEqualTo(prior.getCompletedDate());
            assertThat(details.attachments(997)).isEmpty();
            paths.forEach(path -> assertThat(path).doesNotExist());
            call(delete("/api/goals/997"),204); second=false;
        } finally {
            if(first) goals.delete(996);
            if(second) goals.delete(997);
        }
    }
}
