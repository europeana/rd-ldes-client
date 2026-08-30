package eu.europeana.metis.ldaggregation.harvesting.ldes.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

class HttpUtilTest {

  @ParameterizedTest
  @ValueSource(ints = {408, 425, 429, 500, 502, 503, 504})
  @Tag("spec-compliance-pending")
  void retriesRequiredTransientStatuses(int transientStatus) throws Exception {
    AtomicInteger requests = new AtomicInteger();
    try (ScenarioServer server = new ScenarioServer(exchange -> {
      int status = requests.incrementAndGet() == 1 ? transientStatus : 200;
      if (status == 429) {
        exchange.getResponseHeaders().add("Retry-After", "0");
      }
      respond(exchange, status, status == 200 ? "ok" : "retry");
    })) {
      HttpRequest response = HttpUtil.makeRequest(server.url());

      assertEquals(200, response.getResponseStatusCode());
      assertEquals(2, requests.get());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {400, 401, 403, 404, 410})
  @Tag("spec-compliance-pending")
  void doesNotRetryTerminalStatuses(int terminalStatus) throws Exception {
    AtomicInteger requests = new AtomicInteger();
    try (ScenarioServer server = new ScenarioServer(exchange -> {
      requests.incrementAndGet();
      respond(exchange, terminalStatus, "terminal");
    })) {
      assertThrows(AccessException.class, () -> HttpUtil.makeRequest(server.url()));
      assertEquals(1, requests.get());
    }
  }

  private static void respond(HttpExchange exchange, int status, String value) throws IOException {
    byte[] body = value.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(status, body.length);
    exchange.getResponseBody().write(body);
    exchange.close();
  }

  @FunctionalInterface
  private interface Handler {
    void handle(HttpExchange exchange) throws IOException;
  }

  private static final class ScenarioServer implements AutoCloseable {
    private final HttpServer server;

    private ScenarioServer(Handler handler) throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/resource", handler::handle);
      server.start();
    }

    private String url() {
      return "http://127.0.0.1:" + server.getAddress().getPort() + "/resource";
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
