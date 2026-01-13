package nl.esciencecenter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;

public class RsdApiConnector {

	private final URI domain;
	private final String jwt;


	public RsdApiConnector(URI domain, String jwtSecret) {
		this.domain = domain;

		this.jwt = JWT.create()
				.withClaim("iss", "estro_import_script")
				.withClaim("role", "rsd_admin")
				.withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
				.sign(Algorithm.HMAC256(jwtSecret));
	}

	public void saveSoftware(Collection<EstroSoftware> software) throws IOException, InterruptedException {
		try (HttpClient client = HttpClient.newHttpClient()) {
			URI communityUrl = URI.create(domain.toASCIIString() + "/api/v1/community?slug=eq.estro");
			HttpRequest httpRequest = HttpRequest.newBuilder()
					.uri(communityUrl)
					.GET()
					.build();

			HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			String estroId = extractEstroCommunityId(response.body());

			URI softwareUrl = URI.create(domain.toASCIIString() + "/api/v1/software?select=id");

			for (EstroSoftware estroSoftware : software) {
				String jsonBody = toSoftwareJson(estroSoftware);
				httpRequest = HttpRequest.newBuilder()
						.uri(softwareUrl)
						.POST(HttpRequest.BodyPublishers.ofString(jsonBody))
						.header("Authorization", "Bearer " + jwt)
						.header("Prefer", "return=representation")
						.build();
				response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

				if (response.statusCode() != 201) {
					System.out.println(response.statusCode());
					System.out.println(response.body());
					System.out.println(jsonBody);
					System.out.println();
					continue;
				}

				String softwareId = extractId(response.body());
				String communitySoftwareJson = toCommunityForSoftwareJson(softwareId, estroId);
				URI softwareForCommunityUrl = URI.create(domain.toASCIIString() + "/api/v1/software_for_community");
				httpRequest = HttpRequest.newBuilder()
						.uri(softwareForCommunityUrl)
						.POST(HttpRequest.BodyPublishers.ofString(communitySoftwareJson))
						.header("Authorization", "Bearer " + jwt)
						.build();
				response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 201) {
					System.out.println(response.statusCode());
					System.out.println(response.body());
					System.out.println(communitySoftwareJson);
					System.out.println();
					continue;
				}

				if (estroSoftware.gitUrl().isPresent()) {
					String gitJson = toGitUrlJson(estroSoftware, softwareId);

					URI gitRepoUrl = URI.create(domain.toASCIIString() + "/api/v1/repository_url");

					httpRequest = HttpRequest.newBuilder()
							.uri(gitRepoUrl)
							.POST(HttpRequest.BodyPublishers.ofString(gitJson))
							.header("Authorization", "Bearer " + jwt)
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

	private static String extractEstroCommunityId(String response) {
		return JsonParser.parseString(response).getAsJsonArray().get(0).getAsJsonObject().getAsJsonPrimitive("id").getAsString();
	}

	private static String extractId(String response) {
		return JsonParser.parseString(response).getAsJsonArray().get(0).getAsJsonObject().getAsJsonPrimitive("id").getAsString();
	}

	private static String toCommunityForSoftwareJson(String softwareId, String estroId) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("software", softwareId);
		jsonObject.addProperty("community", estroId);
		jsonObject.addProperty("status", "approved");

		return jsonObject.toString();
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
