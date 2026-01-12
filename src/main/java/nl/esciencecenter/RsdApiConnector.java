package nl.esciencecenter;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;

public class RsdApiConnector {

	private final URI domain;
	private final String apiToken;


	public RsdApiConnector(URI domain, String apiToken) {
		this.domain = domain;
		this.apiToken = apiToken;
	}

	public void saveSoftware(Collection<EstroSoftware> software) throws IOException, InterruptedException {
		URI softwareUrl = URI.create(domain.toString() + "/api/v2/software");

		try (HttpClient client = HttpClient.newHttpClient()) {
			for (EstroSoftware estroSoftware : software) {
				String jsonBody = toSoftwareJson(estroSoftware);
				HttpRequest httpRequest = HttpRequest.newBuilder()
						.uri(softwareUrl)
						.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
						.header("Authorization", "Bearer " + apiToken)
						.build();
				HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() != 201) {
					System.out.println(response.statusCode());
					System.out.println(response.body());
					System.out.println(jsonBody);
					System.out.println();
				}
			}
		}
	}

	private String toSoftwareJson(EstroSoftware software) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("is_published", true);
		jsonObject.addProperty("brand_name", software.name());
		jsonObject.addProperty("slug", sluggify(software.name()));
		jsonObject.add("concept_doi", software.doi().isPresent() ? new JsonPrimitive(software.doi().get()) : JsonNull.INSTANCE);
		jsonObject.add("get_started_url", software.website().isPresent() ? new JsonPrimitive(software.website().get().toString()) : JsonNull.INSTANCE);

		return jsonObject.toString();
	}

	private static String sluggify(String name) {
		return name
				.strip()
				.toLowerCase()
				.replaceAll("[^a-z0-9]+", "-");
	}
}
