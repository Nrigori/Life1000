package com.life1000;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.life1000.auth.*;
import com.life1000.common.ApiExceptionHandler;
import com.life1000.home.*;
import com.life1000.quote.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

@WebMvcTest({HomeController.class, QuoteController.class, AuthController.class})
@Import({SecurityConfiguration.class, AuthService.class, ApiExceptionHandler.class})
@ActiveProfiles("mysql")
class Phase5ControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean HomeService home;
    @MockitoBean QuoteService quotes;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { TestCredentials.configure(registry); }
    String token() throws Exception {
        var response=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("username",TestCredentials.USERNAME,"password",TestCredentials.PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return "Bearer "+json.readTree(response.getContentAsByteArray()).get("accessToken").asText();
    }
    @Test void allNewEndpointsRequireJwt() throws Exception {
        for(var path:new String[]{"/api/home/background","/api/stats","/api/quotes","/api/quotes/random"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"a\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/quotes/1")).andExpect(status().isUnauthorized());
        verifyNoInteractions(home,quotes);
    }
    @Test void noCandidatesReturnUncached204() throws Exception {
        String token=token();
        for(var path:new String[]{"/api/home/background","/api/quotes/random"})
            mvc.perform(get(path).header("Authorization",token)).andExpect(status().isNoContent())
                    .andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void blankQuotesCannotReachService() throws Exception {
        String token=token();
        for(String input:new String[]{"{}", "{\"content\":\"\"}", "{\"content\":\"  \"}"})
            mvc.perform(post("/api/quotes").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(input))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(quotes);
    }
    @Test void statsReturnExactlySpecifiedFields() throws Exception {
        when(home.stats()).thenReturn(new HomeService.Stats(8,3,2,992,1,4,5,6));
        mvc.perform(get("/api/stats").header("Authorization",token())).andExpect(status().isOk())
                .andExpect(jsonPath("$.writtenCount").value(8)).andExpect(jsonPath("$.completedCount").value(3))
                .andExpect(jsonPath("$.inProgressCount").value(2)).andExpect(jsonPath("$.blankCount").value(992))
                .andExpect(jsonPath("$.completedThisYear").value(1)).andExpect(jsonPath("$.imageCount").value(4))
                .andExpect(jsonPath("$.documentCount").value(5)).andExpect(jsonPath("$.quoteCount").value(6));
    }
}
