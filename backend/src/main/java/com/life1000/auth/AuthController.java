package com.life1000.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthService.TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.login(request.username(), request.password()));
    }

    public record LoginRequest(
            @NotBlank @Size(max = 200) String username,
            @NotBlank @Size(max = 1000) String password) {
        @Override
        public String toString() { return "LoginRequest[credentials=REDACTED]"; }
    }
}
