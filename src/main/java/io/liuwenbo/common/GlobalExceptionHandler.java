package io.liuwenbo.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        return Result.fail(e.getMsg());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("未预期异常", e);
        return Result.fail("服务开小差了");
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDup(DuplicateKeyException e) {
        log.warn("唯一索引冲突", e);
        return Result.fail("请求重复，请稍后再试");
    }
}
