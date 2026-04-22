package nl.esciencecenter;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

public class RsdApiConnector {

	private final URI domain;
	private final String jwt;
	private final Map<String, String> categoryToId = new HashMap<>();
	private final Map<String, String> repoUrlToId = new HashMap<>();
	private final Map<String, String> keywordToId = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
	private static final Collection<String> ACCEPTED_IMAGE_MIME_TYPES = Set.of(
			"image/avif",
			"image/gif",
			"image/jpeg",
			"image/png",
			"image/svg+xml",
			"image/webp",
			"image/x-ico"
	);
	private final URI categoryUrl;
	private static final HttpClient client = HttpClient.newBuilder().build();

	public RsdApiConnector(URI domain, String jwtSecret) {
		this.domain = domain;

		this.jwt = JWT.create()
				.withClaim("iss", "estro_import_script")
				.withClaim("role", "rsd_admin")
				.withExpiresAt(Instant.now().plus(Duration.ofHours(1)))
				.sign(Algorithm.HMAC256(jwtSecret));

		this.categoryUrl = URI.create(domain.toASCIIString() + "/api/v1/category");
	}

	private void getAllRepoUrls() throws IOException, InterruptedException {
		try (HttpClient client = HttpClient.newHttpClient()) {
			URI reposUrl = URI.create(domain.toASCIIString() + "/api/v1/repository_url?select=id,url");
			HttpRequest httpRequest = HttpRequest.newBuilder()
					.uri(reposUrl)
					.GET()
					.header("Authorization", "Bearer " + jwt)
					.build();

			HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				System.out.println(response.statusCode());
				System.out.println(response.body());
				System.out.println();
				return;
			}

			JsonArray jsonArray = JsonParser.parseString(response.body()).getAsJsonArray();
			for (JsonElement jsonElement : jsonArray) {
				String id = jsonElement.getAsJsonObject().getAsJsonPrimitive("id").getAsString();
				String url = jsonElement.getAsJsonObject().getAsJsonPrimitive("url").getAsString();
				repoUrlToId.put(url, id);
			}
		}
	}

	public void saveSoftware(Collection<EstroSoftware> software) throws IOException, InterruptedException {
		getAllRepoUrls();

		try (HttpClient client = HttpClient.newHttpClient()) {
			URI communityUrl = URI.create(domain.toASCIIString() + "/api/v1/community?slug=eq.rs4rt");
			HttpRequest httpRequest = HttpRequest.newBuilder()
					.uri(communityUrl)
					.GET()
					.build();

			HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
			String estroId = extractId(response.body());

			String categoryJson = createCategoryJson(estroId, "Field", "ESTRO field", Optional.empty());
			response = doAdminPostRequest(categoryUrl, categoryJson, Collections.emptyList());
			if (response.statusCode() != 201) {
				System.out.println(response.statusCode());
				System.out.println(response.body());
				System.out.println(categoryJson);
				System.out.println();
				return;
			}
			String rootEstroFieldCategoryId = extractId(response.body());

			categoryJson = createCategoryJson(estroId, "Code type", "ESTRO code type", Optional.empty());
			response = doAdminPostRequest(categoryUrl, categoryJson, Collections.emptyList());
			if (response.statusCode() != 201) {
				System.out.println(response.statusCode());
				System.out.println(response.body());
				System.out.println(categoryJson);
				System.out.println();
				return;
			}
			String rootCodeTypeCategoryId = extractId(response.body());

			URI softwareUrl = URI.create(domain.toASCIIString() + "/api/v1/software?select=id");

			for (EstroSoftware estroSoftware : software) {
				Optional<String> imageId = Optional.empty();
				if (estroSoftware.image().isPresent()) {
					try {
						imageId = Optional.of(saveImage(estroSoftware.image().get()));
					} catch (Exception e) {
						System.err.println("Skipping image for " + estroSoftware.name());
						e.printStackTrace();
					}
				}

				String jsonBody = toSoftwareJson(estroSoftware, imageId);
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

				for (String keyword : estroSoftware.keywords()) {
					if (!keywordToId.containsKey(keyword)) {
						String keywordJson = "{\"value\": \"%s\"}".formatted(keyword);
						URI keywordUrl = URI.create(domain.toASCIIString() + "/api/v1/keyword?select=id&on_conflict=value");
						response = doAdminPostRequest(keywordUrl, keywordJson, List.of(List.of("Prefer", "resolution=merge-duplicates")));
						if (response.statusCode() >= 300) {
							System.out.println(response.statusCode());
							System.out.println(response.body());
							System.out.println(keywordJson);
							System.out.println();
							continue;
						}
						String keywordId = extractId(response.body());
						keywordToId.put(keyword, keywordId);
					}

					String keywordId = keywordToId.get(keyword);
					String keywordForSoftwareJson = toKeywordForSoftwareJson(softwareId, keywordId);
					URI keywordForSoftwareUrl = URI.create(domain.toASCIIString() + "/api/v1/keyword_for_software");
					response = doAdminPostRequest(keywordForSoftwareUrl, keywordForSoftwareJson, Collections.emptyList());
					if (response.statusCode() != 201) {
						System.out.println(response.statusCode());
						System.out.println(response.body());
						System.out.println(keywordForSoftwareJson);
						System.out.println();
					}
				}

				String codeType = estroSoftware.codeType();
				saveCategoryForSoftware(codeType, estroId, rootCodeTypeCategoryId, softwareId);

				if (estroSoftware.estroField().isPresent()) {
					String estroFieldName = estroSoftware.estroField().get();
					saveCategoryForSoftware(estroFieldName, estroId, rootEstroFieldCategoryId, softwareId);
				}

				if (estroSoftware.gitUrl().isPresent()) {
					String url = estroSoftware.gitUrl().get().toASCIIString();
					if (!repoUrlToId.containsKey(url)) {
						URI gitRepoUrl = URI.create(domain.toASCIIString() + "/api/v1/repository_url");
						String gitJson = toGitUrlJson(estroSoftware);
						httpRequest = HttpRequest.newBuilder()
								.uri(gitRepoUrl)
								.POST(HttpRequest.BodyPublishers.ofString(gitJson))
								.header("Authorization", "Bearer " + jwt)
								.header("Prefer", "return=representation")
								.build();

						response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

						if (response.statusCode() != 201) {
							System.out.println(response.statusCode());
							System.out.println(response.body());
							System.out.println(gitJson);
							System.out.println();
						}

						String id = JsonParser
								.parseString(response.body())
								.getAsJsonArray()
								.get(0)
								.getAsJsonObject()
								.getAsJsonPrimitive("id")
								.getAsString();

						repoUrlToId.put(url, id);
					}

					String repoUrlId = repoUrlToId.get(url);
					String repoUrlForSoftwareJson = toRepoUrlForSoftwareJson(softwareId, repoUrlId);
					URI gitRepoForSoftwareUrl = URI.create(domain.toASCIIString() + "/api/v1/repository_url_for_software");

					httpRequest = HttpRequest.newBuilder()
							.uri(gitRepoForSoftwareUrl)
							.POST(HttpRequest.BodyPublishers.ofString(repoUrlForSoftwareJson))
							.header("Authorization", "Bearer " + jwt)
							.build();

					response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

					if (response.statusCode() != 201) {
						System.out.println(response.statusCode());
						System.out.println(response.body());
						System.out.println(repoUrlForSoftwareJson);
						System.out.println();
					}
				}
			}
		}
	}

	private void saveCategoryForSoftware(String codeType, String estroId, String parentCategoryId, String softwareId) throws IOException, InterruptedException {
		HttpResponse<String> response;
		if (!categoryToId.containsKey(codeType)) {
			String categoryJson = createCategoryJson(estroId, codeType, codeType, Optional.of(parentCategoryId));
			response = doAdminPostRequest(categoryUrl, categoryJson, Collections.emptyList());
			if (response.statusCode() != 201) {
				System.out.println(response.statusCode());
				System.out.println(response.body());
				System.out.println(categoryJson);
				System.out.println();
				return;
			}
			categoryToId.put(codeType, extractId(response.body()));
		}

		String categoryId = categoryToId.get(codeType);
		URI categoryForSoftwareUrl = URI.create(domain.toASCIIString() + "/api/v1/category_for_software");
		String categoryForSoftwareJson = "{\"category_id\": \"%s\", \"software_id\": \"%s\"}".formatted(categoryId, softwareId);
		response = doAdminPostRequest(categoryForSoftwareUrl, categoryForSoftwareJson, Collections.emptyList());
		if (response.statusCode() != 201) {
			System.out.println(response.statusCode());
			System.out.println(response.body());
			System.out.println(categoryForSoftwareJson);
			System.out.println();
		}
	}

	private String saveImage(Image image) throws IOException, InterruptedException {
		String mimeType = image.mimeType();
		if (!ACCEPTED_IMAGE_MIME_TYPES.contains(mimeType)) {
			throw new IllegalArgumentException("Unsupported mime type for image: " + mimeType);
		}

		byte[] base64EncodedData = Base64.getEncoder().encode(image.bytes());
		JsonObject imageObject = new JsonObject();
		imageObject.addProperty("data", new String(base64EncodedData));
		imageObject.addProperty("mime_type", mimeType);

		URI imageUri = URI.create(domain.toASCIIString() + "/api/v1/image?select=id");
		HttpResponse<String> response = doAdminPostRequest(imageUri, imageObject.toString(), List.of(List.of("Prefer", "resolution=merge-duplicates")));

		return extractId(response.body());
	}

	private HttpResponse<String> doAdminPostRequest(URI uri, String body, Collection<List<String>> extraHeaders) throws IOException, InterruptedException {
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
				.header("Authorization", "Bearer " + jwt)
				.header("Prefer", "return=representation");

		for (List<String> extraHeader : extraHeaders) {
			requestBuilder.header(extraHeader.get(0), extraHeader.get(1));
		}

		HttpRequest request = requestBuilder.build();
		return client.send(request, HttpResponse.BodyHandlers.ofString());
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

	private static String toRepoUrlForSoftwareJson(String softwareId, String repoUrlId) {
		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("software", softwareId);
		jsonObject.addProperty("repository_url", repoUrlId);
		jsonObject.addProperty("position", 0);

		return jsonObject.toString();
	}

	private static String toGitUrlJson(EstroSoftware software) {
		JsonObject jsonObject = new JsonObject();

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

	private static String toSoftwareJson(EstroSoftware software, Optional<String> imageId) {
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

		jsonObject.add("image_id", imageId.isPresent() ? new JsonPrimitive(imageId.get()) : JsonNull.INSTANCE);

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
