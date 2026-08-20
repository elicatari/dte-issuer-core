package com.elicatari.dteissuer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elicatari.dteissuer.shared.ProblemTypes;
import com.elicatari.dteissuer.shared.RequestMdc;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = DteHttpSliceTestConfig.class)
@AutoConfigureMockMvc
class DteHttpTest extends AbstractJpaPostgresTest {

    private static final String BODY = "{\"rut\":\"12.345.678-5\",\"neto\":1000}";
    private static final String OTHER_BODY = "{\"rut\":\"12.345.678-5\",\"neto\":2000}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Test
    void postAlphaThenReplaySameKeyKeepsFolio() throws Exception {
        MvcResult first = mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "http-replay")
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().exists(RequestMdc.HEADER))
                .andExpect(jsonPath("$.status").value("issued"))
                .andReturn();
        String id = JsonPath.read(first.getResponse().getContentAsString(), "$.id");
        int folio = JsonPath.read(first.getResponse().getContentAsString(), "$.folio");

        mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "http-replay")
                        .content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.folio").value(folio));
    }

    @Test
    void postSameKeyDifferentBodyIs409NotOldResponse() throws Exception {
        mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "http-collision")
                        .content(BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "http-collision")
                        .content(OTHER_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(ProblemTypes.IDEMPOTENCY_CONFLICT.toString()));
    }

    @Test
    void postWithoutCafIs409FolioExhausted() throws Exception {
        mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "gamma")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "http-gamma")
                        .content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value(ProblemTypes.FOLIO_EXHAUSTED.toString()));
    }

    @Test
    void betaDoesNotSeeAlphaDte() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "http-isolation")
                        .content(BODY))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/dte/" + id).with(jwt().jwt(jwt -> jwt.claim("tenant_id", "beta"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/dte").with(jwt().jwt(jwt -> jwt.claim("tenant_id", "beta"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + id + "')]").doesNotExist());
    }

    @Test
    @Timeout(30)
    void concurrentSameKeyIssuesASingleDte() throws Exception {
        String tenant = "idem-conc";
        insertFolioRange(dataSource, tenant, 1, 8);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return mockMvc.perform(post("/api/v1/dte")
                                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenant)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "same-key-parallel")
                                .content(BODY))
                        .andReturn();
            });
        }
        List<Future<MvcResult>> futures = pool.invokeAll(tasks, 20, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(pool.awaitTermination(25, TimeUnit.SECONDS)).isTrue();

        List<UUID> ids = new ArrayList<>();
        List<Integer> folios = new ArrayList<>();
        for (Future<MvcResult> future : futures) {
            MvcResult result = settleIdempotency(tenant, future.get(5, TimeUnit.SECONDS));
            assertThat(result.getResponse().getStatus()).isEqualTo(201);
            String body = result.getResponse().getContentAsString();
            ids.add(UUID.fromString(JsonPath.read(body, "$.id")));
            folios.add(JsonPath.read(body, "$.folio"));
        }

        assertThat(ids.stream().distinct()).hasSize(1);
        assertThat(folios.stream().distinct()).containsExactly(1);

        MvcResult listed = mockMvc.perform(get("/api/v1/dte").with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenant))))
                .andExpect(status().isOk())
                .andReturn();
        List<String> listedIds = JsonPath.read(listed.getResponse().getContentAsString(), "$[*].id");
        assertThat(listedIds).containsExactly(ids.getFirst().toString());
    }

    private MvcResult settleIdempotency(String tenant, MvcResult first) throws Exception {
        int status = first.getResponse().getStatus();
        if (status == 201) {
            return first;
        }
        String type = JsonPath.read(first.getResponse().getContentAsString(), "$.type");
        assertThat(type).isEqualTo(ProblemTypes.IDEMPOTENCY_IN_PROGRESS.toString());
        for (int attempt = 0; attempt < 64; attempt++) {
            MvcResult retry = mockMvc.perform(post("/api/v1/dte")
                            .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenant)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "same-key-parallel")
                            .content(BODY))
                    .andReturn();
            if (retry.getResponse().getStatus() == 201) {
                return retry;
            }
            String retryType = JsonPath.read(retry.getResponse().getContentAsString(), "$.type");
            assertThat(retryType).isEqualTo(ProblemTypes.IDEMPOTENCY_IN_PROGRESS.toString());
            Thread.onSpinWait();
        }
        throw new AssertionError("la clave siguió en curso; no hubo segundo folio pero el ganador no releyó");
    }
}