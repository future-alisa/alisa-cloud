package com.alisa.util;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public Result(T data) {
        this.code = 200;
        this.message = "成功";
        this.data = data;
    }
}
