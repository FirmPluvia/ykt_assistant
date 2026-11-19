package com.ykt.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

/** Account-scoped opt-ins. Unedited selections dynamically include all eligible other accounts. */
final class SyncSettings {
    static final String CHECKIN="checkin",ANSWER="answer",SUBJECTIVE="subjective";
    final AccountStore store;final int service;final String active;private final SharedPreferences prefs;private final String scope;
    SyncSettings(Context c,AccountStore store,int service,String active){this.store=store;this.service=service;this.active=active;prefs=c.getSharedPreferences("request_sync",0);scope=active+":";}
    boolean available(){return store.list().size()>1&&store.get(active)!=null;}
    List<AccountStore.Account> eligible(){List<AccountStore.Account> out=new ArrayList<>();for(AccountStore.Account a:store.list())if(a.service==service&&!a.key.equals(active))out.add(a);return out;}
    boolean enabled(String kind){return prefs.getBoolean(scope+(SUBJECTIVE.equals(kind)?ANSWER:kind),false);}
    void enabled(String kind,boolean value){prefs.edit().putBoolean(scope+(SUBJECTIVE.equals(kind)?ANSWER:kind),value).apply();}
    boolean independentSubjective(){return prefs.getBoolean(scope+"subjective_separate",false);}
    void independentSubjective(boolean value){prefs.edit().putBoolean(scope+"subjective_separate",value).apply();}
    boolean resultCards(){return prefs.getBoolean(scope+"result_cards",false);}
    void resultCards(boolean value){prefs.edit().putBoolean(scope+"result_cards",value).apply();}
    private String selectionKey(String kind){return scope+"selected:"+(SUBJECTIVE.equals(kind)?ANSWER:kind);}
    List<AccountStore.Account> selected(String kind){String key=selectionKey(kind);Set<String> saved=prefs.contains(key)?prefs.getStringSet(key,Collections.emptySet()):null;List<AccountStore.Account> out=new ArrayList<>();for(AccountStore.Account a:eligible())if(saved==null||saved.contains(a.key))out.add(a);return out;}
    void select(String kind,Collection<String> keys){Set<String> valid=new HashSet<>();for(AccountStore.Account a:eligible())if(keys.contains(a.key))valid.add(a.key);SharedPreferences.Editor e=prefs.edit();if(valid.size()==eligible().size())e.remove(selectionKey(kind));else e.putStringSet(selectionKey(kind),valid);e.apply();}
}
