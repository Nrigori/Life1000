package com.life1000.goal;

import com.life1000.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("mysql")
@RequestMapping("/api")
public class GoalDetailController {
    private final GoalDetailService service;
    private final LocalFiles files;
    public GoalDetailController(GoalDetailService service, LocalFiles files) { this.service=service; this.files=files; }
    @GetMapping("/goals/{slot}/check-items") public List<GoalCheckItem> checks(@PathVariable int slot) { return service.checks(slot); }
    @PostMapping("/goals/{slot}/check-items") @ResponseStatus(HttpStatus.CREATED)
    public GoalCheckItem addCheck(@PathVariable int slot,@Valid @RequestBody GoalDetailService.CheckInput input) { return service.addCheck(slot,input); }
    @PutMapping("/check-items/{id}") public GoalCheckItem updateCheck(@PathVariable long id,@Valid @RequestBody GoalDetailService.CheckInput input) { return service.updateCheck(id,input); }
    @DeleteMapping("/check-items/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCheck(@PathVariable long id) { service.deleteCheck(id); }
    @GetMapping("/goals/{slot}/records") public List<GoalRecord> records(@PathVariable int slot) { return service.records(slot); }
    @PostMapping("/goals/{slot}/records") @ResponseStatus(HttpStatus.CREATED)
    public GoalRecord addRecord(@PathVariable int slot,@Valid @RequestBody GoalDetailService.RecordInput input) { return service.addRecord(slot,input); }
    @PutMapping("/records/{id}") public GoalRecord updateRecord(@PathVariable long id,@Valid @RequestBody GoalDetailService.RecordInput input) { return service.updateRecord(id,input); }
    @DeleteMapping("/records/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecord(@PathVariable long id) throws IOException { service.deleteRecord(id); }
    @GetMapping("/goals/{slot}/attachments") public List<GoalAttachment> attachments(@PathVariable int slot) { return service.attachments(slot); }
    @PostMapping(value="/goals/{slot}/attachments",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.CREATED)
    public GoalAttachment upload(@PathVariable int slot,@RequestParam(defaultValue="GENERAL") String stage,
            @RequestParam(required=false) Long recordId,@RequestParam MultipartFile file) throws IOException { return service.upload(slot,recordId,stage,file); }
    @DeleteMapping("/attachments/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable long id) throws IOException { service.deleteAttachment(id); }
    @GetMapping("/goals/{slot}/cover") public ResponseEntity<GoalAttachment> cover(@PathVariable int slot) {
        var image=service.cover(slot); return image==null?ResponseEntity.noContent().build():ResponseEntity.ok(image);
    }
    @PutMapping("/goals/{slot}/cover/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setCover(@PathVariable int slot,@PathVariable long id) { service.setCover(slot,id); }
    public record BackgroundInput(@NotNull Boolean allowed) {}
    @PutMapping("/attachments/{id}/home-background")
    public GoalAttachment background(@PathVariable long id,@Valid @RequestBody BackgroundInput input) { return service.homeBackground(id,input.allowed()); }
    @GetMapping("/attachments/{id}/content")
    public ResponseEntity<FileSystemResource> content(@PathVariable long id,@RequestParam(defaultValue="false") boolean download) throws IOException {
        var value=service.attachment(id); var path=files.resolve(value.getFilePath());
        if(!Files.isRegularFile(path)) throw com.life1000.common.ApiException.notFound("附件文件不存在");
        var disposition=(Boolean.TRUE.equals(value.getIsImage()) && !download)?ContentDisposition.inline():ContentDisposition.attachment();
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(value.getMimeType()))
                .contentLength(value.getFileSize()).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,disposition.filename(value.getOriginalName(),StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }
}
