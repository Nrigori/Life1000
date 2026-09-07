package com.life1000.goal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.life1000.TestCredentials;
import com.life1000.auth.*;
import com.life1000.common.ApiExceptionHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@WebMvcTest({CompletionController.class, TimelineController.class, AuthController.class})
@Import({SecurityConfiguration.class, AuthService.class, ApiExceptionHandler.class})
@ActiveProfiles("mysql")
class CompletionControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean CompletionService service;
    @MockitoBean TimelineService timeline;
    @DynamicPropertySource static void credentials(DynamicPropertyRegistry registry) { TestCredentials.configure(registry); }
    String token() throws Exception {
        var response=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                Map.of("username",TestCredentials.USERNAME,"password",TestCredentials.PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return "Bearer "+json.readTree(response.getContentAsByteArray()).get("accessToken").asText();
    }
    @Test void completionAndTimelineRequireAuthentication() throws Exception {
        mvc.perform(post("/api/goals/27/complete")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/goals/27/uncomplete")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/goals/27/completion")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/timeline")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/timeline/2028")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service,timeline);
    }
    @Test void missingDateAndInvalidRatingsAreRejectedBeforeService() throws Exception {
        String token=token();
        for(String input:new String[]{"{}", "{\"completedDate\":null}", "{\"completedDate\":\"not-a-date\"}",
                "{\"completedDate\":\"2028-06-17\",\"rating\":0}", "{\"completedDate\":\"2028-06-17\",\"rating\":6}"})
            mvc.perform(post("/api/goals/27/complete").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(input))
                    .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void multipartBindsCompletionAndOptionalProofs() throws Exception {
        mvc.perform(multipart("/api/goals/27/complete")
                .file(new MockMultipartFile("completion","","application/json","{\"completedDate\":\"2028-06-17\"}".getBytes()))
                .file(new MockMultipartFile("files","proof.txt","text/plain","proof".getBytes())).header("Authorization",token()))
                .andExpect(status().isOk());
        verify(service).save(eq(27),argThat(input -> input.rating()==null && input.completionNote()==null
                && input.completedDate().toString().equals("2028-06-17")),argThat(files -> files.size()==1),eq(false));
    }
    @Test void optionalFieldsCanBeEmptyForCreateAndEdit() throws Exception {
        String token=token();
        mvc.perform(post("/api/goals/27/complete").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completedDate\":\"2028-06-17\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/goals/27/completion").header("Authorization",token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"completedDate\":\"2029-01-02\",\"rating\":null,\"completionNote\":null}")).andExpect(status().isOk());
        verify(service).save(eq(27),any(),eq(java.util.List.of()),eq(false));
        verify(service).save(eq(27),any(),eq(java.util.List.of()),eq(true));
    }
}
