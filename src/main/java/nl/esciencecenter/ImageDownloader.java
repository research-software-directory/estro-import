package nl.esciencecenter;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ImageDownloader {

	public static Image downloadImage(String url) throws Exception {
		URI uri = new URI(url);

		try (HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()) {
			HttpRequest request = HttpRequest.newBuilder(uri).build();
			System.out.print("Downloading " + url);

			HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				throw new Exception("%d %s".formatted(response.statusCode(), response.uri()));
			}

			String mimeType = response.headers().firstValue("content-type").orElseThrow();

			System.out.println(" success");
			return new Image(
					response.body(),
					mimeType
			);
		} catch (Exception e) {
			System.out.println(" failure");
			throw e;
		}
	}
}
