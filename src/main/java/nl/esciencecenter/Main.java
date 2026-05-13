package nl.esciencecenter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class Main {
	public static void main(String[] args) throws Exception {
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream is = classloader.getResourceAsStream("registry-v2.1.csv");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		List<String> lines = bufferedReader.lines().toList();

		Map<String, Integer> indexMap = EstroSoftware.parseHeader(lines.getFirst());

		Collection<EstroSoftware> successfullyParsedSoftware = new ArrayList<>();
		int success = 0;
		int fail = 0;
		int skipped = 0;
		for (String line : lines.subList(1, lines.size())) {
			EstroSoftware estroSoftware;
			try {
				estroSoftware = EstroSoftware.fromCsvLine(line, indexMap);
				if (estroSoftware.name().equals("3DSlicer")) {
					++skipped;
					continue;
				}
				successfullyParsedSoftware.add(estroSoftware);
				++success;
			} catch (RuntimeException e) {
				e.printStackTrace();
				++fail;
			}
		}

		System.out.println("Parsed software entries success: " + success);
		System.out.println("Parsed software entries skipped: " + skipped);
		System.out.println("Parsed software entries failure: " + fail);

		URI baseDomain = URI.create(args[0]);
		String apiToken = args[1];
		RsdApiConnector rsdApiConnector = new RsdApiConnector(baseDomain, apiToken);
		rsdApiConnector.saveSoftware(successfullyParsedSoftware);
	}
}
