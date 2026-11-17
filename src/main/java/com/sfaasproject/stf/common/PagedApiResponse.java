package com.sfaasproject.stf.common;

/** 목록 성공 응답: {@code {success:true, data, meta}}. */
public record PagedApiResponse<T>(boolean success, T data, PageMeta meta) {

    public static <T> PagedApiResponse<T> ok(T data, PageMeta meta) {
        return new PagedApiResponse<>(true, data, meta);
    }
}
