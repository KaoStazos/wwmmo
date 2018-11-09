package au.com.codeka.warworlds.ctrl;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import androidx.fragment.app.Fragment;
import au.com.codeka.warworlds.R;

/**
 * A {@link Fragment} that loads and displays preferences.
 *
 * Taken from http://www.michenux.net/android-preferencefragmentcompat-906.html and cleaned up a
 * bit.
 */
public abstract class PreferenceFragment extends Fragment {
  private static final int FIRST_REQUEST_CODE = 100;
  private static final String PREFERENCES_TAG = "android:preferences";

  private boolean havePrefs;
  private boolean initDone;
  private ListView list;
  private PreferenceManager preferenceManager;
  private final Handler handler = new Handler();

  final private Runnable requestFocus = new Runnable() {
    public void run() {
      list.focusableViewAvailable(list);
    }
  };

  private void bindPreferences() {
    PreferenceScreen localPreferenceScreen = getPreferenceScreen();
    if (localPreferenceScreen != null) {
      ListView localListView = getListView();
      localPreferenceScreen.bind(localListView);
    }
  }

  private void ensureList() {
    if (list == null) {
      View view = getView();
      if (view == null) {
        throw new IllegalStateException("Content view not yet created");
      }

      View listView = view.findViewById(android.R.id.list);
      if (listView == null) {
        throw new RuntimeException(
            "Your content must have a ListView whose id attribute is 'android.R.id.list'");
      }
      if (!(listView instanceof ListView)) {
        throw new RuntimeException(
            "Content has view with id attribute 'android.R.id.list' that is not a ListView class");
      }
      list = (ListView) listView;

      handler.post(requestFocus);
    }
  }

  private void postBindPreferences() {
    handler.post(new Runnable() {
      @Override
      public void run() {
        bindPreferences();
      }
    });
  }

  private void requirePreferenceManager() {
    if (this.preferenceManager == null) {
      throw new RuntimeException("This should be called after super.onCreate.");
    }
  }

  public void addPreferencesFromResource(int resId) {
    requirePreferenceManager();
    PreferenceScreen screen = inflateFromResource(getActivity(), resId, getPreferenceScreen());
    setPreferenceScreen(screen);
  }

  public Preference findPreference(CharSequence key) {
    if (preferenceManager == null) {
      return null;
    }
    return preferenceManager.findPreference(key);
  }

  public ListView getListView() {
    ensureList();
    return list;
  }

  public PreferenceManager getPreferenceManager() {
    return preferenceManager;
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
    getListView().setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
    if (havePrefs) {
      bindPreferences();
    }
    initDone = true;
    if (savedInstanceState != null) {
      Bundle localBundle = savedInstanceState.getBundle(PREFERENCES_TAG);
      if (localBundle != null) {
        PreferenceScreen screen = getPreferenceScreen();
        if (screen != null) {
          screen.restoreHierarchyState(localBundle);
        }
      }
    }
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    dispatchActivityResult(requestCode, resultCode, data);
  }

  @Override
  public void onCreate(Bundle paramBundle) {
    super.onCreate(paramBundle);
    preferenceManager = createPreferenceManager();
  }

  @Override
  public View onCreateView(LayoutInflater paramLayoutInflater, ViewGroup paramViewGroup,
      Bundle paramBundle) {
    return paramLayoutInflater.inflate(R.layout.preference_list, paramViewGroup, false);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    dispatchActivityDestroy();
  }

  @Override
  public void onDestroyView() {
    list = null;
    handler.removeCallbacks(requestFocus);
    super.onDestroyView();
  }

  @Override
  public void onSaveInstanceState(Bundle bundle) {
    super.onSaveInstanceState(bundle);
    PreferenceScreen screen = getPreferenceScreen();
    if (screen != null) {
      Bundle localBundle = new Bundle();
      screen.saveHierarchyState(localBundle);
      bundle.putBundle(PREFERENCES_TAG, localBundle);
    }
  }

  @Override
  public void onStop() {
    super.onStop();
    dispatchActivityStop();
  }

  /** Access methods with visibility private **/

  private PreferenceManager createPreferenceManager() {
    try {
      Constructor<PreferenceManager> c = PreferenceManager.class.getDeclaredConstructor(
          Activity.class, int.class);
      c.setAccessible(true);
      return c.newInstance(this.getActivity(), FIRST_REQUEST_CODE);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private PreferenceScreen getPreferenceScreen() {
    try {
      Method m = PreferenceManager.class.getDeclaredMethod("getPreferenceScreen");
      m.setAccessible(true);
      return (PreferenceScreen) m.invoke(preferenceManager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void setPreferenceScreen(PreferenceScreen preferenceScreen) {
    try {
      Method m = PreferenceManager.class.getDeclaredMethod(
          "setPreferences", PreferenceScreen.class);
      m.setAccessible(true);
      boolean result = (Boolean) m.invoke(preferenceManager, preferenceScreen);
      if (result && preferenceScreen != null) {
        havePrefs = true;
        if (initDone) {
          postBindPreferences();
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void dispatchActivityResult(int requestCode, int resultCode, Intent data) {
    try {
      Method m = PreferenceManager.class.getDeclaredMethod(
          "dispatchActivityResult", int.class, int.class, Intent.class);
      m.setAccessible(true);
      m.invoke(preferenceManager, requestCode, resultCode, data);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void dispatchActivityDestroy() {
    try {
      Method m = PreferenceManager.class.getDeclaredMethod("dispatchActivityDestroy");
      m.setAccessible(true);
      m.invoke(preferenceManager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void dispatchActivityStop() {
    try {
      Method m = PreferenceManager.class.getDeclaredMethod("dispatchActivityStop");
      m.setAccessible(true);
      m.invoke(preferenceManager);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public PreferenceScreen inflateFromResource(Context context, int resId,
      PreferenceScreen rootPreferences) {
    PreferenceScreen preferenceScreen ;
    try {
      Method m = PreferenceManager.class.getDeclaredMethod(
          "inflateFromResource", Context.class, int.class, PreferenceScreen.class);
      m.setAccessible(true);
      preferenceScreen = (PreferenceScreen) m.invoke(
          preferenceManager, context, resId, rootPreferences);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return preferenceScreen;
  }
}