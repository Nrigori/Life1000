package com.life1000.goal;

import com.life1000.entity.GoalCompletion;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile("mysql")
@RequestMapping("/api/goals/{slot}")
public class CompletionController {
    private final CompletionService service;
    public CompletionController(CompletionService service) { this.service = service; }
    @GetMapping("/completion")
    public ResponseEntity<GoalCompletion> get(@PathVariable int slot) {
        var archive = service.get(slot);
        return archive == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(archive);
    }
    @PostMapping(value="/complete", consumes=MediaType.APPLICATION_JSON_VALUE)
    public GoalCompletion complete(@PathVariable int slot, @Valid @RequestBody CompletionService.Input input) throws IOException {
        return service.save(slot, input, List.of(), false);
    }
    @PostMapping(value="/complete", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public GoalCompletion completeFiles(@PathVariable int slot, @Valid @RequestPart("completion") CompletionService.Input input,
            @RequestPart(value="files", required=false) List<MultipartFile> files) throws IOException {
        return service.save(slot, input, files == null ? List.of() : files, false);
    }
    @PutMapping(value="/completion", consumes=MediaType.APPLICATION_JSON_VALUE)
    public GoalCompletion edit(@PathVariable int slot, @Valid @RequestBody CompletionService.Input input) throws IOException {
        return service.save(slot, input, List.of(), true);
    }
    @PutMapping(value="/completion", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public GoalCompletion editFiles(@PathVariable int slot, @Valid @RequestPart("completion") CompletionService.Input input,
            @RequestPart(value="files", required=false) List<MultipartFile> files) throws IOException {
        return service.save(slot, input, files == null ? List.of() : files, true);
    }
    @PostMapping("/uncomplete") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void undo(@PathVariable int slot) { service.undo(slot); }
}
