package com.londonmeet.server.service;

import com.londonmeet.pojo.dto.request.UserProfileUpdateRequest;
import com.londonmeet.pojo.vo.CoverUploadVO;
import com.londonmeet.pojo.vo.UserProfileVO;
import com.londonmeet.server.security.LoginUser;
import org.springframework.web.multipart.MultipartFile;

public interface UserProfileService {

    UserProfileVO getProfile(LoginUser loginUser);

    UserProfileVO updateProfile(UserProfileUpdateRequest request, LoginUser loginUser);

    CoverUploadVO uploadCover(MultipartFile file, LoginUser loginUser);
}
