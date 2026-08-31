# rd-ldes-client

A Java client for discovering, traversing, and synchronizing [Linked Data
Event Streams (LDES)](https://w3id.org/ldes/specification). It supports generic
LDES members as RDF datasets, ordered and unordered traversal, common SHACL
property paths, HTTP cache validation, resumable synchronization, and LDES
context reporting.

The historical class name `TraversalOrderedByTimestamp` is retained for API
compatibility. It is the main generic traversal implementation and also
supports unordered streams.

## Getting started with MINT and Rijksmuseum

### Requirements

- JDK 21+
- Maven 3.8 or newer

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
scenarios.