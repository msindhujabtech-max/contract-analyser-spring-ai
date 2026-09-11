public class Palindrome {

    public static void main(String args[]){
        System.out.println("Approach 1: is Palindrome ?  India " +isPalindrome("India"));
        System.out.println("Approach 1: is Palindrome ?  racecar " +isPalindrome("racecar"));

        System.out.println("Approach 2 : is Palindrome ?  India " +isPalindromeApproach2("India"));
        System.out.println("Approach 2 : is Palindrome ?  racecar " +isPalindromeApproach2("racecar"));

    }

    private static boolean isPalindrome(String text){
        return text.equals(new StringBuilder(text).reverse().toString());
    
}

private static boolean isPalindromeApproach2(String text){

    char[] charArray = text.toCharArray();
    char[] result = new char[charArray.length];

    for (int i = 0; i < charArray.length; i++){            // start at 0, not 1
        result[i] = charArray[charArray.length - 1 - i];   // reverse
    }

    return text.equals(new String(result));                // new String(result), not result.toString()

}

}
