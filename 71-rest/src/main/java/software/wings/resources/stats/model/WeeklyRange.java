package software.wings.resources.stats.model;

import com.google.common.base.Preconditions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.harness.time.CalendarUtils;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import javax.annotation.Nullable;

@Value
@Slf4j
@JsonIgnoreProperties({"label", "inRange"})
public class WeeklyRange {
  @Nullable private String label;

  private String endDay;
  private String endTime;
  private String startDay;
  private String startTime;
  private String timeZone;

  @JsonCreator
  public WeeklyRange(@JsonProperty("label") @Nullable String label, @JsonProperty("endDay") String endDay,
      @JsonProperty("endTime") String endTime, @JsonProperty("startDay") String startDay,
      @JsonProperty("startTime") String startTime, @JsonProperty("timeZone") String timeZone) {
    Preconditions.checkArgument(preCheckArguments(startDay, startTime, endDay, endTime, timeZone),
        "Start Time should be strictly smaller than End Time");
    this.label = label;
    this.endDay = endDay;
    this.endTime = endTime;
    this.startDay = startDay;
    this.startTime = startTime;
    this.timeZone = timeZone;
  }

  private boolean preCheckArguments(String startDay, String startTime, String endDay, String endTime, String timeZone) {
    try {
      Calendar startCalendar = CalendarUtils.getCalendar(getDayEnum(startDay), startTime, timeZone);
      Calendar endCalendar = CalendarUtils.getCalendar(getDayEnum(endDay), endTime, timeZone);
      return startCalendar.before(endCalendar);
    } catch (Exception e) {
      logger.error("Error while verifying deployment window boundary prechecks" + e);
    }
    return false;
  }

  /**
   * @return boolean indicating if current point in time is the weekly window range
   */
  public boolean isInRange() {
    Calendar calendar = CalendarUtils.getCalendarForTimeZone(timeZone);

    int currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
    int currentHourOfDay = calendar.get(Calendar.HOUR_OF_DAY);

    calendar.set(Calendar.DAY_OF_WEEK, getDayEnum(startDay));
    int startingDayOfWindow = calendar.get(Calendar.DAY_OF_WEEK);
    calendar.setTime(CalendarUtils.getDate(startTime));
    int startingHourOfDay = calendar.get(Calendar.HOUR_OF_DAY);

    calendar.set(Calendar.DAY_OF_WEEK, getDayEnum(endDay));
    int endingDayOfWindow = calendar.get(Calendar.DAY_OF_WEEK);
    calendar.setTime(CalendarUtils.getDate(endTime));
    int endingHourOfDay = calendar.get(Calendar.HOUR_OF_DAY);

    return checkIfLiesInRange(startingDayOfWindow, endingDayOfWindow, currentDayOfWeek)
        && checkIfLiesInRange(startingHourOfDay, endingHourOfDay, currentHourOfDay);
  }

  /***
   * Get int corresponding to Calendar.DAY_OF_WEEK enum for day name
   * @param dayOfWeek
   * @return int corresponding to Calendar.DAY_OF_WEEK enum
   */
  private int getDayEnum(String dayOfWeek) {
    switch (dayOfWeek) {
      case "Sunday":
        return Calendar.SUNDAY;
      case "Monday":
        return Calendar.MONDAY;
      case "Tuesday":
        return Calendar.TUESDAY;
      case "Wednesday":
        return Calendar.WEDNESDAY;
      case "Thursday":
        return Calendar.THURSDAY;
      case "Friday":
        return Calendar.FRIDAY;
      case "Saturday":
        return Calendar.SATURDAY;
      default:
        return Integer.MAX_VALUE;
    }
  }

  private static boolean checkIfLiesInRange(long start, long end, long value) {
    return start <= value && value < end;
  }
}