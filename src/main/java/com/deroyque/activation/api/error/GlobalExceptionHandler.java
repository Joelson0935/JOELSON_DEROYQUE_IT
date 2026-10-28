package com.deroyque.activation.api.error;

import java.net.URI;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.deroyque.activation.domain.InvalidStateTransitionException;
import com.deroyque.activation.service.DuplicateExternalIdException;
import com.deroyque.activation.service.RequestNotFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tratamento centralizado de erros, respondendo no formato Problem Details (RFC 9457) via
 * {@link ProblemDetail}. Nunca expõe stack trace ou detalhes internos ao consumidor.
 *
 * <p>Cada resposta carrega um {@code traceId} (propriedade estendida) para correlacionar com os
 * logs. O {@code traceId} vem do MDC (preenchido por {@code TraceIdFilter}).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String PROBLEM_BASE = "https://api.deroyque.com/problems/";

    /** Dados inválidos (Bean Validation). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        var problem = base(HttpStatus.BAD_REQUEST, "validation-error", "Dados inválidos",
                "Um ou mais campos não passaram na validação.", request);
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        problem.setProperty("errors", errors);
        return problem;
    }

    /** JSON malformado ou corpo ilegível. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return base(HttpStatus.BAD_REQUEST, "malformed-request", "Requisição malformada",
                "O corpo da requisição não pôde ser lido.", request);
    }

    /** Parâmetro de tipo inválido (ex.: status inexistente no enum). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return base(HttpStatus.BAD_REQUEST, "invalid-parameter", "Parâmetro inválido",
                "O parâmetro '%s' possui valor inválido.".formatted(ex.getName()), request);
    }

    /** Registro inexistente. */
    @ExceptionHandler(RequestNotFoundException.class)
    public ProblemDetail handleNotFound(RequestNotFoundException ex, HttpServletRequest request) {
        return base(HttpStatus.NOT_FOUND, "not-found", "Recurso não encontrado",
                "A solicitação informada não existe.", request);
    }

    /** Duplicidade com conteúdo divergente. */
    @ExceptionHandler(DuplicateExternalIdException.class)
    public ProblemDetail handleDuplicate(DuplicateExternalIdException ex, HttpServletRequest request) {
        var problem = base(HttpStatus.CONFLICT, "duplicate-external-id", "Solicitação duplicada",
                "Já existe uma solicitação com este externalId e conteúdo divergente.", request);
        problem.setProperty("externalId", ex.getExternalId());
        return problem;
    }

    /** Transição de estado inválida. */
    @ExceptionHandler(InvalidStateTransitionException.class)
    public ProblemDetail handleInvalidTransition(InvalidStateTransitionException ex, HttpServletRequest request) {
        return base(HttpStatus.CONFLICT, "invalid-state-transition", "Transição de estado inválida",
                "A operação não é permitida para o estado atual da solicitação.", request);
    }

    /** Escopo/autorização insuficiente (lançada por @PreAuthorize). */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(org.springframework.security.access.AccessDeniedException ex,
                                            HttpServletRequest request) {
        return base(HttpStatus.FORBIDDEN, "forbidden", "Acesso negado",
                "O token não possui o escopo necessário para esta operação.", request);
    }

    /** Fallback para erros inesperados — nunca vaza detalhes internos. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        // Detalhe completo só no log (com traceId), não na resposta.
        log.error("Erro inesperado ao processar {}", request.getRequestURI(), ex);
        return base(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error", "Erro interno",
                "Ocorreu um erro inesperado. Use o traceId para suporte.", request);
    }

    private ProblemDetail base(HttpStatus status, String type, String title,
                               String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(PROBLEM_BASE + type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("traceId", org.slf4j.MDC.get("traceId"));
        return problem;
    }

    /** Detalhe de erro de campo exposto em respostas de validação. */
    public record FieldErrorDetail(String field, String message) {
    }
}
