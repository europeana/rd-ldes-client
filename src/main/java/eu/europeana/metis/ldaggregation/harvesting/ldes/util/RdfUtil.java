package eu.europeana.metis.ldaggregation.harvesting.ldes.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.security.InvalidParameterException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.RDFReaderI;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandler;

import eu.europeana.metis.ldaggregation.harvesting.ldes.http.AccessException;
import eu.europeana.metis.ldaggregation.harvesting.ldes.http.HttpRequest;
import eu.europeana.metis.ldaggregation.harvesting.ldes.http.HttpResponse;
import eu.europeana.metis.ldaggregation.harvesting.ldes.http.HttpUtil;

public class RdfUtil {
	public static class Jena {
		public static Resource createResource() {
			return ResourceFactory.createResource();
		}

		public static Resource createResource(String uri) {
			return ResourceFactory.createResource(uri);
		}

		public static Model createModel() {
			return ModelFactory.createDefaultModel();
		}

		public static Property createProperty(String uri) {
			return ResourceFactory.createProperty(uri);
		}

		public static Statement createStatement(Resource sub, Property pred, RDFNode obj) {
			return ResourceFactory.createStatement(sub, pred, obj);
		}

		public static Resource getResourceIfExists(String uri, Model model) {
			Resource createResource = model.createResource(uri);
			StmtIterator stms = model.listStatements(createResource, null, (RDFNode) null);
			if (stms.hasNext())
				return createResource;
			else
				return null;
		}
	}

	public static final String CONTENT_TYPES_ACCEPT_HEADER = Lang.RDFXML.getContentType().getContentTypeStr() + ", "
			+ Lang.TURTLE.getContentType().getContentTypeStr() + ", " + Lang.JSONLD.getContentType().getContentTypeStr();
	public static final String CONTENT_TYPES_QUADS_ACCEPT_HEADER = Lang.JSONLD.getContentType().getContentTypeStr() + ", "
	    + Lang.TRIG.getContentType().getContentTypeStr();

	protected static ErrorHandler lenientErrorHandler = new ErrorHandler() {
    @Override
    public void warning(String message, long line, long col) {
        System.err.println("WARNING at line " + line + ", col " + col + ": " + message);
        // You can choose to ignore warnings, log them differently, etc.
    }

    
    @Override
    public void error(String message, long line, long col) {
        System.err.println("ERROR at line " + line + ", col " + col + ": " + message);
        // You can throw an exception here to stop parsing immediately
        // throw new RiotException("Parsing error encountered: " + message);
    }

    @Override
    public void fatal(String message, long line, long col) {
        System.err.println("FATAL at line " + line + ", col " + col + ": " + message);
        // Fatal errors typically always throw a RiotException
        throw new RiotException("Fatal parsing error: " + message);
    }
};

	
	
	
	
	
	
	
	public static Resource findResource(Resource startResource, Property... propertiesToFollow) {
		Resource curRes = startResource;
		for (int i = 0; i < propertiesToFollow.length; i++) {
			Statement propStm = curRes.getProperty(propertiesToFollow[i]);
			if (propStm == null)
				return null;
			curRes = (Resource) propStm.getObject();
		}
		return curRes;
	}

	public static String getUriOrId(Resource srcResource) {
		return srcResource.isURIResource() ? srcResource.getURI() : srcResource.getId().getBlankNodeId().toString();
	}

	public static String getUriOrLiteralValue(RDFNode resource) {
		return resource.isURIResource() ? resource.asResource().getURI()
				: (resource.isLiteral() ? resource.asLiteral().getString() : null);
	}

	public static boolean contains(String uri, Model inModel) {
		return exists(uri, inModel);
	}

	public static boolean exists(String uri, Model inModel) {
		return inModel.contains(inModel.getResource(uri), null);
	}

	public static Lang fromMimeType(String mimeType) {
		if (mimeType == null)
			return null;
		if (mimeType.contains(";"))
			mimeType = mimeType.substring(0, mimeType.indexOf(';')).trim();
		if (mimeType.contains(","))
			mimeType = mimeType.substring(0, mimeType.indexOf(',')).trim();
		return RDFLanguages.contentTypeToLang(mimeType);
	}

	public static Model readRdf(String content) {
//		System.out.println(content);
		return readRdf(new StringReader(content));
	}

	public static Model readRdf(byte[] content) {
		ByteArrayInputStream in = new ByteArrayInputStream(content);
		Model model = readRdf(in);
		try {
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		return model;
	}

	public static Model readRdf(Reader content) {
		Model model = null;
		for (Lang l : new Lang[] { Lang.RDFXML, Lang.TURTLE, Lang.JSONLD }) {
			try {
				model = ModelFactory.createDefaultModel();
				RDFReaderI reader = model.getReader(l.getName());
				reader.setProperty("allowBadURIs", "true");
				reader.read(model, content, null);
				break;
			} catch (Exception e) {
				e.printStackTrace();
				// ignore and try another reader
			}
		}
		return model;
	}

	public static Model readRdf(InputStream content) {
		Model model = null;
		for (Lang l : new Lang[] { Lang.RDFXML, Lang.TURTLE, Lang.JSONLD }) {
			try {
				model = ModelFactory.createDefaultModel();
				RDFReaderI reader = model.getReader(l.getName());
				reader.setProperty("allowBadURIs", "true");
				reader.read(model, content, null);
				break;
			} catch (Exception e) {
				e.printStackTrace();
				// ignore and try another reader
			}
		}
		return model;
	}

	public static Model readRdf(String content, Lang l) {
		if (l == null)
			return readRdf(content);
		return readRdf(new StringReader(content), l);
	}

	public static Model readRdf(Reader content, Lang l) {
		if (l == null)
			return readRdf(content);
		Model model = ModelFactory.createDefaultModel();
		RDFReaderI reader = model.getReader(l.getName());
		reader.setProperty("allowBadURIs", "true");
		reader.read(model, content, null);
		return model;
	}

	public static Model readRdf(InputStream content, Lang l) {
		if (l == null)
			return readRdf(content);
		Model model = ModelFactory.createDefaultModel();
		RDFReaderI reader = model.getReader(l.getName());
		reader.setProperty("allowBadURIs", "true");
		reader.read(model, content, null);
		return model;
	}

	public static Model readRdf(byte[] content, Lang l) {
		if (l == null)
			return readRdf(content);
		ByteArrayInputStream in = new ByteArrayInputStream(content);
		Model model = readRdf(in, l);
		try {
			in.close();
		} catch (IOException e) {
			throw new RuntimeException(e.getMessage(), e);
		}
		return model;
	}

  public static Dataset readRdfQuads(byte[] content, Lang lang) {
    if (lang == null)
      throw new InvalidParameterException("Lang must be provided");
    Dataset dataset = DatasetFactory.createTxnMem();
    try (ByteArrayInputStream is = new ByteArrayInputStream(content)) {
      RDFParser.create().source(is) // Set the input stream
          .lang(lang) // Specify the language (important for accurate parsing)
          .errorHandler(lenientErrorHandler) // Set your custom error handler
          .parse(dataset);
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    return dataset;
  }
	
//	public static final String UTF8_BOM = "\uFEFF";
	public static Model readRdfFromUri(String uri) throws AccessException, InterruptedException, IOException {
		HttpRequest rdfReq = HttpUtil.makeRequest(uri, "Accept", CONTENT_TYPES_ACCEPT_HEADER);
		return readRdf(new HttpResponse(rdfReq));
	}

	public static Dataset readRdfQuadsFromUri(String uri) throws AccessException, InterruptedException, IOException {
	  HttpRequest rdfReq = HttpUtil.makeRequest(uri, "Accept", CONTENT_TYPES_QUADS_ACCEPT_HEADER);
	  return readRdfQuads(new HttpResponse(rdfReq));
	}

	public static void writeRdf(Model model, Lang l, OutputStream out) {
		model.write(out, l.getName());
	}

	public static void writeRdf(Model model, Lang l, Writer out) {
		model.write(out, l.getName());
	}

	public static byte[] writeRdf(Model model, Lang l) {
		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
		RdfUtil.writeRdf(model, l, outBytes);
		try {
			outBytes.close();
		} catch (IOException e) {
			/* does not happen with byte array */ }
		return outBytes.toByteArray();
	}

	public static String writeRdfToString(Model model, Lang l) {
		StringWriter writer = new StringWriter();
		RdfUtil.writeRdf(model, l, writer);
		try {
			writer.close();
		} catch (IOException e) {
			/* does not happen with string writer */ }
		return writer.toString();
	}

	public static String printStatements(Resource rdf) {
		StringBuilder sb = new StringBuilder();
		StmtIterator typeProperties = rdf.listProperties();
		for (Statement st : typeProperties.toList())
			sb.append(st.toString()).append('\n');
		return sb.toString();
	}

	public static String printStatements(Model rdf) {
		StringBuilder sb = new StringBuilder();
		StmtIterator typeProperties = rdf.listStatements();
		for (Statement st : typeProperties.toList())
			sb.append(st.toString()).append('\n');
		return sb.toString();
	}

	public static String printStatementsOfNamespace(Model rdf, String ns) {
		StringBuilder sb = new StringBuilder();
		StmtIterator typeProperties = rdf.listStatements();
		for (Statement st : typeProperties.toList())
			if (st.getPredicate().getNameSpace().equals(ns))
				sb.append(st.toString()).append('\n');
		return sb.toString();
	}

	public static String printStatementsOfNamespace(Resource rdf, String ns) {
		StringBuilder sb = new StringBuilder();
		StmtIterator typeProperties = rdf.listProperties();
		for (Statement st : typeProperties.toList())
			if (st.getPredicate().getNameSpace().equals(ns))
				sb.append(st.toString()).append('\n');
		return sb.toString();
	}

	public static Set<Resource> findResourceWithProperties(Model model, Property propA, RDFNode valuePropA,
			Property propB, RDFNode valuePropB) {
		Set<Resource> matching = null;
		StmtIterator stms = model.listStatements(null, propA, valuePropA);
		if (!stms.hasNext())
			return Collections.emptySet();
		matching = new HashSet<>();
		while (stms.hasNext()) {
			Statement st = stms.next();
			matching.add(st.getSubject());
		}
		if (propB == null && valuePropB == null)
			return matching;
		Set<Resource> ret = null;
		for (Resource r : matching) {
			stms = model.listStatements(r, propB, valuePropB);
			while (stms.hasNext()) {
				if (ret == null)
					ret = new HashSet<>();
				Statement st = stms.next();
				ret.add(r);
			}
		}
		return ret == null ? Collections.emptySet() : ret;
	}

	public static Resource findFirstResourceWithProperties(Model model, Property propA, RDFNode valuePropA,
			Property propB, RDFNode valuePropB) {
		Set<Resource> matching = null;
		StmtIterator stms = model.listStatements(null, propA, valuePropA);
		if (!stms.hasNext())
			return null;
		matching = new HashSet<>();
		while (stms.hasNext()) {
			Statement st = stms.next();
			matching.add(st.getSubject());
		}
		for (Resource r : matching) {
			stms = model.listStatements(r, propB, valuePropB);
			if (stms.hasNext()) {
				return r;
			}
		}
		return null;
	}

	public static Model readRdf(HttpResponse rdf) {
		Model model = readRdf(rdf.body, fromMimeType(rdf.getHeader("Content-Type")));
		if (model.isEmpty())
			return null;
		return model;
	}

	public static Dataset readRdfQuads(HttpResponse rdf) {
	  Dataset model = readRdfQuads(rdf.body, fromMimeType(rdf.getHeader("Content-Type")));
	  if (model.isEmpty())
	    return null;
	  return model;
	}
	
	public static Model readRdf(HttpResponse rdf, Lang l) {
		Model model = readRdf(rdf.body, l);
		if (model.isEmpty())
			return null;
		return model;
	}

}
