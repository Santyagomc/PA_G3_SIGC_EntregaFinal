package com.backend.sigcapi.exceptions;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.backend.sigcapi.util.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 400 - validación de campos (@Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        ApiResponse<Map<String, String>> response = new ApiResponse<>(false,
                "Error de validación en los campos enviados", errors);

        return ResponseEntity.badRequest().body(response);
    }

    // 400 - petición mal formada (JSON ilegible, parámetro/parte faltante, tipo o enum inválido)
    @ExceptionHandler({ HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, MethodArgumentTypeMismatchException.class })
    public ResponseEntity<ApiResponse<String>> handleBadRequest(Exception ex) {
        ApiResponse<String> response = new ApiResponse<>(false,
                "Solicitud inválida: revise el cuerpo, los parámetros o los archivos enviados.");
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 400 - reglas de negocio
    @ExceptionHandler({ BusinessRuleException.class, InvalidPasswordException.class })
    public ResponseEntity<ApiResponse<String>> handleBusinessRuleException(RuntimeException ex) {
        ApiResponse<String> response = new ApiResponse<>(false, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    // 401 - no autenticado
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<String>> handleAuthentication(AuthenticationException ex) {
        ApiResponse<String> response = new ApiResponse<>(false, "Debes autenticarte para realizar esta acción.");
        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

    // 403 - sin permisos (por ejemplo @PreAuthorize("hasRole('ADMIN')"))
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<String>> handleAccessDenied(AccessDeniedException ex) {
        ApiResponse<String> response = new ApiResponse<>(false,
                "No tienes permisos de Administrador para realizar esta acción.");
        return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
    }

    // 404 - recurso inexistente
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleResourceNotFound(ResourceNotFoundException ex) {
        ApiResponse<String> response = new ApiResponse<>(false, ex.getMessage());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // 404 - ruta inexistente
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<String>> handleNoHandlerFound(NoHandlerFoundException ex) {
        ApiResponse<String> response = new ApiResponse<>(false, "La ruta solicitada no existe: " + ex.getRequestURL());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // 405 - método HTTP no permitido
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<String>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ApiResponse<String> response = new ApiResponse<>(false, "Método HTTP no permitido para esta ruta.");
        return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
    }

    // 409 - duplicados o violación de restricciones en base de datos
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<String>> handleConflict(DataIntegrityViolationException ex) {
        ApiResponse<String> response = new ApiResponse<>(false,
                "El recurso ya existe o viola una restricción de integridad.");
        return new ResponseEntity<>(response, HttpStatus.CONFLICT);
    }

    // 413 - archivo demasiado grande
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<String>> handleTooLarge(MaxUploadSizeExceededException ex) {
        ApiResponse<String> response = new ApiResponse<>(false, "El archivo supera el tamaño máximo permitido.");
        return new ResponseEntity<>(response, HttpStatus.CONTENT_TOO_LARGE);
    }

    // 500 - error inesperado (el detalle se registra en el log, no se expone al cliente)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGlobalException(Exception ex) {
        log.error("Error interno no controlado", ex);
        ApiResponse<String> response = new ApiResponse<>(false, "Ocurrió un error interno. Inténtalo de nuevo más tarde.");
        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
