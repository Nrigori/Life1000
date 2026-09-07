package com.life1000.goal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.life1000.common.ApiException;
import com.life1000.entity.*;
import com.life1000.mapper.*;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("mysql")
@Transactional(rollbackFor = IOException.class)
public class CompletionService {
    public record Input(@NotNull LocalDate completedDate, String completionNote, @Min(1) @Max(5) Integer rating) {}
    private final LifeGoalMapper goals;
    private final GoalCompletionMapper completions;
    private final GoalDetailService details;
    public CompletionService(LifeGoalMapper goals, GoalCompletionMapper completions, GoalDetailService details) {
        this.goals = goals; this.completions = completions; this.details = details;
    }
    private LifeGoal lock(int slot) {
        LifeGoalService.validateSlot(slot);
        var goal = goals.selectOne(new LambdaQueryWrapper<LifeGoal>().eq(LifeGoal::getSlotNo, slot).last("FOR UPDATE"));
        if (goal == null) throw ApiException.notFound("该编号尚未写下");
        return goal;
    }
    private GoalCompletion archive(long goalId) {
        return completions.selectOne(new LambdaQueryWrapper<GoalCompletion>().eq(GoalCompletion::getGoalId, goalId));
    }
    public GoalCompletion get(int slot) { return archive(lock(slot).getId()); }
    public GoalCompletion save(int slot, Input input, List<MultipartFile> files, boolean editing) throws IOException {
        if (input.completedDate() == null || input.completedDate().getYear() < 1000 || input.completedDate().getYear() > 9999
                || (input.rating() != null && (input.rating() < 1 || input.rating() > 5)))
            throw ApiException.badRequest("完成日期必填且须为有效日期，评分只能为 1～5");
        var goal = lock(slot);
        if (editing != (goal.getStatus() == GoalStatus.COMPLETED))
            throw ApiException.conflict(editing ? "该事项尚未完成，请重新读取" : "该事项已经完成，请重新读取");
        var archive = archive(goal.getId());
        GoalStatus before = editing && archive != null ? archive.getStatusBeforeCompletion() : goal.getStatus();
        if (before != GoalStatus.IN_PROGRESS && before != GoalStatus.NOT_STARTED) before = GoalStatus.NOT_STARTED;
        if (archive == null) {
            archive = new GoalCompletion();
            archive.setGoalId(goal.getId()); archive.setCompletedDate(input.completedDate());
            archive.setCompletionNote(input.completionNote()); archive.setRating(input.rating()); archive.setStatusBeforeCompletion(before);
            completions.insert(archive);
        } else {
            completions.update(null, new LambdaUpdateWrapper<GoalCompletion>().eq(GoalCompletion::getId, archive.getId())
                    .set(GoalCompletion::getCompletedDate, input.completedDate())
                    .set(GoalCompletion::getCompletionNote, input.completionNote())
                    .set(GoalCompletion::getRating, input.rating()).set(GoalCompletion::getStatusBeforeCompletion, before));
        }
        goals.update(null, new LambdaUpdateWrapper<LifeGoal>().eq(LifeGoal::getId, goal.getId()).set(LifeGoal::getStatus, GoalStatus.COMPLETED));
        for (var file : files) details.upload(slot, null, "COMPLETION", file);
        return archive(goal.getId());
    }
    public void undo(int slot) {
        var goal = lock(slot);
        if (goal.getStatus() != GoalStatus.COMPLETED) throw ApiException.conflict("该事项当前不是已完成状态");
        var archive = archive(goal.getId());
        var before = archive == null ? null : archive.getStatusBeforeCompletion();
        if (before != GoalStatus.IN_PROGRESS && before != GoalStatus.NOT_STARTED) before = GoalStatus.NOT_STARTED;
        goals.update(null, new LambdaUpdateWrapper<LifeGoal>().eq(LifeGoal::getId, goal.getId()).set(LifeGoal::getStatus, before));
    }
}
