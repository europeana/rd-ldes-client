# rd-ldes-client

A Java 21 client for discovering, traversing, and synchronizing [Linked Data
Event Streams (LDES)](https://w3id.org/ldes/specification). It supports generic
LDES members as RDF datasets, ordered and unordered traversal, common SHACL
property paths, HTTP cache validation, resumable synchronization, and LDES
context reporting.

The historical class name `TraversalOrderedByTimestamp` is retained for API
compatibility. It is the main generic traversal implementation and also
supports unordered streams.

## Getting started with MINT and Rijksmuseum

### Requirements

- JDK 21
- Maven 3.8 or newer
- Network access to the LDES server being crawled

Build and install the snapshot in your local Maven repository:

```sh
git clone https://github.com/europeana/rd-ldes-client.git
cd rd-ldes-client
mvn clean install
```

Add the client to your Maven application:

```xml
<dependency>
  <groupId>eu.europeana.metis</groupId>
  <artifactId>ldes-client</artifactId>
  <version>0.1-SNAPSHOT</version>
</dependency>
```

Place the following example in `src/main/java/LdesExample.java` in your Maven
application. It performs a one-shot crawl and writes every extracted member
dataset as N-Quads. Use either of the public entry points shown below.

```java
import java.io.IOException;
import java.time.Instant;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import eu.europeana.metis.ldaggregation.harvesting.ldes.ActivityHandler;
import eu.europeana.metis.ldaggregation.harvesting.ldes.CrawlReport;
import eu.europeana.metis.ldaggregation.harvesting.ldes.TraversalMode;
import eu.europeana.metis.ldaggregation.harvesting.ldes.TraversalOrderedByTimestamp;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.Member;

public final class LdesExample {
  private static final String MINT =
      "https://mint-ldes.ails.ece.ntua.gr/euscreenxl/1008/ldes";
  private static final String RIJKSMUSEUM =
      "https://data.rijksmuseum.nl/ldes/collection.json";

  public static void main(String[] args) throws Exception {
    // Replace MINT with RIJKSMUSEUM, or pass an entry point as the first argument.
    String entryPoint = args.length == 0 ? MINT : args[0];

    ActivityHandler handler = new ActivityHandler() {
      @Override
      public TraversalMode getTraversalMode() {
        return TraversalMode.AUTO;
      }

      @Override
      public void processActivity(Member member) throws IOException {
        System.out.println("# member " + member.getId());
        RDFDataMgr.write(System.out, member.getDataset().asDatasetGraph(), Lang.NQUADS);
      }

      @Override
      public boolean isSupportedResourceType(String type) {
        return true;
      }

      @Override
      public Instant getLastCrawlTimestamp(String streamUri) {
        return null; // One-shot example: no previous synchronization state.
      }

      @Override
      public void crawlStart(String streamUri) {
        System.err.println("Crawling " + streamUri);
      }

      @Override
      public void crawlEnd(Instant latestTimestamp) {
        System.err.println("Latest timestamp: " + latestTimestamp);
      }

      @Override
      public void crawlFail(String message, Exception cause) {
        System.err.println("Crawl failed: " + message);
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
    };

    CrawlReport report = new TraversalOrderedByTimestamp(handler).processStream(entryPoint);
    if (!report.wasSuccessful()) {
      throw new IllegalStateException(report.toString());
    }
  }
}
```

Run MINT by omitting the argument, or pass the Rijksmuseum endpoint:

```sh
mvn org.codehaus.mojo:exec-maven-plugin:3.5.1:java \
  -Dexec.mainClass=LdesExample

mvn org.codehaus.mojo:exec-maven-plugin:3.5.1:java \
  -Dexec.mainClass=LdesExample \
  -Dexec.args=https://data.rijksmuseum.nl/ldes/collection.json
```

These are third-party public services, so availability and dataset size are
outside this project's control. Do not assume that a first crawl will be small
or fast.

## How the client works

`TraversalOrderedByTimestamp` starts at an LDES entry point, discovers its
event stream and root view, follows TREE relations, extracts members, and calls
your `ActivityHandler`. The handler is the integration boundary for member
processing, state, caching, and lifecycle events.

The most important callbacks are:

| Callback | Responsibility |
| --- | --- |
| `processActivity(Member)` | Process one previously unseen member. Use `Member.getDataset()` for its RDF dataset. |
| `processContext(LdesContext)` | Observe stream/root IRIs, shapes, view descriptions, retention policies, ordering paths, and polling metadata. |
| `getProcessedMemberIds(String)` | Restore durable member identifiers before a synchronization run. |
| `crawlEnd(Instant, Set<String>)` | Commit the successful run's timestamp and processed-member state. |
| `getCachedResponse(String)` | Restore a cached node representation and its ETag/immutability metadata. |
| `cacheResponse(String, CachedResponse)` | Persist a retrieved representation for conditional requests or immutable reuse. |
| `crawlFail(String, Exception)` | Record or report a failed run without advancing committed synchronization state. |

The older abstract methods on `ActivityHandler`, including
`isSupportedResourceType`, remain for compatibility with the original
ActivityStreams client. Generic LDES applications should filter members in
`processActivity` based on their RDF instead of assuming ActivityStreams
properties are present.

### Traversal modes

Choose a mode by overriding `ActivityHandler.getTraversalMode()`:

- `AUTO` orders globally when the stream declares a timestamp or sequence path;
  otherwise it uses unordered traversal. This is the recommended default.
- `UNORDERED` emits members without applying the stream's ordering paths.
- `ORDERED` requires an `ldes:timestampPath` or `ldes:sequencePath` and reports
  an error when neither is available.

Ordered traversal supports direct properties, SHACL sequence, alternative,
inverse, zero-or-one, zero-or-more, and one-or-more paths. Timestamp and
sequence values are used for global ordering; transaction finalizers are
emitted after non-finalized members with equal ordering values.

### Member datasets

Always prefer `Member.getDataset()` for generic LDES processing. It contains:

- the member's default-graph subject star;
- recursively reachable blank nodes, with cycle protection; and
- the complete named graph whose graph IRI equals the member IRI, when present.

Unrelated page triples and unrelated named graphs are excluded.
`getObjectNamedGraph()` returns a legacy union model for older ActivityStreams
consumers and cannot preserve named-graph boundaries.

## Production synchronization

A one-shot handler may rely on all default state and cache methods. A recurring
synchronizer should persist both processed member IDs and HTTP representations.
Persisting only the latest timestamp is not sufficient: streams may rebalance,
reuse equal ordering values, or expose unordered members.

Recommended practices:

- Store state per event-stream entry point; do not share one member-ID set
  across unrelated streams.
- Commit processed IDs and the latest timestamp atomically from the two-argument
  `crawlEnd` callback. Do not advance them from `crawlStart` or `crawlFail`.
- Key `CachedResponse` values by the exact resource URI supplied to the cache
  callbacks. Preserve the response bytes, content type, ETag, and immutable
  flag without RDF reserialization.
- Keep immutable cached nodes indefinitely or until an explicit administrative
  reset. Mutable nodes are revisited and conditionally requested using ETags.
- Make `processActivity` idempotent. Durable state protects normal restarts,
  but idempotency also protects against crashes between an external side effect
  and the final state commit.
- Create a separate traversal instance for each run. Traversal instances hold
  mutable crawl state and are not intended for concurrent use.
- Apply backpressure in `processActivity` rather than starting unbounded
  asynchronous work. A successful crawl means the callback returned
  successfully, not that detached work eventually completed.
- Inspect `CrawlReport.wasSuccessful()`. Traversal errors are reported through
  the handler and report instead of always escaping `processStream`.
- Treat `LdesContext.pollingInterval()` as server metadata. This client exposes
  it but does not autonomously schedule the next crawl.

For durable implementations, use a database transaction or write a temporary
state file followed by an atomic rename. Cache writes may happen during a run,
but the processed-member checkpoint should only become visible after success.

## HTTP and RDF support

The client advertises and parses:

- JSON-LD (`application/ld+json`)
- TriG (`application/trig`)
- N-Quads (`application/n-quads`)
- N-Triples (`application/n-triples`)
- Turtle (`text/turtle`)

It resolves relative IRIs against the response URL, follows redirects, treats
a related node returning `410 Gone` as empty, retries bounded transient
responses (`408`, `425`, `429`, `500`, `502`, `503`, and `504`), and honors
`Retry-After`, ETags, `Cache-Control: immutable`, and `ldes:immutable true`.

## Building and testing

Run the complete local regression suite:

```sh
mvn clean test
```

Create the local snapshot JAR:

```sh
mvn clean package
```

The tests cover RDF formats, discovery, TREE relations, generic extraction,
blank-node closure, SHACL paths, and HTTP retry behavior. HTTP tests start a
loopback server and therefore require permission to bind to `127.0.0.1`.

### LDES specification conformance

The [LDES conformance workflow](.github/workflows/ldes-conformance.yml) runs on
every push and pull request and can be started manually. It:

1. runs the Maven regression suite;
2. checks out a pinned revision of the external conformance suite;
3. builds the repository-owned adapter under `.ldes-conformance/`;
4. runs every applicable LDES 1.0 scenario;
5. requires an EARL report and zero failed, indeterminate, or untested
   applicable assertions; and
6. uploads `earl.ttl`, `summary.json`, coverage, and per-test evidence as a
   GitHub Actions artifact.

The current verified result is 58 passed and 4 explicitly inapplicable optional
scenarios. The undeclared optional capabilities are TREE Profile extraction
and autonomous polling. See [SPEC_COMPLIANCE_PLAN.md](SPEC_COMPLIANCE_PLAN.md)
for the implementation record and pinned suite revision.

## Project layout

```text
src/main/java/                       Client implementation
src/test/java/                       Unit and regression tests
.ldes-conformance/adapters/          External-suite protocol adapter
.github/workflows/ldes-conformance.yml
                                     Conformance and EARL workflow
SPEC_COMPLIANCE_PLAN.md              Compliance implementation record
```

## Contributing

Contributions are welcome. Keep changes focused and preserve unrelated working
tree changes when developing locally.

Before opening a pull request:

1. Add or update a focused regression test for observable behavior.
2. Run `mvn clean test` on JDK 21.
3. Run the conformance workflow for changes to discovery, traversal, member
   extraction, HTTP behavior, ordering, state, caching, or context.
4. Update this README when public APIs, supported behavior, requirements, or
   operational guidance change.
5. Update the repository-owned conformance adapter when an `ActivityHandler`,
   `Member`, state, cache, or context API change affects the protocol bridge.

### Design guidelines

- Keep the generic LDES algorithm independent of ActivityStreams. Legacy
  ActivityStreams behavior is a compatibility layer, not a requirement for a
  valid member.
- Test externally observable RDF and HTTP behavior rather than Jena iteration
  order or private implementation details.
- Preserve RDF dataset boundaries. Avoid flattening named graphs unless calling
  a clearly documented legacy API.
- Resolve IRIs using the retrieved document URL, including redirects.
- Keep retries bounded and restrict them to explicitly transient failures.
- Do not silently downgrade malformed or ambiguous discovery data; report a
  useful validation failure.
- Treat state formats as durable interfaces. Changes must be restart-safe and
  should include migration guidance when compatibility cannot be preserved.
- Keep optional capabilities explicit. An unsupported optional feature should
  be reported as inapplicable, never claimed as passing.
- Avoid adding production dependencies for examples or tests when an existing
  JDK, Jena, or Apache HTTP facility is sufficient.

When reporting a bug, include the entry-point URL or a minimal RDF fixture, the
response content type and relevant HTTP status/headers, the traversal mode, the
client revision, and the complete `CrawlReport` or exception. Remove secrets
and private dataset content before sharing logs or cached responses.
