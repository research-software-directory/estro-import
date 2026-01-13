package nl.esciencecenter;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
		URI softwareUrl = URI.create(domain.toString() + "/api/v2/software?select=id");

		try (HttpClient client = HttpClient.newHttpClient()) {
			for (EstroSoftware estroSoftware : software) {
				String jsonBody = toSoftwareJson(estroSoftware);
				HttpRequest httpRequest = HttpRequest.newBuilder()
						.uri(softwareUrl)
						.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
						.header("Authorization", "Bearer " + apiToken)
						.header("Prefer", "return=representation")
						.build();
				HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() != 201) {
					System.out.println(response.statusCode());
					System.out.println(response.body());
					System.out.println(jsonBody);
					System.out.println();
					continue;
				}

				if (estroSoftware.gitUrl().isPresent()) {
					String id = extractId(response.body());
					String gitJson = toGitUrlJson(estroSoftware, id);

					URI gitRepoUrl = URI.create(domain.toString() + "/api/v2/repository_url");

					httpRequest = HttpRequest.newBuilder()
							.uri(gitRepoUrl)
							.POST(HttpRequest.BodyPublishers.ofString(gitJson))
							.header("Authorization", "Bearer " + apiToken)
							.build();

					response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

					if (response.statusCode() != 201) {
						System.out.println(response.statusCode());
						System.out.println(response.body());
						System.out.println(gitJson);
						System.out.println();
					}
				}
			}
		}
	}

	private static String extractId(String response) {
		return JsonParser.parseString(response).getAsJsonArray().get(0).getAsJsonObject().getAsJsonPrimitive("id").getAsString();
	}

	private static String toGitUrlJson(EstroSoftware software, String id) {
		JsonObject jsonObject = new JsonObject();

		jsonObject.addProperty("software", id);

		String url = software.gitUrl().orElseThrow().toString();
		jsonObject.addProperty("url", url);

		if (url.contains("github.com")) {
			jsonObject.addProperty("code_platform", "github");
		} else if (url.contains("gitlab")) {
			jsonObject.addProperty("code_platform", "gitlab");
		} else {
			jsonObject.addProperty("code_platform", "other");
		}

		return jsonObject.toString();
	}

	private static String toSoftwareJson(EstroSoftware software) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("is_published", true);
		jsonObject.addProperty("brand_name", software.name());
		String shortStatement = software.shortStatement();
		if (shortStatement.length() > 300) {
			shortStatement = shortStatement.substring(0, 297) + "...";
		}
		jsonObject.addProperty("short_statement", shortStatement);
		jsonObject.addProperty("slug", sluggify(software.name()));
		jsonObject.add("concept_doi", software.doi().isPresent() ? new JsonPrimitive(software.doi().get()) : JsonNull.INSTANCE);
		jsonObject.add("get_started_url", software.website().isPresent() ? new JsonPrimitive(software.website().get().toString()) : JsonNull.INSTANCE);

		return jsonObject.toString();
	}

	private static String sluggify(String name) {
		return name
				.strip()
				.toLowerCase()
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-+", "")
				.replaceAll("-+$", "");
	}
}
