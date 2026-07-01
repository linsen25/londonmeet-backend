package com.londonmeet.pojo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "activity_registrations")
public class ActivityRegistration {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_JOINED_GROUP = "joined_group";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_BLACKLISTED = "blacklisted";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_ACTIVITY_CANCELLED = "activity_cancelled";
    public static final String STATUS_EXPIRED = "expired";

    public static final int NOTICE_APPLICATION_SUBMITTED = 1001;
    public static final int NOTICE_FULL = 1002;
    public static final int NOTICE_TIME_CONFLICT = 1003;
    public static final int NOTICE_APPROVED = 1004;
    public static final int NOTICE_JOINED_GROUP = 1005;
    public static final int NOTICE_REJECTED = 1006;
    public static final int NOTICE_CANCELLED = 1007;
    public static final int NOTICE_WAITING_FULL = 1008;
    public static final int NOTICE_EXPIRED = 1009;
    public static final int NOTICE_BLACKLISTED = 1010;
    public static final int NOTICE_ACTIVITY_CANCELLED = 1011;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "activity_id", nullable = false)
    private Long activityId;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "notice_code", nullable = false)
    private Integer noticeCode;

    @Column(name = "application_text", length = 100)
    private String applicationText;

    @Column(name = "cancellation_reason_type", length = 30)
    private String cancellationReasonType;

    @Column(name = "cancellation_reason_text", length = 100)
    private String cancellationReasonText;

    @Column(name = "review_reason_type", length = 30)
    private String reviewReasonType;

    @Column(name = "review_reason_text", length = 200)
    private String reviewReasonText;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "joined_group_at")
    private LocalDateTime joinedGroupAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (status == null) {
            status = STATUS_PENDING;
        }
        if (noticeCode == null) {
            noticeCode = NOTICE_APPLICATION_SUBMITTED;
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
