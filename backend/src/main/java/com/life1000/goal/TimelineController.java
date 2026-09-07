package com.life1000.goal;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("mysql")
@RequestMapping("/api/timeline")
public class TimelineController {
    private final TimelineService service;
    public TimelineController(TimelineService service) { this.service = service; }
    @GetMapping public List<TimelineService.Year> years() { return service.years(); }
    @GetMapping("/{year}") public List<TimelineService.Entry> entries(@PathVariable int year) { return service.entries(year); }
}
