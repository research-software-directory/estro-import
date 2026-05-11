// SPDX-FileCopyrightText: 2024 - 2025 Ewan Cahen (Netherlands eScience Center) <e.cahen@esciencecenter.nl>
// SPDX-FileCopyrightText: 2024 - 2025 Netherlands eScience Center
//
// SPDX-License-Identifier: Apache-2.0

package nl.esciencecenter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GitHubSpdxLicenseRepository {

	private static final Gson gson = new Gson();

	private GitHubSpdxLicenseRepository() {
	}

	public static Map<String, SpdxLicense> getLicensesByIdMap() throws Exception {
		String url = "https://raw.githubusercontent.com/spdx/license-list-data/refs/heads/main/json/licenses.json";

		try (HttpClient client = HttpClient.newHttpClient()) {
			HttpRequest request = HttpRequest.newBuilder(URI.create(url)).build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new Exception(
						"Unexpected response while getting SPDX licenses, status code " + response.statusCode()
				);
			}
			String json = response.body();
			return parseLicensesJson(json);
		}
	}

	static Map<String, SpdxLicense> parseLicensesJson(String json) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		JsonArray licensesJsonArray = root.getAsJsonArray("licenses");
		TypeToken<List<SpdxLicense>> spdxListTypeToken = new TypeToken<>() {
		};
		List<SpdxLicense> licenses = gson.fromJson(licensesJsonArray, spdxListTypeToken);

		return licenses.stream().collect(Collectors.toMap(SpdxLicense::licenseId, Function.identity()));
	}
}
