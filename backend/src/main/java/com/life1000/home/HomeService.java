package com.life1000.home;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life1000.entity.*;
import com.life1000.mapper.*;
import java.time.LocalDate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class HomeService {
    public record Stats(long writtenCount, long completedCount, long inProgressCount, long blankCount,
                        long completedThisYear, long imageCount, long documentCount, long quoteCount) {}
    public record Background(long id, String originalName) {}
    private final LifeGoalMapper goals;
    private final GoalAttachmentMapper attachments;
    private final AppSettingMapper settings;
    public HomeService(LifeGoalMapper goals, GoalAttachmentMapper attachments, AppSettingMapper settings) {
        this.goals = goals; this.attachments = attachments; this.settings = settings;
    }
    public Stats stats() {
        LocalDate start = LocalDate.now().withDayOfYear(1);
        return goals.stats(start, start.plusYears(1));
    }
    private String setting(String key) {
        var value = settings.selectOne(new LambdaQueryWrapper<AppSetting>().eq(AppSetting::getSettingKey, key));
        return value == null ? null : value.getSettingValue();
    }
    // 固定模式只匹配已有图片元数据，不要求参与随机；未设置模式时默认随机，固定引用无效则返回空。
    // 随机不记上次结果，三种附件阶段均可参与；实际图片仍通过认证附件 API 读取。
    @Transactional(readOnly=true)
    public Background background() {
        var query = new LambdaQueryWrapper<GoalAttachment>().eq(GoalAttachment::getIsImage, true);
        if ("FIXED".equalsIgnoreCase(setting("HOME_BACKGROUND_MODE"))) {
            String path = setting("HOME_FIXED_BACKGROUND_PATH");
            if (path == null || path.isBlank()) return null;
            // Resolve configuration only to existing attachment metadata; never serve arbitrary paths.
            query.eq(GoalAttachment::getFilePath, path).orderByAsc(GoalAttachment::getId).last("LIMIT 1");
        } else {
            query.eq(GoalAttachment::getAllowHomeBackground, true).last("ORDER BY RAND() LIMIT 1");
        }
        var file = attachments.selectOne(query);
        return file == null ? null : new Background(file.getId(), file.getOriginalName());
    }
}
