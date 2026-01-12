package nl.esciencecenter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Main {
	public static void main(String[] args) throws IOException, InterruptedException {
		ClassLoader classloader = Thread.currentThread().getContextClassLoader();
		InputStream is = classloader.getResourceAsStream("data-estro.csv");
		BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		List<String> lines = bufferedReader.lines().skip(1).toList();

		Collection<EstroSoftware> successfullyParsedSoftware = new ArrayList<>();
		int success = 0;
		int fail = 0;
		for (String line : lines) {
			// System.out.println(line);
			EstroSoftware estroSoftware;
			try {
				estroSoftware = EstroSoftware.fromCsvLine(line);
				successfullyParsedSoftware.add(estroSoftware);
				++success;
				System.out.println(estroSoftware);
			} catch (RuntimeException e) {
				++fail;
			}
		}

		System.out.println(success);
		System.out.println(fail);

		URI baseDomain = URI.create(args[0]);
		String apiToken = args[1];
		RsdApiConnector rsdApiConnector = new RsdApiConnector(baseDomain, apiToken);
		rsdApiConnector.saveSoftware(successfullyParsedSoftware);
	}
}
