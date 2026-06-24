package com.londonmeet.server.service;

import com.londonmeet.pojo.vo.TagVO;

import java.util.List;

public interface TagService {

    List<TagVO> listEnabledTags();
}
