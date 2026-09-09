package eu.europeana.metis.ldaggregation.harvesting.ldes.model;

import java.io.IOException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.europeana.metis.ldaggregation.harvesting.ldes.ActivityHandler;
import eu.europeana.metis.ldaggregation.harvesting.ldes.CachedResponse;
import eu.europeana.metis.ldaggregation.harvesting.ldes.LdesContext;
import eu.europeana.metis.ldaggregation.harvesting.ldes.TraversalMode;
import eu.europeana.metis.ldaggregation.harvesting.ldes.TraversalOrderedByTimestamp;

/** Protocol bridge between the language-neutral conformance suite and this client. */
public final class EuropeanaAdapter implements ActivityHandler {
  private static final ObjectMapper JSON = new ObjectMapper();

  private final PrintStream protocol;
  private final String runId;
  private final Path timestampFile;
  private final Path membersFile;
  private final Path cacheDirectory;
  private final TraversalMode mode;
  private boolean failed;
  private int emitted;

  private EuropeanaAdapter(PrintStream protocol, String runId, Path stateDirectory,
      TraversalMode mode) {
    this.protocol = protocol;
    this.runId = runId;
    timestampFile = stateDirectory.resolve("last-crawl.txt");
    membersFile = stateDirectory.resolve("processed-members.txt");
    cacheDirectory = stateDirectory.resolve("cache");
    this.mode = mode;
  }

  public static void main(String[] args) throws Exception {
    PrintStream protocol = System.out;
    System.setOut(System.err);
    JsonNode input = JSON.readTree(System.in);
    Path state = Path.of(input.path("stateDirectory").asText());
    Files.createDirectories(state);
    TraversalMode mode = "ordered".equals(input.path("mode").asText())
        ? TraversalMode.ORDERED : TraversalMode.UNORDERED;
    EuropeanaAdapter handler = new EuropeanaAdapter(
        protocol, input.path("runId").asText(), state, mode);
    try {
      new TraversalOrderedByTimestamp(handler).processStream(input.path("entrypoint").asText());
      if (!handler.failed) {
        handler.emit(event(handler.runId, "statistics", "membersEmitted", handler.emitted,
            "lastRun", Instant.now().toString()));
        handler.emit(event(handler.runId, "run-end", "status", "completed"));
      }
    } catch (Throwable error) {
      handler.error(error);
    }
  }

  @Override
  public boolean isSupportedResourceType(String type) {
    return true;
  }

  @Override
  public void processActivity(Member activity) throws IOException {
    StringWriter dataset = new StringWriter();
    RDFDataMgr.write(dataset, activity.getDataset().asDatasetGraph(), Lang.NQUADS);
    emitted++;
    try {
      emit(event(runId, "member", "id", activity.getId(), "dataset", dataset.toString(),
          "mediaType", "application/n-quads"));
    } catch (Exception exception) {
      throw new IOException(exception);
    }
  }

  @Override
  public TraversalMode getTraversalMode() {
    return mode;
  }

  @Override
  public Set<String> getProcessedMemberIds(String streamUri) {
    try {
      return Files.exists(membersFile)
          ? new LinkedHashSet<>(Files.readAllLines(membersFile)) : Set.of();
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
  }

  @Override
  public Optional<CachedResponse> getCachedResponse(String resourceUri) {
    try {
      Path file = cacheFile(resourceUri);
      if (!Files.exists(file))
        return Optional.empty();
      JsonNode value = JSON.readTree(file.toFile());
      return Optional.of(new CachedResponse(
          Base64.getDecoder().decode(value.path("body").asText()),
          value.path("contentType").asText(),
          value.path("etag").isNull() ? null : value.path("etag").asText(),
          value.path("immutable").asBoolean()));
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
  }

  @Override
  public void cacheResponse(String resourceUri, CachedResponse response) throws Exception {
    Files.createDirectories(cacheDirectory);
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("body", Base64.getEncoder().encodeToString(response.body()));
    value.put("contentType", response.contentType());
    value.put("etag", response.etag());
    value.put("immutable", response.immutable());
    JSON.writeValue(cacheFile(resourceUri).toFile(), value);
  }

  @Override
  public void processContext(LdesContext context) throws Exception {
    StringWriter dataset = new StringWriter();
    RDFDataMgr.write(dataset, context.dataset().asDatasetGraph(), Lang.NQUADS);
    Map<String, Object> value = event(runId, "context",
        "eventStream", context.eventStream(), "rootNode", context.rootNode(),
        "dataset", dataset.toString(), "mediaType", "application/n-quads");
    put(value, "shapes", context.shapes());
    put(value, "viewDescriptions", context.viewDescriptions());
    put(value, "retentionPolicies", context.retentionPolicies());
    // Always present, empty included: the empty list is the evidence that the
    // client resolved every announced policy and found none of them empty.
    value.put("emptyRetentionPolicies", context.emptyRetentionPolicies());
    put(value, "timestampPath", context.timestampPath());
    put(value, "sequencePath", context.sequencePath());
    put(value, "versionTimestampPath", context.versionTimestampPath());
    put(value, "versionSequencePath", context.versionSequencePath());
    put(value, "pollingInterval", context.pollingInterval());
    emit(value);
  }

  private static void put(Map<String, Object> target, String key, Object value) {
    if (value != null
        && (!(value instanceof java.util.Collection<?> values) || !values.isEmpty()))
      target.put(key, value);
  }

  private Path cacheFile(String resourceUri) {
    String name = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(resourceUri.getBytes(StandardCharsets.UTF_8));
    return cacheDirectory.resolve(name + ".json");
  }

  @Override
  public void log(String message) {
    System.err.println(message);
  }

  @Override
  public void log(String message, Exception exception) {
    System.err.println(message);
    exception.printStackTrace(System.err);
  }

  @Override
  public Instant getLastCrawlTimestamp(String streamUri) {
    try {
      return Files.exists(timestampFile)
          ? Instant.parse(Files.readString(timestampFile).trim()) : null;
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @Override
  public void crawlStart(String streamUri) {
    System.err.println("Europeana crawl started: " + streamUri);
  }

  @Override
  public void crawlEnd(Instant latestTimestamp) throws Exception {
    if (latestTimestamp != null)
      Files.writeString(timestampFile, latestTimestamp.toString());
  }

  @Override
  public void crawlEnd(Instant latestTimestamp, Set<String> processedMemberIds) throws Exception {
    crawlEnd(latestTimestamp);
    Files.write(membersFile, processedMemberIds);
  }

  @Override
  public void crawlFail(String errorMessage, Exception cause) throws Exception {
    failed = true;
    emit(event(runId, "error", "kind", "client", "message",
        errorMessage == null ? cause.toString() : errorMessage));
  }

  private void error(Throwable error) throws Exception {
    if (failed)
      return;
    failed = true;
    emit(event(runId, "error", "kind", "client", "message",
        error.getMessage() == null ? error.toString() : error.getMessage()));
  }

  private static Map<String, Object> event(String runId, String type, Object... properties) {
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("protocolVersion", 0);
    event.put("type", type);
    event.put("runId", runId);
    for (int index = 0; index < properties.length; index += 2)
      event.put((String) properties[index], properties[index + 1]);
    return event;
  }

  private void emit(Map<String, Object> event) throws Exception {
    protocol.println(JSON.writeValueAsString(event));
    protocol.flush();
  }
}
