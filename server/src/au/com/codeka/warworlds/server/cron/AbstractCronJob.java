package au.com.codeka.warworlds.server.cron;

import org.reflections.Reflections;

import java.util.Set;

/**
 * This is the base class for all cron jobs.
 */
public abstract class AbstractCronJob {
  /**
   * Run the job.
   * @param extra Extra parameters that can be configured.
   * @return A string that'll be display to an admin to indicate the status of this run.
   */
  public abstract String run(String extra) throws Exception;

  public static Set<Class<?>> findAllJobClasses() {
    return new Reflections("au.com.codeka.warworlds.server")
        .getTypesAnnotatedWith(CronJob.class);
  }

}