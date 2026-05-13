package nl.esciencecenter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record EstroSoftware(
		String name,
		String shortStatement,
		SoftwareLongDescription description,
		Optional<String> license,
		List<String> keywords,
		String codeType,
		Optional<String> estroField,
		Optional<URI> website,
		Optional<URI> gitUrl,
		Optional<String> doi,
		Optional<Image> image
) {

	private static final Pattern DOI_PATTERN = Pattern.compile("10(\\.\\w+)+/\\S+");
	private static final Pattern CSV_DELIMITER = Pattern.compile("\\|");

	static Map<String, Integer> parseHeader(String headerLine) {
		String[] split = CSV_DELIMITER.split(headerLine);

		Map<String, Integer> result = HashMap.newHashMap(split.length);
		for (int i = 0; i < split.length; i++) {
			result.put(split[i], i);
		}

		return result;
	}

	static EstroSoftware fromCsvLine(String line, Map<String, Integer> indexMap) {
		try {
			String[] split = CSV_DELIMITER.split(line);
			String name = split[indexMap.get("Name")];
			String shortStatement = split[indexMap.get("Short description (≤300 chars, summarized)")];

			SoftwareLongDescription description = SoftwareLongDescription.parseFromString(split[indexMap.get("Description")]);

			String rawLicense = split[indexMap.get("License")];
			Optional<String> license = rawLicense == null || rawLicense.isBlank() ? Optional.empty() : Optional.of(rawLicense.strip());

			String[] keywordsSplit = split[indexMap.get("Keywords [Max 3]")].split(", ");
			List<String> keywords = Arrays.asList(keywordsSplit);

			String codeType = split[indexMap.get("Code type")];

			String rawEstroField = split[indexMap.get("Field")];
			Optional<String> estroField = rawEstroField == null || rawEstroField.isBlank() ? Optional.empty() : Optional.of(rawEstroField);

			Optional<URI> website = extractUrl(split[indexMap.get("Website")]);

			Optional<URI> gitUrl = extractUrl(split[indexMap.get("Source code")]);

			String rawDoi = split[indexMap.get("Concept DOI (from zenodo)")];
			Matcher doiMatcher = DOI_PATTERN.matcher(rawDoi);
			Optional<String> doi = doiMatcher.find() ? Optional.of(doiMatcher.group()) : Optional.empty();

			Optional<Image> optionalImage = Optional.empty();
			int imageIndex = indexMap.get("Image url");
			if (split.length > imageIndex) {
				String imageUrl = split[imageIndex];

				try {
					Image downloadedImage = ImageDownloader.downloadImage(imageUrl);
					optionalImage = Optional.of(downloadedImage);
				} catch (Exception e) {
					System.err.println("Skipping image of " + name);
					e.printStackTrace();
				}
			}

			return new EstroSoftware(
					name,
					shortStatement,
					description,
					license,
					keywords,
					codeType,
					estroField,
					website,
					gitUrl,
					doi,
					optionalImage
			);
		} catch (RuntimeException e) {
			throw new RuntimeException(line, e);
		}
	}

	public static Optional<URI> extractUrl(String rawUrl) {
		if (rawUrl == null || rawUrl.contains(" ") || !rawUrl.contains(".")) {
			return Optional.empty();
		}

		if (!rawUrl.startsWith("https://") && !rawUrl.startsWith("http://")) {
			rawUrl = "https://" + rawUrl;
		}

		try {
			return Optional.of(new URI(rawUrl));
		} catch (URISyntaxException e) {
			e.printStackTrace();
			return Optional.empty();
		}
	}
}
