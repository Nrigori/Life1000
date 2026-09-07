package com.life1000.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.life1000.entity.AppSetting;

public interface AppSettingMapper extends BaseMapper<AppSetting> {
    @org.apache.ibatis.annotations.Delete("DELETE FROM app_setting WHERE setting_key='HOME_FIXED_BACKGROUND_PATH' AND setting_value=#{path}")
    int clearFixed(@org.apache.ibatis.annotations.Param("path") String path);
}
