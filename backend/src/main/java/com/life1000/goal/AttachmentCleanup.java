package com.life1000.goal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.life1000.entity.GoalAttachment;
import com.life1000.mapper.GoalAttachmentMapper;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;

@Component
@Profile("mysql")
@EnableScheduling
public class AttachmentCleanup {
    private static final Logger log = LoggerFactory.getLogger(AttachmentCleanup.class);
    private final LocalFiles files;
    private final GoalAttachmentMapper attachments;
    public AttachmentCleanup(LocalFiles files, GoalAttachmentMapper attachments) {
        this.files = files; this.attachments = attachments;
    }
    public void prepare(List<GoalAttachment> values) throws IOException {
        var markers = new ArrayList<Path>();
        try {
            for (var attachment : values) markers.add(files.queue(attachment.getFilePath()));
        } catch (IOException exception) {
            for (var marker : markers) {
                try { Files.deleteIfExists(marker); } catch (IOException failure) { exception.addSuppressed(failure); }
            }
            throw exception;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    for (var marker : markers) {
                        try { Files.deleteIfExists(marker); } catch (IOException e) { log.warn("Cleanup marker retained", e); }
                    }
                } else { retry(); }
            }
        });
    }
    public void uploaded(String path) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                files.settled(path);
                retry();
            }
        });
    }
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public synchronized void retry() {
        try {
            for (var marker : files.pending()) {
                String path = Files.readString(marker);
                if (files.uploading(path)) continue;
                boolean exists = attachments.selectCount(new LambdaQueryWrapper<GoalAttachment>()
                        .eq(GoalAttachment::getFilePath, path)) > 0;
                try {
                    if (!exists) {
                        files.remove(path);
                        Files.deleteIfExists(marker);
                    } else if (marker.getFileName().toString().startsWith("upload-")) {
                        // A committed upload keeps its file; only its journal is removed.
                        Files.deleteIfExists(marker);
                    }
                } catch (IOException e) { log.warn("File cleanup will retry", e); }
            }
        } catch (Exception e) { log.warn("Attachment cleanup deferred", e); }
    }
}
