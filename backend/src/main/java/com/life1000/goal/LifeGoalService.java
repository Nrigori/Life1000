package com.life1000.goal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.life1000.common.ApiException;
import com.life1000.entity.GoalStatus;
import com.life1000.entity.LifeGoal;
import com.life1000.mapper.CategoryMapper;
import com.life1000.mapper.LifeGoalMapper;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("mysql")
public class LifeGoalService {
    private final LifeGoalMapper mapper;
    private final CategoryMapper categories;

    public LifeGoalService(LifeGoalMapper mapper, CategoryMapper categories) {
        this.mapper = mapper;
        this.categories = categories;
    }

    public static void validateSlot(int slotNo) {
        if (slotNo < 1 || slotNo > 1000) throw ApiException.badRequest("编号必须为 1～1000");
    }

    public LifeGoal get(int slotNo) {
        validateSlot(slotNo);
        LifeGoal goal = mapper.selectOne(new LambdaQueryWrapper<LifeGoal>().eq(LifeGoal::getSlotNo, slotNo));
        if (goal == null) throw ApiException.notFound("该编号尚未写下");
        return goal;
    }

    public List<LifeGoal> range(int fromSlot, int toSlot) {
        validateSlot(fromSlot);
        validateSlot(toSlot);
        if (fromSlot > toSlot) throw ApiException.badRequest("起始编号不能大于结束编号");
        return mapper.selectList(new LambdaQueryWrapper<LifeGoal>()
                .between(LifeGoal::getSlotNo, fromSlot, toSlot).orderByAsc(LifeGoal::getSlotNo));
    }

    public List<LifeGoal> search(String keyword, Long categoryId, GoalStatus status) {
        String term = keyword == null ? "" : keyword.strip();
        // Escape SQL LIKE metacharacters so a literal % or _ searches literally.
        term = term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return mapper.selectList(new LambdaQueryWrapper<LifeGoal>()
                .apply(!term.isEmpty(), "title LIKE {0} ESCAPE '!'", "%" + term + "%")
                .eq(categoryId != null, LifeGoal::getCategoryId, categoryId)
                .eq(status != null, LifeGoal::getStatus, status)
                .orderByAsc(LifeGoal::getSlotNo));
    }

    private void validateCategory(Long id) {
        if (id != null && categories.selectById(id) == null) {
            throw ApiException.badRequest("所选分类不存在");
        }
    }

    @Transactional
    public LifeGoal create(int slotNo, GoalCreateRequest request) {
        validateSlot(slotNo);
        validateCategory(request.categoryId());
        LifeGoal goal = new LifeGoal();
        goal.setSlotNo(slotNo);
        goal.setTitle(request.title().strip());
        goal.setCategoryId(request.categoryId());
        goal.setReason(request.reason());
        goal.setStatus(GoalStatus.NOT_STARTED);
        try {
            mapper.insert(goal);
        } catch (DuplicateKeyException exception) {
            throw ApiException.conflict("该编号已经写下，请编辑已有事项");
        }
        return get(slotNo);
    }

    private static void validateStatusChange(LifeGoal goal, GoalStatus status) {
        if (status == GoalStatus.COMPLETED
                || (goal.getStatus() == GoalStatus.COMPLETED && status != null)) {
            throw ApiException.badRequest("基础接口不能完成或撤销完成事项");
        }
    }

    @Transactional
    public LifeGoal update(int slotNo, GoalUpdateRequest request) {
        LifeGoal goal = get(slotNo);
        validateCategory(request.categoryId());
        validateStatusChange(goal, request.status());
        mapper.update(null, new LambdaUpdateWrapper<LifeGoal>()
                .eq(LifeGoal::getId, goal.getId())
                .set(LifeGoal::getTitle, request.title().strip())
                .set(LifeGoal::getCategoryId, request.categoryId())
                .set(LifeGoal::getReason, request.reason())
                .set(request.status() != null, LifeGoal::getStatus, request.status()));
        return get(slotNo);
    }

    @Transactional
    public LifeGoal updateStatus(int slotNo, GoalStatus status) {
        LifeGoal goal = get(slotNo);
        validateStatusChange(goal, status);
        mapper.update(null, new LambdaUpdateWrapper<LifeGoal>().eq(LifeGoal::getId, goal.getId())
                .set(LifeGoal::getStatus, status));
        return get(slotNo);
    }

    @Transactional
    public void delete(int slotNo) {
        validateSlot(slotNo);
        if (mapper.delete(new LambdaQueryWrapper<LifeGoal>().eq(LifeGoal::getSlotNo, slotNo)) == 0) {
            throw ApiException.notFound("该编号尚未写下");
        }
    }
}
