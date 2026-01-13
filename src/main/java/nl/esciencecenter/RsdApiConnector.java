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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RsdApiConnector {

	private final URI domain;
	private final String jwt;
	private final Map<String, String> categoryToId = new HashMap<>();

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
			String estroId = extractId(response.body());

			URI categoryUrl = URI.create(domain.toASCIIString() + "/api/v1/category");
			String categoryJson = createCategoryJson(estroId, "Field", "ESTRO field", Optional.empty());
			httpRequest = HttpRequest.newBuilder()
					.uri(categoryUrl)
					.POST(HttpRequest.BodyPublishers.ofString(categoryJson))
					.header("Authorization", "Bearer " + jwt)
					.header("Prefer", "return=representation")
					.build();
			response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 201) {
				System.out.println(response.statusCode());
				System.out.println(response.body());
				System.out.println(categoryJson);
				System.out.println();
				return;
			}
			String rootCategoryId = extractId(response.body());

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

				String keywordJson = "{\"value\": \"%s\"}".formatted(estroSoftware.keyword());
				URI keywordUrl = URI.create(domain.toASCIIString() + "/api/v1/keyword?select=id&on_conflict=value");
				httpRequest = HttpRequest.newBuilder()
						.uri(keywordUrl)
						.POST(HttpRequest.BodyPublishers.ofString(keywordJson))
						.header("Authorization", "Bearer " + jwt)
						.header("Prefer", "resolution=merge-duplicates")
						.header("Prefer", "return=representation")
						.build();
				response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() >= 300) {
					System.out.println(response.statusCode());
					System.out.println(response.body());
					System.out.println(keywordJson);
					System.out.println();
					continue;
				}
				String keywordId = extractId(response.body());
				String keywordForSoftwareJson = toKeywordForSoftwareJson(softwareId, keywordId);
				URI keywordForSoftwareUrl = URI.create(domain.toASCIIString() + "/api/v1/keyword_for_software");
				httpRequest = HttpRequest.newBuilder()
						.uri(keywordForSoftwareUrl)
						.POST(HttpRequest.BodyPublishers.ofString(keywordForSoftwareJson))
						.header("Authorization", "Bearer " + jwt)
						.build();
				response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 201) {
					System.out.println(response.statusCode());
					System.out.println(response.body());
					System.out.println(keywordForSoftwareJson);
					System.out.println();
					continue;
				}

				if (estroSoftware.estroField().isPresent()) {
					String estroFieldName = estroSoftware.estroField().get();
					if (!categoryToId.containsKey(estroFieldName)) {
						categoryJson = createCategoryJson(estroId, estroFieldName, estroFieldName, Optional.of(rootCategoryId));
						httpRequest = HttpRequest.newBuilder()
								.uri(categoryUrl)
								.POST(HttpRequest.BodyPublishers.ofString(categoryJson))
								.header("Authorization", "Bearer " + jwt)
								.header("Prefer", "return=representation")
								.build();
						response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
						if (response.statusCode() != 201) {
							System.out.println(response.statusCode());
							System.out.println(response.body());
							System.out.println(categoryJson);
							System.out.println();
							continue;
						}
						categoryToId.put(estroFieldName, extractId(response.body()));
					}

					String categoryId = categoryToId.get(estroFieldName);
					URI categoryForSoftwareUrl = URI.create(domain.toASCIIString() + "/api/v1/category_for_software");
					String categoryForSoftwareJson = "{\"category_id\": \"%s\", \"software_id\": \"%s\"}".formatted(categoryId, softwareId);
					httpRequest = HttpRequest.newBuilder()
							.uri(categoryForSoftwareUrl)
							.POST(HttpRequest.BodyPublishers.ofString(categoryForSoftwareJson))
							.header("Authorization", "Bearer " + jwt)
							.build();
					response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
					if (response.statusCode() != 201) {
						System.out.println(response.statusCode());
						System.out.println(response.body());
						System.out.println(categoryForSoftwareJson);
						System.out.println();
						continue;
					}
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

	private static String createCategoryJson(String estroId, String shortName, String longName, Optional<String> parent) {
		JsonObject jsonObject = new JsonObject();

		jsonObject.addProperty("community", estroId);
		jsonObject.addProperty("short_name", shortName);
		jsonObject.addProperty("name", longName);
		jsonObject.addProperty("allow_software", true);
		jsonObject.add("parent", parent.isPresent() ? new JsonPrimitive(parent.get()) : JsonNull.INSTANCE);

		return jsonObject.toString();
	}

	private static String extractId(String response) {
		return JsonParser.parseString(response).getAsJsonArray().get(0).getAsJsonObject().getAsJsonPrimitive("id").getAsString();
	}

	private static String toKeywordForSoftwareJson(String softwareId, String keywordId) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("software", softwareId);
		jsonObject.addProperty("keyword", keywordId);

		return jsonObject.toString();
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
