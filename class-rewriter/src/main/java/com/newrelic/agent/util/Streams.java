package com.newrelic.agent.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Streams {
	public static final int DEFAULT_BUFFER_SIZE = 1024 * 8;

	private Streams() {}
	
	public static int copy(InputStream input, OutputStream output) throws IOException {
		return copy(input, output, Streams.DEFAULT_BUFFER_SIZE, false);
	}
	
	public static int copy(InputStream input, OutputStream output, boolean closeStreams) throws IOException {
		return copy(input, output, Streams.DEFAULT_BUFFER_SIZE, closeStreams);
	}

	public static int copy(InputStream input, OutputStream output, int bufferSize) throws IOException {
		return copy(input, output, bufferSize, false);
	}

	/**
	 * Copy bytes from an InputStream to an OutputStream.
	 * @param input the InputStream to read from
	 * @param output the OutputStream to write to
	 * @return the number of bytes copied
	 * @throws IOException In case of an I/O problem
	 */
	public static int copy(InputStream input, OutputStream output, int bufferSize, boolean closeStreams) throws IOException {
		try {
		    byte[] buffer = new byte[bufferSize];
		    int count = 0;
		    int n = 0;
		    while (-1 != (n = input.read(buffer))) {
		        output.write(buffer, 0, n);
		        count += n;
		    }
		    return count;
		} finally {
			if (closeStreams) {
				input.close();
				output.close();
			}
		}
	}
	
	public static byte[] slurpBytes(final InputStream in) throws IOException {
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			copy(in, out);
			out.flush();
			return out.toByteArray();
		}
		finally {
			out.close();
		}
	}
	
	public static String slurp(final InputStream in, final String encoding) throws IOException {
		final byte[] bytes = slurpBytes(in);
		return new String(bytes, encoding);
	}
	
	/**
	 * Copy newBytes to file.
	 * @param file
	 * @param newBytes
	 * @throws IOException
	 */
	public static void copyBytesToFile(File file, byte[] newBytes) throws IOException {
		OutputStream oStream = new FileOutputStream(file);
		try {
			Streams.copy(new ByteArrayInputStream(newBytes), oStream, true);
		} finally {
			oStream.close();
		}
	}
}
