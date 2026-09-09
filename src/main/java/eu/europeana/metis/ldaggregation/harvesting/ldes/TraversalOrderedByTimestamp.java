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
import org.apache.jena.rdf.model.Model;
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
      Page page;
      try {
        page = retrieve(nodeUrl);
      } catch (AccessException error) {
        if ("410".equals(error.getCode()))
          continue;
        throw error;
      }
      if (page == null || page.dataset() == null)
        throw new ValidationException("No RDF representation found at " + nodeUrl);
      // Redirects move the document: everything below is about the page we ended
      // up on, whose IRI is also the base every relative IRI resolved against.
      Dataset dataset = page.dataset();
      String pageUrl = page.url();
      if (!nodeUrl.equals(pageUrl) && !visitedNodes.add(pageUrl))
        continue;
      Model pageModel = RdfUtil.unionModel(dataset);

      if (firstDocument) {
        discoverRoot(pageModel, pageUrl);
        firstDocument = false;
        if (!sameDocument(pageUrl, rootNode))
          pending.addLast(rootNode);
      }
      LdesNode node = new LdesNode(pageModel, dataset, eventStreamId);
      if (rootDataset == null && sameDocument(pageUrl, rootNode)) {
        rootDataset = dataset;
        configureOrdering(node);
        handler.processContext(extractContext());
      }
      for (Member member : node.getMembers()) {
        // Unordered traversal has nothing to wait for: a member whose page has
        // been read is complete. Buffering it until the whole tree is crawled
        // would stall the consumer behind the slowest branch and hold the entire
        // stream in memory; ordered emission has no such choice.
        if (handler.getTraversalMode() == TraversalMode.UNORDERED) {
          emitMember(member);
          continue;
        }
        members.putIfAbsent(member.getId(), member);
        memberPriorities.putIfAbsent(member.getId(), nodePriorities.getOrDefault(nodeUrl, 2));
      }

      // A tree:relation is scoped to the page that carries it. Relations hanging
      // off any other subject describe someone else's node and are not ours to
      // follow, so the frontier is read from the page IRI alone.
      for (Resource relation : resourceObjects(pageModel.getResource(pageUrl),
          LdesConstants.TREE_RELATION))
        for (String target : resourceValues(relation, LdesConstants.TREE_NODE))
          pending.addLast(target);
    }
	if (rootDataset == null)
	  throw new ValidationException("Could not retrieve the discovered LDES root node");
  }

  /**
   * Identifies the event stream and its root node from the entry document.
   *
   * <p>LDES 1.0 s4.2 puts the current-page pattern first: a {@code tree:view}
   * pointing at the page in hand names the root outright. Only when the page
   * makes no such claim may the entry point's own {@code tree:view} be used,
   * which keeps a stray view statement about some other resource from
   * redirecting the crawl.</p>
   */
  private void discoverRoot(Model pageModel, String pageUrl) throws ValidationException {
    Set<Resource> currentPage = pageModel
        .listSubjectsWithProperty(LdesConstants.TREE_VIEW, pageModel.getResource(pageUrl)).toSet();
    if (currentPage.size() > 1)
      throw new ValidationException("Multiple current-page tree:view matches");
    if (currentPage.size() == 1 && currentPage.iterator().next().isURIResource()) {
      eventStreamId = currentPage.iterator().next().getURI();
      rootNode = pageUrl;
      return;
    }
    List<String> announced = resourceValues(pageModel.getResource(streamUrl),
        LdesConstants.TREE_VIEW);
    if (announced.size() != 1)
      throw new ValidationException("LDES entry point must identify exactly one tree:view");
    eventStreamId = streamUrl;
    rootNode = announced.get(0);
  }

  /** A retrieved document together with the IRI it was finally served from. */
  private record Page(String url, Dataset dataset) {
  }

  private Page retrieve(String nodeUrl) throws Exception {
    Optional<CachedResponse> cached = handler.getCachedResponse(nodeUrl);
    if (cached.isPresent() && cached.get().immutable()) {
      nodePriorities.put(nodeUrl, 0);
      return new Page(nodeUrl, parse(cached.get(), nodeUrl));
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
      return new Page(nodeUrl, parse(cached.get(), nodeUrl));
    }
	HttpResponse response = new HttpResponse(fetched);

    String pageUrl = fetched.getFinalUrl();
    Dataset dataset = RdfUtil.readRdfQuads(response.body,
        RdfUtil.fromMimeType(response.getContentType()), pageUrl);
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
    return new Page(pageUrl, dataset);
  }

  private static Dataset parse(CachedResponse response, String baseUri) {
    return RdfUtil.readRdfQuads(response.body(), RdfUtil.fromMimeType(response.contentType()), baseUri);
  }

  private LdesContext extractContext() {
    Model model = RdfUtil.unionModel(rootDataset);
    Resource stream = model.getResource(eventStreamId);
    Resource root = model.getResource(rootNode);
    List<String> shapes = resourceValues(stream, LdesConstants.TREE_SHAPE);
    // A view description is usually a blank node, so the retention policies it
    // announces are only reachable by walking the node itself, never its IRI.
    List<Resource> viewDescriptionNodes = resourceObjects(root, LdesConstants.TREE_VIEW_DESCRIPTION);
    Set<String> retention = new java.util.TreeSet<>(resourceValues(root, LdesConstants.LDES_RETENTION_POLICY));
    for (Resource description : viewDescriptionNodes)
      retention.addAll(resourceValues(description, LdesConstants.LDES_RETENTION_POLICY));
    // LDES 1.0 s4.4: a policy the page names but never describes means the view
    // retains no members, which a consumer can only act on if we single it out.
    List<String> emptyRetention = retention.stream()
        .filter(policy -> !model.contains(model.getResource(policy), null, (RDFNode) null))
        .toList();
    List<String> viewDescriptions = viewDescriptionNodes.stream().filter(RDFNode::isURIResource)
        .map(Resource::getURI).sorted().toList();
    return new LdesContext(eventStreamId, rootNode, rootDataset, shapes, viewDescriptions,
        List.copyOf(retention), emptyRetention, resourceValue(stream, LdesConstants.LDES_TIMESTAMP_PATH),
        resourceValue(stream, LdesConstants.LDES_SEQUENCE_PATH),
        resourceValue(stream, LdesConstants.LDES_VERSION_TIMESTAMP_PATH),
        resourceValue(stream, LdesConstants.LDES_VERSION_SEQUENCE_PATH),
        integerValue(stream, LdesConstants.LDES_POLLING_INTERVAL));
  }

  private static List<String> resourceValues(Resource subject,
      org.apache.jena.rdf.model.Property property) {
    return resourceObjects(subject, property).stream().filter(RDFNode::isURIResource)
        .map(Resource::getURI).sorted().toList();
  }

  private static List<Resource> resourceObjects(Resource subject,
      org.apache.jena.rdf.model.Property property) {
    return subject.listProperties(property).mapWith(statement -> statement.getObject())
        .filterKeep(RDFNode::isResource).mapWith(RDFNode::asResource).toList();
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

    for (Member member : ordered)
      emitMember(member);
  }

  /** Hands one not-yet-seen member to the handler. */
  private void emitMember(Member member) throws Exception {
    if (!processedMemberIds.add(member.getId()))
      return;
    updateLatestTimestamp(member);
    handler.processActivity(member);
    ActivityType activityType = activityType(member);
    if (activityType != null)
      report.incrementActivity(activityType);
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
