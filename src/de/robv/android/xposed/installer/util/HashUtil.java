package de.robv.android.xposed.installer.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtil {
	public static final String hash(String input, String algorithm) {
		try {
			MessageDigest md = MessageDigest.getInstance(algorithm);
			byte[] messageDigest = md.digest(input.getBytes());
			return toHexString(messageDigest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	public static final String md5(String input) {
		return hash(input, "MD5");
	}
	
	public static final String sha1(String input) {
		return hash(input, "SHA-1");
	}

	
	private static String toHexString(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (byte b : bytes) {
			int unsignedB = b & 0xff;
			if (unsignedB < 0x10)
				sb.append("0");
			sb.append(Integer.toHexString(unsignedB));
		}
		return sb.toString();
	}
}
