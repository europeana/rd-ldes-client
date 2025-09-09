package eu.europeana.metis.ldaggregation.harvesting.ldes.model;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Iterator;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;

import eu.europeana.metis.ldaggregation.harvesting.ldes.LdesConstants;
import eu.europeana.metis.ldaggregation.harvesting.ldes.ValidationException;

/**
 * Data object for a as:Activity
 *
 */
public class Member implements Comparable<Member>{
  
  Resource activityRes;
  Dataset quadsDataset;
  Property timestampPath;
  Resource versionPath;
  
	public Member(Resource activityRes, Dataset quadsDataset, Property timestampPath, Resource versionPath) throws ValidationException {
	  this.activityRes=activityRes;
	  this.quadsDataset=quadsDataset;
	  this.timestampPath= timestampPath;
	  this.versionPath= versionPath;
	  validate();
	}

	public void validate() throws ValidationException {
		ActivityType typeOfActivity = null;
		try{
		  typeOfActivity = getTypeOfActivity();
		  if (typeOfActivity==null)
		    throw new ValidationException("'type' is missing.");
		}catch (IllegalArgumentException e) {
		  throw new ValidationException("'type' is invalid.");      
    }

		try {
		  Instant endTime = getTimestamp();
		  if (endTime==null)
		    throw new ValidationException("'endTime' is missing from activity");
		} catch (Exception e) {
			throw new ValidationException("Invalid date/time in as:endTime.", e);
		}
		
		Resource object = getObject();
		if (object==null)
			throw new ValidationException("'object' is missing");
		if (!object.isURIResource())
			throw new ValidationException("'object' does not contain a URI");

		if (typeOfActivity==ActivityType.Move && getTarget()==null)
			throw new ValidationException("'target' is missing (required in Move activities)");
	}

	public boolean isBefore(Instant lastHarvest) {
		return getTimestamp().isBefore(lastHarvest);
	}

  public Instant getTimestamp() {
    Statement endTimeSt = activityRes.getProperty(timestampPath); 
    if(endTimeSt!=null)
      return parseXsdDate(endTimeSt.getObject().asLiteral().getString());
    return null;
  }

	private Instant parseXsdDate(String timeString) {
		if (timeString.length() <= 21) {
			return Instant.parse(timeString);
		} else {
			OffsetDateTime instant = OffsetDateTime.parse(timeString);
			return instant.toInstant();
		}
	}

	public static boolean validateXsdDateTime(String dateTimeValue) {
		try {
			if (dateTimeValue.length() <= 21) {
				Instant.parse(dateTimeValue);
			} else {
				OffsetDateTime.parse(dateTimeValue);
			}
			return true;
		} catch (DateTimeParseException e) {
			return false;
		}
	}

	public ActivityType getTypeOfActivity() {
	  Statement typeSt = activityRes.getProperty(RDF.type);
	  if(typeSt!=null)
	    return ActivityType.valueOf(typeSt.getObject().asResource().getLocalName());
	  return null;
	}

	public Resource getObject() {
	  Statement objectSt = activityRes.getProperty(LdesConstants.AS_OBJECT);
		if (objectSt != null)
		  return objectSt.getObject().asResource();
		return null;
	}

	public Resource getTarget() {
	  Statement objectSt = activityRes.getProperty(LdesConstants.AS_TARGET);
	  if (objectSt != null)
	    return objectSt.getObject().asResource();
	  return null;
	}

	@Override
	public int compareTo(Member other) {
//    long diffMillis = ChronoUnit.MILLIS.between(other.getTimestamp(), getTimestamp());
    long diffMillis = ChronoUnit.MILLIS.between(getTimestamp(), other.getTimestamp());
    if(diffMillis>Integer.MAX_VALUE)
      return Integer.MAX_VALUE;
    if(diffMillis<Integer.MIN_VALUE)
      return Integer.MIN_VALUE;
    return (int) diffMillis;
	}

  /**
   * @return
   */
  public Model getObjectNamedGraph() {
    return quadsDataset.getNamedModel(activityRes.getURI());
  }
	
}
