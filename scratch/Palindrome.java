public class Palindrome {

    public static void main(String args[]){
        System.out.println("is Palindrome ?  India " +isPalindrome("India"));
        System.out.println("is Palindrome ?  racecar " +isPalindrome("racecar"));

    }

    private static boolean isPalindrome(String text){
        return text.equals(new StringBuilder(text).reverse().toString());
    
}

}
