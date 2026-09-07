package com.life1000;

import com.fasterxml.jackson.databind.*;
import com.life1000.entity.*;
import com.life1000.goal.*;
import com.life1000.home.HomeService;
import com.life1000.quote.QuoteService;
import java.time.LocalDate;
import java.nio.file.Path;
import java.util.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("mysql")
@EnabledIfEnvironmentVariable(named="DB_USERNAME",matches=".+")
@Transactional
class Phase5MysqlIntegrationTest {
    @TempDir static Path directory;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired HomeService home;
    @Autowired com.life1000.mapper.AppSettingMapper settings;
    @Autowired LifeGoalService goals;
    @Autowired GoalDetailService details;
    @Autowired CompletionService completions;
    String token;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        TestCredentials.configure(properties);
        properties.add("life1000.upload-directory",()->directory.toString());
    }
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object value) throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(value));
    }
    JsonNode call(MockHttpServletRequestBuilder request,int expected) throws Exception {
        var response=mvc.perform(request.header("Authorization",token)).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsByteArray().length==0?json.nullNode():json.readTree(response.getContentAsByteArray());
    }
    @Test void quoteCrudRandomBackgroundAndStatsUseRealData() throws Exception {
        for(int slot:new int[]{993,994,995})
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM life_goal WHERE slot_no=?",Integer.class,slot))
                    .as("Use blank slots 993, 994, 995; existing goals are never cleared").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM quote",Integer.class)).as("Use a dedicated test DB with no quotes").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM goal_attachment WHERE is_image=TRUE AND allow_home_background=TRUE",Integer.class))
                .as("Use a test DB without preexisting background candidates").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_setting WHERE setting_key IN ('HOME_BACKGROUND_MODE','HOME_FIXED_BACKGROUND_PATH')",Integer.class)).isZero();
        token="Bearer "+json.readTree(mvc.perform(body(post("/api/auth/login"),Map.of("username",TestCredentials.USERNAME,"password",TestCredentials.PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()).get("accessToken").asText();
        call(get("/api/quotes/random"),204); call(get("/api/home/background"),204);
        long quote=call(body(post("/api/quotes"),Map.of("content","此心安处是吾乡。","source","苏轼《定风波》")),201).get("id").asLong();
        long hidden=call(body(post("/api/quotes"),Map.of("content","只留在收藏里","includeHome",false)),201).get("id").asLong();
        for(int i=0;i<5;i++) assertThat(call(get("/api/quotes/random"),200).get("id").asLong()).isEqualTo(quote);
        assertThat(call(get("/api/quotes").param("keyword","苏轼"),200).size()).isEqualTo(1);
        assertThat(call(get("/api/quotes").param("keyword","吾乡"),200).size()).isEqualTo(1);
        call(body(put("/api/quotes/"+quote),Map.of("content","100%_ 原文","includeHome",false)),200);
        assertThat(call(get("/api/quotes").param("keyword","%_"),200).size()).isEqualTo(1);
        assertThat(call(get("/api/quotes").param("keyword","%_"),200).get(0).get("source").isNull()).isTrue();
        call(get("/api/quotes/random"),204);
        call(body(put("/api/quotes/"+quote),Map.of("content","100%_ 原文","includeHome",true)),200);
        assertThat(call(get("/api/quotes/random"),200).get("id").asLong()).isEqualTo(quote);
        var base=home.stats();
        for(int slot:new int[]{993,994,995}) goals.create(slot,new GoalCreateRequest("Phase 5 test",null,null));
        goals.updateStatus(994,GoalStatus.IN_PROGRESS);
        completions.save(993,new CompletionService.Input(LocalDate.now(),null,null),List.of(),false);
        completions.save(994,new CompletionService.Input(LocalDate.now(),null,null),List.of(),false);
        completions.undo(994);
        completions.save(995,new CompletionService.Input(LocalDate.now().minusYears(1),null,null),List.of(),false);
        var buffer=new ByteArrayOutputStream(); ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",buffer);
        var photo=new MockMultipartFile("file","photo.png","image/png",buffer.toByteArray());
        var general=details.upload(994,null,"GENERAL",photo);
        var record=details.addRecord(994,new GoalDetailService.RecordInput(LocalDate.now(),"记录"));
        var process=details.upload(994,record.getId(),"PROCESS",photo);
        var completion=details.upload(993,null,"COMPLETION",photo);
        var document=details.upload(994,null,"GENERAL",new MockMultipartFile("file","note.txt","text/plain","note".getBytes()));
        // Even a legacy true eligibility flag cannot make a document a random image.
        jdbc.update("UPDATE goal_attachment SET allow_home_background=TRUE WHERE id=?",document.getId());
        for(var candidate:List.of(general,process,completion)) {
            details.homeBackground(candidate.getId(),true);
            assertThat(call(get("/api/home/background"),200).get("id").asLong()).isEqualTo(candidate.getId());
            mvc.perform(get("/api/attachments/"+candidate.getId()+"/content").header("Authorization",token))
                    .andExpect(status().isOk()).andExpect(content().bytes(buffer.toByteArray()));
            mvc.perform(get("/api/attachments/"+candidate.getId()+"/content")).andExpect(status().isUnauthorized());
            details.homeBackground(candidate.getId(),false);
        }
        call(get("/api/home/background"),204);
        var mode=new AppSetting(); mode.setSettingKey("HOME_BACKGROUND_MODE"); mode.setSettingValue("FIXED"); settings.insert(mode);
        var path=new AppSetting(); path.setSettingKey("HOME_FIXED_BACKGROUND_PATH"); path.setSettingValue(general.getFilePath()); settings.insert(path);
        assertThat(home.background().id()).isEqualTo(general.getId());
        var stats=home.stats();
        assertThat(stats.writtenCount()).isEqualTo(base.writtenCount()+3);
        assertThat(stats.completedCount()).isEqualTo(base.completedCount()+2);
        assertThat(stats.inProgressCount()).isEqualTo(base.inProgressCount()+1);
        assertThat(stats.blankCount()).isEqualTo(1000-stats.writtenCount());
        assertThat(stats.completedThisYear()).isEqualTo(base.completedThisYear()+1);
        assertThat(stats.imageCount()).isEqualTo(base.imageCount()+3);
        assertThat(stats.documentCount()).isEqualTo(base.documentCount()+1);
        assertThat(stats.quoteCount()).isEqualTo(2);
        call(get("/api/stats"),200);
        details.deleteAttachment(general.getId());
        call(get("/api/home/background"),204);
        call(delete("/api/quotes/"+quote),204); call(delete("/api/quotes/"+hidden),204);
        call(get("/api/quotes/random"),204);
        assertThat(home.stats().quoteCount()).isZero();
    }
}
