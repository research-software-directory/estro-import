package nl.esciencecenter;

public record Image(
		byte[] bytes,
		String mimeType
) {
}
