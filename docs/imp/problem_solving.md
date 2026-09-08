# Problem Solving Practice — 36 Java Coding Questions

**Format for every problem:** Approach first → dry-run → code.
**Interview habit:** Before writing code, say the trigger sentence out loud —
*"This is a ___ problem because ___, so I'll use ___."* Then dry-run with one
example before claiming it works.

---

## How to choose an approach (decision guide)

Ask these in order:
1. Is it a string/array I scan once? → single loop, O(n).
2. Do I need counts/frequencies or "have I seen this?" → HashMap or HashSet, O(n).
3. Is the array/string sorted, or do I compare two ends? → two pointers.
4. Do I need a running window (longest/smallest substring/subarray)? → sliding window.
5. "Find a pair/target that sums/matches"? → HashMap (store complement) or two pointers if sorted.
6. Nested structure / repeated subproblem? → recursion / DP.
7. Order matters, need last-seen or matching? → Stack.

Two structures solve ~60% of interview problems: **HashMap** (counting/lookup) and
**two pointers** (pairs/in-place). Master those first.

---

## The 6-step method to say out loud in interviews
1. Restate + clarify constraints (null? empty? allowed methods?).
2. Do one example by hand (input → output, note indices).
3. State the approach in one sentence before coding.
4. Write it slowly, narrating types and loop bounds.
5. Dry-run with your example, tracing variables.
6. State edge cases + time/space complexity.

---

## Java fundamentals to memorize
- Array valid indices: `0` to `length - 1`. `array[length]` is ALWAYS out of bounds.
- Forward loop: `for (int i = 0; i < length; i++)`
- Backward loop: `for (int i = length - 1; i >= 0; i--)`
- `char[]` to `String`: `new String(charArray)` (NOT `.toString()`).
- The type is `char` (lowercase), not `Char`.
- Counting one-liner: `map.put(k, map.getOrDefault(k, 0) + 1);`

---

# Warm-up: Reverse a string (3 ways)

**Input:** `"hello"` → **Output:** `"olleh"`
**Approach:** convert to char array and either loop backward, two-pointer swap, or build with StringBuilder.

```java
// Version 1 — char array, loop backward
public String reverse1(String input) {
    char[] chars = input.toCharArray();
    char[] result = new char[chars.length];
    for (int i = 0; i < chars.length; i++) {
        result[i] = chars[chars.length - 1 - i];
    }
    return new String(result);
}

// Version 2 — two-pointer swap in place
public String reverse2(String input) {
    char[] c = input.toCharArray();
    int left = 0, right = c.length - 1;
    while (left < right) {
        char temp = c[left];
        c[left] = c[right];
        c[right] = temp;
        left++;
        right--;
    }
    return new String(c);
}

// Version 3 — StringBuilder from the end
public String reverse3(String input) {
    StringBuilder sb = new StringBuilder();
    for (int i = input.length() - 1; i >= 0; i--) {
        sb.append(input.charAt(i));
    }
    return sb.toString();
}
```
**Another approach (built-in):** `new StringBuilder(input).reverse().toString();`
**Streams:**
```java
String reversed = input.chars().mapToObj(c -> String.valueOf((char) c))
    .reduce("", (a, b) -> b + a);   // prepend each char
```

---

## 1. Palindrome check
**Input:** `"racecar"` → **Output:** `true`  |  `"hello"` → `false`
**Approach:** compare two ends → two pointers, O(1) space.
Dry-run `"racecar"`: (r,r)(a,a)(e,e) meet → palindrome.
```java
public boolean isPalindrome(String s) {
    int left = 0, right = s.length() - 1;
    while (left < right) {
        if (s.charAt(left) != s.charAt(right)) return false;
        left++;
        right--;
    }
    return true;
}
```
Time O(n), space O(1).
**Another approach (reverse & compare):** `input.equals(new StringBuilder(input).reverse().toString());`
**Streams:**
```java
boolean isPalindrome = IntStream.range(0, s.length() / 2)
    .allMatch(i -> s.charAt(i) == s.charAt(s.length() - 1 - i));
```

---

## 2. First non-repeating character
**Input:** `"swiss"` → **Output:** `'w'`
**Approach:** need counts → frequency map (LinkedHashMap keeps order), then find first with count 1.
Dry-run `"swiss"`: {s:3,w:1,i:1} → 'w'.
```java
public Character firstNonRepeating(String s) {
    Map<Character, Integer> counts = new LinkedHashMap<>();
    for (char c : s.toCharArray()) {
        counts.put(c, counts.getOrDefault(c, 0) + 1);
    }
    for (Map.Entry<Character, Integer> e : counts.entrySet()) {
        if (e.getValue() == 1) return e.getKey();
    }
    return null;
}
```
Time O(n).
**Another approach (indexOf == lastIndexOf):**
```java
for (char c : s.toCharArray())
    if (s.indexOf(c) == s.lastIndexOf(c)) return c;   // appears once
```
**Streams:**
```java
Character result = s.chars().mapToObj(c -> (char) c)
    .collect(Collectors.groupingBy(c -> c, LinkedHashMap::new, Collectors.counting()))
    .entrySet().stream().filter(e -> e.getValue() == 1)
    .map(Map.Entry::getKey).findFirst().orElse(null);
```

---

## 3. Two Sum (return indices)
**Input:** `[2,7,11]`, target `9` → **Output:** `[0,1]`
**Approach:** for each x, need target - x. If already seen it → pair. HashMap value→index.
Dry-run `[2,7,11]`, target 9: see 2 (store), see 7 (need 2, found) → [0,1].
```java
public int[] twoSum(int[] nums, int target) {
    Map<Integer, Integer> seen = new HashMap<>();
    for (int i = 0; i < nums.length; i++) {
        int need = target - nums[i];
        if (seen.containsKey(need)) {
            return new int[]{ seen.get(need), i };
        }
        seen.put(nums[i], i);
    }
    return new int[]{-1, -1};
}
```
Time O(n), space O(n). Trade space for time.
**Another approach (brute force O(n^2)):**
```java
for (int i = 0; i < nums.length; i++)
    for (int j = i + 1; j < nums.length; j++)
        if (nums[i] + nums[j] == target) return new int[]{i, j};
```
**Streams:** not ideal here — you need index pairs + early exit, so a loop/HashMap is clearer.

---

## 4. Anagram check
**Input:** `"listen"`, `"silent"` → **Output:** `true`  |  `"abc"`, `"abd"` → `false`
**Approach:** same chars + counts. Count up for a, down for b; all zero → anagram.
Dry-run `"listen"`,`"silent"` → all counts 0.
```java
public boolean isAnagram(String a, String b) {
    if (a.length() != b.length()) return false;
    int[] counts = new int[26];
    for (int i = 0; i < a.length(); i++) {
        counts[a.charAt(i) - 'a']++;
        counts[b.charAt(i) - 'a']--;
    }
    for (int c : counts) {
        if (c != 0) return false;
    }
    return true;
}
```
`char - 'a'` maps 'a'→0 ... 'z'→25. Time O(n), space O(1).
**Another approach (sort & compare):**
```java
char[] a1 = a.toCharArray(); char[] b1 = b.toCharArray();
Arrays.sort(a1); Arrays.sort(b1);
boolean anagram = Arrays.equals(a1, b1);
```
**Streams:**
```java
boolean anagram = a.length() == b.length() &&
    a.chars().sorted().boxed().collect(Collectors.toList())
     .equals(b.chars().sorted().boxed().collect(Collectors.toList()));
```

---

## 5. Find duplicates in an array
**Input:** `[1,2,3,2]` → **Output:** `[2]`
**Approach:** "have I seen it?" → HashSet. `add` returns false if present.
Dry-run `[1,2,3,2]` → 2.
```java
public List<Integer> findDuplicates(int[] nums) {
    Set<Integer> seen = new HashSet<>();
    List<Integer> dups = new ArrayList<>();
    for (int n : nums) {
        if (!seen.add(n)) {
            dups.add(n);
        }
    }
    return dups;
}
```
Time O(n), space O(n).
**Another approach (frequency map, count > 1):**
```java
Map<Integer,Integer> freq = new HashMap<>();
for (int n : nums) freq.merge(n, 1, Integer::sum);
freq.forEach((k, v) -> { if (v > 1) dups.add(k); });
```
**Streams:**
```java
List<Integer> dups = Arrays.stream(nums).boxed()
    .collect(Collectors.groupingBy(n -> n, Collectors.counting()))
    .entrySet().stream().filter(e -> e.getValue() > 1)
    .map(Map.Entry::getKey).collect(Collectors.toList());
```

---

## 6. Reverse words in a sentence ("hello world" → "world hello")
**Input:** `"hello world"` → **Output:** `"world hello"`
**Approach:** split on spaces, walk tokens from the end.
```java
public String reverseWords(String sentence) {
    String[] words = sentence.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = words.length - 1; i >= 0; i--) {
        sb.append(words[i]);
        if (i > 0) sb.append(" ");
    }
    return sb.toString();
}
```
Time O(n). Edge: no trailing space.
**Another approach (Collections.reverse):**
```java
List<String> words = Arrays.asList(sentence.trim().split("\\s+"));
Collections.reverse(words);
String result = String.join(" ", words);
```
**Streams:**
```java
String result = Arrays.stream(sentence.trim().split("\\s+"))
    .reduce((a, b) -> b + " " + a).orElse("");
```

---

## 7. Second largest element
**Input:** `[3,7,1,7,5]` → **Output:** `5`
**Approach:** one pass, track largest + second; handle duplicates & init with MIN_VALUE.
Dry-run `[3,7,1,7,5]` → 5.
```java
public int secondLargest(int[] nums) {
    int largest = Integer.MIN_VALUE, second = Integer.MIN_VALUE;
    for (int n : nums) {
        if (n > largest) {
            second = largest;
            largest = n;
        } else if (n > second && n != largest) {
            second = n;
        }
    }
    return second;
}
```
Time O(n).
**Another approach (sort distinct, take 2nd from end):**
```java
int[] sorted = Arrays.stream(nums).distinct().sorted().toArray();
int second = sorted[sorted.length - 2];
```
**Streams:**
```java
int second = Arrays.stream(nums).distinct().boxed()
    .sorted(Comparator.reverseOrder()).skip(1).findFirst().orElseThrow();
```

---

## 8. FizzBuzz
**Input:** `n=15` → **Output:** `1 2 Fizz 4 Buzz Fizz 7 8 Fizz Buzz 11 Fizz 13 14 FizzBuzz`
**Approach:** check divisible-by-15 FIRST, then 3, then 5. Branch order matters.
```java
public void fizzBuzz(int n) {
    for (int i = 1; i <= n; i++) {
        if (i % 15 == 0)      System.out.println("FizzBuzz");
        else if (i % 3 == 0)  System.out.println("Fizz");
        else if (i % 5 == 0)  System.out.println("Buzz");
        else                  System.out.println(i);
    }
}
```
**Another approach (build string per number):**
```java
String s = "";
if (i % 3 == 0) s += "Fizz";
if (i % 5 == 0) s += "Buzz";
System.out.println(s.isEmpty() ? i : s);
```
**Streams:**
```java
IntStream.rangeClosed(1, n).mapToObj(i ->
    i % 15 == 0 ? "FizzBuzz" : i % 3 == 0 ? "Fizz" : i % 5 == 0 ? "Buzz" : "" + i)
    .forEach(System.out::println);
```

---

## 9. Count occurrences of each word
**Input:** `"a b a"` → **Output:** `{a=2, b=1}`
**Approach:** frequency map on words (same shape as #2).
```java
public Map<String, Integer> wordCount(String text) {
    Map<String, Integer> counts = new HashMap<>();
    for (String word : text.toLowerCase().trim().split("\\s+")) {
        counts.put(word, counts.getOrDefault(word, 0) + 1);
    }
    return counts;
}
```
**Another approach (merge):** `counts.merge(word, 1, Integer::sum);` inside the loop.
**Streams:**
```java
Map<String,Long> counts = Arrays.stream(text.toLowerCase().trim().split("\\s+"))
    .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
```

---

## 10. Fibonacci (nth)
**Input:** `n=5` → **Output:** `5`  (sequence: 0,1,1,2,3,5)
**Approach:** iterative with two variables beats naive recursion (O(2^n) → O(n)).
Dry-run n=5: 0,1,1,2,3,5.
```java
public int fib(int n) {
    if (n <= 1) return n;
    int prev = 0, curr = 1;
    for (int i = 2; i <= n; i++) {
        int next = prev + curr;
        prev = curr;
        curr = next;
    }
    return curr;
}
```
**Another approach (recursion + memoization):**
```java
int fib(int n, Map<Integer,Integer> memo) {
    if (n <= 1) return n;
    if (memo.containsKey(n)) return memo.get(n);
    int val = fib(n-1, memo) + fib(n-2, memo);
    memo.put(n, val); return val;
}
```
**Streams:**
```java
int nth = Stream.iterate(new int[]{0,1}, f -> new int[]{f[1], f[0]+f[1]})
    .limit(n + 1).reduce((a, b) -> b).get()[0];
```

---

## 11. Remove duplicates from sorted array (in place)
**Input:** `[1,1,2,3,3]` → **Output:** length `3`, array `[1,2,3,...]`
**Approach:** sorted + in-place → slow/fast pointers.
Dry-run `[1,1,2,3,3]` → length 3, `[1,2,3,...]`.
```java
public int removeDuplicates(int[] nums) {
    if (nums.length == 0) return 0;
    int slow = 0;
    for (int fast = 1; fast < nums.length; fast++) {
        if (nums[fast] != nums[slow]) {
            slow++;
            nums[slow] = nums[fast];
        }
    }
    return slow + 1;
}
```
Time O(n), space O(1).
**Another approach (LinkedHashSet, not in-place):** `Set<Integer> set = new LinkedHashSet<>(); for (int n : nums) set.add(n);`
**Streams:** `int[] unique = Arrays.stream(nums).distinct().toArray();`

---

## 12. Longest substring without repeating characters (sliding window)
**Input:** `"abcabcbb"` → **Output:** `3`  (the substring "abc")
**Approach:** longest window with a property → sliding window + Set; expand right, shrink left on repeat.
Dry-run `"abcabcbb"` → 3.
```java
public int longestUnique(String s) {
    Set<Character> window = new HashSet<>();
    int left = 0, max = 0;
    for (int right = 0; right < s.length(); right++) {
        while (window.contains(s.charAt(right))) {
            window.remove(s.charAt(left));
            left++;
        }
        window.add(s.charAt(right));
        max = Math.max(max, right - left + 1);
    }
    return max;
}
```
Time O(n).
**Another approach (HashMap of last index — jump left):**
```java
Map<Character,Integer> lastSeen = new HashMap<>();
for (int right = 0; right < s.length(); right++) {
    char c = s.charAt(right);
    if (lastSeen.containsKey(c)) left = Math.max(left, lastSeen.get(c) + 1);
    lastSeen.put(c, right);
    max = Math.max(max, right - left + 1);
}
```
**Streams:** not suitable — sliding window needs mutable pointers (say this).

---

## 13. Count vowels and consonants
**Input:** `"java"` → **Output:** `Vowels: 2, Consonants: 2`
**Approach:** single scan, classify each letter via a vowel set; ignore non-letters.
Dry-run `"java"` → vowels 2, consonants 2.
```java
public void countVowelsConsonants(String s) {
    String vowels = "aeiou";
    int vowelCount = 0, consonantCount = 0;
    for (char c : s.toLowerCase().toCharArray()) {
        if (Character.isLetter(c)) {
            if (vowels.indexOf(c) >= 0) vowelCount++;
            else consonantCount++;
        }
    }
    System.out.println("Vowels: " + vowelCount + ", Consonants: " + consonantCount);
}
```
Time O(n).
**Another approach (regex):**
```java
String letters = s.toLowerCase().replaceAll("[^a-z]", "");
int vowels = letters.replaceAll("[^aeiou]", "").length();
int consonants = letters.length() - vowels;
```
**Streams:**
```java
long vowels = s.toLowerCase().chars().filter(c -> "aeiou".indexOf(c) >= 0).count();
```

---

## 14. Check if a number is prime
**Input:** `13` → **Output:** `true`  |  `12` → `false`
**Approach:** no divisor between 2 and sqrt(n). Loop only to sqrt(n) using `i*i <= n`.
Dry-run 13: check 3 → prime.
```java
public boolean isPrime(int n) {
    if (n <= 1) return false;
    if (n <= 3) return true;
    if (n % 2 == 0) return false;
    for (int i = 3; i * i <= n; i += 2) {
        if (n % i == 0) return false;
    }
    return true;
}
```
Time O(sqrt(n)).
**Another approach (count divisors == 2):**
```java
long divisors = IntStream.rangeClosed(1, n).filter(i -> n % i == 0).count();
boolean isPrime = n > 1 && divisors == 2;
```
**Streams:**
```java
boolean isPrime = n > 1 &&
    IntStream.rangeClosed(2, (int) Math.sqrt(n)).noneMatch(i -> n % i == 0);
```

---

## 15. Reverse an integer (123 → 321)
**Input:** `123` → **Output:** `321`  |  `-45` → `-54`
**Approach:** peel digits with `% 10`, build `result*10 + digit`, drop with `/ 10`.
Dry-run 123 → 3, 32, 321.
```java
public int reverseNumber(int n) {
    int result = 0;
    while (n != 0) {
        int digit = n % 10;
        result = result * 10 + digit;
        n = n / 10;
    }
    return result;
}
```
Handles negatives in Java automatically.
**Another approach (string reverse then parse):**
```java
boolean neg = n < 0;
String rev = new StringBuilder(String.valueOf(Math.abs(n))).reverse().toString();
int result = (neg ? -1 : 1) * Integer.parseInt(rev);
```
**Streams:** digit math loop is clearer; streams add no value.

---

## 16. Move all zeros to the end (keep order)
**Input:** `[0,1,0,3]` → **Output:** `[1,3,0,0]`
**Approach:** in-place + keep order → slow/fast pointers; copy non-zeros, then fill zeros.
Dry-run `[0,1,0,3]` → `[1,3,0,0]`.
```java
public void moveZeros(int[] nums) {
    int slow = 0;
    for (int fast = 0; fast < nums.length; fast++) {
        if (nums[fast] != 0) {
            nums[slow] = nums[fast];
            slow++;
        }
    }
    while (slow < nums.length) {
        nums[slow] = 0;
        slow++;
    }
}
```
Time O(n), space O(1).
**Another approach (swap non-zeros forward):**
```java
int lastNonZero = 0;
for (int i = 0; i < nums.length; i++)
    if (nums[i] != 0) { int t = nums[lastNonZero]; nums[lastNonZero++] = nums[i]; nums[i] = t; }
```
**Streams (new array, not in-place):**
```java
int[] result = IntStream.concat(
    Arrays.stream(nums).filter(x -> x != 0),
    Arrays.stream(nums).filter(x -> x == 0)).toArray();
```

---

## 17. Missing number in array of 0..n
**Input:** `[0,1,3]` → **Output:** `2`
**Approach:** expected sum n(n+1)/2 minus actual sum = missing. (XOR alternative avoids overflow.)
Dry-run `[0,1,3]` → 6-4 = 2.
```java
public int missingNumber(int[] nums) {
    int n = nums.length;
    int expectedSum = n * (n + 1) / 2;
    int actualSum = 0;
    for (int num : nums) actualSum += num;
    return expectedSum - actualSum;
}
```
Time O(n), space O(1).
**Another approach (XOR — avoids overflow):**
```java
int xor = nums.length;
for (int i = 0; i < nums.length; i++) xor ^= i ^ nums[i];
return xor;
```
**Streams:**
```java
int expected = IntStream.rangeClosed(0, nums.length).sum();
int actual = Arrays.stream(nums).sum();
return expected - actual;
```

---

## 18. Valid parentheses
**Input:** `"({})"` → **Output:** `true`  |  `"(]"` → `false`
**Approach:** matching/nesting → Stack. Push opens; pop & match on closes; empty at end = valid.
Dry-run `"({})"` → valid.
```java
public boolean isValid(String s) {
    Deque<Character> stack = new ArrayDeque<>();
    for (char c : s.toCharArray()) {
        if (c == '(' || c == '{' || c == '[') {
            stack.push(c);
        } else {
            if (stack.isEmpty()) return false;
            char open = stack.pop();
            if ((c == ')' && open != '(') ||
                (c == '}' && open != '{') ||
                (c == ']' && open != '[')) return false;
        }
    }
    return stack.isEmpty();
}
```
Time O(n).
**Another approach (repeatedly remove pairs — simple but O(n^2)):**
```java
while (s.contains("()") || s.contains("[]") || s.contains("{}"))
    s = s.replace("()", "").replace("[]", "").replace("{}", "");
boolean valid = s.isEmpty();
```
**Streams:** not suitable — needs a LIFO stack (say this).

---

## 19. Merge two sorted arrays
**Input:** `[1,3]`, `[2,4]` → **Output:** `[1,2,3,4]`
**Approach:** two pointers, take smaller front each time; then drain leftovers.
Dry-run `[1,3]`,`[2,4]` → `[1,2,3,4]`.
```java
public int[] merge(int[] a, int[] b) {
    int[] result = new int[a.length + b.length];
    int i = 0, j = 0, k = 0;
    while (i < a.length && j < b.length) {
        if (a[i] <= b[j]) result[k++] = a[i++];
        else              result[k++] = b[j++];
    }
    while (i < a.length) result[k++] = a[i++];
    while (j < b.length) result[k++] = b[j++];
    return result;
}
```
Time O(n+m).
**Another/Streams approach (concat then sort — simpler but O((n+m)log(n+m))):**
```java
int[] merged = IntStream.concat(Arrays.stream(a), Arrays.stream(b)).sorted().toArray();
```

---

## 20. Most frequent element
**Input:** `[1,2,2,3,2]` → **Output:** `2`
**Approach:** counts → HashMap, then find max-count entry.
Dry-run `[1,2,2,3,2]` → 2.
```java
public int mostFrequent(int[] nums) {
    Map<Integer, Integer> counts = new HashMap<>();
    for (int n : nums) counts.put(n, counts.getOrDefault(n, 0) + 1);
    int result = nums[0], maxCount = 0;
    for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
        if (e.getValue() > maxCount) {
            maxCount = e.getValue();
            result = e.getKey();
        }
    }
    return result;
}
```
Time O(n).
**Another approach (track max while building map):**
```java
int result = nums[0], max = 0;
for (int n : nums) { int c = freq.merge(n, 1, Integer::sum); if (c > max) { max = c; result = n; } }
```
**Streams:**
```java
int result = Arrays.stream(nums).boxed()
    .collect(Collectors.groupingBy(n -> n, Collectors.counting()))
    .entrySet().stream().max(Map.Entry.comparingByValue())
    .map(Map.Entry::getKey).orElseThrow();
```

---

## 21. Digital root (9875 → 2)
**Input:** `9875` → **Output:** `2`  (9875→29→11→2)
**Approach:** repeatedly sum digits until single digit (loop within loop).
Dry-run 9875 → 29 → 11 → 2.
```java
public int digitalRoot(int n) {
    while (n > 9) {
        int sum = 0;
        while (n > 0) {
            sum += n % 10;
            n /= 10;
        }
        n = sum;
    }
    return n;
}
```
**Another approach (math formula O(1)):** `int root = (n == 0) ? 0 : 1 + (n - 1) % 9;`
**Streams:** digit-sum loop is clearer; the formula beats both.

---

## 22. Factorial (iterative + recursive)
**Input:** `5` → **Output:** `120`  (5×4×3×2×1)
**Approach:** recursion is natural (n! = n*(n-1)!); iteration avoids stack depth. Use long.
```java
public long factorialIter(int n) {
    long result = 1;
    for (int i = 2; i <= n; i++) result *= i;
    return result;
}

public long factorialRec(int n) {
    if (n <= 1) return 1;              // base case
    return n * factorialRec(n - 1);
}
```
Always state the base case.
**Another approach (BigInteger for large n):**
```java
BigInteger f = BigInteger.ONE;
for (int i = 2; i <= n; i++) f = f.multiply(BigInteger.valueOf(i));
```
**Streams:** `long fact = LongStream.rangeClosed(1, n).reduce(1, (a, b) -> a * b);`

---

## 23. Count occurrences in sorted array (binary search)
**Input:** `[1,2,2,2,3]`, target `2` → **Output:** `3`
**Approach:** sorted → binary search for first & last index; count = last-first+1.
Dry-run `[1,2,2,2,3]`, target 2 → 3.
```java
public int countOccurrences(int[] nums, int target) {
    int first = findBound(nums, target, true);
    if (first == -1) return 0;
    int last = findBound(nums, target, false);
    return last - first + 1;
}

private int findBound(int[] nums, int target, boolean findFirst) {
    int lo = 0, hi = nums.length - 1, result = -1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;   // avoids overflow
        if (nums[mid] == target) {
            result = mid;
            if (findFirst) hi = mid - 1;
            else           lo = mid + 1;
        } else if (nums[mid] < target) {
            lo = mid + 1;
        } else {
            hi = mid - 1;
        }
    }
    return result;
}
```
Time O(log n).
**Another approach (linear count O(n)):** `long count = Arrays.stream(nums).filter(x -> x == target).count();`
**Streams:** same as above; note binary search is O(log n) and faster for sorted data.

---

## 24. Maximum subarray sum (Kadane)
**Input:** `[-2,1,-3,4,-1,2,1]` → **Output:** `6`  (subarray [4,-1,2,1])
**Approach:** at each element, extend current run or start fresh; track best.
Dry-run `[-2,1,-3,4,-1,2,1]` → 6.
```java
public int maxSubArray(int[] nums) {
    int currentSum = nums[0];
    int maxSum = nums[0];
    for (int i = 1; i < nums.length; i++) {
        currentSum = Math.max(nums[i], currentSum + nums[i]);
        maxSum = Math.max(maxSum, currentSum);
    }
    return maxSum;
}
```
Time O(n), space O(1).
**Another approach (brute force O(n^2)):**
```java
for (int i = 0; i < nums.length; i++) {
    int sum = 0;
    for (int j = i; j < nums.length; j++) { sum += nums[j]; max = Math.max(max, sum); }
}
```
**Streams:** not suitable — Kadane needs running carry-forward state (say this).

---

## 25. Swap two numbers without a temp variable
**Input:** `a=5, b=3` → **Output:** `a=3, b=5`
**Approach:** sum/difference trick (or XOR). Puzzle, not production code.
Dry-run a=5,b=3 → a=3,b=5.
```java
public void swap(int a, int b) {
    a = a + b;
    b = a - b;
    a = a - b;
    System.out.println("a=" + a + ", b=" + b);
}
```
**Another approach (XOR):** `a = a ^ b; b = a ^ b; a = a ^ b;`
**Streams:** N/A (not a collection problem).

---

## 26. Are two strings rotations? ("abcd" & "cdab")
**Input:** `"abcd"`, `"cdab"` → **Output:** `true`  |  `"abcd"`, `"abdc"` → `false`
**Approach:** a rotation of s is a substring of s+s.
Dry-run "abcd"+"abcd" contains "cdab" → yes.
```java
public boolean areRotations(String s1, String s2) {
    if (s1.length() != s2.length()) return false;
    return (s1 + s1).contains(s2);
}
```
Time O(n).
**Another approach (manual rotate & compare):**
```java
for (int i = 0; i < s1.length(); i++)
    if ((s1.substring(i) + s1.substring(0, i)).equals(s2)) return true;
```
**Streams:** the `(s1+s1).contains(s2)` trick is best; streams add nothing.

---

## 27. GCD (Euclid's algorithm)
**Input:** `48, 18` → **Output:** `6`
**Approach:** gcd(a,b) = gcd(b, a % b) until b = 0.
Dry-run gcd(48,18) → 6.
```java
public int gcd(int a, int b) {
    while (b != 0) {
        int remainder = a % b;
        a = b;
        b = remainder;
    }
    return a;
}
```
LCM follow-up: `a / gcd(a,b) * b`.
**Another approach (recursive):** `int gcd(int a,int b){ return b==0 ? a : gcd(b, a%b); }`
**Streams (gcd of an array):** `int g = Arrays.stream(nums).reduce(0, (a, b) -> gcd(a, b));`

---

## 28. Sort objects by a field (Comparator)
**Input:** `[(Anu,50000),(Bala,70000)]` → **Output:** `[(Bala,70000),(Anu,50000)]` (salary desc)
**Approach:** Comparator.comparing with thenComparing (tie-break) and reversed (desc).
```java
class Employee {
    String name; int salary;
    Employee(String n, int s) { name = n; salary = s; }
}

public void sortEmployees(List<Employee> employees) {
    employees.sort(
        Comparator.comparingInt((Employee e) -> e.salary).reversed()
                  .thenComparing(e -> e.name)
    );
}
```
Time O(n log n).
**Another approach (anonymous Comparator):**
```java
employees.sort(new Comparator<Employee>() {
    public int compare(Employee a, Employee b) { return b.salary - a.salary; }
});
```
**Streams (returns new list):**
```java
List<Employee> sorted = employees.stream()
    .sorted(Comparator.comparingInt((Employee e) -> e.salary).reversed())
    .collect(Collectors.toList());
```

---

## 29. Group words by first letter (streams)
**Input:** `["apple","banana","avocado"]` → **Output:** `{a=[apple, avocado], b=[banana]}`
**Approach:** "group by a key" → Collectors.groupingBy.
Dry-run ["apple","banana","avocado"] → {a:[apple,avocado], b:[banana]}.
```java
public Map<Character, List<String>> groupByFirstLetter(List<String> words) {
    return words.stream()
        .filter(w -> !w.isEmpty())
        .collect(Collectors.groupingBy(w -> w.charAt(0)));
}
```
Pre-Java-8 equivalent: `map.computeIfAbsent(key, k -> new ArrayList<>()).add(w);`
**Another approach (loop + computeIfAbsent):**
```java
Map<Character,List<String>> map = new HashMap<>();
for (String w : words) map.computeIfAbsent(w.charAt(0), k -> new ArrayList<>()).add(w);
```
(The streams version above IS the primary; this is the pre-streams loop.)

---

## 30. Reverse a singly linked list
**Input:** `1->2->3` → **Output:** `3->2->1`
**Approach:** walk list, reverse each next pointer; track prev/curr/next.
Dry-run 1->2->3 → 3->2->1.
```java
class Node { int val; Node next; Node(int v){val=v;} }

public Node reverseList(Node head) {
    Node prev = null;
    Node curr = head;
    while (curr != null) {
        Node next = curr.next;   // save next first
        curr.next = prev;
        prev = curr;
        curr = next;
    }
    return prev;
}
```
Time O(n), space O(1).
**Another approach (recursive):**
```java
Node reverse(Node head) {
    if (head == null || head.next == null) return head;
    Node newHead = reverse(head.next);
    head.next.next = head; head.next = null;
    return newHead;
}
```
**Streams:** N/A (linked list pointer manipulation).

---

## 31. Find middle of a linked list
**Input:** `1->2->3->4->5` → **Output:** `3`
**Approach:** fast/slow pointers; slow +1, fast +2; slow ends at middle.
Dry-run 1->2->3->4->5 → 3.
```java
public Node findMiddle(Node head) {
    Node slow = head, fast = head;
    while (fast != null && fast.next != null) {
        slow = slow.next;
        fast = fast.next.next;
    }
    return slow;
}
```
Same idea detects cycles (Floyd's).
**Another approach (count then walk half):**
```java
int len = 0; for (Node p = head; p != null; p = p.next) len++;
Node mid = head; for (int i = 0; i < len/2; i++) mid = mid.next;
```
**Streams:** N/A (linked list traversal).

---

## 32. Two Sum on a SORTED array (two pointers)
**Input:** `[1,3,4,6]`, target `9` → **Output:** `[1,3]` (values 3+6)
**Approach:** sorted → two pointers; sum too small move left up, too big move right down. O(1) space.
Dry-run `[1,3,4,6]`, target 9 → [1,3].
```java
public int[] twoSumSorted(int[] nums, int target) {
    int left = 0, right = nums.length - 1;
    while (left < right) {
        int sum = nums[left] + nums[right];
        if (sum == target) return new int[]{left, right};
        else if (sum < target) left++;
        else right--;
    }
    return new int[]{-1, -1};
}
```
Time O(n), space O(1).
**Another approach (binary search for complement):**
```java
for (int i = 0; i < nums.length; i++) {
    int idx = Arrays.binarySearch(nums, i+1, nums.length, target - nums[i]);
    if (idx >= 0) return new int[]{i, idx};
}
```
**Streams:** not ideal (index pairs + early exit).

---

## 33. Longest common prefix
**Input:** `["flower","flow","flight"]` → **Output:** `"fl"`
**Approach:** start with first string, shrink until all start with it.
Dry-run ["flower","flow","flight"] → "fl".
```java
public String longestCommonPrefix(String[] strs) {
    if (strs.length == 0) return "";
    String prefix = strs[0];
    for (int i = 1; i < strs.length; i++) {
        while (!strs[i].startsWith(prefix)) {
            prefix = prefix.substring(0, prefix.length() - 1);
            if (prefix.isEmpty()) return "";
        }
    }
    return prefix;
}
```
Time O(n * m).
**Another approach (vertical scanning char by char):**
```java
for (int i = 0; i < strs[0].length(); i++) {
    char c = strs[0].charAt(i);
    for (String s : strs)
        if (i == s.length() || s.charAt(i) != c) return strs[0].substring(0, i);
}
return strs[0];
```
**Streams:**
```java
String prefix = Arrays.stream(strs).reduce((a, b) -> {
    int i = 0;
    while (i < a.length() && i < b.length() && a.charAt(i) == b.charAt(i)) i++;
    return a.substring(0, i);
}).orElse("");
```

---

## 34. Rotate an array by k (in place)
**Input:** `[1,2,3,4,5]`, `k=2` → **Output:** `[4,5,1,2,3]`
**Approach:** reverse whole, reverse first k, reverse rest. Handle k > length with k %= n.
Dry-run `[1,2,3,4,5]`, k=2 → `[4,5,1,2,3]`.
```java
public void rotate(int[] nums, int k) {
    int n = nums.length;
    k = k % n;
    reverse(nums, 0, n - 1);
    reverse(nums, 0, k - 1);
    reverse(nums, k, n - 1);
}

private void reverse(int[] nums, int start, int end) {
    while (start < end) {
        int temp = nums[start];
        nums[start] = nums[end];
        nums[end] = temp;
        start++;
        end--;
    }
}
```
Time O(n), space O(1).
**Another approach (extra array, place at (i+k)%n):**
```java
int n = nums.length; int[] result = new int[n];
for (int i = 0; i < n; i++) result[(i + k) % n] = nums[i];
```
**Streams:** not clean for in-place rotation.

---

## 35. Print a matrix in spiral order
**Input:** `[[1,2,3],[4,5,6],[7,8,9]]` → **Output:** `[1,2,3,6,9,8,7,4,5]`
**Approach:** four shrinking boundaries (top/bottom/left/right); guard against double-print.
```java
public List<Integer> spiralOrder(int[][] matrix) {
    List<Integer> result = new ArrayList<>();
    if (matrix.length == 0) return result;
    int top = 0, bottom = matrix.length - 1;
    int left = 0, right = matrix[0].length - 1;
    while (top <= bottom && left <= right) {
        for (int c = left; c <= right; c++) result.add(matrix[top][c]);
        top++;
        for (int r = top; r <= bottom; r++) result.add(matrix[r][right]);
        right--;
        if (top <= bottom) {
            for (int c = right; c >= left; c--) result.add(matrix[bottom][c]);
            bottom--;
        }
        if (left <= right) {
            for (int r = bottom; r >= top; r--) result.add(matrix[r][left]);
            left++;
        }
    }
    return result;
}
```
Time O(rows*cols).
**Another approach (direction vectors + turn on boundary):** use `dirs = {{0,1},{1,0},{0,-1},{-1,0}}`, move until out of bounds/visited, then rotate direction.
**Streams:** N/A (2D traversal with state).

---

## 36. Climbing stairs (intro DP)
**Input:** `n=4` → **Output:** `5`  (ways: 1+1+1+1, 1+1+2, 1+2+1, 2+1+1, 2+2)
**Approach:** ways(n) = ways(n-1) + ways(n-2) — Fibonacci in disguise; iterate O(n)/O(1).
Dry-run n=4 → 5.
```java
public int climbStairs(int n) {
    if (n <= 2) return n;
    int oneStepBack = 2;
    int twoStepsBack = 1;
    for (int i = 3; i <= n; i++) {
        int current = oneStepBack + twoStepsBack;
        twoStepsBack = oneStepBack;
        oneStepBack = current;
    }
    return oneStepBack;
}
```
Time O(n), space O(1).
**Another approach (recursion + memo):**
```java
int climb(int n, int[] memo) {
    if (n <= 2) return n;
    if (memo[n] != 0) return memo[n];
    return memo[n] = climb(n-1, memo) + climb(n-2, memo);
}
```
**Streams:**
```java
int ways = Stream.iterate(new int[]{1,2}, f -> new int[]{f[1], f[0]+f[1]})
    .limit(n - 1).reduce((a, b) -> b).get()[0];
```

---

# Pattern cheat-sheet (memorize the triggers)

| When you see... | Reach for... |
|---|---|
| Compare two ends / sorted pairs | Two pointers (O(1) space) |
| "Have I seen it?" / duplicates | HashSet (O(1) lookup) |
| Counting / frequency / "first ..." | HashMap (`getOrDefault`) |
| "Find pair summing to target" | HashMap (store complement) |
| Longest/smallest window | Sliding window |
| In-place array edit, keep order | Slow/fast pointers |
| Anagram / fixed alphabet | int[26] count |
| Nesting / matching brackets | Stack |
| Sorted array + search/count | Binary search (O(log n)) |
| Merge two sorted sequences | Two pointers |
| Max/min contiguous subarray sum | Kadane / DP |
| Digit manipulation / reverse number | `% 10` and `/ 10` loop |
| Missing/duplicate in 0..n | Sum formula or XOR |
| "Group by / count by a key" | `Collectors.groupingBy` / `computeIfAbsent` |
| "Sort by field(s)" | `Comparator.comparing().thenComparing()` |
| Linked list reverse / rewire | 3 pointers: prev/curr/next |
| "Middle of list" / cycle | Fast + slow pointers |
| Rotate array in place | Reverse-whole-then-parts |
| 2D matrix traversal | Four shrinking boundaries |
| "Ways to do X" / step count | DP: express f(n) via f(n-1), f(n-2) |
| Rotation of a string | Substring of `s + s` |
| GCD / remainder repetition | Euclid's `gcd(b, a%b)` |

---

# Final advice
- Practice ~30 min/day: write BY HAND first (no IDE autocomplete), dry-run, THEN run.
- For every problem, say the trigger sentence out loud: "This is X because Y, so I'll use Z."
- Interviewers score problem-solving on hearing you pick and justify the right tool — not just the final code.
- Master HashMap + two pointers first; they cover ~60% of questions.

---

## 37. Validate country name capitalization (charArray approach)

**Rule:** valid only if the name is ALL uppercase (`INDIA`), ALL lowercase (`india`),
or title case (`India` — first upper, rest lower). Any mixed case (`iNdia`, `inDIA`,
`INdia`) is invalid.

**Approach:** convert to `char[]`, check if the first char is uppercase, then scan the
rest tracking two flags (`restAllUpper`, `restAllLower`). Match against the 3 valid patterns.

```java
public class ValidateCountry {

    public static void main(String[] args) {
        System.out.println("INDIA -> " + validateCountryName("INDIA"));
        System.out.println("India -> " + validateCountryName("India"));
        System.out.println("india -> " + validateCountryName("india"));
        System.out.println("iNdia -> " + validateCountryName("iNdia"));
        System.out.println("inDIA -> " + validateCountryName("inDIA"));
        System.out.println("INdia -> " + validateCountryName("INdia"));
    }

    public static boolean validateCountryName(String country) {
        if (country == null || country.isEmpty()) return false;   // edge case

        char[] charArray = country.toCharArray();
        boolean firstIsUpper = Character.isUpperCase(charArray[0]);

        boolean restAllUpper = true;
        boolean restAllLower = true;
        for (int i = 1; i < charArray.length; i++) {
            if (Character.isUpperCase(charArray[i])) {
                restAllLower = false;   // found upper -> not all lower
            } else {
                restAllUpper = false;   // found lower -> not all upper
            }
        }

        if (firstIsUpper && restAllUpper) return true;   // INDIA
        if (firstIsUpper && restAllLower) return true;   // India
        if (!firstIsUpper && restAllLower) return true;  // india
        return false;                                    // anything else
    }
}
```

**Actual output (compiled & run with Java 8):**
```
INDIA -> true
India -> true
india -> true
iNdia -> false
inDIA -> false
INdia -> false
```

**Dry-run trace:**

| Input | firstIsUpper | rest | Rule matched | Result |
|-------|:-----------:|------|--------------|:------:|
| `INDIA` | true | all upper | firstUpper + restUpper | true |
| `India` | true | all lower | firstUpper + restLower | true |
| `india` | false | all lower | firstLower + restLower | true |
| `iNdia` | false | has upper (N) | none | false |
| `inDIA` | false | has upper | none | false |
| `INdia` | true | mixed (N up, dia low) | none | false |

**Key methods:** `toCharArray()` (String → char[]), `Character.isUpperCase(c)` (case check).
**Another approach (equals against upper/lower/title case):**
```java
boolean valid = country.equals(country.toUpperCase())      // INDIA
             || country.equals(country.toLowerCase())      // india
             || country.equals(country.substring(0,1).toUpperCase()
                              + country.substring(1).toLowerCase());  // India
```
**Streams (check rest all same case):**
```java
boolean firstUpper = Character.isUpperCase(country.charAt(0));
boolean restLower = country.chars().skip(1).allMatch(Character::isLowerCase);
boolean restUpper = country.chars().skip(1).allMatch(Character::isUpperCase);
boolean valid = (firstUpper && (restLower || restUpper)) || (!firstUpper && restLower);
```
Time O(n), space O(n) for the char array.

---

# When streams are NOT a good fit (say this in interviews)

Streams shine for **transform / filter / group / reduce** over collections. Avoid them when:
- You need **index pairs** (Two Sum) — loops are clearer.
- You need **early exit with running state** (sliding window, Kadane).
- You need a **stack/queue** (valid parentheses).
- You do **in-place mutation** (rotate, move zeros).
- You manipulate **linked-list pointers**.

**Rule of thumb:** stateful/index-driven/in-place → loop. Declarative transform/aggregate → streams.
