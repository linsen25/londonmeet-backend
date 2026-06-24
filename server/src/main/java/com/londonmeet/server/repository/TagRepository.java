package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, Long> {

    List<Tag> findByEnabledTrueOrderBySortOrderAscIdAsc();

    List<Tag> findByIdInAndEnabledTrue(Collection<Long> ids);

    List<Tag> findByNameInAndEnabledTrue(Collection<String> names);

    List<Tag> findAllByOrderBySortOrderAscIdAsc();

    Optional<Tag> findByNameIgnoreCase(String name);
}
