package eu.europeana.metis.ldaggregation.harvesting.ldes.util;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import com.apicatalog.jsonld.loader.SchemeRouter;

/**
 * A JSON-LD document loader that fetches each remote context only once.
 *
 * <p>Every JSON-LD page of an event stream normally references the same context
 * document. Left to the default loader, crawling a stream of a thousand pages
 * fetches that context a thousand times, which is traffic the publisher never
 * asked for. Contexts are versioned by IRI and served as stable documents, so
 * they are safe to reuse for the lifetime of the crawl.</p>
 *
 * <p>The cache is bounded and evicts the least recently used entry, so a client
 * that meets an unbounded set of context IRIs cannot grow without limit.</p>
 */
public final class CachingJsonLdLoader implements DocumentLoader {
  private static final int MAX_ENTRIES = 128;

  private final DocumentLoader delegate;
  private final Map<URI, Document> contexts;

  public CachingJsonLdLoader() {
    this(SchemeRouter.defaultInstance());
  }

  public CachingJsonLdLoader(DocumentLoader delegate) {
    this.delegate = delegate;
    this.contexts = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<URI, Document> eldest) {
        return size() > MAX_ENTRIES;
      }
    });
  }

  @Override
  public Document loadDocument(URI url, DocumentLoaderOptions options) throws JsonLdError {
    Document cached = contexts.get(url);
    if (cached != null)
      return cached;
    Document loaded = delegate.loadDocument(url, options);
    contexts.put(url, loaded);
    return loaded;
  }
}
