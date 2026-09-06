package com.life1000.goal;

import com.life1000.common.ApiException;
import com.life1000.entity.GoalStatus;
import com.life1000.entity.LifeGoal;
import com.life1000.mapper.CategoryMapper;
import com.life1000.mapper.LifeGoalMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LifeGoalServiceTest {
    private final LifeGoalMapper mapper = mock(LifeGoalMapper.class);
    private final CategoryMapper categories = mock(CategoryMapper.class);
    private final LifeGoalService service = new LifeGoalService(mapper, categories);

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1001, Integer.MAX_VALUE})
    void outOfRangeSlotsNeverReachDatabase(int slot) {
        assertThatThrownBy(() -> service.create(slot, new GoalCreateRequest("title", null, null)))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.get(slot)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.delete(slot)).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper, categories);
    }

    @Test
    void reversedRangeIsRejected() {
        assertThatThrownBy(() -> service.range(10, 1)).isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void absentSlotIs404InsteadOfInventedGoal() {
        assertThatThrownBy(() -> service.get(1)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus().value()).isEqualTo(404));
    }

    @Test
    void uniqueConstraintConflictBecomes409() {
        when(mapper.insert(any(LifeGoal.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThatThrownBy(() -> service.create(1, new GoalCreateRequest("title", null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus().value()).isEqualTo(409));
    }

    @Test
    void invalidCategoryNeverInserts() {
        assertThatThrownBy(() -> service.create(1, new GoalCreateRequest("title", 999L, null)))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void completionAndUndoCannotUseBasicUpdate() {
        LifeGoal goal = new LifeGoal();
        goal.setId(1L);
        goal.setStatus(GoalStatus.NOT_STARTED);
        when(mapper.selectOne(any())).thenReturn(goal);
        assertThatThrownBy(() -> service.update(1,
                new GoalUpdateRequest("title", null, null, GoalStatus.COMPLETED))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.updateStatus(1, GoalStatus.COMPLETED)).isInstanceOf(ApiException.class);
        goal.setStatus(GoalStatus.COMPLETED);
        assertThatThrownBy(() -> service.updateStatus(1, GoalStatus.NOT_STARTED)).isInstanceOf(ApiException.class);
        verify(mapper, never()).update(isNull(), any());
    }
}
