public class ValidateCountry {

    public static void main(String[] args) {
        System.out.println("INDIA -> " + validateCountryName("INDIA"));  // true
        System.out.println("India -> " + validateCountryName("India"));  // true
        System.out.println("india -> " + validateCountryName("india"));  // true
        System.out.println("iNdia -> " + validateCountryName("iNdia"));  // false
        System.out.println("inDIA -> " + validateCountryName("inDIA"));  // false
        System.out.println("INdia -> " + validateCountryName("INdia"));  // false
    }

    public static boolean validateCountryName(String country) {
        // Edge case: null or empty
        if (country == null || country.isEmpty()) {
            return false;
        }

        char[] charArray = country.toCharArray();

        // Step 1: is the first letter uppercase?
        boolean firstIsUpper = Character.isUpperCase(charArray[0]);

        // Step 2: check the REST of the letters (from index 1)
        boolean restAllUpper = true;
        boolean restAllLower = true;

        for (int i = 1; i < charArray.length; i++) {
            if (Character.isUpperCase(charArray[i])) {
                restAllLower = false;   // found an uppercase -> not all lower
            } else {
                restAllUpper = false;   // found a lowercase -> not all upper
            }
        }

        // Step 3: decide based on the 3 valid patterns
        if (firstIsUpper && restAllUpper) {
            return true;   // "INDIA"  - first upper, rest upper
        }
        if (firstIsUpper && restAllLower) {
            return true;   // "India"  - first upper, rest lower
        }
        if (!firstIsUpper && restAllLower) {
            return true;   // "india"  - first lower, rest lower
        }

        return false;      // anything else -> invalid
    }
}
