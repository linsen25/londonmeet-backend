package com.londonmeet.common.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * Generic pagination response object.
 *
 * @param <T> page item type
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> implements Serializable {

    /**
     * Total number of records.
     */
    private Long total;

    /**
     * Current page records.
     */
    private List<T> records;
}