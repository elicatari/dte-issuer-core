package com.elicatari.dteissuer.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class RequestMdcTest {

    @AfterEach
    void clearMdc() {
        RequestMdc.clear();
    }

    @Test
    void blankOrUnsafeIncomingIsReplaced() {
        assertThat(RequestMdc.resolve(null)).isNotBlank();
        assertThat(RequestMdc.resolve(" ")).isNotBlank();
        assertThat(RequestMdc.resolve("bad\nid")).isNotEqualTo("bad\nid");
        assertThat(RequestMdc.resolve("x".repeat(129))).hasSizeLessThan(129);
    }

    @Test
    void safeIncomingIsKept() {
        assertThat(RequestMdc.resolve("corr-alpha-1")).isEqualTo("corr-alpha-1");
    }

    @Test
    void putAndClearDoNotLeakBetweenCalls() {
        RequestMdc.putRequestId("r1");
        RequestMdc.putTenantId("alpha");
        assertThat(MDC.get(RequestMdc.REQUEST_ID)).isEqualTo("r1");
        assertThat(MDC.get(RequestMdc.TENANT_ID)).isEqualTo("alpha");
        RequestMdc.clear();
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }
}