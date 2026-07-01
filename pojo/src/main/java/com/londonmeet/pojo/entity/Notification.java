package com.londonmeet.pojo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notifications")
public class Notification {

    public static final String TYPE_REGISTRATION_SUBMITTED_CREATOR = "registration_submitted_creator";
    public static final String TYPE_REGISTRATION_SUBMITTED_USER = "registration_submitted_user";
    public static final String TYPE_REGISTRATION_APPROVED = "registration_approved";
    public static final String TYPE_REGISTRATION_REJECTED = "registration_rejected";
    public static final String TYPE_ADMIN_MESSAGE = "admin_message";
    public static final String TYPE_ACTIVITY_UPDATED = "activity_updated";
    public static final String TYPE_ACTIVITY_QR_UPDATED = "activity_qr_updated";
    public static final String TYPE_ACTIVITY_QR_EXPIRING = "activity_qr_expiring";
    public static final String TYPE_ACTIVITY_QR_CHANGE_REQUESTED = "activity_qr_change_requested";
    public static final String TYPE_REGISTRATION_CANCELLED_CREATOR = "registration_cancelled_creator";
    public static final String TYPE_ACTIVITY_PUBLISHED = "activity_published";
    public static final String TYPE_REVIEW_REMINDER = "review_reminder";
    public static final String TYPE_REVIEW_AVAILABLE = "review_available";
    public static final String TYPE_REVIEW_RECEIVED = "review_received";
    public static final String TYPE_REPORT_RECEIVED = "report_received";
    public static final String TYPE_REPORT_RESULT = "report_result";
    public static final String TYPE_FEEDBACK_RECEIVED = "feedback_received";
    public static final String TYPE_FEEDBACK_RESULT = "feedback_result";
    public static final String TYPE_ADMIN_ACTIVITY_ACTION = "admin_activity_action";
    public static final String TYPE_ACTIVITY_START_REMINDER = "activity_start_reminder";
    public static final String TYPE_ACTIVITY_FULL = "activity_full";
    public static final String TYPE_REVIEW_MODERATED = "review_moderated";
    public static final String TYPE_REGISTRATION_WAITING_FULL = "registration_waiting_full";
    public static final String TYPE_ACTIVITY_SLOT_AVAILABLE = "activity_slot_available";
    public static final String TYPE_REGISTRATION_EXPIRED = "registration_expired";
    public static final String TYPE_ACTIVITY_CANCELLED = "activity_cancelled";
    public static final String TYPE_ACCOUNT_STATUS_CHANGED = "account_status_changed";
    public static final String TYPE_ACCOUNT_APPEAL_RESULT = "account_appeal_result";

    public static final String RELATED_ACTIVITY = "activity";
    public static final String RELATED_PENDING_REVIEW = "pending_review";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "content", nullable = false, length = 500)
    private String content;

    @Column(name = "related_type", length = 50)
    private String relatedType;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
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
