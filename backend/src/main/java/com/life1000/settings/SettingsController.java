package com.life1000.settings;
import java.io.IOException;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
@RestController @Profile("mysql") @RequestMapping("/api/settings")
public class SettingsController {
    private final SettingsService service;
    public SettingsController(SettingsService service) { this.service=service; }
    @GetMapping public SettingsService.Settings read() { return service.read(); }
    @PutMapping public SettingsService.Settings update(@RequestBody Map<String,String> values) throws IOException { return service.update(values); }
    @GetMapping("/images") public List<SettingsService.Image> images() { return service.images(); }
}
