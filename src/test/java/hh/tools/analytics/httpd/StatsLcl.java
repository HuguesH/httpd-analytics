package hh.tools.analytics.httpd;

/**
 * Created by Hugues on 14/03/2017.
 */
public class StatsLcl {
    String timestamp;
    String numcr;
    String profile;
    String user;
    String correlationId;
    String action;
    String log;

    public String toString() {
        StringBuilder strB = new StringBuilder();
        String[] times = timestamp.split(" ");
        strB.append(" ").append(times[0]).append(Application.CSV_SEP).append(" ").append(times[1]).append(Application.CSV_SEP).append(numcr).append(Application.CSV_SEP)
                .append(profile).append(Application.CSV_SEP).append(user).append(Application.CSV_SEP)
                .append(correlationId).append(Application.CSV_SEP).append(action).append(Application.CSV_SEP).append(log);
        return strB.toString();
    }

    public static StatsLcl getStatEntete() {
        StatsLcl statsEntete = new StatsLcl();
        statsEntete.timestamp = "Date Timestamp";
        statsEntete.numcr = "Num Dist";
        statsEntete.correlationId = "CorrelationId";
        statsEntete.user = "User";
        statsEntete.profile = "Profile";
        statsEntete.action = "action";
        statsEntete.log = "log";
        return statsEntete;
    }


}
