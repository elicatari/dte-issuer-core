package com.elicatari.dteissuer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.elicatari.dteissuer.application.port.out.FolioRangeRepository;
import com.elicatari.dteissuer.domain.Folio;
import com.elicatari.dteissuer.domain.TenantId;
import com.elicatari.dteissuer.shared.ProblemTypes;
import com.elicatari.dteissuer.shared.TenantContext;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * N POST en paralelo del mismo tenant: folios distintos, sin huecos. Debe
 * ponerse rojo si el bloqueo de F2-T04 se cambia por {@code MAX(folio)+1}.
 */
@SpringBootTest(classes = DteHttpSliceTestConfig.class)
@AutoConfigureMockMvc
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FolioConcurrencyIT extends AbstractJpaPostgresTest {

    private static final String BODY = "{\"rut\":\"12.345.678-5\",\"neto\":1000}";
    private static final int THREADS = 8;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private FolioRangeRepository folioRanges;

    @Test
    @Timeout(30)
    void concurrentPostsOnExactRangeGetDistinctConsecutiveFoliosAndExhaustIt() throws Exception {
        String tenant = "folio-conc";
        insertFolioRange(dataSource, tenant, 1, THREADS);

        List<IssueHttpResult> results = postInParallel(tenant, THREADS, "conc-full");

        List<Integer> createdFolios = results.stream()
                .filter(IssueHttpResult::created)
                .map(IssueHttpResult::folio)
                .toList();
        assertThat(createdFolios).hasSize(THREADS);
        assertThat(results).allMatch(IssueHttpResult::created);

        Set<Integer> distinct = Set.copyOf(createdFolios);
        assertThat(distinct).hasSize(THREADS);
        int min = distinct.stream().mapToInt(Integer::intValue).min().orElseThrow();
        int max = distinct.stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertThat(min).isEqualTo(1);
        assertThat(max).isEqualTo(THREADS);
        assertThat(max - min + 1).isEqualTo(THREADS);

        List<?> listed = listFolios(tenant);
        assertThat(listed).hasSize(THREADS).doesNotHaveDuplicates();

        TenantContext.set(new TenantId(tenant));
        try {
            var range = folioRanges.findByTenantId(new TenantId(tenant)).orElseThrow();
            assertThat(range.exhausted()).isTrue();
            assertThat(range.next()).isEqualTo(new Folio(THREADS + 1L));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @Timeout(30)
    void concurrentPostsOnShortRangeIssueOnlyAvailableFoliosAndReturn409() throws Exception {
        int available = THREADS - 3;
        String tenant = "folio-short";
        insertFolioRange(dataSource, tenant, 1, available);

        List<IssueHttpResult> results = postInParallel(tenant, THREADS, "conc-short");

        List<IssueHttpResult> created = results.stream().filter(IssueHttpResult::created).toList();
        List<IssueHttpResult> conflicts = results.stream().filter(r -> !r.created()).toList();
        assertThat(created).hasSize(available);
        assertThat(conflicts).hasSize(3);
        assertThat(conflicts)
                .allMatch(result -> result.status() == 409)
                .allMatch(result -> ProblemTypes.FOLIO_EXHAUSTED.toString().equals(result.problemType()));

        Set<Integer> distinct = Set.copyOf(created.stream().map(IssueHttpResult::folio).toList());
        assertThat(distinct).hasSize(available);
        assertThat(distinct).contains(1, available);

        assertThat(listFolios(tenant)).hasSize(available).doesNotHaveDuplicates();

        TenantContext.set(new TenantId(tenant));
        try {
            assertThat(folioRanges.findByTenantId(new TenantId(tenant)).orElseThrow().exhausted()).isTrue();
        } finally {
            TenantContext.clear();
        }
    }

    private List<IssueHttpResult> postInParallel(String tenant, int n, String keyPrefix) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CyclicBarrier barrier = new CyclicBarrier(n);
        List<Callable<IssueHttpResult>> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String key = keyPrefix + "-" + i;
            tasks.add(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                MvcResult result = mockMvc.perform(post("/api/v1/dte")
                                .with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenant)))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", key)
                                .content(BODY))
                        .andReturn();
                return IssueHttpResult.from(result);
            });
        }
        List<Future<IssueHttpResult>> futures = pool.invokeAll(tasks, 20, TimeUnit.SECONDS);
        pool.shutdown();
        assertThat(pool.awaitTermination(25, TimeUnit.SECONDS)).isTrue();

        List<IssueHttpResult> results = new ArrayList<>();
        for (Future<IssueHttpResult> future : futures) {
            assertThat(future.isCancelled()).isFalse();
            results.add(future.get(5, TimeUnit.SECONDS));
        }
        return results;
    }

    private List<Integer> listFolios(String tenant) throws Exception {
        MvcResult listed = mockMvc.perform(get("/api/v1/dte").with(jwt().jwt(jwt -> jwt.claim("tenant_id", tenant))))
                .andReturn();
        assertThat(listed.getResponse().getStatus()).isEqualTo(200);
        return JsonPath.read(listed.getResponse().getContentAsString(), "$[*].folio");
    }

    private record IssueHttpResult(int status, Integer folio, String problemType) {

        boolean created() {
            return status == 201 && folio != null;
        }

        static IssueHttpResult from(MvcResult result) throws Exception {
            int status = result.getResponse().getStatus();
            String body = result.getResponse().getContentAsString();
            if (status == 201) {
                int folio = JsonPath.read(body, "$.folio");
                return new IssueHttpResult(status, folio, null);
            }
            String type = JsonPath.read(body, "$.type");
            return new IssueHttpResult(status, null, type);
        }
    }
}