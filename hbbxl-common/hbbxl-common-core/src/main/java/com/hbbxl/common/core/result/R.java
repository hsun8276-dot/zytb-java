package com.hbbxl.common.core.result;

import lombok.Data;
import com.hbbxl.common.core.constant.CommonConstants;

import java.io.Serializable;

/**
 * 统一响应结果
 */
@Data
public class R<T> implements Serializable {

    private int code;
    private String message;
    private T data;

    public static <T> R<T> ok() {
        return build(CommonConstants.SUCCESS, "操作成功", null);
    }

    public static <T> R<T> ok(T data) {
        return build(CommonConstants.SUCCESS, "操作成功", data);
    }

    public static <T> R<T> ok(T data, String message) {
        return build(CommonConstants.SUCCESS, message, data);
    }

    public static <T> R<T> fail(String message) {
        return build(CommonConstants.FAIL, message, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return build(code, message, null);
    }

    private static <T> R<T> build(int code, String message, T data) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        r.setData(data);
        return r;
    }
}
