package com.elicatari.dteissuer.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.elicatari.dteissuer.adapter.out.messaging.DteIssuedQueues;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.rabbitmq.RabbitMQContainer;

@SpringBootTest(classes = DteIssuedConsumerITConfig.class)
@AutoConfigureMockMvc
class DteIssuedConsumerIT extends AbstractJpaPostgresTest {

    private static final RabbitMQContainer rabbit;

    static {
        rabbit = new RabbitMQContainer("rabbitmq:3-management");
        rabbit.start();
    }

    @DynamicPropertySource
    static void rabbitmq(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbit::getHost);
        registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbit::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbit::getAdminPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Test
    void postAlphaPublishesDteIssuedAfterPersist() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/dte")
                        .with(jwt().jwt(jwt -> jwt.claim("tenant_id", "alpha")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "rabbit-consume-it")
                        .content("{\"rut\":\"12.345.678-5\",\"neto\":1000}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        String dteId = JsonPath.read(body, "$.id");
        int folio = JsonPath.read(body, "$.folio");

        Message message = rabbitTemplate.receive(DteIssuedQueues.NAME, 10_000);
        assertThat(message).as("si se borra el publisher este assert queda rojo").isNotNull();
        assertThat(message.getMessageProperties().getHeaders())
                .containsEntry(DteIssuedQueues.HEADER_EVENT_NAME, DteIssuedQueues.EVENT_NAME)
                .containsEntry(DteIssuedQueues.HEADER_EVENT_VERSION, DteIssuedQueues.EVENT_VERSION);
        assertThat(message.getMessageProperties().getMessageId()).isNotBlank();

        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        assertThat((String) JsonPath.read(payload, "$.tenant_id")).isEqualTo("alpha");
        assertThat((String) JsonPath.read(payload, "$.dteId")).isEqualTo(dteId);
        assertThat((Integer) JsonPath.read(payload, "$.folio")).isEqualTo(folio);
        assertThat((String) JsonPath.read(payload, "$.rut")).isEqualTo("12345678-5");
        assertThat((String) JsonPath.read(payload, "$.eventId")).isNotBlank();
        assertThat((String) JsonPath.read(payload, "$.occurredAt")).isNotBlank();
    }
}