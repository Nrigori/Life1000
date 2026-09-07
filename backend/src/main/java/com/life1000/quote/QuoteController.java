package com.life1000.quote;

import com.life1000.entity.Quote;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("mysql")
@RequestMapping("/api/quotes")
public class QuoteController {
    private final QuoteService service;
    public QuoteController(QuoteService service) { this.service = service; }
    @GetMapping public List<Quote> list(@RequestParam(defaultValue="") @Size(max=1000) String keyword) { return service.list(keyword); }
    @GetMapping("/random") public ResponseEntity<Quote> random() {
        var quote = service.random();
        return quote == null ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
                : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(quote);
    }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Quote create(@Valid @RequestBody QuoteService.Input input) { return service.create(input); }
    @PutMapping("/{id}") public Quote update(@PathVariable long id, @Valid @RequestBody QuoteService.Input input) { return service.update(id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable long id) { service.delete(id); }
}
