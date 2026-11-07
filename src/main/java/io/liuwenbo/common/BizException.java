package io.liuwenbo.common;

import lombok.Data;

@Data
public class BizException extends RuntimeException{
    private String msg;

    public BizException(String msg) {
        this.msg = msg;
    }
}
