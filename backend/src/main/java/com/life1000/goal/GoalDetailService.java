package com.life1000.goal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.life1000.common.ApiException;
import com.life1000.entity.*;
import com.life1000.mapper.*;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;
import javax.imageio.ImageIO;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Profile("mysql")
@Transactional(rollbackFor = IOException.class)
public class GoalDetailService {
    public record CheckInput(@NotBlank @Size(max=10000) String content, @NotNull Boolean completed) {}
    public record RecordInput(@NotNull LocalDate recordDate, @NotBlank String content) {}
    private final LifeGoalMapper goals;
    private final GoalCheckItemMapper checks;
    private final GoalRecordMapper records;
    private final GoalAttachmentMapper attachments;
    private final LocalFiles files;
    private final AttachmentCleanup cleanup;
    public GoalDetailService(LifeGoalMapper goals, GoalCheckItemMapper checks, GoalRecordMapper records,
                             GoalAttachmentMapper attachments, LocalFiles files, AttachmentCleanup cleanup) {
        this.goals=goals; this.checks=checks; this.records=records; this.attachments=attachments; this.files=files; this.cleanup=cleanup;
    }
    private LifeGoal goal(int slot) {
        LifeGoalService.validateSlot(slot);
        var value=goals.selectOne(new LambdaQueryWrapper<LifeGoal>().eq(LifeGoal::getSlotNo,slot).last("FOR UPDATE"));
        if(value==null) throw ApiException.notFound("该编号尚未写下");
        return value;
    }
    public List<GoalCheckItem> checks(int slot) {
        return checks.selectList(new LambdaQueryWrapper<GoalCheckItem>().eq(GoalCheckItem::getGoalId,goal(slot).getId())
                .orderByAsc(GoalCheckItem::getSortOrder,GoalCheckItem::getId));
    }
    public GoalCheckItem addCheck(int slot, CheckInput input) {
        var existing=checks(slot);
        var item=new GoalCheckItem();
        item.setGoalId(goal(slot).getId()); item.setContent(input.content().strip()); item.setCompleted(input.completed());
        item.setSortOrder(existing.stream().mapToInt(GoalCheckItem::getSortOrder).max().orElse(-1)+1);
        checks.insert(item); return checks.selectById(item.getId());
    }
    public GoalCheckItem updateCheck(long id, CheckInput input) {
        var item=checks.selectById(id); if(item==null) throw ApiException.notFound("完成条件不存在");
        item.setContent(input.content().strip()); item.setCompleted(input.completed()); checks.updateById(item);
        return checks.selectById(id);
    }
    public void deleteCheck(long id) { if(checks.deleteById(id)==0) throw ApiException.notFound("完成条件不存在"); }
    public List<GoalRecord> records(int slot) {
        return records.selectList(new LambdaQueryWrapper<GoalRecord>().eq(GoalRecord::getGoalId,goal(slot).getId())
                .orderByAsc(GoalRecord::getRecordDate,GoalRecord::getId));
    }
    public GoalRecord addRecord(int slot, RecordInput input) {
        var value=new GoalRecord(); value.setGoalId(goal(slot).getId());
        value.setContent(input.content()); value.setRecordDate(input.recordDate()); records.insert(value);
        return records.selectById(value.getId());
    }
    private GoalRecord record(long id) {
        var value=records.selectById(id); if(value==null) throw ApiException.notFound("记录不存在"); return value;
    }
    public GoalRecord updateRecord(long id, RecordInput input) {
        var value=record(id); value.setContent(input.content()); value.setRecordDate(input.recordDate()); records.updateById(value);
        return records.selectById(id);
    }
    public void deleteRecord(long id) throws IOException {
        var value=record(id);
        // Serialize attachment creation/deletion on the parent goal.
        goals.selectOne(new LambdaQueryWrapper<LifeGoal>().eq(LifeGoal::getId,value.getGoalId()).last("FOR UPDATE"));
        cleanup.prepare(attachments.selectList(new LambdaQueryWrapper<GoalAttachment>().eq(GoalAttachment::getRecordId,id).last("FOR UPDATE")));
        records.deleteById(id);
    }
    public List<GoalAttachment> attachments(int slot) {
        return attachments.selectList(new LambdaQueryWrapper<GoalAttachment>().eq(GoalAttachment::getGoalId,goal(slot).getId())
                .in(GoalAttachment::getStage,"GENERAL","PROCESS").orderByAsc(GoalAttachment::getCreatedAt,GoalAttachment::getId));
    }
    public GoalAttachment attachment(long id) {
        var value=attachments.selectById(id);
        if(value==null || !Set.of("GENERAL","PROCESS").contains(value.getStage())) throw ApiException.notFound("附件不存在");
        return value;
    }
    public GoalAttachment upload(int slot, Long recordId, String stage, MultipartFile file) throws IOException {
        var parent=goal(slot);
        if (!Set.of("GENERAL","PROCESS").contains(stage) || ("PROCESS".equals(stage) != (recordId != null)))
            throw ApiException.badRequest("附件阶段与过程记录不匹配");
        if(recordId!=null && !record(recordId).getGoalId().equals(parent.getId()))
            throw ApiException.badRequest("过程记录不属于当前事项");
        if(file.isEmpty()) throw ApiException.badRequest("不能上传空文件");
        String name=Optional.ofNullable(file.getOriginalFilename()).orElse("未命名文件").replace('\\','/');
        name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "_");
        if(name.isBlank() || name.length()>255) throw ApiException.badRequest("文件名须为 1～255 个字符");
        String path=files.save(slot,file);
        cleanup.uploaded(path);
        var value=new GoalAttachment();
        value.setGoalId(parent.getId()); value.setRecordId(recordId); value.setStage(stage);
        value.setFilePath(path); value.setFileName(java.nio.file.Path.of(path).getFileName().toString());
        value.setOriginalName(name); value.setFileSize(Files.size(files.resolve(path)));
        String extension=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        String mime=switch(extension) {
            case "pdf" -> "application/pdf";
            case "txt" -> "text/plain";
            case "zip" -> "application/zip";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
        // Only actual raster images supported by ImageIO are served inline. SVG/HTML remain downloads.
        try(var input=ImageIO.createImageInputStream(files.resolve(path).toFile())) {
            var readers=ImageIO.getImageReaders(input);
            if(readers.hasNext()) {
                var reader=readers.next();
                try {
                    reader.setInput(input);
                    String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                    if(Set.of("jpeg","jpg","png","gif","bmp").contains(format)
                            && reader.getWidth(0)>0 && reader.getHeight(0)>0)
                        mime="image/"+(format.equals("jpg")?"jpeg":format);
                } finally { reader.dispose(); }
            }
        } catch(IOException ignored) { /* A malformed image remains a downloadable file. */ }
        value.setMimeType(mime); value.setIsImage(mime.startsWith("image/")); value.setAllowHomeBackground(false);
        attachments.insert(value); return attachments.selectById(value.getId());
    }
    public void deleteAttachment(long id) throws IOException {
        var value=attachment(id);
        goals.selectOne(new LambdaQueryWrapper<LifeGoal>().eq(LifeGoal::getId,value.getGoalId()).last("FOR UPDATE"));
        cleanup.prepare(List.of(value));
        attachments.deleteById(id); // FK clears any explicit cover.
    }
    public GoalAttachment cover(int slot) {
        var parent=goal(slot);
        if(parent.getCoverAttachmentId()!=null) {
            var selected=attachments.selectById(parent.getCoverAttachmentId());
            if(selected!=null && selected.getGoalId().equals(parent.getId()) && Boolean.TRUE.equals(selected.getIsImage())) return selected;
        }
        return attachments.selectOne(new LambdaQueryWrapper<GoalAttachment>().eq(GoalAttachment::getGoalId,parent.getId())
                .eq(GoalAttachment::getIsImage,true).in(GoalAttachment::getStage,"GENERAL","PROCESS")
                .orderByDesc(GoalAttachment::getCreatedAt,GoalAttachment::getId).last("LIMIT 1"));
    }
    public void setCover(int slot,long id) {
        var parent=goal(slot); var image=attachment(id);
        if(!image.getGoalId().equals(parent.getId()) || !Boolean.TRUE.equals(image.getIsImage()))
            throw ApiException.badRequest("封面必须是当前事项的图片");
        goals.update(null,new LambdaUpdateWrapper<LifeGoal>().eq(LifeGoal::getId,parent.getId()).set(LifeGoal::getCoverAttachmentId,id));
    }
    public GoalAttachment homeBackground(long id,boolean allowed) {
        var image=attachment(id);
        if(!Boolean.TRUE.equals(image.getIsImage())) throw ApiException.badRequest("只有图片可以作为首页背景");
        image.setAllowHomeBackground(allowed); attachments.updateById(image); return attachments.selectById(id);
    }
}
