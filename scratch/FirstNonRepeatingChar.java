import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class FirstNonRepeatingChar {


    public static void main(String args[]){

        System.out.println("First Non Repeating Char of Swiss is:: " + findNonRepeatingChar("swiss"));
         System.out.println("Java 8 :: First Non Repeating Char of Swiss is:: " + findNonRepeatingCharJava8("swiss"));
    }


    private static char findNonRepeatingChar(String text){
Map<Character,Integer> counts = new LinkedHashMap<>();

for (char c : text.toCharArray()){
    counts.put(c,counts.getOrDefault(c, 0) +1 );
}

for(Map.Entry<Character,Integer> count : counts.entrySet()){
if(count.getValue()==1) return count.getKey();
}

 return 0;

    }
    
private static char findNonRepeatingCharJava8(String text){


    return text.chars().mapToObj(c -> (char) c)
    .collect(Collectors.groupingBy(c-> c, LinkedHashMap::new, Collectors.counting()))
    .entrySet().stream().filter(e-> e.getValue()==1)
    .map(Map.Entry::getKey).findFirst().orElse(null);
     
}


}
