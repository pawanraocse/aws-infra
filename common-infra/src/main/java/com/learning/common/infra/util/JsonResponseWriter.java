package com.learning.common.infra.util;

import com.learning.common.constants.HeaderNames;
import com.learning.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.io.IOException;


public final class JsonResponseWriter {
    private JsonResponseWriter() {}

    public static Mono<Void> writeReactive(ServerWebExchange exchange, HttpStatus status, String code, String message) {
        var response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(new IllegalStateException("Response already committed"));
        }
        String requestId = exchange.getRequest().getHeaders().getFirst(HeaderNames.REQUEST_ID);
        String path = exchange.getRequest().getPath().value();
        ErrorResponse body = ErrorResponse.of(status.value(), code, message, requestId, path);
        byte[] json = toJson(body).getBytes();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(json)));
    }

    public static void writeServlet(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        if (response.isCommitted()) return;
        String requestId = request.getHeader(HeaderNames.REQUEST_ID);
        String path = request.getRequestURI();
        ErrorResponse body = ErrorResponse.of(status.value(), code, message, requestId, path);
        String json = toJson(body);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(json);
    }

    private static String toJson(ErrorResponse body) {
        // Simple manual JSON (avoid object mapper dependency here); escaped quotes in message
        String msgEscaped = body.message() == null ? "" : body.message().replace("\"", "\\\"");
        return "{" +
                "\"timestamp\":\"" + body.timestamp() + "\"," +
                "\"status\":" + body.status() + "," +
                "\"code\":\"" + body.code() + "\"," +
                "\"message\":\"" + msgEscaped + "\"," +
                "\"requestId\":\"" + (body.requestId() == null ? "" : body.requestId()) + "\"," +
                "\"path\":\"" + (body.path() == null ? "" : body.path()) + "\"}";
    }
}
