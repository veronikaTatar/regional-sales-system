package by.regsales.common;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import java.util.LinkedHashMap;
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class) ResponseEntity<ApiError> api(ApiException e) { return ResponseEntity.status(e.getStatus()).body(new ApiError(e.getCode(),e.getMessage(),e.getFieldErrors())); }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        var fields=new LinkedHashMap<String,String>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(),f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR","Проверьте заполнение формы",fields));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception e) { return ResponseEntity.badRequest().body(new ApiError("INVALID_REQUEST","Некорректный формат запроса")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge() {
        return ResponseEntity.status(413).body(new ApiError("FILE_TOO_LARGE", "Размер файла должен быть не более 1 МБ"));
    }
    @ExceptionHandler(DataIntegrityViolationException.class) ResponseEntity<ApiError> conflict(DataIntegrityViolationException e) { return ResponseEntity.status(409).body(new ApiError("CONFLICT","Запись с такими данными уже существует или нарушены ограничения БД")); }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ApiError> denied(AccessDeniedException e) { return ResponseEntity.status(403).body(new ApiError("FORBIDDEN","Недостаточно прав для выполнения операции")); }
}
