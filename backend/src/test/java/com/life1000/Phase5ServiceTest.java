package com.life1000;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.life1000.entity.*;
import com.life1000.home.HomeService;
import com.life1000.quote.QuoteService;
import com.life1000.mapper.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class Phase5ServiceTest {
    QuoteMapper quotes=mock(QuoteMapper.class);
    GoalAttachmentMapper attachments=mock(GoalAttachmentMapper.class);
    AppSettingMapper settings=mock(AppSettingMapper.class);
    LifeGoalMapper goals=mock(LifeGoalMapper.class);
    @BeforeEach void init() {
        for(var type:new Class<?>[]{Quote.class,GoalAttachment.class,AppSetting.class})
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),type);
    }
    @Test void newQuotesDefaultToHomeAndSourceCanBeCleared() {
        var service=new QuoteService(quotes);
        service.create(new QuoteService.Input(" words ",null,null));
        verify(quotes).insert(argThat((Quote value)->value.getContent().equals("words") && value.getIncludeHome()));
        when(quotes.selectById(1L)).thenReturn(new Quote());
        service.update(1,new QuoteService.Input("words",null,false));
        verify(quotes).update(isNull(),argThat((LambdaUpdateWrapper<Quote> query)->
                query.getSqlSet().contains("source") && query.getParamNameValuePairs().containsValue(null)
                && query.getParamNameValuePairs().containsValue(false)));
    }
    @Test void randomQuoteFiltersHomeFlagAndEmptyReturnsNull() {
        assertThat(new QuoteService(quotes).random()).isNull();
        verify(quotes).selectOne(argThat((LambdaQueryWrapper<Quote> query)->
                query.getSqlSegment().contains("include_home") && query.getSqlSegment().contains("ORDER BY RAND() LIMIT 1")
                && query.getParamNameValuePairs().containsValue(true)));
    }
    @Test void searchEscapesWildcardsAndMatchesBothContentAndSource() {
        new QuoteService(quotes).list("100%_!");
        verify(quotes).selectList(argThat((LambdaQueryWrapper<Quote> query)->
                query.getSqlSegment().contains("content LIKE") && query.getSqlSegment().contains("source LIKE")
                && query.getParamNameValuePairs().containsValue("%100!%!_!!%")));
    }
    @Test void defaultBackgroundRequiresBothImageAndEligibility() {
        assertThat(new HomeService(goals,attachments,settings).background()).isNull();
        verify(attachments).selectOne(argThat((LambdaQueryWrapper<GoalAttachment> query)->
                query.getSqlSegment().contains("is_image") && query.getSqlSegment().contains("allow_home_background")
                && query.getSqlSegment().contains("ORDER BY RAND() LIMIT 1")
                && !query.getSqlSegment().contains("stage") && !query.getSqlSegment().contains("status")));
    }
    @Test void fixedBackgroundUsesExistingAttachmentPathWithoutEligibilityFilter() {
        var mode=new AppSetting(); mode.setSettingValue("FIXED");
        var path=new AppSetting(); path.setSettingValue("goals/027/uuid");
        when(settings.selectOne(any())).thenReturn(mode,path);
        new HomeService(goals,attachments,settings).background();
        verify(attachments).selectOne(argThat((LambdaQueryWrapper<GoalAttachment> query)->
                query.getSqlSegment().contains("file_path") && !query.getSqlSegment().contains("allow_home_background")
                && query.getParamNameValuePairs().containsValue("goals/027/uuid")));
    }
    @Test void invalidFixedConfigurationDoesNotFallBackToArbitraryFiles() {
        var mode=new AppSetting(); mode.setSettingValue("FIXED");
        when(settings.selectOne(any())).thenReturn(mode,null);
        assertThat(new HomeService(goals,attachments,settings).background()).isNull();
        verifyNoInteractions(attachments);
    }
    @Test void statsUseCurrentYearBoundariesInOneQuery() {
        new HomeService(goals,attachments,settings).stats();
        LocalDate start=LocalDate.now().withDayOfYear(1);
        verify(goals).stats(start,start.plusYears(1));
        verifyNoInteractions(attachments,settings);
    }
}
