package com.londonmeet.server.service;

import com.londonmeet.pojo.dto.request.ReviewSubmitRequest;
import com.londonmeet.pojo.dto.request.ReviewBatchGoodRequest;
import com.londonmeet.pojo.vo.ReviewSubmitVO;
import com.londonmeet.pojo.vo.ReviewTaskVO;
import com.londonmeet.pojo.vo.ReviewBatchGoodVO;
import com.londonmeet.server.security.LoginUser;

import java.util.List;

public interface ReviewService {

    List<ReviewTaskVO> listTasks(String mode, LoginUser loginUser);

    ReviewSubmitVO submit(ReviewSubmitRequest request, LoginUser loginUser);

    ReviewBatchGoodVO submitBatchGood(ReviewBatchGoodRequest request, LoginUser loginUser);
}
