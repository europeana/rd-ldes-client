/**
 * 
 */
package eu.europeana.metis.ldaggregation.harvesting.ldes;

/**
 * @author Nuno Freire
 * @since 28/05/2025
 */
public class DEV {

  public static void trace(String msg) {
    StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    if (stackTraceElements.length > 2) {
        StackTraceElement caller = stackTraceElements[2]; // The direct caller
        System.out.print("Trace: " + caller.getClassName() + "." + caller.getMethodName()+ ":"+caller.getLineNumber()+"--");
//        System.out.print("Called from file: " + caller.getFileName() + " at line: " + caller.getLineNumber());
    }
    System.out.println(msg);
  }
  
  
  
  public static void traceResource(String uri, String message) {
    if(uri.equals("urn:sanitizedURI:PH_KPI_1902_2_001_0"))
      System.out.println("TraceResource: " + uri + " - " + message);
  }

  public static void print(String message) {
      System.out.println("Trace: " + message);
  }
}
