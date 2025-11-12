package com.learning.gateway.filter;

import com.learning.gateway.support.BaseGatewayFilterTest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

class RequestIdGlobalFilterTest extends BaseGatewayFilterTest {

    private final RequestIdGlobalFilter filter = new RequestIdGlobalFilter();

    @Test
    @DisplayName("generates request id when none provided and propagates to context")
    void generatesRequestIdWhenMissing() {
        var request = get("/api/items").build();
        var webExchange = exchange(request);

        AtomicReference<String> contextRequestId = new AtomicReference<>();
        var chain = chain(ex -> Mono.deferContextual(ctx -> {
            contextRequestId.set(RequestIdGlobalFilter.requestIdFromContext(ctx, null));
            return Mono.empty();
        }));

        StepVerifier.create(filter.filter(webExchange, chain))
                .verifyComplete();

        var mutatedRequest = chain.lastRequest();
        String generated = mutatedRequest.getHeaders().getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER);
        Assertions.assertThat(generated).isNotBlank();
        Assertions.assertThat(contextRequestId.get()).isEqualTo(generated);
        Assertions.assertThat(chain.lastExchange().getAttributes())
                .containsEntry(RequestIdGlobalFilter.REQUEST_ID_HEADER, generated);
    }

    @Test
    @DisplayName("preserves existing request id and reuses for downstream context")
    void preservesExistingRequestId() {
        var request = get("/api/items")
                .header(RequestIdGlobalFilter.REQUEST_ID_HEADER, "existing-id")
                .build();
        var webExchange = exchange(request);

        AtomicReference<String> contextRequestId = new AtomicReference<>();
        var chain = chain(ex -> Mono.deferContextual(ctx -> {
            contextRequestId.set(RequestIdGlobalFilter.requestIdFromContext(ctx, null));
            return Mono.empty();
        }));

        StepVerifier.create(filter.filter(webExchange, chain))
                .verifyComplete();

        var mutatedRequest = chain.lastRequest();
        Assertions.assertThat(mutatedRequest.getHeaders().getFirst(RequestIdGlobalFilter.REQUEST_ID_HEADER))
                .isEqualTo("existing-id");
        Assertions.assertThat(contextRequestId.get()).isEqualTo("existing-id");
        Assertions.assertThat(chain.lastExchange().getAttributes())
                .containsEntry(RequestIdGlobalFilter.REQUEST_ID_HEADER, "existing-id");
    }
}

