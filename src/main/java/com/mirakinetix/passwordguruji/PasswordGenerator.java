package com.mirakinetix.passwordguruji;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

final class PasswordGenerator {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{}:,.?";
    private static final String[] WORDS = {
        "acorn","amber","anchor","apple","april","arch","arrow","artist","atlas","autumn",
        "bamboo","beacon","birch","blossom","blue","breeze","brook","cabin","cactus","candle",
        "canvas","cedar","circle","cobalt","comet","coral","crane","crystal","daisy","delta",
        "dune","eagle","earth","echo","ember","falcon","fern","field","finch","flame","forest",
        "fossil","frost","garden","glacier","golden","granite","harbor","hazel","honey","island",
        "ivory","jasmine","juniper","kettle","lagoon","lantern","laurel","lemon","lilac","lotus",
        "maple","marble","meadow","meteor","mint","monarch","moss","mountain","nectar","ocean",
        "olive","onyx","orchid","otter","panda","paper","pebble","pepper","phoenix","piano",
        "pine","planet","plum","pocket","prairie","quartz","rabbit","rainbow","raven","river",
        "robin","rocket","saffron","sailor","silver","spruce","star","sunset","tiger","timber",
        "topaz","velvet","violet","willow","winter","zephyr"
    };

    private PasswordGenerator() {}

    static String generate(int length, boolean upper, boolean lower, boolean digits, boolean symbols) {
        List<String> groups = new ArrayList<>();
        if (upper) groups.add(UPPER);
        if (lower) groups.add(LOWER);
        if (digits) groups.add(DIGITS);
        if (symbols) groups.add(SYMBOLS);
        if (groups.isEmpty()) throw new IllegalArgumentException("Choose at least one character type.");
        if (length < groups.size()) throw new IllegalArgumentException("Length must be at least the selected character types.");
        StringBuilder alphabet = new StringBuilder();
        List<Character> chars = new ArrayList<>(length);
        for (String group : groups) { alphabet.append(group); chars.add(group.charAt(RANDOM.nextInt(group.length()))); }
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
}
