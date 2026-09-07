package com.life1000;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.life1000.auth.*;
import com.life1000.common.*;
import com.life1000.settings.*;
import com.life1000.backup.*;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@WebMvcTest({SettingsController.class,BackupController.class,AuthController.class})
@Import({SecurityConfiguration.class,AuthService.class,ApiExceptionHandler.class})
@ActiveProfiles("mysql")
class Phase6ControllerTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json;
    @MockitoBean SettingsService settings; @MockitoBean BackupService backup;
    @TempDir Path temp;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){TestCredentials.configure(registry);}
    String token() throws Exception {
        var response=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
            Map.of("username",TestCredentials.USERNAME,"password",TestCredentials.PASSWORD)))).andExpect(status().isOk()).andReturn().getResponse();
        return "Bearer "+json.readTree(response.getContentAsByteArray()).get("accessToken").asText();
    }
    @Test void settingsAndBackupRequireAuthentication() throws Exception {
        for(var path:new String[]{"/api/settings","/api/settings/images","/api/backup/export"})mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/settings").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(settings,backup);
    }
    @Test void settingsReadAndUpdateUseExistingContract() throws Exception {
        var value=new SettingsService.Settings("RANDOM",null,null,new SettingsService.Usage(2,3,400));
        when(settings.read()).thenReturn(value);when(settings.update(anyMap())).thenReturn(value);
        mvc.perform(get("/api/settings").header("Authorization",token())).andExpect(status().isOk()).andExpect(jsonPath("$.files.totalBytes").value(400));
        mvc.perform(put("/api/settings").header("Authorization",token()).contentType(MediaType.APPLICATION_JSON).content("{\"HOME_BACKGROUND_MODE\":\"RANDOM\"}")).andExpect(status().isOk());
        verify(settings).update(Map.of("HOME_BACKGROUND_MODE","RANDOM"));
    }
    @Test void exportDownloadsAndCleansTemporaryDirectory() throws Exception {
        Path dir=Files.createDirectory(temp.resolve("export"));Path zip=Files.write(dir.resolve("data.zip"),new byte[]{80,75,3,4});
        when(backup.create()).thenReturn(new BackupService.Export(dir,zip,"Life1000_Backup_2026-09-07.zip"));
        mvc.perform(get("/api/backup/export").header("Authorization",token())).andExpect(status().isOk())
            .andExpect(content().contentType("application/zip")).andExpect(header().string("Content-Disposition","attachment; filename=\"Life1000_Backup_2026-09-07.zip\""))
            .andExpect(content().bytes(new byte[]{80,75,3,4}));
        assertThat(dir).doesNotExist();
    }
    @Test void unexpectedFailureNeverLeaksTechnicalDetails() throws Exception {
        when(settings.read()).thenThrow(new NullPointerException("secret internal detail"));
        mvc.perform(get("/api/settings").header("Authorization",token())).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("操作暂时无法完成，请稍后重试。"));
    }
}
