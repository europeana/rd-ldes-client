package eu.europeana.metis.ldaggregation.harvesting.ldes;

/** Persistable HTTP representation used for conditional and immutable-node retrieval. */
public record CachedResponse(byte[] body, String contentType, String etag, boolean immutable) {
  public CachedResponse {
    body = body == null ? new byte[0] : body.clone();
  }

  @Override
  public byte[] body() {
    return body.clone();
  }
}
