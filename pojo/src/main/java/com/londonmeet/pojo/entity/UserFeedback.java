package com.londonmeet.pojo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_feedback")
public class UserFeedback {
    public static final String TYPE_FEEDBACK = "FEEDBACK";
    public static final String TYPE_ACCOUNT_APPEAL = "ACCOUNT_APPEAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "type", nullable = false, length = 30)
    private String type;
    @Column(name = "subject", nullable = false, length = 100)
    private String subject;
    @Column(name = "content", nullable = false, length = 1000)
    private String content;
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
        if (type == null) type = TYPE_FEEDBACK;
        if (status == null) status = "PENDING";
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
