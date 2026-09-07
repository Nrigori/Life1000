package com.life1000.backup;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("mysql")
public class BackupController {
    private final BackupService service;
    public BackupController(BackupService service) { this.service=service; }
    @GetMapping("/api/backup/export")
    public void export(HttpServletResponse response) throws IOException {
        try(var result=service.create()) {
            response.setContentType("application/zip");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(result.name()).build().toString());
            response.setHeader(HttpHeaders.CACHE_CONTROL,"no-store");
            response.setContentLengthLong(Files.size(result.zip()));
            Files.copy(result.zip(),response.getOutputStream());
        }
    }
}
