package com.mirakinetix.passwordguruji;

import java.nio.file.*;
import java.util.*;

/** Runs against the installed JAR and its bundled runtime with temporary synthetic data only. */
public final class PackagingProbe {
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("password-guruji-package-probe-");
        Path file = directory.resolve("dummy.pgv");
        char[] password = "synthetic-package-probe-123".toCharArray();
        try {
            VaultStore vault = new VaultStore(file);
            vault.create(password);
            require(vault.load(password).isEmpty(), "empty vault");
            vault.save(password,List.of(VaultEntry.create("dummy","test@example.invalid","dummy-password","","","Login")));
            require(vault.load(password).getFirst().password().equals("dummy-password"), "encrypted round trip");
            boolean rejected = false;
            try { vault.load("wrong".toCharArray()); } catch(java.security.GeneralSecurityException expected) { rejected = true; }
            require(rejected,"wrong password rejection");
            require(PasswordGenerator.generate(24,true,true,true,true).length()==24,"password generator");
            require(PasswordGenerator.passphrase(6,"-").split("-").length==6,"bundled word list");
            require(Totp.codeAt("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",59).equals("287082"),"TOTP vector");
            byte[] bytes=Files.readAllBytes(file); bytes[bytes.length-1]^=1; Files.write(file,bytes);
            rejected=false;
            try { vault.load(password); } catch(java.security.GeneralSecurityException expected) { rejected=true; }
            require(rejected,"tamper rejection");
            System.out.println("Installed runtime and installed JAR: 7 functional checks passed.");
        } finally {
            Arrays.fill(password,'\0');
            Files.deleteIfExists(file); Files.deleteIfExists(directory);
        }
    }
    private static void require(boolean condition,String label) {
        if(!condition) throw new AssertionError("Installed package failed: "+label);
    }
}
