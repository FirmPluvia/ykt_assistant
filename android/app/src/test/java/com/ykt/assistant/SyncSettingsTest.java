package com.ykt.assistant;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28)
public class SyncSettingsTest {
    AccountStore store;SyncSettings settings;
    @Before public void setup()throws Exception{android.content.Context c=RuntimeEnvironment.getApplication();c.getSharedPreferences("saved_accounts",0).edit().clear().commit();c.getSharedPreferences("request_sync",0).edit().clear().commit();store=new AccountStore(c);add(0,"self");settings=new SyncSettings(c,store,0,"0|self");}
    void add(int service,String uid)throws Exception{store.remember(service,RainApi.object("user_id",uid,"name",uid),"[]");}
    @Test public void singleAccountHasNoSynchronizationAndSwitchesDefaultOff()throws Exception{assertFalse(settings.available());assertFalse(settings.enabled(SyncSettings.CHECKIN));assertFalse(settings.enabled(SyncSettings.ANSWER));assertFalse(settings.enabled(SyncSettings.SUBJECTIVE));assertFalse(settings.independentSubjective());assertFalse(settings.resultCards());add(0,"b");assertTrue(settings.available());assertEquals(1,settings.selected(SyncSettings.ANSWER).size());}
    @Test public void allRecipientsRemainDynamicAndExcludeCurrentAndOtherServers()throws Exception{add(0,"b");add(1,"x");assertEquals(1,settings.selected(SyncSettings.ANSWER).size());add(0,"c");assertEquals(2,settings.selected(SyncSettings.ANSWER).size());store.remove("0|b");assertEquals("0|c",settings.selected(SyncSettings.ANSWER).get(0).key);}
    @Test public void editedSelectionPersistsAndEmptySelectionDoesNotBecomeAll()throws Exception{add(0,"b");add(0,"c");settings.select(SyncSettings.ANSWER,Collections.singleton("0|b"));add(0,"d");assertEquals(1,settings.selected(SyncSettings.ANSWER).size());settings.select(SyncSettings.ANSWER,Collections.emptySet());assertTrue(settings.selected(SyncSettings.ANSWER).isEmpty());assertEquals(3,settings.selected(SyncSettings.CHECKIN).size());}
    @Test public void subjectiveSharesAnswerSwitchAndCanEnableIndependentText()throws Exception{add(0,"b");settings.enabled(SyncSettings.ANSWER,true);assertTrue(settings.enabled(SyncSettings.SUBJECTIVE));assertFalse(settings.independentSubjective());settings.independentSubjective(true);assertTrue(settings.independentSubjective());settings.independentSubjective(false);assertFalse(settings.independentSubjective());settings.enabled(SyncSettings.SUBJECTIVE,true);assertTrue(settings.enabled(SyncSettings.ANSWER));assertTrue(settings.enabled(SyncSettings.SUBJECTIVE));settings.enabled(SyncSettings.ANSWER,false);assertFalse(settings.enabled(SyncSettings.SUBJECTIVE));}
    @Test public void anotherActiveAccountDoesNotInheritOptInOrRecipientSubset()throws Exception{add(0,"b");add(0,"c");settings.enabled(SyncSettings.ANSWER,true);settings.select(SyncSettings.ANSWER,Collections.singleton("0|b"));SyncSettings other=new SyncSettings(RuntimeEnvironment.getApplication(),store,0,"0|b");assertFalse(other.enabled(SyncSettings.ANSWER));assertEquals(2,other.selected(SyncSettings.ANSWER).size());}
    @Test public void resultCardsDefaultOffAndPreferenceIsAccountScoped()throws Exception{add(0,"b");assertFalse(settings.resultCards());settings.resultCards(true);SyncSettings restored=new SyncSettings(RuntimeEnvironment.getApplication(),store,0,"0|self");assertTrue(restored.resultCards());assertFalse(new SyncSettings(RuntimeEnvironment.getApplication(),store,0,"0|b").resultCards());}
}
