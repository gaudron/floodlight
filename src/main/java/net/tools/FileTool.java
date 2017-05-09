package net.tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class FileTool {

	public static String fileToString(String filename) throws IOException {

		String file_str;
		try(BufferedReader br = new BufferedReader(new FileReader(filename))) {
			StringBuilder sb = new StringBuilder();
			String line = br.readLine();

			while (line != null) {
				//log.info(line);
				sb.append(line);
				sb.append(System.lineSeparator());
				line = br.readLine();
			}
			file_str = sb.toString();
			return file_str;
		}

	}
}
