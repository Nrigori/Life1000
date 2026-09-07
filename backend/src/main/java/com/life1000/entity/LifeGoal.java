package com.life1000.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("life_goal")
public class LifeGoal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer slotNo;
    private String title;
    private Long categoryId;
    private GoalStatus status;
    private String reason;
    private Long coverAttachmentId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private java.time.LocalDate completedDate;
    public java.time.LocalDate getCompletedDate() { return completedDate; }
    public void setCompletedDate(java.time.LocalDate value) { completedDate = value; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getSlotNo() { return slotNo; }
    public void setSlotNo(Integer slotNo) { this.slotNo = slotNo; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public GoalStatus getStatus() { return status; }
    public void setStatus(GoalStatus status) { this.status = status; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getCoverAttachmentId() { return coverAttachmentId; }
    public void setCoverAttachmentId(Long coverAttachmentId) { this.coverAttachmentId = coverAttachmentId; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
