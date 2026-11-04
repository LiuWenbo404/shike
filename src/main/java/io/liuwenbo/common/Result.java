package io.liuwenbo.common;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> ok(){
        return new Result<T>(){{
            setCode(200);
            setMsg("ok");
        }};
    }

    public static <T> Result<T> ok(T data){
        Result<T> ok = ok();
        ok.setData(data);
        return ok;
    }

    public static <T> Result<T> fail(String massage){
        return new Result<T>(){{
            setCode(500);
            setMsg(massage);
        }};
    }
}
