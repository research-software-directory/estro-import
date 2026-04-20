package nl.esciencecenter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;

public record EstroSoftware(
		String name,
		String shortStatement,
		String keyword,
		Optional<String> estroField,
		Optional<URI> website,
		Optional<URI> gitUrl,
		Optional<String> doi,
		Optional<Image> image
) {

	private static final Pattern DOI_PATTERN = Pattern.compile("^10(\\.\\w+)+/\\S+$");

	static EstroSoftware fromCsvLine(String line) {
		try {
			String[] split = line.split("\\|");
			String name = split[0];
			String shortStatement = split[12];

			String keyword = split[3];

			String rawEstroField = split[4];
			Optional<String> estroField = rawEstroField == null || rawEstroField.isBlank() ? Optional.empty() : Optional.of(rawEstroField);

			Optional<URI> website = extractUrl(split[8]);

			Optional<URI> gitUrl = extractUrl(split[10]);

			String rawDoi = split[13];
			Optional<String> doi = DOI_PATTERN.matcher(rawDoi).find() ? Optional.of(rawDoi) : Optional.empty();

			Optional<Image> optionalImage = Optional.empty();
			if (split.length >= 16) {
				String imageUrl = split[15];

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
					keyword,
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

	private static Optional<URI> extractUrl(String rawUrl) {
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
