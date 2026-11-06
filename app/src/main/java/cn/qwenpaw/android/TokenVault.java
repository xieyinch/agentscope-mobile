package cn.qwenpaw.android;
import io.github.xieyinch.agentscope.R;
import io.github.xieyinch.agentscope.BuildConfig;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Per-account secrets, encrypted with a non-exportable device key. Tokens are always kept;
 * passwords only when the user opts in for that account. The prefs file is excluded from backups.
 */
public final class TokenVault {
    private final Context context;
    public TokenVault(Context context) { this.context=context; }
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore"); ks.load(null);
        if (!ks.containsAlias("qwenpaw-token")) {
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("qwenpaw-token",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            g.generateKey();
        }
        return (javax.crypto.SecretKey)ks.getKey("qwenpaw-token",null);
    }
    private SharedPreferences prefs() { return context.getSharedPreferences("vault",0); }
    private void save(String name,String value) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key());
        prefs().edit()
            .putString(name+".iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP))
            .putString(name,Base64.encodeToString(c.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Base64.NO_WRAP)).apply();
    }
    private String read(String name) {
        try {
            var p=prefs(); String t=p.getString(name,""); if(t.isEmpty()) return "";
            Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(p.getString(name+".iv",""),Base64.NO_WRAP)));
            return new String(c.doFinal(Base64.decode(t,Base64.NO_WRAP)),java.nio.charset.StandardCharsets.UTF_8);
        } catch(Exception e) { remove(name); return ""; }
    }
    private void remove(String name) { prefs().edit().remove(name).remove(name+".iv").apply(); }
    public void saveToken(String account,String token) throws Exception { save("token:"+account,token); }
    public String readToken(String account) { return read("token:"+account); }
    public boolean hasToken(String account) { return !prefs().getString("token:"+account,"").isEmpty(); }
    public void clearToken(String account) { remove("token:"+account); }
    public void savePassword(String account,String password) throws Exception { save("password:"+account,password); }
    public String readPassword(String account) { return read("password:"+account); }
    public void clearPassword(String account) { remove("password:"+account); }
    public void forget(String account) { clearToken(account); clearPassword(account); }
    /** Moves the single-account token entry under an account id without decrypting it. */
    public void migrateLegacy(String account) {
        var p=prefs(); String token=p.getString("token",""), iv=p.getString("iv","");
        var edit=p.edit().remove("token").remove("iv");
        if(!token.isEmpty() && !iv.isEmpty()) edit.putString("token:"+account,token).putString("token:"+account+".iv",iv);
        edit.apply();
    }
}
