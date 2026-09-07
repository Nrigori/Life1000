package com.life1000.common;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);
    // 未预期的技术异常仅写服务端日志，响应不透出 SQL、路径或堆栈；业务错误另走明确提示。
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String,String>> unexpected(Exception exception) {
        log.error("Request failed",exception);
        return ResponseEntity.status(500).body(Map.of("code","INTERNAL_ERROR","message","操作暂时无法完成，请稍后重试。"));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, String>> tooLarge(Exception exception) {
        return ResponseEntity.status(413).body(Map.of("code", "FILE_TOO_LARGE", "message", "文件不能超过 50 MB"));
    }
    @ExceptionHandler(java.io.IOException.class)
    ResponseEntity<Map<String, String>> fileError(Exception exception) {
        log.warn("File request failed",exception);
        return ResponseEntity.status(503).body(Map.of("code", "FILE_UNAVAILABLE", "message", "文件暂时不可读写，请检查上传目录权限后重试"));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, String>> businessError(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(Map.of("code", exception.getCode(), "message", exception.getMessage()));
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class, HandlerMethodValidationException.class,
            ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<Map<String, String>> invalidRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", "INVALID_REQUEST", "message", "请求字段或参数不合法"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> constraintConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409)
                .body(Map.of("code", "CONFLICT", "message", "数据冲突，请重新查询后再试"));
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<Map<String, String>> databaseUnavailable(DataAccessException exception) {
        log.warn("Database request failed",exception);
        return ResponseEntity.status(503)
                .body(Map.of("code", "DATABASE_UNAVAILABLE", "message", "数据库暂时不可用"));
    }
}
