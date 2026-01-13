package nl.esciencecenter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;

public record EstroSoftware(
		String name,
		String shortStatement,
		Collection<String> keywords,
		Optional<URI> website,
		Optional<URI> gitUrl,
		Optional<String> doi
) {

	private static final Pattern DOI_PATTERN = Pattern.compile("^10(\\.\\w+)+/\\S+$");

	static EstroSoftware fromCsvLine(String line) {
		String[] split = line.split("\\|");
		String name = split[0];
		String shortStatement = split[12];

		Optional<URI> website = extractUrl(split[8]);

		Optional<URI> gitUrl = extractUrl(split[10]);

		String rawDoi = split[13];
		Optional<String> doi = DOI_PATTERN.matcher(rawDoi).find() ? Optional.of(rawDoi) : Optional.empty();

		return new EstroSoftware(
				name,
				shortStatement,
				Collections.emptyList(),
				website,
				gitUrl,
				doi
		);
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
