package com.life1000.goal;

import com.life1000.common.ApiException;
import com.life1000.mapper.GoalCompletionMapper;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("mysql")
public class TimelineService {
    public record Entry(int year, LocalDate completedDate, int slotNo, String title) {}
    public record Year(int year, long count) {}
    private final GoalCompletionMapper completions;
    public TimelineService(GoalCompletionMapper completions) { this.completions = completions; }
    // 只补当前年份以容纳空状态，历史年份来自实际完成数据，不生成人为的空白年份序列。
    public List<Year> years() {
        Map<Integer, Long> years = new TreeMap<>(Comparator.reverseOrder());
        years.put(LocalDate.now().getYear(), 0L);
        for (var entry : completions.timeline()) years.merge(entry.year(), 1L, Long::sum);
        return years.entrySet().stream().map(entry -> new Year(entry.getKey(), entry.getValue())).toList();
    }
    public List<Entry> entries(int year) {
        if (year < 1000 || year > 9999) throw ApiException.badRequest("年份不合法");
        return completions.timeline().stream().filter(entry -> entry.year() == year).toList();
    }
}
