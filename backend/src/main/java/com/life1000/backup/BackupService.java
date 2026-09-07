package com.life1000.backup;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.life1000.goal.LocalFiles;
import com.life1000.settings.SettingsService;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.zip.*;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service @Profile("mysql")
public class BackupService {
    private static final List<String> TABLES=List.of("category","life_goal","goal_check_item","goal_record","goal_completion","goal_attachment","quote","app_setting");
    private final JdbcTemplate jdbc;
    private final LocalFiles files;
    private final ObjectMapper json;
    public BackupService(JdbcTemplate jdbc,LocalFiles files,ObjectMapper json) { this.jdbc=jdbc; this.files=files; this.json=json; }
    public record Export(Path directory,Path zip,String name) implements AutoCloseable {
        @Override public void close() throws IOException {
            if(!Files.exists(directory)) return;
            try(var paths=Files.walk(directory)) {
                for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    // 同次导出的业务表读取使用可重复读快照；父事项、附件按统一顺序加共享锁，阻止导出中被删除。
    @Transactional(isolation=Isolation.REPEATABLE_READ,rollbackFor=IOException.class)
    public Export create() throws IOException {
        // Parent-first locks protect committed immutable attachment files until the ZIP is complete.
        jdbc.queryForList("SELECT id FROM life_goal ORDER BY id FOR SHARE");
        jdbc.queryForList("SELECT id FROM goal_attachment ORDER BY id FOR SHARE");
        Map<String,Object> data=new LinkedHashMap<>();
        for(String table:TABLES) {
            var rows=jdbc.queryForList("SELECT * FROM "+table+" ORDER BY id"); // table names are constants
            for(var row:rows) row.replaceAll((key,value)->value instanceof java.sql.Timestamp timestamp?timestamp.toLocalDateTime().toString()
                    :value instanceof java.sql.Date date?date.toLocalDate().toString():value);
            // Only application settings are business data; no environment/configuration secrets are read.
            if(table.equals("app_setting")) rows.removeIf(row->!SettingsService.KEYS.contains(row.get("setting_key")));
            data.put(table,rows);
        }
        @SuppressWarnings("unchecked") var goals=(List<Map<String,Object>>)data.get("life_goal");
        @SuppressWarnings("unchecked") var attachments=(List<Map<String,Object>>)data.get("goal_attachment");
        Map<Long,Integer> slots=new HashMap<>();
        for(var goal:goals) slots.put(((Number)goal.get("id")).longValue(),((Number)goal.get("slot_no")).intValue());
        String name="Life1000_Backup_"+LocalDate.now()+".zip";
        Path directory=Files.createTempDirectory("life1000-backup-");
        Export result=new Export(directory,directory.resolve(name),name);
        try {
            var missing=new ArrayList<Map<String,Object>>();
            long imageCount=0;
            try(var zip=new ZipOutputStream(Files.newOutputStream(result.zip()),StandardCharsets.UTF_8)) {
                zip.putNextEntry(new ZipEntry("images/")); zip.closeEntry();
                zip.putNextEntry(new ZipEntry("documents/")); zip.closeEntry();
                for(var attachment:attachments) {
                    boolean image=Boolean.TRUE.equals(attachment.get("is_image")) || "1".equals(String.valueOf(attachment.get("is_image")));
                    if(image) imageCount++;
                    long id=((Number)attachment.get("id")).longValue();
                    int slot=slots.get(((Number)attachment.get("goal_id")).longValue());
                    String original=String.valueOf(attachment.get("original_name"));
                    String safe=original.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").replaceAll("[. ]+$","");
                    if(safe.isBlank()) safe="attachment";
                    safe=safe.codePoints().limit(100).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();
                    // 编号与附件 id 构成稳定唯一前缀；export_file 将 JSON 元数据与 ZIP 条目对应起来。
                    String entry=(image?"images/":"documents/")+"%03d_%d_".formatted(slot,id)+safe;
                    String path=String.valueOf(attachment.get("file_path"));
                    // 先确认整个文件能复制到临时区，再写 ZIP；缺失文件保留元数据并写入说明，不输出半个条目。
                    Path staging=directory.resolve("attachment-"+id);
                    try {
                        if(!path.matches("goals/[0-9]{3,4}/[a-fA-F0-9-]{36}")) throw new IOException("Unsafe attachment path");
                        Files.copy(files.resolve(path),staging);
                    } catch(IOException | RuntimeException failure) {
                        LoggerFactory.getLogger(BackupService.class).warn("Backup could not read attachment {}",id,failure);
                        Files.deleteIfExists(staging);
                        attachment.put("export_file",null);
                        missing.add(Map.of("attachmentId",id,"slotNo",slot,"originalName",original,"reason","文件缺失、不可读取或路径不合法"));
                        continue;
                    }
                    attachment.put("export_file",entry);
                    zip.putNextEntry(new ZipEntry(entry)); Files.copy(staging,zip); zip.closeEntry();
                    Files.delete(staging);
                }
                write(zip,"life1000.json",data);
                Map<String,Object> info=new LinkedHashMap<>();
                info.put("backupVersion",1); info.put("application","Life1000"); info.put("createdAt",OffsetDateTime.now().toString());
                info.put("totalGoals",goals.size()); info.put("totalAttachments",attachments.size());
                info.put("imageCount",imageCount); info.put("documentCount",attachments.size()-imageCount);
                info.put("missingFiles",missing);
                info.put("settingsScope","Only HOME_BACKGROUND_MODE and HOME_FIXED_BACKGROUND_PATH; environment secrets are never exported.");
                write(zip,"backup-info.json",info);
            }
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override public void afterCompletion(int status) {
                            if (status != STATUS_COMMITTED) {
                                try { result.close(); } catch(IOException e) { LoggerFactory.getLogger(BackupService.class).warn("Backup cleanup failed",e); }
                            }
                        }
                    });
            return result;
        } catch(IOException | RuntimeException failure) {
            try { result.close(); } catch(IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
    private void write(ZipOutputStream zip,String name,Object value) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(value));
        zip.closeEntry();
    }
}
