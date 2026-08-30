package eu.europeana.metis.ldaggregation.harvesting.ldes.http;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
	import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.http.Header;

import eu.europeana.metis.ldaggregation.harvesting.ldes.http.UrlRequest.HttpMethod;

public class HttpUtil {

	public static final boolean ALWAYS_FOLLOW_REDIRECTS = true;
	public static final int RETRIES_ON_IOEXCEPTION = 3;
	public static final int RETRIES_ON_HTTP_STATUS = 8;
	public static final int RETRY_SLEEP_MILISECONDS = 20;
	private static final Set<Integer> RETRYABLE_STATUSES = Set.of(408, 425, 429, 500, 502, 503, 504);

	public static List<Header> getAndStoreWithHeaders(String resourceUri, File storeFile)
			throws IOException, InterruptedException, AccessException {
		return getAndStoreWithHeaders(new UrlRequest(resourceUri), storeFile);
	}

	public static List<Header> getAndStoreWithHeaders(UrlRequest resourceUri, File storeFile)
			throws IOException, InterruptedException, AccessException {
		HttpRequest resourceRequest = makeRequest(resourceUri);
		FileUtils.writeByteArrayToFile(storeFile, resourceRequest.getContent().asBytes(), false);
		List<Header> meta = new ArrayList<>(5);
		for (String headerName : new String[] { "Content-Type", "Content-Encoding",
				"Content-Disposition"/* , "Content-MD5" */ }) {
			for (Header h : resourceRequest.getResponse().getHeaders(headerName))
				meta.add(h);
		}
		return meta;
	}

	public static byte[] getAndStore(String resourceUri, File storeFile)
			throws IOException, InterruptedException, AccessException {
		HttpRequest resourceRequest = makeRequest(resourceUri);
		byte[] asBytes = resourceRequest.getContent().asBytes();
		FileUtils.writeByteArrayToFile(storeFile, asBytes, false);
		return asBytes;
	}

//	public static HttpRequest getResponse(String resourceUri) throws HttpRequestException, InterruptedException, IOException {
//		UrlRequest ldReq=new UrlRequest(resourceUri);
//		HttpRequest rdfResourceRequest = new HttpRequest(ldReq);
//		Global.getHttpRequestService().fetch(rdfResourceRequest);
//		return rdfResourceRequest;
//	}

	public static HttpRequest makeRequest(String resourceUri) throws AccessException, InterruptedException {
		UrlRequest ldReq = new UrlRequest(resourceUri);
		return makeRequest(ldReq);
	}

	public static HttpRequest makeHeadRequest(String resourceUri) throws AccessException, InterruptedException {
		UrlRequest ldReq = new UrlRequest(resourceUri);
		ldReq.setMethod(HttpMethod.HEAD);
		return makeRequest(ldReq);
	}

	public static HttpRequest makeRequest(UrlRequest ldReq) throws AccessException, InterruptedException {
		int tries = 0;
		while (true) {
			HttpRequest resourceRequest = new HttpRequest(ldReq);
			try {
				tries++;
				HttpRequestService.INSTANCE.fetch(resourceRequest);
				int resStatusCode = resourceRequest.getResponseStatusCode();
				if (resStatusCode != 200 && resStatusCode != 304/* not modified */) {
					if (resStatusCode >= 300 && resStatusCode < 400) {
						String location = resourceRequest.getResponseHeader("Location");
						if (location != null) {
							ldReq.setUrl(java.net.URI.create(ldReq.getUrl()).resolve(location).toString());
							resourceRequest.getResponse().close();
							return makeRequest(ldReq);
						}
					} else if (RETRYABLE_STATUSES.contains(resStatusCode)) {
						if (tries >= RETRIES_ON_HTTP_STATUS)
							throw new AccessException(ldReq.getUrl(), resStatusCode);
						long waitMillis = RETRY_SLEEP_MILISECONDS;
						String retryAfter = resourceRequest.getResponseHeader("Retry-After");
						if (retryAfter != null) {
							try {
								waitMillis = Math.max(0, Long.parseLong(retryAfter) * 1000);
							} catch (NumberFormatException e) {
								waitMillis = RETRY_SLEEP_MILISECONDS;
							}
						}
						resourceRequest.getResponse().close();
						Thread.sleep(waitMillis);
						continue;
					} else {
						throw new AccessException(ldReq.getUrl(), resStatusCode);
					}
				}
				return resourceRequest;
			} catch (IOException ex) {
				if (tries > RETRIES_ON_IOEXCEPTION)
					throw new AccessException(ldReq.getUrl(), ex);
			}
		}
	}

	public static HttpRequest makeRequest(String resourceUri, String acceptHeader)
			throws AccessException, InterruptedException {
		return makeRequest(resourceUri, "Accept", acceptHeader);
	}

	public static HttpRequest makeRequest(String resourceUri, String oneHheader, String headerValue)
			throws AccessException, InterruptedException {
		UrlRequest r = new UrlRequest(resourceUri, oneHheader, headerValue);
		return makeRequest(r);
	}

	public static String makeRequestForContent(String resourceUri, String oneHheader, String headerValue)
			throws AccessException, InterruptedException, IOException {
		UrlRequest r = new UrlRequest(resourceUri, oneHheader, headerValue);
		return makeRequestForContent(r);
	}

	public static String makeRequestForContent(String url) throws IOException, AccessException, InterruptedException {
		UrlRequest r = new UrlRequest(url);
		return makeRequestForContent(r);
	}

	private static String makeRequestForContent(UrlRequest url)
			throws IOException, AccessException, InterruptedException {
		HttpRequest reqRes = makeRequest(url);
		int statusCode = reqRes.getResponse().getStatusLine().getStatusCode();
		if (statusCode == 200)
			return reqRes.getContent().asString();
		else {
			throw new AccessException(url.getUrl(), "HTTP response status code " + statusCode);
		}
	}

	public static List<Entry<String, String>> convertHeaderStruct(List<Header> headers) {
		List<Entry<String, String>> meta = new ArrayList<>(headers.size());
		for (Header h : headers)
			meta.add(new AbstractMap.SimpleEntry<String, String>(h.getName(), h.getValue()));
		return meta;
	}

}
