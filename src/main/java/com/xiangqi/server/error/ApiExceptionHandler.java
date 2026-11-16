package com.xiangqi.server.error;

import com.xiangqi.core.IllegalMoveException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.util.List;

/**
 * 统一异常出口：只返回稳定错误码与友好文案，避免泄露堆栈与服务端实现。
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(GameException.class)
    public ResponseEntity<ApiError> handleGame(GameException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(IllegalMoveException.class)
    public ResponseEntity<ApiError> handleIllegalMove(IllegalMoveException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("ILLEGAL_MOVE", exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_ARGUMENT", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors();
        String message = errors.isEmpty() ? "请求参数不合法" : errors.get(0).getField() + " 参数不合法";
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_FAILED", message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("MALFORMED_BODY", "请求体格式错误"));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ApiError("METHOD_NOT_ALLOWED", "请求方法不支持"));
    }

    /**
     * SSE 客户端主动断开属于正常现象，不记录错误、也不再写入响应体。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncClientGone(AsyncRequestNotUsableException exception) {
        // 连接已断开，无需处理
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        LOGGER.error("未处理的服务端异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("INTERNAL_ERROR", "服务器内部错误，请稍后再试"));
    }

    public record ApiError(String code, String message) {
    }
}
