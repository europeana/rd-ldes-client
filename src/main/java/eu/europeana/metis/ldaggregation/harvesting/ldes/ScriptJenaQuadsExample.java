/**
 * 
 */
package eu.europeana.metis.ldaggregation.harvesting.ldes;

import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandler;
import org.apache.jena.sparql.core.Quad; // For iterating quads
import org.apache.jena.graph.Node; // For graph names

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * @author Nuno Freire
 * @since 28/05/2025
 */
public class ScriptJenaQuadsExample {

  public static void main(String[] args) {
    try {
      String filePath = "TEST-RIJK.jsonld";
//            String filePath = "https://data.rijksmuseum.nl/ldes/2024/12/19.json?page=0";

      ErrorHandler customErrorHandler = new ErrorHandler() {
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
      Dataset dataset = DatasetFactory.createTxnMem();
      RDFParser.create().source(filePath) // Set the input stream
          .lang(Lang.JSONLD11) // Specify the language (important for accurate parsing)
          .errorHandler(customErrorHandler) // Set your custom error handler
          .parse(dataset);

//            // Create a default Jena Dataset
//            Dataset dataset = RDFDataMgr.loadDataset(filePath, Lang.JSONLD);

      // Access the default graph
      Model defaultModel = dataset.getDefaultModel();
      System.out.println("--- Default Graph ---");
      defaultModel.write(System.out, "TURTLE");

      // Access named graphs
      System.out.println("\n--- Named Graphs ---");
      dataset.listNames().forEachRemaining(graphNameNode -> {
        System.out.println("Graph Name: " + graphNameNode);
        Model namedGraphModel = dataset.getNamedModel(graphNameNode);
//              namedGraphModel.write(System.out, "TURTLE");
      });

      // Iterate over all quads (triples with a graph name)
      System.out.println("\n--- All Quads ---");
      dataset.asDatasetGraph().find().forEachRemaining(quad -> {
        System.out.println("Graph: " + (quad.isDefaultGraph() ? "DEFAULT" : quad.getGraph().getURI()) + ", Subject: "
            + quad.getSubject() + ", Predicate: " + quad.getPredicate() + ", Object: " + quad.getObject());
      });

      dataset.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

//      https://mint-ldes.ails.ece.ntua.gr/euscreenxl/1008/ldes/2021/9/20?page=0

}
