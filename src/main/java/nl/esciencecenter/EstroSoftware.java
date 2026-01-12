package nl.esciencecenter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.regex.Pattern;

public record EstroSoftware(
		String name,
		Collection<String> keywords,
		Optional<URI> website,
		Optional<URI> gitUrl,
		Optional<String> doi
) {

	private static final Pattern DOI_PATTERN = Pattern.compile("^10(\\.\\w+)+/\\S+$");

	static EstroSoftware fromCsvLine(String line) {
		String[] split = line.split("\\|");
		String name = split[0];

		Optional<URI> website = Optional.empty();
		try {
			String rawWebsite = split[8];
			if (rawWebsite != null && !rawWebsite.startsWith("https://") && !rawWebsite.startsWith("http://")) {
				rawWebsite = "https://" + rawWebsite;
			}
			website = rawWebsite == null ? Optional.empty() : Optional.of(new URI(rawWebsite));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		Optional<URI> gitUrl = Optional.empty();
		try {
			gitUrl = Optional.of(new URI(split[10]));
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		String rawDoi = split[13];
		Optional<String> doi = DOI_PATTERN.matcher(rawDoi).find() ? Optional.of(rawDoi) : Optional.empty();

		return new EstroSoftware(
				name,
				Collections.emptyList(),
				website,
				gitUrl,
				doi
		);
	}
}
