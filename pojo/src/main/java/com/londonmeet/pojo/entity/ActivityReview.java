package com.londonmeet.pojo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "activity_reviews")
public class ActivityReview {

    public static final String TARGET_ACTIVITY = "activity";
    public static final String TARGET_MEMBER = "member";
    public static final String STATUS_NORMAL = "NORMAL";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_EXCLUDED = "EXCLUDED";
    public static final long ACTIVITY_TARGET_ID = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reviewer_user_id", nullable = false)
    private Long reviewerUserId;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "overall_score", nullable = false, precision = 3, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "scores_json", nullable = false, columnDefinition = "TEXT")
    private String scoresJson;

    @Column(name = "reason", length = 300)
    private String reason;

    @Column(name = "batch_good", nullable = false)
    private Boolean batchGood;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "admin_note", length = 500)
    private String adminNote;

    @Column(name = "handled_by")
    private Long handledBy;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (targetId == null) {
            targetId = ACTIVITY_TARGET_ID;
        }
        if (status == null) {
            status = STATUS_NORMAL;
        }
        if (batchGood == null) {
            batchGood = false;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
