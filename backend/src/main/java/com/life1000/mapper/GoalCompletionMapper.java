package com.life1000.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.life1000.entity.GoalCompletion;
import com.life1000.goal.TimelineService;
import java.util.List;
import org.apache.ibatis.annotations.Select;

public interface GoalCompletionMapper extends BaseMapper<GoalCompletion> {
    // 时间轴不另存节点：修改完成日期或撤销完成后查询立即反映变化；同日按固定编号稳定排序。
    @Select("""
        SELECT YEAR(c.completed_date) AS year, c.completed_date AS completedDate,
               g.slot_no AS slotNo, g.title AS title
        FROM goal_completion c JOIN life_goal g ON g.id = c.goal_id
        WHERE g.status = 'COMPLETED'
        ORDER BY c.completed_date ASC, g.slot_no ASC
        """)
    List<TimelineService.Entry> timeline();
}
