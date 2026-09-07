package com.life1000.goal;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.life1000.common.ApiException;
import com.life1000.entity.*;
import com.life1000.mapper.*;
import java.time.LocalDate;
import java.util.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class CompletionServiceTest {
    LifeGoalMapper goals = mock(LifeGoalMapper.class);
    GoalCompletionMapper archives = mock(GoalCompletionMapper.class);
    GoalDetailService details = mock(GoalDetailService.class);
    CompletionService service = new CompletionService(goals, archives, details);
    LifeGoal goal;
    GoalCompletion archive;
    @BeforeEach void setup() {
        for (var type : new Class<?>[]{LifeGoal.class, GoalCompletion.class})
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), type);
        goal = new LifeGoal(); goal.setId(27L); goal.setStatus(GoalStatus.IN_PROGRESS);
        when(goals.selectOne(any())).thenReturn(goal);
        when(archives.selectOne(any())).thenAnswer(call -> archive);
        when(archives.insert(any(GoalCompletion.class))).thenAnswer(call -> {
            archive = call.getArgument(0); archive.setId(1L); return 1;
        });
    }
    CompletionService.Input input(Integer rating) { return new CompletionService.Input(LocalDate.of(2028,6,17), null, rating); }
    @Test void completionAllowsEmptyNoteAndRatingAndPreservesPreviousState() throws Exception {
        var result = service.save(27, input(null), List.of(), false);
        assertThat(result.getRating()).isNull(); assertThat(result.getCompletionNote()).isNull();
        assertThat(result.getStatusBeforeCompletion()).isEqualTo(GoalStatus.IN_PROGRESS);
        verify(goals).update(isNull(),argThat((LambdaUpdateWrapper<LifeGoal> value) ->
                value.getParamNameValuePairs().containsValue(GoalStatus.COMPLETED)));
        verifyNoInteractions(details);
    }
    @ParameterizedTest @ValueSource(ints={0,6,-1})
    void invalidRatingsCannotWrite(int rating) {
        assertThatThrownBy(() -> service.save(27,input(rating),List.of(),false)).isInstanceOf(ApiException.class);
        verifyNoInteractions(goals, archives, details);
    }
    @Test void completionDateIsRequired() {
        assertThatThrownBy(() -> service.save(27,new CompletionService.Input(null,null,null),List.of(),false))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(goals, archives, details);
    }
    @Test void undoKeepsArchiveAndProofsAndRecompletionReusesRow() throws Exception {
        service.save(27,input(5),List.of(),false);
        goal.setStatus(GoalStatus.COMPLETED);
        service.undo(27);
        verify(goals).update(isNull(),argThat((LambdaUpdateWrapper<LifeGoal> value) ->
                value.getParamNameValuePairs().containsValue(GoalStatus.IN_PROGRESS)));
        verify(archives,never()).delete(any());
        verifyNoInteractions(details);
        goal.setStatus(GoalStatus.NOT_STARTED);
        service.save(27,input(4),List.of(),false);
        verify(archives,times(1)).insert(any(GoalCompletion.class));
        verify(archives).update(isNull(),argThat((LambdaUpdateWrapper<GoalCompletion> value) ->
                value.getParamNameValuePairs().containsValue(GoalStatus.NOT_STARTED)));
    }
    @Test void editCannotOverwriteStatusBeforeCompletionAndCanClearOptionalFields() throws Exception {
        service.save(27,input(5),List.of(),false); goal.setStatus(GoalStatus.COMPLETED);
        service.save(27,input(null),List.of(),true);
        verify(archives).update(isNull(),argThat((LambdaUpdateWrapper<GoalCompletion> value) ->
                value.getParamNameValuePairs().containsValue(GoalStatus.IN_PROGRESS)
                && value.getSqlSet().contains("completion_note") && value.getSqlSet().contains("rating")
                && value.getParamNameValuePairs().containsValue(null)));
    }
    @Test void duplicateCompletionAndEditingUncompletedGoalAreRejected() throws Exception {
        assertThatThrownBy(() -> service.save(27,input(null),List.of(),true)).isInstanceOf(ApiException.class);
        goal.setStatus(GoalStatus.COMPLETED);
        assertThatThrownBy(() -> service.save(27,input(null),List.of(),false)).isInstanceOf(ApiException.class);
        verify(archives,never()).insert(any(GoalCompletion.class));
    }
    @Test void missingHistoricalStateFallsBackWithoutDeletingAnything() {
        goal.setStatus(GoalStatus.COMPLETED);
        service.undo(27);
        verify(goals).update(isNull(),argThat((LambdaUpdateWrapper<LifeGoal> value) ->
                value.getParamNameValuePairs().containsValue(GoalStatus.NOT_STARTED)));
        verifyNoInteractions(details);
    }
    @Test void yearsContainOnlyDataYearsAndCurrentYearInDescendingOrder() {
        int current = LocalDate.now().getYear();
        when(archives.timeline()).thenReturn(List.of(
                new TimelineService.Entry(current-1,LocalDate.of(current-1,1,1),12,"a"),
                new TimelineService.Entry(current-1,LocalDate.of(current-1,1,1),31,"b"),
                new TimelineService.Entry(current+1,LocalDate.of(current+1,1,1),47,"c")));
        var timeline = new TimelineService(archives);
        assertThat(timeline.years()).containsExactly(new TimelineService.Year(current+1,1),
                new TimelineService.Year(current,0),new TimelineService.Year(current-1,2));
        assertThat(timeline.entries(current-1)).extracting(TimelineService.Entry::slotNo).containsExactly(12,31);
        assertThat(timeline.entries(current)).isEmpty();
    }
}
