package com.londonmeet.server.service;

import com.londonmeet.pojo.dto.request.WxLoginRequest;
import com.londonmeet.pojo.vo.AvatarUploadVO;
import com.londonmeet.pojo.vo.LoginUserVO;
import com.londonmeet.server.security.LoginUser;
import org.springframework.web.multipart.MultipartFile;

public interface WxLoginService {

    LoginUserVO login(WxLoginRequest request);

    AvatarUploadVO uploadAvatar(MultipartFile file, LoginUser loginUser);
}
