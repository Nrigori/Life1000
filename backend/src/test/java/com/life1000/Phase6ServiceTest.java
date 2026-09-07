package com.life1000;
import com.life1000.settings.*;
import com.life1000.backup.*;
import com.life1000.goal.*;
import com.life1000.entity.GoalAttachment;
import com.life1000.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class Phase6ServiceTest {
    @TempDir Path directory;
    JdbcTemplate jdbc=mock(JdbcTemplate.class);
    @Test void settingsRejectUnknownKeysAndModesBeforeWriting() {
        var service=new SettingsService(jdbc,new LocalFiles(directory.toString()));
        for(var input:List.of(Map.of("DB_PASSWORD","secret"),Map.of("HOME_BACKGROUND_MODE","ROTATE"),Map.<String,String>of()))
            assertThatThrownBy(()->service.update(input)).hasMessageContaining(input.containsKey("HOME_BACKGROUND_MODE")?"背景模式":"只允许");
        verifyNoInteractions(jdbc);
    }
    @Test void fixedBackgroundRejectsUnknownOrDocumentPath() {
        when(jdbc.queryForList(anyString(),eq(Long.class),any())).thenReturn(List.of());
        var service=new SettingsService(jdbc,new LocalFiles(directory.toString()));
        assertThatThrownBy(()->service.update(Map.of("HOME_FIXED_BACKGROUND_PATH","../private.txt"))).hasMessageContaining("请选择已有");
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }
    @Test void attachmentCleanupClearsFixedReferenceInTheSameTransaction() throws Exception {
        var settings=mock(AppSettingMapper.class);
        var attachments=mock(GoalAttachmentMapper.class);
        var cleanup=new AttachmentCleanup(new LocalFiles(directory.toString()),attachments,settings);
        var value=new GoalAttachment();value.setFilePath("goals/027/"+UUID.randomUUID());
        TransactionSynchronizationManager.initSynchronization();
        try{cleanup.prepare(List.of(value));verify(settings).clearFixed(value.getFilePath());}
        finally{TransactionSynchronizationManager.clearSynchronization();}
    }
    @Test void zipContainsAllTablesDistinctNamesAndMissingManifestButNoPrivateFiles() throws Exception {
        when(jdbc.queryForList(anyString())).thenAnswer(call->new ArrayList<Map<String,Object>>());
        var goals=new ArrayList<Map<String,Object>>();goals.add(new LinkedHashMap<>(Map.of("id",1L,"slot_no",27)));
        var attachments=new ArrayList<Map<String,Object>>();
        for(int id=1;id<=4;id++) {
            String path="goals/027/"+UUID.randomUUID();
            if(id<=2){Path p=directory.resolve(path);Files.createDirectories(p.getParent());Files.writeString(p,"contents-"+id);}
            if(id==4)path=".cleanup/private";
            attachments.add(new LinkedHashMap<>(Map.of("id",id,"goal_id",1,"original_name","../同名.txt","file_path",path,"is_image",id==1,"file_size",10)));
        }
        Files.createDirectories(directory.resolve(".cleanup"));Files.writeString(directory.resolve(".cleanup/private"),"MUST_NOT_EXPORT");
        when(jdbc.queryForList("SELECT * FROM life_goal ORDER BY id")).thenReturn(goals);
        when(jdbc.queryForList("SELECT * FROM goal_attachment ORDER BY id")).thenReturn(attachments);
        when(jdbc.queryForList("SELECT * FROM app_setting ORDER BY id")).thenReturn(new ArrayList<>(List.of(
            new LinkedHashMap<>(Map.of("id",1,"setting_key","DB_PASSWORD","setting_value","MUST_NOT_EXPORT")),
            new LinkedHashMap<>(Map.of("id",2,"setting_key","HOME_BACKGROUND_MODE","setting_value","RANDOM")))));
        var json=new ObjectMapper();
        var service=new BackupService(jdbc,new LocalFiles(directory.toString()),json);
        Path dir;
        try(var result=service.create();var zip=new ZipFile(result.zip().toFile())) {
            dir=result.directory();
            var data=json.readTree(zip.getInputStream(zip.getEntry("life1000.json")));
            assertThat(data.size()).isEqualTo(8);
            assertThat(data.get("app_setting").size()).isEqualTo(1);
            var info=json.readTree(zip.getInputStream(zip.getEntry("backup-info.json")));
            assertThat(info.get("missingFiles").size()).isEqualTo(2);
            assertThat(info.get("backupVersion").asInt()).isEqualTo(1);
            assertThat(info.get("imageCount").asInt()).isEqualTo(1);
            assertThat(info.get("documentCount").asInt()).isEqualTo(3);
            var rows=data.get("goal_attachment");
            assertThat(rows.get(0).get("export_file").asText()).startsWith("images/027_1_").doesNotContain("../");
            assertThat(rows.get(1).get("export_file").asText()).startsWith("documents/027_2_");
            for(int i=0;i<2;i++)assertThat(new String(zip.getInputStream(zip.getEntry(rows.get(i).get("export_file").asText())).readAllBytes())).isEqualTo("contents-"+(i+1));
            assertThat(data.toString()).doesNotContain("MUST_NOT_EXPORT");
            assertThat(zip.stream().map(e->e.getName())).noneMatch(name->name.contains(".cleanup")||name.contains("src/"));
        }
        assertThat(dir).doesNotExist();
    }

    @Test void failedTransactionCleansAnAlreadyPreparedZip() throws Exception {
        when(jdbc.queryForList(anyString())).thenAnswer(call->new ArrayList<Map<String,Object>>());
        TransactionSynchronizationManager.initSynchronization();
        try {
            var result=new BackupService(jdbc,new LocalFiles(directory.toString()),new ObjectMapper()).create();
            assertThat(result.zip()).exists();
            for(var sync:TransactionSynchronizationManager.getSynchronizations())
                sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            assertThat(result.directory()).doesNotExist();
            result.close(); // Safe even if the download/controller has already cleaned it.
        } finally {TransactionSynchronizationManager.clearSynchronization();}
    }

}
