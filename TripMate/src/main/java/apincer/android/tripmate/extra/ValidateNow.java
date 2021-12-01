package apincer.android.tripmate.extra;

import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ValidateNow {

	public static boolean isValidCreditCard(String cardNumber) {

		if (cardNumber.length() == 0) {
			return false;
		}
		final String digitsOnly = ValidateNow.getDigitsOnly(cardNumber);
		int sum = 0;
		int digit = 0;
		int addend = 0;
		boolean timesTwo = false;

		for (int i = digitsOnly.length() - 1; i >= 0; i--) {
			digit = Integer.parseInt(digitsOnly.substring(i, i + 1));
			if (timesTwo) {
				addend = digit * 2;
				if (addend > 9) {
					addend -= 9;
				}
			} else {
				addend = digit;
			}
			sum += addend;
			timesTwo = !timesTwo;
		}

		final int modulus = sum % 10;
		return modulus == 0;

	}

	private static String getDigitsOnly(String s) {
		final StringBuffer digitsOnly = new StringBuffer();
		char c;
		for (int i = 0; i < s.length(); i++) {
			c = s.charAt(i);
			if (Character.isDigit(c)) {
				digitsOnly.append(c);
			}
		}
		return digitsOnly.toString();
	}

	public static boolean validateEmail(final String text) {

		if (text.length() == 0) {
			return false;
		}

		// Input the string for validation
		// String email = "xyz@.com";
		// Set the email pattern string
		final Pattern p = Pattern
				.compile("^[a-z][a-z0-9_.]*@[a-z][a-z0-9_.]{1,}\\.[a-z][a-z0-9_]{1,}$");

		// Match the given string with the pattern
		final Matcher m = p.matcher(text);

		// check whether match is found
		final boolean matchFound = m.matches();

		final StringTokenizer st = new StringTokenizer(text, ".");
		String lastToken = null;
		while (st.hasMoreTokens()) {
			lastToken = st.nextToken();
		}

		if (matchFound && lastToken.length() >= 2
				&& text.length() - 1 != lastToken.length()) {

			// validate the country code
			return true;
		} else {
			return false;
		}
	}

}
