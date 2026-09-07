package com.life1000.settings;

import com.life1000.common.ApiException;
import com.life1000.goal.LocalFiles;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @Profile("mysql")
public class SettingsService {
    public static final Set<String> KEYS=Set.of("HOME_BACKGROUND_MODE","HOME_FIXED_BACKGROUND_PATH");
    public record Image(long attachmentId,int slotNo,String originalName,String filePath,boolean allowHomeBackground,boolean fixed) {}
    public record Usage(long imageCount,long documentCount,long totalBytes) {}
    public record Settings(String mode,String fixedPath,Image fixedImage,Usage files) {}
    private final JdbcTemplate jdbc;
    private final LocalFiles files;
    public SettingsService(JdbcTemplate jdbc,LocalFiles files) { this.jdbc=jdbc; this.files=files; }
    private String value(String key) {
        var values=jdbc.queryForList("SELECT setting_value FROM app_setting WHERE setting_key=?",String.class,key);
        return values.isEmpty()?null:values.getFirst();
    }
    public List<Image> images() {
        String fixed=value("HOME_FIXED_BACKGROUND_PATH");
        return jdbc.query("""
            SELECT a.id,g.slot_no,a.original_name,a.file_path,a.allow_home_background
            FROM goal_attachment a JOIN life_goal g ON g.id=a.goal_id
            WHERE a.is_image=TRUE ORDER BY g.slot_no,a.id
            """,(rs,n)->new Image(rs.getLong(1),rs.getInt(2),rs.getString(3),rs.getString(4),rs.getBoolean(5),Objects.equals(fixed,rs.getString(4))));
    }
    public Settings read() {
        String path=value("HOME_FIXED_BACKGROUND_PATH");
        Image fixed=path==null?null:images().stream().filter(Image::fixed).findFirst().orElse(null);
        if(fixed!=null) {
            try { if(!Files.isRegularFile(files.resolve(path))) fixed=null; }
            catch(IOException | RuntimeException e) { fixed=null; }
        }
        Usage usage=jdbc.queryForObject("""
            SELECT COALESCE(SUM(is_image=TRUE),0),COALESCE(SUM(is_image=FALSE),0),COALESCE(SUM(file_size),0)
            FROM goal_attachment
            """,(rs,n)->new Usage(rs.getLong(1),rs.getLong(2),rs.getLong(3)));
        return new Settings("FIXED".equalsIgnoreCase(value("HOME_BACKGROUND_MODE"))?"FIXED":"RANDOM",fixed==null?null:path,fixed,usage);
    }
    @Transactional(rollbackFor=IOException.class)
    public Settings update(Map<String,String> input) throws IOException {
        if(input.isEmpty() || !KEYS.containsAll(input.keySet())) throw ApiException.badRequest("只允许修改首页背景设置");
        if(input.containsKey("HOME_BACKGROUND_MODE") && !Set.of("RANDOM","FIXED").contains(Objects.toString(input.get("HOME_BACKGROUND_MODE"),"")))
            throw ApiException.badRequest("背景模式必须为 RANDOM 或 FIXED");
        String path=input.get("HOME_FIXED_BACKGROUND_PATH");
        if(path!=null && !path.isBlank()) {
            // Same parent-first lock order as attachment/goal deletion.
            var parents=jdbc.queryForList("SELECT goal_id FROM goal_attachment WHERE file_path=? AND is_image=TRUE",Long.class,path);
            if(parents.isEmpty()) throw ApiException.badRequest("请选择已有的图片附件");
            jdbc.queryForList("SELECT id FROM life_goal WHERE id=? FOR UPDATE",parents.getFirst());
            if(jdbc.queryForList("SELECT id FROM goal_attachment WHERE file_path=? AND is_image=TRUE FOR UPDATE",Long.class,path).isEmpty()
                    || !Files.isRegularFile(files.resolve(path))) throw ApiException.badRequest("图片已经不存在，请重新选择");
        }
        for(var entry:input.entrySet()) {
            if(entry.getKey().equals("HOME_FIXED_BACKGROUND_PATH") && (entry.getValue()==null || entry.getValue().isBlank()))
                jdbc.update("DELETE FROM app_setting WHERE setting_key=?",entry.getKey());
            else jdbc.update("""
                INSERT INTO app_setting(setting_key,setting_value) VALUES(?,?)
                ON DUPLICATE KEY UPDATE setting_value=VALUES(setting_value)
                """,entry.getKey(),entry.getValue());
        }
        return read();
    }
}
