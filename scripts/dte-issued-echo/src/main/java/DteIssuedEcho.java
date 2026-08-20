import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Echo de demo: imprime {@code DteIssued} de {@code dte.issued}.
 *
 * <p>No es un segundo bounded context ni un worker de auditoría. Vive en {@code
 * scripts/}, no en el jar de la API. El cierre del hueco Rabbit sigue siendo
 * {@code DteIssuedConsumerIT}.
 */
public final class DteIssuedEcho {

    private DteIssuedEcho() {}

    public static void main(String[] args) throws InterruptedException {
        String host = env("RABBITMQ_HOST", "rabbitmq");
        int port = Integer.parseInt(env("RABBITMQ_PORT", "5672"));
        String user = env("RABBITMQ_USER", "dte");
        String password = env("RABBITMQ_PASSWORD", "change-me");
        String queue = env("QUEUE", "dte.issued");

        log("dte-issued-echo escuchando "
                + host
                + ":"
                + port
                + " queue="
                + queue
                + " (log/echo de demo, no es un segundo servicio)");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(user);
        factory.setPassword(password);
        factory.setRequestedHeartbeat(30);
        factory.setConnectionTimeout(5_000);
        factory.setAutomaticRecoveryEnabled(false);

        while (true) {
            try (Connection connection = factory.newConnection();
                    Channel channel = connection.createChannel()) {
                channel.queueDeclare(queue, true, false, false, null);
                channel.basicQos(1);
                CountDownLatch disconnected = new CountDownLatch(1);
                DeliverCallback onMessage = (consumerTag, delivery) -> {
                    printEvent(queue, delivery.getProperties().getHeaders(), delivery.getBody());
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                };
                channel.basicConsume(queue, false, onMessage, consumerTag -> disconnected.countDown());
                log("conectado, esperando DteIssued en " + queue);
                disconnected.await();
                log("broker no disponible, reintento en 2s");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log("broker no disponible, reintento en 2s: " + ex);
            }
            Thread.sleep(2_000);
        }
    }

    private static void printEvent(String queue, Map<String, Object> headers, byte[] body) {
        String json = new String(body, StandardCharsets.UTF_8);
        String rut = jsonValue(json, "rut");
        log("DteIssued echo"
                + " queue="
                + queue
                + " eventName="
                + header(headers, "eventName")
                + " eventVersion="
                + header(headers, "eventVersion")
                + " eventId="
                + jsonValue(json, "eventId")
                + " tenant_id="
                + jsonValue(json, "tenant_id")
                + " dteId="
                + jsonValue(json, "dteId")
                + " folio="
                + jsonValue(json, "folio")
                + " rut="
                + maskRut(rut)
                + " occurredAt="
                + jsonValue(json, "occurredAt"));
    }

    /** Misma regla que {@code LogRedaction} en la API: {@code 12345678-5} queda {@code ******78-5}. */
    static String maskRut(String canonical) {
        if (canonical == null || canonical.isBlank()) {
            return "***";
        }
        int dash = canonical.lastIndexOf('-');
        if (dash <= 0) {
            return "***";
        }
        String body = canonical.substring(0, dash);
        String suffix = canonical.substring(dash);
        if (body.length() <= 2) {
            return "*".repeat(body.length()) + suffix;
        }
        return "*".repeat(body.length() - 2) + body.substring(body.length() - 2) + suffix;
    }

    /**
     * Extrae un campo del JSON plano que publica la API. No hace falta Jackson
     * para un echo de demo: el payload no anida objetos.
     */
    static String jsonValue(String json, String key) {
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) {
            return null;
        }
        int start = i + needle.length();
        while (start < json.length() && json.charAt(start) == ' ') {
            start++;
        }
        if (start >= json.length()) {
            return null;
        }
        if (json.charAt(start) == '"') {
            int end = json.indexOf('"', start + 1);
            return end < 0 ? null : json.substring(start + 1, end);
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
            end++;
        }
        return json.substring(start, end).trim();
    }

    private static String header(Map<String, Object> headers, String key) {
        if (headers == null) {
            return null;
        }
        Object value = headers.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void log(String message) {
        System.out.println(message);
    }
}