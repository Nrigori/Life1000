package com.life1000.home;

import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("mysql")
@RequestMapping("/api")
public class HomeController {
    private final HomeService service;
    public HomeController(HomeService service) { this.service = service; }
    @GetMapping("/stats") public ResponseEntity<HomeService.Stats> stats() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.stats());
    }
    @GetMapping("/home/background") public ResponseEntity<HomeService.Background> background() {
        var value = service.background();
        return value == null ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
                : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
