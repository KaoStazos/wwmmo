package au.com.codeka.warworlds.server.cron;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeMap;

import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.data.SqlResult;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;
import au.com.codeka.warworlds.server.model.EmpireRank;

/**
 * Updates the ranks of empires.
 */
public class UpdateRanksCronJob extends CronJob {
    @Override
    public void run(String extra) throws Exception {
        TreeMap<Integer, EmpireRank> ranks = new TreeMap<Integer, EmpireRank>();

        String sql = "SELECT id AS empire_id FROM empires WHERE state <> 2";
        try (SqlStmt stmt = DB.prepare(sql)) {
            SqlResult res = stmt.select();
            while (res.next()) {
                EmpireRank rank = new EmpireRank(res);
                ranks.put(rank.getEmpireID(), rank);
            }
        }

        sql = "SELECT empire_id, SUM(num_ships) FROM fleets WHERE empire_id IS NOT NULL GROUP BY empire_id";
        try (SqlStmt stmt = DB.prepare(sql)) {
            SqlResult res = stmt.select();
            while (res.next()) {
                int empireID = res.getInt(1);
                long totalShips = res.getLong(2);
                if (!ranks.containsKey(empireID)) {
                    continue;
                }
                ranks.get(empireID).setTotalShips(totalShips);
            }
        }

        sql = "SELECT empire_id, COUNT(*) FROM buildings GROUP BY empire_id";
        try (SqlStmt stmt = DB.prepare(sql)) {
            SqlResult res = stmt.select();
            while (res.next()) {
                int empireID = res.getInt(1);
                long totalBuildings = res.getLong(2);
                if (!ranks.containsKey(empireID)) {
                    continue;
                }
                ranks.get(empireID).setTotalBuildings(totalBuildings);
            }
        }

        sql = "SELECT empire_id, COUNT(*), SUM(population) FROM colonies WHERE empire_id IS NOT NULL GROUP BY empire_id";
        try (SqlStmt stmt = DB.prepare(sql)) {
            SqlResult res = stmt.select();
            while (res.next()) {
                int empireID = res.getInt(1);
                long totalColonies = res.getLong(2);
                long totalPopulation = res.getLong(3);
                if (!ranks.containsKey(empireID)) {
                    continue;
                }
                ranks.get(empireID).setTotalColonies(totalColonies);
                ranks.get(empireID).setTotalPopulation(totalPopulation);
            }
        }

        sql = "SELECT empire_id, COUNT(*) FROM (" +
               " SELECT empire_id, star_id" +
               " FROM stars" +
               " INNER JOIN colonies ON colonies.star_id = stars.id" +
               " WHERE colonies.empire_id IS NOT NULL" +
               " GROUP BY empire_id, star_id" +
              ") AS stars" +
             " GROUP BY empire_id";
        try (SqlStmt stmt = DB.prepare(sql)) {
            SqlResult res = stmt.select();
            while (res.next()) {
                int empireID = res.getInt(1);
                long totalStars = res.getLong(2);
                if (!ranks.containsKey(empireID)) {
                    continue;
                }
                ranks.get(empireID).setTotalStars(totalStars);
            }
        }

        ArrayList<EmpireRank> sortedRanks = new ArrayList<EmpireRank>(ranks.values());
        Collections.sort(sortedRanks, new Comparator<EmpireRank>() {
            @Override
            public int compare(EmpireRank left, EmpireRank right) {
                long diff = right.getTotalPopulation() - left.getTotalPopulation();

                if (diff != 0)
                    return (int) diff;

                diff = right.getTotalColonies() - left.getTotalColonies();
                if (diff != 0)
                    return (int) diff;

                diff = right.getTotalStars() - left.getTotalStars();
                if (diff != 0)
                    return (int) diff;

                diff = right.getTotalShips() - left.getTotalShips();
                return (int) diff;
            }
        });

        try (Transaction t = DB.beginTransaction()) {
            sql = "DELETE FROM empire_ranks WHERE empire_id = ?";
            try (SqlStmt stmt = t.prepare(sql)) {
                for (EmpireRank rank : sortedRanks) {
                    stmt.setInt(1, rank.getEmpireID());
                    stmt.update();
                }
            }

            sql = "INSERT INTO empire_ranks (empire_id, rank, total_stars, total_colonies," +
                                           " total_buildings, total_ships, total_population)" +
                 " VALUES (?, ?, ?, ?, ?, ?, ?);";
            try (SqlStmt stmt = t.prepare(sql)) {
                int rankValue = 1;
                for (EmpireRank rank : sortedRanks) {
                    stmt.setInt(1, rank.getEmpireID());
                    stmt.setInt(2, rankValue);
                    stmt.setLong(3, rank.getTotalStars());
                    stmt.setLong(4, rank.getTotalColonies());
                    stmt.setLong(5, rank.getTotalBuildings());
                    stmt.setLong(6, rank.getTotalShips());
                    stmt.setLong(7, rank.getTotalPopulation());
                    stmt.update();

                    rankValue ++;
                }
            }

            t.commit();
        }
    }
}
