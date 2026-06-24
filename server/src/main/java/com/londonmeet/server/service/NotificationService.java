package com.londonmeet.server.service;

import com.londonmeet.pojo.vo.NotificationUnreadCountVO;
import com.londonmeet.pojo.vo.NotificationVO;
import com.londonmeet.server.security.LoginUser;

import java.util.List;

public interface NotificationService {

    void createNotification(
            Long userId,
            String type,
            String title,
            String content,
            String relatedType,
            Long relatedId
    );

    List<NotificationVO> listNotifications(Integer pageSize, LoginUser loginUser);

    NotificationUnreadCountVO unreadCount(LoginUser loginUser);

    NotificationVO markRead(Long id, LoginUser loginUser);

    NotificationUnreadCountVO markAllRead(LoginUser loginUser);
}
