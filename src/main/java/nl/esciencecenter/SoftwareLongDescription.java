package nl.esciencecenter;

import java.net.URI;
import java.util.Optional;

public class SoftwareLongDescription {
	private final String descriptionText;
	private final URI descriptionUrl;

	private SoftwareLongDescription(String descriptionText, URI descriptionUrl) {
		this.descriptionText = descriptionText;
		this.descriptionUrl = descriptionUrl;
	}

	public static SoftwareLongDescription parseFromString(String s) {
		Optional<URI> maybeUrl = EstroSoftware.extractUrl(s);
		if (maybeUrl.isPresent()) {
			return new SoftwareLongDescription(null, maybeUrl.get());
		} else if (s != null) {
			String descriptionWithNewlines = s.replace("\\n", "\n");
			return new SoftwareLongDescription(descriptionWithNewlines, null);
		} else {
			return new SoftwareLongDescription(null, null);
		}
	}

	public String descriptionType() {
		return descriptionUrl == null ? "markdown" : "link";
	}

	public Optional<String> descriptionText() {
		return Optional.ofNullable(descriptionText);
	}

	public Optional<URI> descriptionUrl() {
		return Optional.ofNullable(descriptionUrl);
	}
}
