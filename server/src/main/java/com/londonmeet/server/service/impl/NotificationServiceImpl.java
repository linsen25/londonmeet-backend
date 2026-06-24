package com.londonmeet.server.service.impl;

import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.pojo.entity.Notification;
import com.londonmeet.pojo.vo.NotificationUnreadCountVO;
import com.londonmeet.pojo.vo.NotificationVO;
import com.londonmeet.server.repository.NotificationRepository;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    @Override
    @Transactional
    public void createNotification(
            Long userId,
            String type,
            String title,
            String content,
            String relatedType,
            Long relatedId
    ) {
        if (userId == null || !StringUtils.hasText(type) || !StringUtils.hasText(title)) {
            return;
        }

        Notification notification = Notification.builder()
                .userId(userId)
                .type(type.trim())
                .title(limit(title, 100))
                .content(limit(content, 500))
                .relatedType(trimToNull(relatedType))
                .relatedId(relatedId)
                .build();

        notificationRepository.save(notification);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationVO> listNotifications(Integer pageSize, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        int size = normalizePageSize(pageSize);

        return notificationRepository.findByUserIdOrderByCreatedAtDesc(
                        userId,
                        PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt"))
                )
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationUnreadCountVO unreadCount(LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        return NotificationUnreadCountVO.builder()
                .count(notificationRepository.countByUserIdAndReadAtIsNull(userId))
                .build();
    }

    @Override
    @Transactional
    public NotificationVO markRead(Long id, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Notification not found."));
        if (!userId.equals(notification.getUserId())) {
            throw new BusinessException("Notification not found.");
        }
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return toVO(notification);
    }

    @Override
    @Transactional
    public NotificationUnreadCountVO markAllRead(LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        List<Notification> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId,
                PageRequest.of(0, MAX_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        LocalDateTime now = LocalDateTime.now();
        notifications.stream()
                .filter(notification -> notification.getReadAt() == null)
                .forEach(notification -> notification.setReadAt(now));
        notificationRepository.saveAll(notifications);
        return unreadCount(loginUser);
    }

    private NotificationVO toVO(Notification notification) {
        return NotificationVO.builder()
                .id(notification.getId())
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .relatedType(notification.getRelatedType())
                .relatedId(notification.getRelatedId())
                .read(notification.getReadAt() != null)
                .readAt(toEpochMillis(notification.getReadAt()))
                .createdAt(toEpochMillis(notification.getCreatedAt()))
                .build();
    }

    private Long requireUserId(LoginUser loginUser) {
        if (loginUser == null || loginUser.userId() == null) {
            throw new BusinessException("Please login first.");
        }
        return loginUser.userId();
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String limit(String value, int maxLength) {
        String text = StringUtils.hasText(value) ? value.trim() : "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
