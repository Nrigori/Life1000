package com.life1000.goal;

import com.life1000.entity.GoalStatus;
import com.life1000.entity.LifeGoal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("mysql")
@RequestMapping("/api/goals")
public class LifeGoalController {
    private final LifeGoalService service;

    public LifeGoalController(LifeGoalService service) { this.service = service; }

    @GetMapping("/range")
    public List<LifeGoal> range(
            @RequestParam(defaultValue = "1") int fromSlot,
            @RequestParam(defaultValue = "50") int toSlot) {
        return service.range(fromSlot, toSlot);
    }

    @GetMapping("/search")
    public List<LifeGoal> search(
            @RequestParam(defaultValue = "") @Size(max = 255) String keyword,
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) GoalStatus status) {
        return service.search(keyword, categoryId, status);
    }

    @GetMapping("/{slotNo}")
    public LifeGoal get(@PathVariable int slotNo) { return service.get(slotNo); }

    @PostMapping("/{slotNo}")
    public ResponseEntity<LifeGoal> create(@PathVariable int slotNo,
                                          @Valid @RequestBody GoalCreateRequest request) {
        return ResponseEntity.created(URI.create("/api/goals/" + slotNo)).body(service.create(slotNo, request));
    }

    @PutMapping("/{slotNo}")
    public LifeGoal update(@PathVariable int slotNo, @Valid @RequestBody GoalUpdateRequest request) {
        return service.update(slotNo, request);
    }

    @PutMapping("/{slotNo}/status")
    public LifeGoal updateStatus(@PathVariable int slotNo, @Valid @RequestBody StatusRequest request) {
        return service.updateStatus(slotNo, request.status());
    }

    @DeleteMapping("/{slotNo}")
    public ResponseEntity<Void> delete(@PathVariable int slotNo) throws java.io.IOException {
        service.delete(slotNo);
        return ResponseEntity.noContent().build();
    }

    public record StatusRequest(@NotNull GoalStatus status) {}
}
