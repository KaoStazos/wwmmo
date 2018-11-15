package au.com.codeka.warworlds.client;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import au.com.codeka.warworlds.client.ctrl.DebugView;
import au.com.codeka.warworlds.client.game.starfield.StarfieldManager;
import au.com.codeka.warworlds.client.game.welcome.CreateEmpireScreen;
import au.com.codeka.warworlds.client.game.welcome.WarmWelcomeScreen;
import au.com.codeka.warworlds.client.game.welcome.WelcomeScreen;
import au.com.codeka.warworlds.client.opengl.RenderSurfaceView;
import au.com.codeka.warworlds.client.ui.ScreenStack;
import au.com.codeka.warworlds.client.util.GameSettings;
import au.com.codeka.warworlds.common.Log;

import static com.google.common.base.Preconditions.checkNotNull;

public class MainActivity extends AppCompatActivity {
  private static final Log log = new Log("MainActivity");

  // Will be non-null between of onCreate/onDestroy.
  @Nullable private StarfieldManager starfieldManager;

  // Will be non-null between onCreate/onDestroy.
  @Nullable private ScreenStack screenStack;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    setSupportActionBar(findViewById(R.id.toolbar));
    checkNotNull(getSupportActionBar()).hide();

    RenderSurfaceView renderSurfaceView = checkNotNull(findViewById(R.id.render_surface));
    renderSurfaceView.setRenderer();
    starfieldManager = new StarfieldManager(renderSurfaceView);
    starfieldManager.create();

    DebugView debugView = checkNotNull(findViewById(R.id.debug_view));
    debugView.setFrameCounter(renderSurfaceView.getFrameCounter());

    screenStack = new ScreenStack(this, findViewById(R.id.fragment_container));

    if (savedInstanceState != null) {
      // TODO: restore the view state?
    }
    if (!GameSettings.i.getBoolean(GameSettings.Key.WARM_WELCOME_SEEN)) {
      screenStack.push(new WarmWelcomeScreen());
    } else if (GameSettings.i.getString(GameSettings.Key.COOKIE).isEmpty()) {
      screenStack.push(new CreateEmpireScreen());
    } else {
      screenStack.push(new WelcomeScreen());
    }
  }

  @Override
  public void onBackPressed() {
    if (!screenStack.pop()) {
      super.onBackPressed();
    }
  }

  @NonNull
  public StarfieldManager getStarfieldManager() {
    return checkNotNull(starfieldManager);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    starfieldManager.destroy();
    starfieldManager = null;
  }
}
