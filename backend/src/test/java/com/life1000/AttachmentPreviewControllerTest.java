package com.life1000;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.life1000.auth.*;
import com.life1000.common.ApiExceptionHandler;
import com.life1000.entity.GoalAttachment;
import com.life1000.goal.*;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({GoalDetailController.class, AuthController.class})
@Import({SecurityConfiguration.class, AuthService.class, ApiExceptionHandler.class})
@ActiveProfiles("mysql")
class AttachmentPreviewControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean GoalDetailService service;
    @MockitoBean LocalFiles files;
    @TempDir Path directory;
    @DynamicPropertySource static void credentials(DynamicPropertyRegistry registry) { TestCredentials.configure(registry); }
    String token() throws Exception {
        var response=mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username",TestCredentials.USERNAME,"password",TestCredentials.PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return "Bearer "+json.readTree(response.getContentAsByteArray()).get("accessToken").asText();
    }
    void fixture(String name,String mime) throws Exception {
        Path path=directory.resolve("fixture");
        Files.writeString(path,"preview content");
        var attachment=new GoalAttachment();
        attachment.setId(1L); attachment.setOriginalName(name); attachment.setMimeType(mime);
        attachment.setIsImage(mime.startsWith("image/")); attachment.setFileSize(Files.size(path)); attachment.setFilePath("fixture");
        when(service.attachment(1)).thenReturn(attachment);
        when(files.resolve(anyString())).thenAnswer(call->new LocalFiles(directory.toString()).resolve(call.getArgument(0)));
    }
    @ParameterizedTest
    @CsvSource({
        "笔记.MD,application/octet-stream,text/markdown",
        "笔记.bin,text/markdown,text/markdown",
        "正文.txt,application/octet-stream,text/plain",
        "正文.bin,text/plain,text/plain",
        "文件.pdf,application/octet-stream,application/pdf",
        "文件.bin,application/pdf,application/pdf",
        "照片.png,image/png,image/png",
        "照片.webp,application/octet-stream,image/webp",
        "动画.gif,image/gif,image/gif"
    })
    void supportedFilesInlineAndDownloadOverride(String name,String mime,String expected) throws Exception {
        fixture(name,mime);
        String token=token();
        mvc.perform(get("/api/attachments/1/content?download=false").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(expected))
                .andExpect(header().string("Content-Disposition",startsWith("inline;")))
                .andExpect(header().string("X-Content-Type-Options","nosniff"))
                .andExpect(content().string("preview content"));
        mvc.perform(get("/api/attachments/1/content?download=true").header("Authorization",token))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",startsWith("attachment;")))
                .andExpect(content().string("preview content"));
    }
    @ParameterizedTest
    @CsvSource({"archive.zip,application/zip","office.docx,application/octet-stream","page.html,text/html","vector.svg,image/svg+xml"})
    void unsupportedFilesRemainDownloads(String name,String mime) throws Exception {
        fixture(name,mime);
        mvc.perform(get("/api/attachments/1/content").header("Authorization",token()))
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",startsWith("attachment;")));
    }
    @Test void authenticationIsStillRequired() throws Exception {
        mvc.perform(get("/api/attachments/1/content")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/attachments/1/content?download=true")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service,files);
    }
    @Test void missingAndUnsafeFilesAreRejected() throws Exception {
        fixture("notes.txt","text/plain");
        String token=token();
        Files.delete(directory.resolve("fixture"));
        mvc.perform(get("/api/attachments/1/content").header("Authorization",token)).andExpect(status().isNotFound());
        service.attachment(1).setFilePath("../outside");
        mvc.perform(get("/api/attachments/1/content").header("Authorization",token)).andExpect(status().isBadRequest());
    }
}
