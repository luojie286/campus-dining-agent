package com.sky.handler;

import com.sky.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.sky.exception.BusinessException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.sky.exception.ForbiddenException;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import com.sky.exception.NotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> uploadTooLarge() {
        return Result.error("图片大小不能超过5 MiB");
    }

    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> invalidUpload() {
        return Result.error("请选择图片文件后重新上传");
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> badRequest(BadRequestException exception) {
        return Result.error(exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> conflict(ConflictException exception) {
        return Result.error(exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> unreadable(
            HttpMessageNotReadableException exception) {

        return Result.error("请求JSON格式、字段类型或字段名称不合法");
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> resourceNotFound(NotFoundException exception) {
        return Result.error(exception.getMessage());
    }

    // Spring MVC 6.2 将未匹配的静态资源请求抛为异常，不能被兜底处理误转成 500。
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> notFound(NoResourceFoundException exception) {
        return Result.error("接口不存在或尚未实现");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
            .findFirst().map(e -> e.getField() + ": " + e.getDefaultMessage()).orElse("请求参数不合法");
        return Result.error(message);
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> business(BusinessException exception) {
        return Result.error(exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> unexpected(Exception exception) {
        log.error("Unhandled request error", exception);
        return Result.error("服务器内部错误，请查看后端日志");
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Result<Void> forbidden(ForbiddenException exception) {
        return Result.error(exception.getMessage());
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> binding(BindException exception) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": "
                        + (error.isBindingFailure()
                        ? "参数类型不正确"
                        : error.getDefaultMessage()))
                .orElse("请求参数不合法");

        return Result.error(message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> typeMismatch(
            MethodArgumentTypeMismatchException exception) {

        return Result.error(
                exception.getName() + ": 参数类型不正确"
        );
    }
}

