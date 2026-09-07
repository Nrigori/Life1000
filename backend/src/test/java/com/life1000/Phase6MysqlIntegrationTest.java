package com.life1000;
import com.fasterxml.jackson.databind.*;
import com.life1000.goal.*;
import com.life1000.entity.*;
import com.life1000.settings.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.zip.ZipInputStream;
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
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named="DB_USERNAME",matches=".+")
@Transactional
class Phase6MysqlIntegrationTest {
    @TempDir static Path directory;
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired GoalDetailService details; @Autowired LocalFiles files;
    @Autowired org.mybatis.spring.SqlSessionTemplate session;
    String token;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        TestCredentials.configure(properties);properties.add("life1000.upload-directory",()->directory.toString());
    }
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object data) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data));
    }
    JsonNode call(MockHttpServletRequestBuilder request,int statusCode) throws Exception {
        // One rollback test transaction spans multiple simulated HTTP requests; clear per-request MyBatis cache.
        session.clearCache();
        var response=mvc.perform(request.header("Authorization",token)).andExpect(status().is(statusCode)).andReturn().getResponse();
        return response.getContentAsByteArray().length==0?json.nullNode():json.readTree(response.getContentAsByteArray());
    }
    void fixed(GoalAttachment image) throws Exception {call(body(put("/api/settings"),Map.of("HOME_FIXED_BACKGROUND_PATH",image.getFilePath(),"HOME_BACKGROUND_MODE","FIXED")),200);}
    @Test void realSettingsCategoryExportAndAllFixedImageDeletionPaths() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).as("Phase 6 requires the dedicated life1000_test database").isEqualTo("life1000_test");
        for(int slot:new int[]{990,991})assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_goal WHERE slot_no=?",Integer.class,slot)).as("Test slots must be blank").isZero();
        token="Bearer "+json.readTree(mvc.perform(body(post("/api/auth/login"),Map.of("username",TestCredentials.USERNAME,"password",TestCredentials.PASSWORD)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("accessToken").asText();
        mvc.perform(get("/api/backup/export")).andExpect(status().isUnauthorized());
        call(get("/api/settings"),200);
        call(body(put("/api/settings"),Map.of("JWT_SECRET","not-allowed")),400);
        call(body(put("/api/settings"),Map.of("HOME_BACKGROUND_MODE","OTHER")),400);
        call(body(put("/api/settings"),Map.of("HOME_BACKGROUND_MODE","RANDOM")),200);
        assertThat(call(get("/api/settings"),200).get("mode").asText()).isEqualTo("RANDOM");
        long category=call(body(post("/api/categories"),Map.of("name","Phase6 旅行","sortOrder",-900)),201).get("id").asLong();
        long second=call(body(post("/api/categories"),Map.of("name","Phase6 阅读","sortOrder",-900)),201).get("id").asLong();
        var cats=call(get("/api/categories"),200);
        var sameOrder=new ArrayList<Long>();cats.forEach(c->{if(c.get("sortOrder").asInt()==-900)sameOrder.add(c.get("id").asLong());});
        assertThat(sameOrder).containsSubsequence(category,second);
        call(body(put("/api/categories/"+second),Map.of("name","Phase6 书页","sortOrder",-901)),200);
        long goal=call(body(post("/api/goals/990"),Map.of("title","Phase6 记录册","categoryId",category)),201).get("id").asLong();
        call(body(post("/api/goals/991"),Map.of("title","Phase6 同名附件")),201);
        call(delete("/api/categories/"+category),204);
        assertThat(call(get("/api/goals/990"),200).get("categoryId").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_goal WHERE id=?",Integer.class,goal)).isEqualTo(1);
        call(body(post("/api/goals/990/check-items"),Map.of("content","保留记录","completed",false)),201);
        long record=call(body(post("/api/goals/990/records"),Map.of("recordDate",LocalDate.now().toString(),"content","记下一段经历")),201).get("id").asLong();
        call(body(post("/api/goals/990/complete"),Map.of("completedDate",LocalDate.now().toString(),"completionNote","留下档案")),200);
        call(body(post("/api/quotes"),Map.of("content","Phase6 测试金句")),201);
        var buffer=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",buffer);
        var photo=new MockMultipartFile("file","same.png","image/png",buffer.toByteArray());
        var general=details.upload(990,null,"GENERAL",photo);
        var process=details.upload(990,record,"PROCESS",photo);
        var completion=details.upload(990,null,"COMPLETION",photo);
        var duplicate=details.upload(991,null,"GENERAL",photo);
        var doc=details.upload(991,null,"GENERAL",new MockMultipartFile("file","same.txt","text/plain","document".getBytes()));
        var missing=details.upload(991,null,"GENERAL",new MockMultipartFile("file","lost.txt","text/plain","missing".getBytes()));
        call(body(put("/api/settings"),Map.of("HOME_FIXED_BACKGROUND_PATH",doc.getFilePath())),400);
        call(body(put("/api/settings"),Map.of("HOME_FIXED_BACKGROUND_PATH","../../secret")),400);
        fixed(general);
        assertThat(call(get("/api/settings"),200).get("fixedImage").get("attachmentId").asLong()).isEqualTo(general.getId());
        assertThat(call(get("/api/home/background"),200).get("id").asLong()).isEqualTo(general.getId());
        call(body(put("/api/attachments/"+general.getId()+"/home-background"),Map.of("allowed",true)),200);
        assertThat(call(get("/api/settings/images"),200).findValues("attachmentId")).extracting(JsonNode::asLong).contains(general.getId(),process.getId(),completion.getId(),duplicate.getId()).doesNotContain(doc.getId());
        assertThat(jdbc.queryForObject("SELECT allow_home_background FROM goal_attachment WHERE id=?",Boolean.class,general.getId())).isTrue();
        var usage=call(get("/api/settings"),200).get("files");
        assertThat(usage.get("totalBytes").asLong()).isEqualTo(jdbc.queryForObject("SELECT COALESCE(SUM(file_size),0) FROM goal_attachment",Long.class));
        assertThat(usage.get("imageCount").asLong()).isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM goal_attachment WHERE is_image=TRUE",Long.class));
        assertThat(usage.get("documentCount").asLong()).isEqualTo(jdbc.queryForObject("SELECT COUNT(*) FROM goal_attachment WHERE is_image=FALSE",Long.class));
        Files.delete(files.resolve(missing.getFilePath()));
        var response=mvc.perform(get("/api/backup/export").header("Authorization",token)).andExpect(status().isOk()).andExpect(content().contentType("application/zip")).andReturn().getResponse();
        Map<String,byte[]> entries=new LinkedHashMap<>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(response.getContentAsByteArray()),java.nio.charset.StandardCharsets.UTF_8)) {
            java.util.zip.ZipEntry entry;
            while((entry=zip.getNextEntry())!=null){assertThat(entries).doesNotContainKey(entry.getName());entries.put(entry.getName(),zip.readAllBytes());}
        }
        var data=json.readTree(entries.get("life1000.json"));
        for(String table:new String[]{"category","life_goal","goal_check_item","goal_record","goal_completion","goal_attachment","quote","app_setting"})
            assertThat(data.has(table)).isTrue();
        for(var row:data.get("goal_attachment")) {
            if(row.get("id").asLong()==missing.getId())assertThat(row.get("export_file").isNull()).isTrue();
            if(Set.of(general.getId(),process.getId(),completion.getId(),duplicate.getId()).contains(row.get("id").asLong())){
                assertThat(row.get("export_file").asText()).startsWith("images/");assertThat(entries.get(row.get("export_file").asText())).isEqualTo(buffer.toByteArray());
            }
            if(row.get("id").asLong()==doc.getId()){assertThat(row.get("export_file").asText()).startsWith("documents/");assertThat(new String(entries.get(row.get("export_file").asText()))).isEqualTo("document");}
        }
        var info=json.readTree(entries.get("backup-info.json"));
        assertThat(info.get("backupVersion").asInt()).isEqualTo(1);
        assertThat(info.get("missingFiles").findValues("attachmentId")).extracting(JsonNode::asLong).contains(missing.getId());
        assertThat(info.get("totalAttachments").asInt()).isEqualTo(data.get("goal_attachment").size());
        assertThat(data.toString()).doesNotContain(TestCredentials.PASSWORD);
        assertThat(entries.keySet()).noneMatch(name->name.contains(".cleanup")||name.contains("../")||name.contains("src/"));
        call(delete("/api/attachments/"+general.getId()),204);
        call(get("/api/home/background"),204);
        assertThat(call(get("/api/settings"),200).get("fixedImage").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_setting WHERE setting_key='HOME_FIXED_BACKGROUND_PATH'",Integer.class)).isZero();
        fixed(process);call(delete("/api/records/"+record),204);call(get("/api/home/background"),204);
        fixed(completion);call(delete("/api/goals/990"),204);call(get("/api/home/background"),204);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_setting WHERE setting_key='HOME_FIXED_BACKGROUND_PATH'",Integer.class)).isZero();
        call(get("/api/goals/990"),404);
    }

    @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void backgroundModePersistsAcrossCommittedRequests() throws Exception {
        assertThat(jdbc.queryForObject("SELECT DATABASE()",String.class)).isEqualTo("life1000_test");
        var original=jdbc.queryForList("SELECT setting_value,updated_at FROM app_setting WHERE setting_key='HOME_BACKGROUND_MODE'");
        token="Bearer "+json.readTree(mvc.perform(body(post("/api/auth/login"),Map.of("username",TestCredentials.USERNAME,"password",TestCredentials.PASSWORD)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("accessToken").asText();
        try {
            for(String mode:List.of("RANDOM","FIXED","RANDOM")) {
                call(body(put("/api/settings"),Map.of("HOME_BACKGROUND_MODE",mode)),200);
                assertThat(jdbc.queryForObject("SELECT setting_value FROM app_setting WHERE setting_key='HOME_BACKGROUND_MODE'",String.class)).isEqualTo(mode);
                assertThat(call(get("/api/settings"),200).get("mode").asText()).isEqualTo(mode);
            }
        } finally {
            if(original.isEmpty())jdbc.update("DELETE FROM app_setting WHERE setting_key='HOME_BACKGROUND_MODE'");
            else jdbc.update("UPDATE app_setting SET setting_value=?,updated_at=? WHERE setting_key='HOME_BACKGROUND_MODE'",
                original.getFirst().get("setting_value"),original.getFirst().get("updated_at"));
        }
    }

}
