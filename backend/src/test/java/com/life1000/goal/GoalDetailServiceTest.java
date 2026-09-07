package com.life1000.goal;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life1000.common.ApiException;
import com.life1000.entity.*;
import com.life1000.mapper.*;
import java.nio.file.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class GoalDetailServiceTest {
    @TempDir Path directory;
    LifeGoalMapper goals=mock(LifeGoalMapper.class);
    GoalCheckItemMapper checks=mock(GoalCheckItemMapper.class);
    GoalRecordMapper records=mock(GoalRecordMapper.class);
    GoalAttachmentMapper attachments=mock(GoalAttachmentMapper.class);
    LocalFiles files;
    AttachmentCleanup cleanup;
    GoalDetailService service;
    @BeforeEach void setup() {
        for(var type: new Class<?>[]{LifeGoal.class,GoalCheckItem.class,GoalRecord.class,GoalAttachment.class})
            TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),type);
        files=new LocalFiles(directory.toString());
        cleanup=new AttachmentCleanup(files,attachments);
        service=new GoalDetailService(goals,checks,records,attachments,files,cleanup);
        var goal=new LifeGoal(); goal.setId(10L); goal.setSlotNo(27);
        when(goals.selectOne(any())).thenReturn(goal);
        when(attachments.insert(any(GoalAttachment.class))).thenAnswer(call -> {
            GoalAttachment value=call.getArgument(0); value.setId(1L);
            when(attachments.selectById(1L)).thenReturn(value); return 1;
        });
        TransactionSynchronizationManager.initSynchronization();
    }
    @AfterEach void reset() { TransactionSynchronizationManager.clearSynchronization(); }
    void finish(int status) {
        for(var sync:TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(status);
    }
    @Test void uuidStoragePreservesSafeOriginalNameAndRollbackRemovesFile() throws Exception {
        var upload=new MockMultipartFile("file","../旅行.txt","text/plain","hello".getBytes());
        var value=service.upload(27,null,"GENERAL",upload);
        assertThat(value.getOriginalName()).isEqualTo("旅行.txt");
        assertThat(value.getFilePath()).startsWith("goals/027/");
        assertThat(value.getFileName()).doesNotContain("旅行");
        assertThat(value.getFileSize()).isEqualTo(5);
        assertThat(value.getIsImage()).isFalse();
        assertThat(Files.readString(files.resolve(value.getFilePath()))).isEqualTo("hello");
        finish(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(files.resolve(value.getFilePath())).doesNotExist();
    }
    @Test void sameNameCannotOverwriteAndClientMimeDoesNotMakeHtmlAnImage() throws Exception {
        var upload=new MockMultipartFile("file","photo.png","image/png","<script>x</script>".getBytes());
        var first=service.upload(27,null,"GENERAL",upload);
        var second=service.upload(27,null,"GENERAL",upload);
        assertThat(first.getFilePath()).isNotEqualTo(second.getFilePath());
        assertThat(first.getIsImage()).isFalse();
        assertThat(first.getMimeType()).isEqualTo("application/octet-stream");
    }
    @Test void realRasterImageCanBeSelectedAndFlagged() throws Exception {
        var bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",bytes);
        var value=service.upload(27,null,"GENERAL",new MockMultipartFile("file","a.png","text/plain",bytes.toByteArray()));
        assertThat(value.getIsImage()).isTrue();
        assertThat(value.getMimeType()).isEqualTo("image/png");
        service.setCover(27,value.getId());
        service.homeBackground(value.getId(),true);
        assertThat(value.getAllowHomeBackground()).isTrue();
        verify(goals).update(isNull(),any());
    }
    @Test void rejectsTraversalStageMismatchAndForeignRecord() {
        assertThatThrownBy(()->files.resolve("../outside")).isInstanceOf(ApiException.class);
        var file=new MockMultipartFile("file","a.txt","text/plain",new byte[]{1});
        assertThatThrownBy(()->service.upload(27,null,"COMPLETION",file)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.upload(27,1L,"GENERAL",file)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.upload(27,null,"PROCESS",file)).isInstanceOf(ApiException.class);
        var record=new GoalRecord(); record.setGoalId(99L); when(records.selectById(1L)).thenReturn(record);
        assertThatThrownBy(()->service.upload(27,1L,"PROCESS",file)).isInstanceOf(ApiException.class);
        verify(attachments,never()).insert(any(GoalAttachment.class));
    }
    @Test void foreignAndNonImageCannotBeCoverOrBackground() {
        var file=new GoalAttachment(); file.setStage("GENERAL"); file.setGoalId(99L); file.setIsImage(true);
        when(attachments.selectById(1L)).thenReturn(file);
        assertThatThrownBy(()->service.setCover(27,1L)).isInstanceOf(ApiException.class);
        file.setGoalId(10L); file.setIsImage(false);
        assertThatThrownBy(()->service.setCover(27,1L)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->service.homeBackground(1L,true)).isInstanceOf(ApiException.class);
        verify(goals,never()).update(isNull(),any());
    }
    @Test void committedDeletionRemovesFileButRollbackRetainsItAndRetrySurvivesNewInstance() throws Exception {
        String path=files.save(27,new MockMultipartFile("file","a.txt","text/plain",new byte[]{1}));
        var value=new GoalAttachment(); value.setFilePath(path);
        when(attachments.selectCount(any())).thenReturn(1L);
        cleanup.prepare(java.util.List.of(value));
        finish(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(files.resolve(path)).exists();
        files.queue(path);
        when(attachments.selectCount(any())).thenReturn(0L);
        new AttachmentCleanup(new LocalFiles(directory.toString()),attachments).retry();
        assertThat(files.resolve(path)).doesNotExist();
        assertThat(files.pending()).isEmpty();
    }
    @Test void explicitCoverWinsThenRecentImageIsFallback() {
        var parent=new LifeGoal(); parent.setId(10L); parent.setCoverAttachmentId(7L);
        when(goals.selectOne(any())).thenReturn(parent);
        var explicit=new GoalAttachment(); explicit.setGoalId(10L); explicit.setIsImage(true);
        when(attachments.selectById(7L)).thenReturn(explicit);
        assertThat(service.cover(27)).isSameAs(explicit);
        verify(attachments,never()).selectOne(any());
        parent.setCoverAttachmentId(null);
        var recent=new GoalAttachment(); when(attachments.selectOne(any())).thenReturn(recent);
        assertThat(service.cover(27)).isSameAs(recent);
        when(attachments.selectOne(any())).thenReturn(null);
        assertThat(service.cover(27)).isNull();
    }

    @Test void restartRecoversUncommittedUploadButKeepsCommittedFile() throws Exception {
        String orphan=files.save(27,new MockMultipartFile("file","lost.txt","text/plain",new byte[]{1}));
        cleanup.retry();
        assertThat(files.resolve(orphan)).exists(); // Active upload cannot be swept.
        var restartedFiles=new LocalFiles(directory.toString());
        new AttachmentCleanup(restartedFiles,attachments).retry();
        assertThat(files.resolve(orphan)).doesNotExist();

        String committed=restartedFiles.save(27,new MockMultipartFile("file","saved.txt","text/plain",new byte[]{2}));
        when(attachments.selectCount(any())).thenReturn(1L);
        new AttachmentCleanup(new LocalFiles(directory.toString()),attachments).retry();
        assertThat(restartedFiles.resolve(committed)).exists();
        assertThat(restartedFiles.pending()).isEmpty();
    }


    @Test void completionProofUsesSameStorageWithNullRecordAndSurvivesCommit() throws Exception {
        var parent = new LifeGoal(); parent.setId(10L); parent.setStatus(GoalStatus.COMPLETED);
        when(goals.selectOne(any())).thenReturn(parent);
        var proof = service.upload(27,null,"COMPLETION",new MockMultipartFile("file","proof.txt","text/plain","proof".getBytes()));
        assertThat(proof.getStage()).isEqualTo("COMPLETION");
        assertThat(proof.getRecordId()).isNull();
        assertThat(proof.getGoalId()).isEqualTo(10L);
        when(attachments.selectCount(any())).thenReturn(1L);
        finish(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(Files.readString(files.resolve(proof.getFilePath()))).isEqualTo("proof");
        assertThat(files.pending()).isEmpty();
    }

}
