package com.londonmeet.pojo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 用户实体类
 * 对应 users 表
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    /**
     * 用户主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 40)
    private String publicId;

    /**
     * 微信小程序唯一标识
     */
    @Column(name = "openid", nullable = false, unique = true, length = 100)
    private String openid;

    /**
     * 微信开放平台唯一标识
     */
    @Column(name = "unionid", length = 100)
    private String unionid;

    /**
     * 用户昵称
     */
    @Column(name = "nickname", length = 50)
    private String nickname;

    /**
     * 用户头像地址
     */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "motto", length = 100)
    private String motto;

    @Column(name = "tags_json", columnDefinition = "TEXT")
    private String tagsJson;

    /**
     * 手机号
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * 用户角色：USER / ADMIN
     */
    @Column(name = "role", nullable = false, length = 30)
    private String role;

    /**
     * 用户状态：ACTIVE / DISABLED
     */
    @Column(name = "status", nullable = false, length = 30)
    private String status;

    /**
     * 最后登录时间
     */
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 插入前自动填充默认值
     */
    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();

        if (publicId == null || publicId.isBlank()) {
            publicId = "usr_" + UUID.randomUUID().toString().replace("-", "");
        }

        if (role == null) {
            role = "USER";
        }

        if (status == null) {
            status = "ACTIVE";
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    /**
     * 更新前自动刷新更新时间
     */
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
