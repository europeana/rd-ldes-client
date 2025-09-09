package eu.europeana.metis.ldaggregation.harvesting.ldes.demo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;

import eu.europeana.metis.ldaggregation.harvesting.ldes.util.RdfUtil;
import eu.europeana.metis.ldaggregation.segmenter.api.record.io.xml.EdmXmlStreamWriter;

/**
 * Implements the step of downloading structured metadata present in the
 * "seeAlso" of IIIF Manifests.
 * 
 * May be configured to download specific metadata profiles (see the IIIF
 * Registry of Profiles at https://iiif.io/api/registry/profiles/)
 * 
 */
public class MetadataHarvester {
	File repositoryFolder;

	public MetadataHarvester(File repositoryFolder) {
		this.repositoryFolder = repositoryFolder;
		if(!repositoryFolder.exists())
		  repositoryFolder.mkdirs();
	}

	public void save(String recordUri, Model rdf) throws FileNotFoundException, IOException {
	  try(FileOutputStream fos=new FileOutputStream(getRecordFile(recordUri))){
  	  EdmXmlStreamWriter writer = new EdmXmlStreamWriter();
  	  writer.write(rdf, fos);
    } catch (XMLStreamException e) {
      throw new IOException(e);
    }
	}

	public void deleteRecord(String recordUri) {
		  getRecordFile(recordUri).delete();
	}
	
	private File getRecordFile(String recordUri) {
	  try {
      return new File(repositoryFolder, URLEncoder.encode(recordUri, "UTF-8")+".rdf");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 not supported", e);
    }
	}

}
