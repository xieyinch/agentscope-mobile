package cn.qwenpaw.android;

import org.json.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class AccountsTest {
    private final JSONObject alice=Accounts.create("a","","https://paw.example.com","alice"),bob=Accounts.create("b"," 家里 ","http://10.0.0.2:8088/team/","bob");
    @Test public void newAccountsStartWithDefaultWorkspaceAndNoSavedPassword(){
        assertEquals("default",Json.str(alice,"agent"));assertEquals("",Json.str(alice,"lastChat"));
        assertFalse(alice.optBoolean("remember"));assertFalse(alice.optBoolean("anonymous"));assertEquals("家里",Json.str(bob,"name"));
    }
    @Test public void savedListDropsEntriesWithoutIdOrServer(){
        JSONArray list=Accounts.parse(Json.arr(alice,Json.obj("id","x"),Json.obj("base","https://a"),"text",bob).toString());
        assertEquals(2,list.length());assertSame(null,Accounts.find(list,"x"));
        assertEquals(0,Accounts.parse("not json").length());assertEquals(0,Accounts.parse(null).length());
    }
    @Test public void sameServerAndUserIsMatchedInsteadOfDuplicated(){
        JSONArray list=Json.arr(alice,bob);
        assertEquals("a",Json.str(Accounts.match(list,"https://paw.example.com","alice"),"id"));
        assertNull(Accounts.match(list,"https://paw.example.com","bob"));
        assertNull(Accounts.match(list,"https://paw.example.com:8443","alice"));
        assertNull(Accounts.find(list,""));assertNull(Accounts.find(list,null));
    }
    @Test public void removingKeepsOtherAccountsInOrder(){
        JSONArray rest=Accounts.without(Json.arr(alice,bob,Accounts.create("c","","https://c.example","")),"b");
        assertEquals(2,rest.length());assertEquals("a",Json.str(rest.optJSONObject(0),"id"));assertEquals("c",Json.str(rest.optJSONObject(1),"id"));
    }
    @Test public void labelsPreferNameThenUserAndServer(){
        assertEquals("家里",Accounts.label(bob));
        assertEquals("alice @ paw.example.com",Accounts.label(alice));
        assertEquals("10.0.0.2:8088/team",Accounts.host("http://10.0.0.2:8088/team/"));
        assertEquals("paw.example.com",Accounts.host("https://paw.example.com:443"));
        JSONObject open=Accounts.create("o","","http://127.0.0.1:8088","default");Json.put(open,"anonymous",true);
        assertEquals("127.0.0.1:8088",Accounts.label(open));assertEquals("",Accounts.label(null));
        assertEquals("not a url",Accounts.host("not a url"));
    }
    @Test public void legacySettingsMoveUnderAccountId(){
        List<String> keys=List.of("https://paw.example.com|alice|default|draft|draft","https://paw.example.com|alice|default|chat-1|level","https://paw.example.com|bob|default|draft|draft","https://paw.example.com|alice|","accounts");
        Map<String,String> moved=Accounts.legacyKeys(keys,"https://paw.example.com","alice","a");
        assertEquals(Map.of("https://paw.example.com|alice|default|draft|draft","acct:a|default|draft|draft","https://paw.example.com|alice|default|chat-1|level","acct:a|default|chat-1|level"),moved);
        assertTrue(Accounts.settingPrefix("a").startsWith("acct:a|"));assertFalse(Accounts.settingPrefix("a").startsWith(Accounts.settingPrefix("ab")));
    }
}
