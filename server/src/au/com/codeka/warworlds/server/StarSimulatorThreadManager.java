package au.com.codeka.warworlds.server;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

import au.com.codeka.common.Log;
import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.data.SqlResult;
import au.com.codeka.warworlds.server.data.SqlStmt;

public class StarSimulatorThreadManager {
  public static final StarSimulatorThreadManager i = new StarSimulatorThreadManager();

  // Time, in milliseconds, between logs of the simulation stats.
  private static final long STATS_LOG_DELAY_MS = 10000;

  private static final Log log = new Log("SIM");

  private final ArrayList<StarSimulatorThread> threads = new ArrayList<>();
  private final Queue<Integer> starIDs = new ArrayDeque<>();
  private final Set<Integer> lastStarIDs = new HashSet<>();
  private final Object lock = new Object();
  private boolean stopped;
  private boolean paused;
  private final Thread monitorThread = new Thread(this::threadMonitor);

  public void start() {
    stopped = false;
    for (int i = 0; i < Configuration.i.getNumStarSimulationThreads(); i++) {
      StarSimulatorThread thread = new StarSimulatorThread(this);
      thread.start();
      threads.add(thread);
    }
    monitorThread.start();
    log.info("Started %d star simulation threads.", threads.size());
  }

  /**
   * Pause the {@link StarSimulatorThread}s. Note the threads will take at least 10 minutes before
   * the restart after being paused, so only pause if it's intended to be for a reasonably long time
   * anyway.
   */
  public void pause() {
    log.info("Pausing star simulations.");
    paused = true;
  }

  public void resume() {
    log.info("Resuming star simulations.");
    paused = false;
  }

  public void stop() {
    stopped = true;
    // Note: we don't wait for the monitor thread to stop, don't really care if it goes for a bit
    // longer, it'll stop itself when it notices stopped is false.
    for (StarSimulatorThread thread : threads) {
      thread.stop();
    }
    starIDs.clear();
  }

  public boolean hasMoreStarsToSimulate() {
    synchronized (lock) {
      return !starIDs.isEmpty();
    }
  }

  /** Returns the ID of the next star to simulate. */
  public int getNextStar() {
    synchronized(lock) {
      if (paused) {
        return 0;
      }

      if (starIDs.isEmpty()) {
        // Grab 50 stars at a time, to save all those queries.
        String sql =
            "SELECT id FROM stars WHERE empire_count > 0 ORDER BY last_simulation ASC LIMIT 50";
        try (SqlStmt stmt = DB.prepare(sql)) {
          SqlResult res = stmt.select();
          while (res.next()) {
            int starID = res.getInt(1);
            // If this starID was handed out in the last set, it's possible that another thread
            // is still simulating it. Ignore it for now, and wait for the next time around.
            if (lastStarIDs.contains(starID)) {
              continue;
            }
            starIDs.add(starID);
          }
        } catch (Exception e) {
          log.error("Error fetching starIDs to simulate.", e);
        }

        // clear out the lastStarIDs set and start afresh with this new batch.
        lastStarIDs.clear();
      }

      if (starIDs.isEmpty()) {
        log.info("Got an empty set, no stars to simulate.");
        return 0;
      }
      int starID = starIDs.remove();
      lastStarIDs.add(starID);
      return starID;
    }
  }

  /**
   * A function that runs in it's own thread whose job is to monitor the star simulation thread(s),
   * periodically log their status, and make sure they're not stuck.
   */
  private void threadMonitor() {
    int nothingInterestingCounter = 0;
    while (!stopped) {
      try {
        int i = 0;
        for (StarSimulatorThread thread : threads) {
          StarSimulatorThread.ProcessingStats stats = thread.stats();
          if (stats.numStars == 0 && stats.currentStar == null) {
            // Nothing interesting to report.
            if (nothingInterestingCounter > 10) {
              log.info("Nothing to report.%s", paused ? " (simulations paused)" : "");
              nothingInterestingCounter = 0;
            }
            nothingInterestingCounter ++;
            continue;
          }

          String currStarMsg = stats.currentStar == null
              ? "(no current star)"
              : String.format(Locale.ENGLISH,
                  "([%d] %s for %dms)",
                  stats.currentStar.getID(),
                  stats.currentStar.getName(),
                  stats.currentStarProcessingTime);
          log.info("[%d] %d stars, %dms avg, %d avg in db, %.0fs idle, %s",
              i, stats.numStars, stats.numStars == 0 ? 0 : stats.totalTimeMs / stats.numStars,
              stats.numStars == 0 ? 0 : stats.dbTimeMs / stats.numStars,
              (float) stats.idleTimeMs / 1000.0f, currStarMsg);

          // TODO: if it appears currentStar is stuck, do something...

          i++;
        }

        try {
          Thread.sleep(STATS_LOG_DELAY_MS);
        } catch (InterruptedException e) {
          log.warning("Got InterruptedException waiting for next round.");
        }
      } catch (Throwable e) {
        log.error("Error in monitor thread.", e);
      }
    }
  }
}
