package com.life1000.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.life1000.entity.LifeGoal;
import com.life1000.home.HomeService;
import java.time.LocalDate;
import org.apache.ibatis.annotations.*;

public interface LifeGoalMapper extends BaseMapper<LifeGoal> {
    @Select("""
        SELECT
          (SELECT COUNT(*) FROM life_goal) AS writtenCount,
          (SELECT COUNT(*) FROM life_goal WHERE status='COMPLETED') AS completedCount,
          (SELECT COUNT(*) FROM life_goal WHERE status='IN_PROGRESS') AS inProgressCount,
          1000 - (SELECT COUNT(*) FROM life_goal) AS blankCount,
          (SELECT COUNT(*) FROM goal_completion c JOIN life_goal g ON g.id=c.goal_id
            WHERE g.status='COMPLETED' AND c.completed_date >= #{start} AND c.completed_date < #{end}) AS completedThisYear,
          (SELECT COUNT(*) FROM goal_attachment WHERE is_image=TRUE) AS imageCount,
          (SELECT COUNT(*) FROM goal_attachment WHERE is_image=FALSE) AS documentCount,
          (SELECT COUNT(*) FROM quote) AS quoteCount
        """)
    HomeService.Stats stats(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
