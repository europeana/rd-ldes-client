package eu.europeana.metis.ldaggregation.harvesting.ldes.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFList;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;

import eu.europeana.metis.ldaggregation.harvesting.ldes.LdesConstants;

/** Evaluates the SHACL property-path forms used by LDES ordering paths. */
public final class ShaclPath {
  private ShaclPath() {
  }

  public static List<RDFNode> evaluate(Model model, Resource start, RDFNode path) {
    if (path == null)
      return List.of();
    return new ArrayList<>(evaluate(model, Set.of(start), path, new LinkedHashSet<>()));
  }

  private static Set<RDFNode> evaluate(Model model, Set<? extends RDFNode> starts, RDFNode path,
      Set<String> active) {
    String guard = path.toString() + starts;
    if (!active.add(guard))
      return Set.of();
    try {
      if (path.isURIResource())
        return forward(model, starts, model.createProperty(path.asResource().getURI()));
      if (!path.isResource())
        return Set.of();

      Resource pathResource = path.asResource();
      if (pathResource.hasProperty(RDF.first)) {
        Set<? extends RDFNode> current = starts;
        for (RDFNode part : pathResource.as(RDFList.class).asJavaList())
          current = evaluate(model, current, part, active);
        return new LinkedHashSet<>(current);
      }
      if (pathResource.hasProperty(LdesConstants.SH_ALTERNATIVE_PATH)) {
        Resource list = pathResource.getPropertyResourceValue(LdesConstants.SH_ALTERNATIVE_PATH);
        Set<RDFNode> result = new LinkedHashSet<>();
        for (RDFNode alternative : list.as(RDFList.class).asJavaList())
          result.addAll(evaluate(model, starts, alternative, active));
        return result;
      }
      if (pathResource.hasProperty(LdesConstants.SH_INVERSE_PATH)) {
        RDFNode inverse = pathResource.getProperty(LdesConstants.SH_INVERSE_PATH).getObject();
        if (!inverse.isURIResource())
          return Set.of();
        return inverse(model, starts, model.createProperty(inverse.asResource().getURI()));
      }
      if (pathResource.hasProperty(LdesConstants.SH_ZERO_OR_ONE_PATH)) {
        RDFNode nested = pathResource.getProperty(LdesConstants.SH_ZERO_OR_ONE_PATH).getObject();
        Set<RDFNode> result = new LinkedHashSet<>(starts);
        result.addAll(evaluate(model, starts, nested, active));
        return result;
      }
      if (pathResource.hasProperty(LdesConstants.SH_ZERO_OR_MORE_PATH)) {
        RDFNode nested = pathResource.getProperty(LdesConstants.SH_ZERO_OR_MORE_PATH).getObject();
        return closure(model, starts, nested, true);
      }
      if (pathResource.hasProperty(LdesConstants.SH_ONE_OR_MORE_PATH)) {
        RDFNode nested = pathResource.getProperty(LdesConstants.SH_ONE_OR_MORE_PATH).getObject();
        return closure(model, starts, nested, false);
      }
      return Set.of();
    } finally {
      active.remove(guard);
    }
  }

  private static Set<RDFNode> forward(Model model, Set<? extends RDFNode> starts, Property property) {
    Set<RDFNode> result = new LinkedHashSet<>();
    for (RDFNode start : starts)
      if (start.isResource())
        model.listObjectsOfProperty(start.asResource(), property).forEachRemaining(result::add);
    return result;
  }

  private static Set<RDFNode> inverse(Model model, Set<? extends RDFNode> starts, Property property) {
    Set<RDFNode> result = new LinkedHashSet<>();
    for (RDFNode start : starts)
      model.listSubjectsWithProperty(property, start).forEachRemaining(result::add);
    return result;
  }

  private static Set<RDFNode> closure(Model model, Set<? extends RDFNode> starts, RDFNode path,
      boolean includeStarts) {
    Set<RDFNode> result = new LinkedHashSet<>();
    Set<RDFNode> frontier = new LinkedHashSet<>(starts);
    Set<RDFNode> expanded = new LinkedHashSet<>();
    if (includeStarts)
      result.addAll(frontier);
    while (!frontier.isEmpty()) {
      expanded.addAll(frontier);
      Set<RDFNode> next = evaluate(model, frontier, path, new LinkedHashSet<>());
      result.addAll(next);
      next.removeAll(expanded);
      if (next.isEmpty())
        break;
      frontier = next;
    }
    return result;
  }
}
