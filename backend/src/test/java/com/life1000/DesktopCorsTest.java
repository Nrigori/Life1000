package com.life1000;

import com.life1000.auth.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class, DesktopCorsConfiguration.class, AuthService.class})
@ActiveProfiles({"mysql", "desktop"})
class DesktopCorsTest {
    @Autowired MockMvc mvc;
    @DynamicPropertySource static void credentials(DynamicPropertyRegistry registry) { TestCredentials.configure(registry); }

    @Test void onlyLocalDesktopOriginMayPreflight() throws Exception {
        mvc.perform(options("/api/attachments/1/content").header("Origin", "http://tauri.localhost")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://tauri.localhost"))
                .andExpect(header().string("Access-Control-Expose-Headers", "Content-Disposition"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        for (String origin : new String[] {"http://evil.test", "http://tauri.localhost.evil.test", "null"}) {
            mvc.perform(options("/api/attachments/1/content").header("Origin", origin)
                    .header("Access-Control-Request-Method", "GET"))
                    .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        }
    }
    @Test void desktopStillRequiresJwtIncludingDownloads() throws Exception {
        for (String path : new String[] {"/api/health", "/api/attachments/1/content", "/api/backup/export"}) {
            mvc.perform(get(path).header("Origin", "http://tauri.localhost"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Access-Control-Allow-Origin", "http://tauri.localhost"));
        }
    }
}
