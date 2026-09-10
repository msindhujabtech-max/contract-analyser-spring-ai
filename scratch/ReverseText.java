public class ReverseText {

    public static void main(String args[]){
 

         System.out.println("Text : india - Reversed Text  " + reverseText("india"));
    }

         private static String reverseText(String text){
            char[] charArray = text.toCharArray();
            char[] reveresedArray = new char[charArray.length];


            for(int i=0; i<charArray.length; i++){

         reveresedArray[i] =  charArray[charArray.length -1 -i ];
            }
return new String(reveresedArray);

         }





    
}
