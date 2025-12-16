package com.domidodo.logx.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private Long total;
    private List<T> records;
    private Long current;
    private Long size;

    public static <T> PageResult<T> of(Long total, List<T> records, Long current, Long size) {
        return new PageResult<>(total, records, current, size);
    }

    public static <T> PageResult<T> of(Long total, List<T> records) {
        return new PageResult<>(total, records, 1L, 20L);
    }
}

