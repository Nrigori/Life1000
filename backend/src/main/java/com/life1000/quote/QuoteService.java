package com.life1000.quote;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.life1000.common.ApiException;
import com.life1000.entity.Quote;
import com.life1000.mapper.QuoteMapper;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class QuoteService {
    public record Input(@NotBlank String content, @Size(max=1000) String source, Boolean includeHome) {}
    private final QuoteMapper quotes;
    public QuoteService(QuoteMapper quotes) { this.quotes = quotes; }
    public List<Quote> list(String keyword) {
        String term = keyword == null ? "" : keyword.strip();
        term = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return quotes.selectList(new LambdaQueryWrapper<Quote>()
                .apply(!term.isEmpty(), "(content LIKE {0} ESCAPE '!' OR source LIKE {0} ESCAPE '!')", "%" + term + "%")
                .orderByDesc(Quote::getId));
    }
    public Quote random() {
        return quotes.selectOne(new LambdaQueryWrapper<Quote>().eq(Quote::getIncludeHome, true).last("ORDER BY RAND() LIMIT 1"));
    }
    @Transactional
    public Quote create(Input input) {
        var quote = new Quote(); quote.setContent(input.content().strip()); quote.setSource(input.source());
        quote.setIncludeHome(input.includeHome() == null || input.includeHome());
        quotes.insert(quote); return quotes.selectById(quote.getId());
    }
    @Transactional
    public Quote update(long id, Input input) {
        if (quotes.selectById(id) == null) throw ApiException.notFound("金句不存在");
        quotes.update(null, new LambdaUpdateWrapper<Quote>().eq(Quote::getId, id)
                .set(Quote::getContent, input.content().strip()).set(Quote::getSource, input.source())
                .set(Quote::getIncludeHome, input.includeHome() == null || input.includeHome()));
        return quotes.selectById(id);
    }
    public void delete(long id) { if (quotes.deleteById(id) == 0) throw ApiException.notFound("金句不存在"); }
}
