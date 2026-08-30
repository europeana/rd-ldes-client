package eu.europeana.metis.ldaggregation.harvesting.ldes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;

import eu.europeana.metis.ldaggregation.harvesting.ldes.http.AccessException;
import eu.europeana.metis.ldaggregation.harvesting.ldes.http.HttpRequest;
import eu.europeana.metis.ldaggregation.harvesting.ldes.http.HttpResponse;
import eu.europeana.metis.ldaggregation.harvesting.ldes.http.HttpUtil;
import eu.europeana.metis.ldaggregation.harvesting.ldes.http.UrlRequest;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.ActivityType;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.LdesNode;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.Member;
import eu.europeana.metis.ldaggregation.harvesting.ldes.model.View;
import eu.europeana.metis.ldaggregation.harvesting.ldes.util.RdfUtil;

/**
 * Generic LDES traversal with optional globally ordered emission.
 *
 * <p>The historical class name is retained for compatibility. Ordering is
 * selected through {@link ActivityHandler#getTraversalMode()} and the stream's
 * timestamp/sequence paths.</p>
 */
public class TraversalOrderedByTimestamp implements TreeTraversal {
  private final ActivityHandler handler;
  private String streamUrl;
  private CrawlReport report;
  private final Map<String, Member> members = new LinkedHashMap<>();
  private final Map<String, Integer> memberPriorities = new LinkedHashMap<>();
  private final Map<String, Integer> nodePriorities = new LinkedHashMap<>();
  private final Set<String> visitedNodes = new HashSet<>();
  private Set<String> processedMemberIds;
  private RDFNode timestampPath;
  private RDFNode sequencePath;
  private RDFNode finalizedPath;
  private RDFNode finalizedObject;
  private Instant latestTimestamp;
  private String eventStreamId;
  private String rootNode;
  private Dataset rootDataset;

  public TraversalOrderedByTimestamp(ActivityHandler handler) {
    this.handler = handler;
  }

  /** Kept for compatibility; RDF retrieval uses the shared HTTP layer. */
  public TraversalOrderedByTimestamp(ActivityHandler handler, HttpClient ignored) {
    this(handler);
  }

  @Override
  public CrawlReport processStream(String streamUrl) throws Exception {
    this.streamUrl = streamUrl;
    members.clear();
    memberPriorities.clear();
    nodePriorities.clear();
    visitedNodes.clear();
    latestTimestamp = null;
    rootDataset = null;
    rootNode = null;
    eventStreamId = null;
    timestampPath = null;
    sequencePath = null;
    finalizedPath = null;
    finalizedObject = null;
    report = new CrawlReport(streamUrl, handler.getLastCrawlTimestamp(streamUrl));
    processedMemberIds = new HashSet<>(handler.getProcessedMemberIds(streamUrl));
    try {
      handler.crawlStart(streamUrl);
      collectStream();
      emitMembers();
      report.setLatestCrawledTimestamp(latestTimestamp);
      handler.crawlEnd(latestTimestamp, processedMemberIds);
    } catch (Exception error) {
      report.setCrawlFailure(error.getMessage());
      handler.crawlFail(error.getMessage(), error);
    }
    return report;
  }

  private void collectStream() throws Exception {
    Deque<String> pending = new ArrayDeque<>();
    pending.add(streamUrl);
    boolean firstDocument = true;

    while (!pending.isEmpty()) {
      String nodeUrl = pending.removeFirst();
      if (!visitedNodes.add(nodeUrl))
        continue;
      Dataset dataset;
      try {
        dataset = retrieve(nodeUrl);
      } catch (AccessException error) {
        if ("410".equals(error.getCode()))
          continue;
        throw error;
      }
      if (dataset == null)
        throw new ValidationException("No RDF representation found at " + nodeUrl);

      LdesNode node = new LdesNode(dataset.getDefaultModel(), dataset);
      if (firstDocument) {
		long currentPageViews = dataset.getDefaultModel()
		    .listSubjectsWithProperty(LdesConstants.TREE_VIEW,
		        dataset.getDefaultModel().getResource(nodeUrl)).toSet().size();
		if (currentPageViews > 1)
		  throw new ValidationException("Multiple current-page tree:view matches");
		if (node.getViewCount() != 1)
		  throw new ValidationException("LDES entry point must identify exactly one tree:view");
		eventStreamId = node.getUri();
		Resource discoveredView = node.getView().orElseThrow().getResource();
		rootNode = discoveredView.isURIResource() && !sameDocument(nodeUrl, discoveredView.getURI())
		    ? discoveredView.getURI() : nodeUrl;
        firstDocument = false;
      }
	  if (rootDataset == null && sameDocument(nodeUrl, rootNode)) {
		rootDataset = dataset;
		configureOrdering(node);
	  }
      List<Member> nodeMembers = node.getMembers();
      for (Member member : nodeMembers) {
        members.putIfAbsent(member.getId(), member);
        memberPriorities.putIfAbsent(member.getId(), nodePriorities.getOrDefault(nodeUrl, 2));
      }

      Optional<View> view = node.getView();
      if (view.isPresent()) {
        Resource viewResource = view.get().getResource();
        if (viewResource.isURIResource() && !sameDocument(nodeUrl, viewResource.getURI()))
          pending.addLast(viewResource.getURI());
        view.get().getNodeRelations().forEach(relations -> pending.addLast(relations.getNodeUrl()));
      } else if (nodeMembers.isEmpty() && node.getEventStreamResource()
          .hasProperty(org.apache.jena.vocabulary.RDF.type, LdesConstants.LDES_EVENT_STREAM)) {
        throw new ValidationException("LDES entry point has no tree:view");
      }
    }
	if (rootDataset == null)
	  throw new ValidationException("Could not retrieve the discovered LDES root node");
	handler.processContext(extractContext());
  }

  private Dataset retrieve(String nodeUrl) throws Exception {
    Optional<CachedResponse> cached = handler.getCachedResponse(nodeUrl);
    if (cached.isPresent() && cached.get().immutable()) {
      nodePriorities.put(nodeUrl, 0);
      return parse(cached.get(), nodeUrl);
    }

    UrlRequest request = new UrlRequest(nodeUrl);
    request.addHttpHeader("Accept", RdfUtil.CONTENT_TYPES_QUADS_ACCEPT_HEADER);
    cached.map(CachedResponse::etag).filter(value -> value != null && !value.isBlank())
        .ifPresent(value -> request.addHttpHeader("If-None-Match", value));
    HttpRequest fetched = HttpUtil.makeRequest(request);
    if (fetched.getResponseStatusCode() == 304) {
      if (cached.isEmpty())
        throw new ValidationException("Received 304 without a cached representation for " + nodeUrl);
	  fetched.getResponse().close();
      return parse(cached.get(), nodeUrl);
    }
	HttpResponse response = new HttpResponse(fetched);

    Dataset dataset = RdfUtil.readRdfQuads(response.body,
        RdfUtil.fromMimeType(response.getContentType()), nodeUrl);
    boolean headerImmutable = Optional.ofNullable(response.getHeader("Cache-Control"))
        .map(value -> value.toLowerCase(java.util.Locale.ROOT).contains("immutable"))
        .orElse(false);
    boolean rdfImmutable = dataset.getDefaultModel().listStatements(null,
        LdesConstants.LDES_IMMUTABLE,
        dataset.getDefaultModel().createTypedLiteral(true)).hasNext();
    boolean immutable = rdfImmutable || headerImmutable;
    nodePriorities.put(nodeUrl, rdfImmutable ? 0 : headerImmutable ? 1 : 2);
    handler.cacheResponse(nodeUrl, new CachedResponse(response.body, response.getContentType(),
        response.getHeader("ETag"), immutable));
    return dataset;
  }

  private static Dataset parse(CachedResponse response, String baseUri) {
    return RdfUtil.readRdfQuads(response.body(), RdfUtil.fromMimeType(response.contentType()), baseUri);
  }

  private LdesContext extractContext() {
    var model = rootDataset.getDefaultModel();
    Resource stream = model.getResource(eventStreamId);
    Resource root = model.getResource(rootNode);
    List<String> shapes = resourceValues(stream, LdesConstants.TREE_SHAPE);
    List<String> viewDescriptions = resourceValues(root, LdesConstants.TREE_VIEW_DESCRIPTION);
    Set<String> retention = new java.util.TreeSet<>(resourceValues(root, LdesConstants.LDES_RETENTION_POLICY));
    for (String description : viewDescriptions)
      retention.addAll(resourceValues(model.getResource(description), LdesConstants.LDES_RETENTION_POLICY));
    return new LdesContext(eventStreamId, rootNode, rootDataset, shapes, viewDescriptions,
        List.copyOf(retention), resourceValue(stream, LdesConstants.LDES_TIMESTAMP_PATH),
        resourceValue(stream, LdesConstants.LDES_SEQUENCE_PATH),
        resourceValue(stream, LdesConstants.LDES_VERSION_TIMESTAMP_PATH),
        resourceValue(stream, LdesConstants.LDES_VERSION_SEQUENCE_PATH),
        integerValue(stream, LdesConstants.LDES_POLLING_INTERVAL));
  }

  private static List<String> resourceValues(Resource subject,
      org.apache.jena.rdf.model.Property property) {
    return subject.listProperties(property).mapWith(statement -> statement.getObject())
        .filterKeep(RDFNode::isURIResource).mapWith(node -> node.asResource().getURI()).toList()
        .stream().sorted().toList();
  }

  private static String resourceValue(Resource subject, org.apache.jena.rdf.model.Property property) {
    var statement = subject.getProperty(property);
    return statement != null && statement.getObject().isURIResource()
        ? statement.getResource().getURI() : null;
  }

  private static Integer integerValue(Resource subject, org.apache.jena.rdf.model.Property property) {
    var statement = subject.getProperty(property);
    if (statement == null || !statement.getObject().isLiteral())
      return null;
    try {
      return statement.getInt();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private void configureOrdering(LdesNode node) throws ValidationException {
    timestampPath = node.getTimestampPathNode();
    sequencePath = node.getSequencePath();
    finalizedPath = node.getTransactionFinalizedPath();
    finalizedObject = node.getTransactionFinalizedObject();
    if (handler.getTraversalMode() == TraversalMode.ORDERED
        && timestampPath == null && sequencePath == null)
      throw new ValidationException("Ordered traversal requires ldes:timestampPath or ldes:sequencePath");
  }

  private void emitMembers() throws Exception {
    List<Member> ordered = new ArrayList<>(members.values());
    if (handler.getTraversalMode() != TraversalMode.UNORDERED
        && (timestampPath != null || sequencePath != null))
      ordered.sort(memberComparator());
    else
      // A stable immutable-first order makes resumable crawls deterministic while
      // retaining discovery order among nodes with the same cache semantics.
      ordered.sort(Comparator.comparingInt(member ->
          memberPriorities.getOrDefault(member.getId(), 2)));

    for (Member member : ordered) {
      if (!processedMemberIds.add(member.getId()))
        continue;
      updateLatestTimestamp(member);
      handler.processActivity(member);
      ActivityType activityType = activityType(member);
      if (activityType != null)
        report.incrementActivity(activityType);
    }
  }

  private Comparator<Member> memberComparator() {
    return (left, right) -> {
      int comparison = comparePath(left, right, timestampPath);
      if (comparison == 0)
        comparison = comparePath(left, right, sequencePath);
      if (comparison == 0)
        comparison = Boolean.compare(isFinalized(left), isFinalized(right));
      if (comparison == 0)
        comparison = left.getId().compareTo(right.getId());
      return comparison;
    };
  }

  private int comparePath(Member left, Member right, RDFNode path) {
    if (path == null)
      return 0;
    return compareValues(first(left.evaluatePath(path)), first(right.evaluatePath(path)));
  }

  private boolean isFinalized(Member member) {
    RDFNode value = first(member.evaluatePath(finalizedPath));
    if (value == null)
      return false;
    if (finalizedObject != null)
      return value.equals(finalizedObject);
    return value.isLiteral() && value.asLiteral().getBoolean();
  }

  private void updateLatestTimestamp(Member member) {
    Instant instant = asInstant(first(member.evaluatePath(timestampPath)));
    if (instant != null && (latestTimestamp == null || instant.isAfter(latestTimestamp)))
      latestTimestamp = instant;
  }

  private static RDFNode first(List<RDFNode> values) {
    return values == null || values.isEmpty() ? null : values.get(0);
  }

  private static int compareValues(RDFNode left, RDFNode right) {
    if (left == null && right == null)
      return 0;
    if (left == null)
      return 1;
    if (right == null)
      return -1;
    if (left.isLiteral() && right.isLiteral()) {
      Literal a = left.asLiteral();
      Literal b = right.asLiteral();
      if (a.getValue() instanceof Number && b.getValue() instanceof Number)
        return new BigDecimal(a.getLexicalForm()).compareTo(new BigDecimal(b.getLexicalForm()));
      Instant ai = asInstant(left);
      Instant bi = asInstant(right);
      if (ai != null && bi != null)
        return ai.compareTo(bi);
      return a.getLexicalForm().compareTo(b.getLexicalForm());
    }
    return left.toString().compareTo(right.toString());
  }

  private static Instant asInstant(RDFNode node) {
    if (node == null || !node.isLiteral())
      return null;
    String lexical = node.asLiteral().getLexicalForm();
    try {
      return Instant.parse(lexical);
    } catch (RuntimeException ignored) {
      try {
        return OffsetDateTime.parse(lexical).toInstant();
      } catch (RuntimeException alsoIgnored) {
        return null;
      }
    }
  }

  private static ActivityType activityType(Member member) {
    try {
      return member.getTypeOfActivity();
    } catch (RuntimeException ignored) {
      return null;
    }
  }

  private static boolean sameDocument(String current, String target) {
    return stripFragment(current).equals(stripFragment(target));
  }

  private static String stripFragment(String iri) {
    int index = iri.indexOf('#');
    return index < 0 ? iri : iri.substring(0, index);
  }
}
