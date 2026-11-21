package com.mirakinetix.passwordguruji;

final class MasterPasswordPolicy {
    private MasterPasswordPolicy() {}
    static int minimum(boolean demonstration) { return demonstration ? 6 : 15; }
    static boolean validNew(char[] password, boolean demonstration) {
        return password != null && Character.codePointCount(password,0,password.length)>=minimum(demonstration)
            && password.length<=1024;
    }
    static void requireNew(char[] password, boolean demonstration) {
        if(!validNew(password,demonstration)) throw new IllegalArgumentException("Use at least "+minimum(demonstration)+" characters (maximum 1,024 UTF-16 units). Existing vault passwords remain valid for unlocking.");
    }
}
