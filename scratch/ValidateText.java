public class ValidateText {
    

    public static void main(String args[]){
       System.out.println("INDIA -> " + validateTextt("INDIA"));  // true
        System.out.println("India -> " + validateTextt("India"));  // true
        System.out.println("india -> " + validateTextt("india"));  // true
        System.out.println("iNdia -> " + validateTextt("iNdia"));  // false
        System.out.println("inDIA -> " + validateTextt("inDIA"));  // false
        System.out.println("INdia -> " + validateTextt("INdia"));  // false
    }

    private static boolean validateTextt(String text){
        char[] charArray = text.toCharArray();
        char[] result = new char[charArray.length];
        boolean restAllUpper = true;
        boolean restAllLower = true;


        boolean isFirstUpper = Character.isUpperCase(charArray[0]);
  
        for(int i=1; i<charArray.length;i++){
            if(Character.isUpperCase(charArray[i])){
             restAllLower = false;
            }
            else{
                restAllUpper=false;
            }

        }
        if(isFirstUpper && restAllLower) {
            return true;
        }
        else if(!isFirstUpper && restAllLower){
            return true;
        }
        else if(isFirstUpper && restAllUpper){
            return true;
        }
        else return false;
    }
}
