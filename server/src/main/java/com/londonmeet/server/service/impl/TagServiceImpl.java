package com.londonmeet.server.service.impl;

import com.londonmeet.pojo.entity.Tag;
import com.londonmeet.pojo.vo.TagVO;
import com.londonmeet.server.repository.TagRepository;
import com.londonmeet.server.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagRepository tagRepository;

    @Override
    public List<TagVO> listEnabledTags() {
        return tagRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(this::toVO)
                .toList();
    }

    private TagVO toVO(Tag tag) {
        return TagVO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .build();
    }
}
