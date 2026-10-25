package com.mirakinetix.passwordguruji;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

final class PasswordGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}:,.?";
    private static final String[] WORDS = loadWordList();

    private PasswordGenerator() {}

    static String generate(int length, boolean upper, boolean lower, boolean digits, boolean symbols) {
        return generate(length, upper, lower, digits, symbols, "");
    }

    static String generate(int length, boolean upper, boolean lower, boolean digits, boolean symbols,
                           String excludedCharacters) {
        String excluded = excludedCharacters == null ? "" : excludedCharacters;
        List<String> groups = new ArrayList<>();
        if (upper) groups.add(without(UPPER, excluded));
        if (lower) groups.add(without(LOWER, excluded));
        if (digits) groups.add(without(DIGITS, excluded));
        if (symbols) groups.add(without(SYMBOLS, excluded));
        if (groups.isEmpty()) throw new IllegalArgumentException("Choose at least one character type.");
        if (groups.stream().anyMatch(String::isEmpty))
            throw new IllegalArgumentException("The exclusions remove every character from a selected type.");
        if (length < groups.size() || length > 128)
            throw new IllegalArgumentException("Length must fit the selected character types and be 128 or fewer.");
        StringBuilder alphabet = new StringBuilder();
        List<Character> chars = new ArrayList<>(length);
        for (String group : groups) {
            alphabet.append(group);
            chars.add(group.charAt(RANDOM.nextInt(group.length())));
        }
        while (chars.size() < length) chars.add(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        for (int i=chars.size()-1; i>0; i--) {
            int j = RANDOM.nextInt(i+1); char v=chars.get(i); chars.set(i, chars.get(j)); chars.set(j, v);
        }
        StringBuilder result = new StringBuilder(length);
        chars.forEach(result::append);
        return result.toString();
    }

    static String passphrase(int count, String separator) {
        if (count < 6 || count > 12) throw new IllegalArgumentException("Choose between 6 and 12 words.");
        StringBuilder result = new StringBuilder();
        for (int i=0; i<count; i++) {
            if (i > 0) result.append(separator);
            result.append(WORDS[RANDOM.nextInt(WORDS.length)]);
        }
        return result.toString();
    }

    static int characterPoolSize(boolean upper,boolean lower,boolean digits,boolean symbols) {
        return characterPoolSize(upper,lower,digits,symbols,"");
    }

    static int characterPoolSize(boolean upper,boolean lower,boolean digits,boolean symbols,String excludedCharacters) {
        String excluded=excludedCharacters==null?"":excludedCharacters;
        return (upper?without(UPPER,excluded).length():0)+(lower?without(LOWER,excluded).length():0)
                +(digits?without(DIGITS,excluded).length():0)+(symbols?without(SYMBOLS,excluded).length():0);
    }

    private static String without(String group,String excluded) {
        StringBuilder allowed=new StringBuilder(group.length());
        for(int i=0;i<group.length();i++)if(excluded.indexOf(group.charAt(i))<0)allowed.append(group.charAt(i));
        return allowed.toString();
    }

    private static String[] loadWordList() {
        String resource="/com/mirakinetix/passwordguruji/eff-large-wordlist.txt";
        try(InputStream stream=PasswordGenerator.class.getResourceAsStream(resource)){
            if(stream==null)throw new IllegalStateException("Bundled passphrase word list is missing.");
            List<String> words=new ArrayList<>(7776);
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
                String line;
                while((line=reader.readLine())!=null){
                    int tab=line.indexOf('	');
                    if(tab<1||tab==line.length()-1)throw new IllegalStateException("Bundled passphrase word list is invalid.");
                    words.add(line.substring(tab+1).trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
            if(words.size()!=7776||new java.util.HashSet<>(words).size()!=7776)
                throw new IllegalStateException("Bundled passphrase word list must contain 7,776 unique words.");
            return words.toArray(String[]::new);
        }catch(IOException ex){throw new ExceptionInInitializerError(ex);}
    }

    static int wordCount() { return WORDS.length; }
}
