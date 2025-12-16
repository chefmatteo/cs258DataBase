import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;

import java.time.LocalDateTime;
import java.sql.Timestamp;
import java.util.Vector;
import java.util.Arrays;
import java.util.Comparator;

public class GigSystem {

    public static void main(String[] args) {

        // You should only need to fetch the connection details once
        // Automatically tries socket connection first, falls back to port connection
        Connection conn = getConnection();

        boolean repeatMenu = true;

        while(repeatMenu){
            System.out.println("_________________________");
            System.out.println("________GigSystem________");
            System.out.println("_________________________");
            System.out.println("1: View Gig Schedule");
            System.out.println("2: Create New Gig");
            System.out.println("q: Quit");

            String menuChoice = readEntry("Please choose an option: ");

            if(menuChoice.length() == 0){
                //Nothing was typed (user just pressed enter) so start the loop again
                continue;
            }
            char option = menuChoice.charAt(0);

            /**
             * If you are going to implement a menu, you must read input before you call the actual methods
             * Do not read input from any of the actual task methods
             */
            switch(option){
                case '1':
                    // Read gigID from user
                    String gigIDInput = readEntry("Enter gig ID: ");
                    try {
                        int gigID = Integer.parseInt(gigIDInput);
                        // Call task1 to get the schedule
                        String[][] schedule = task1(conn, gigID);
                        
                        // Display results
                        if (schedule != null && schedule.length > 0) {
                            System.out.println("\nGig Schedule for Gig ID " + gigID + ":");
                            printTable(schedule);
                        } else {
                            System.out.println("No schedule found for gig ID " + gigID + ".");
                        }
                    } catch (NumberFormatException e) {
                        System.out.println("Invalid gig ID. Please enter a number.");
                    }
                    break;

                case '2':
                    // Task 2: Create New Gig
                    try {
                        String venueName = readEntry("Enter venue name: ");
                        String gigTitle = readEntry("Enter gig title: ");
                        String gigDateInput = readEntry("Enter gig date and time (YYYY-MM-DD HH:MM): ");
                        String ticketPriceInput = readEntry("Enter adult ticket price: ");
                        String numActsInput = readEntry("Enter number of acts: ");
                        
                        // Parse gig start time
                        LocalDateTime gigStart = LocalDateTime.parse(gigDateInput.replace(" ", "T"));
                        int adultTicketPrice = Integer.parseInt(ticketPriceInput);
                        int numActs = Integer.parseInt(numActsInput);
                        
                        // Read act details
                        ActPerformanceDetails[] actDetails = new ActPerformanceDetails[numActs];
                        for (int i = 0; i < numActs; i++) {
                            System.out.println("\nAct " + (i + 1) + ":");
                            String actIdInput = readEntry("  Act ID: ");
                            String feeInput = readEntry("  Fee: ");
                            String actTimeInput = readEntry("  Start time (YYYY-MM-DD HH:MM): ");
                            String durationInput = readEntry("  Duration (minutes): ");
                            
                            int actId = Integer.parseInt(actIdInput);
                            int fee = Integer.parseInt(feeInput);
                            LocalDateTime actTime = LocalDateTime.parse(actTimeInput.replace(" ", "T"));
                            int duration = Integer.parseInt(durationInput);
                            
                            actDetails[i] = new ActPerformanceDetails(actId, fee, actTime, duration);
                        }
                        
                        // Call task2
                        task2(conn, venueName, gigTitle, gigStart, adultTicketPrice, actDetails);
                        System.out.println("Gig creation attempted. Check database or use option 1 to view schedule.");
                        
                    } catch (Exception e) {
                        System.out.println("Error: " + e.getMessage());
                        e.printStackTrace();
                    }
                    break;
                case '3':
                    break;
                case '4':
                    break;
                case '5':
                    break;
                case '6':
                    break;
                case '7':
                    break;
                case '8':
                    break;
                case 'q':
                    repeatMenu = false;
                    break;
                default: 
                    System.out.println("Invalid option");
            }
        }
    }

    /*
     * You should not change the names, input parameters or return types of any of the predefined methods in GigSystem.java
     * You may add extra methods if you wish (and you may overload the existing methods - as long as the original version is implemented)
     */

    // Helper Methods (for Task implementations)
    
    // Helper method to get venue ID by name
    private static int getVenueId(Connection conn, String venueName) throws SQLException {
        String sql = "SELECT venueid FROM VENUE WHERE venuename = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, venueName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("venueid");
                }
            }
        }
        return -1; // Venue not found
    }
    
    // Helper method to check if act exists
    private static boolean actExists(Connection conn, int actId) throws SQLException {
        String sql = "SELECT 1 FROM ACT WHERE actid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, actId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    // Helper method to get act genre
    private static String getActGenre(Connection conn, int actId) throws SQLException {
        String sql = "SELECT genre FROM ACT WHERE actid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, actId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("genre");
                }
            }
        }
        return null;
    }
    
    // Helper method to insert GIG record
    private static int insertGig(Connection conn, int venueId, String gigTitle, LocalDateTime gigStart) throws SQLException {
        String sql = "INSERT INTO GIG (venueid, gigtitle, gigdatetime, gigstatus) VALUES (?, ?, ?, 'G') RETURNING gigid";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, venueId);
            stmt.setString(2, gigTitle);
            stmt.setTimestamp(3, Timestamp.valueOf(gigStart));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("gigid");
                }
            }
        }
        return -1; // Failed to insert
    }
    
    // Helper method to insert ACT_GIG record
    // Throws SQLException if insert fails (e.g., business rule violation by trigger)
    private static void insertActGig(Connection conn, int actId, int gigId, int fee, LocalDateTime onTime, int duration) throws SQLException {
        String sql = "INSERT INTO ACT_GIG (actid, gigid, actgigfee, ontime, duration) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, actId);
            stmt.setInt(2, gigId);
            stmt.setInt(3, fee);
            stmt.setTimestamp(4, Timestamp.valueOf(onTime));
            stmt.setInt(5, duration);
            stmt.executeUpdate();
            // If trigger raises exception for business rule violation, it will propagate up
        }
    }
    
    // Helper method to insert GIG_TICKET record
    private static boolean insertGigTicket(Connection conn, int gigId, char priceType, int price) throws SQLException {
        String sql = "INSERT INTO GIG_TICKET (gigid, pricetype, price) VALUES (?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, gigId);
            stmt.setString(2, String.valueOf(priceType));
            stmt.setInt(3, price);
            stmt.executeUpdate();
            return true;
        }
    }
    
    // Helper method to check if gig exists and is active (not cancelled)
    private static boolean gigExistsAndActive(Connection conn, int gigId) throws SQLException {
        String sql = "SELECT 1 FROM GIG WHERE gigid = ? AND gigstatus = 'G'";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, gigId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }
    
    // Helper method to get ticket price for a specific gig and price type
    private static Integer getTicketPrice(Connection conn, int gigId, char priceType) throws SQLException {
        String sql = "SELECT price FROM GIG_TICKET WHERE gigid = ? AND pricetype = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, gigId);
            stmt.setString(2, String.valueOf(priceType));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("price");
                }
            }
        }
        return null; // Price type not found
    }
    
    // Helper method to insert TICKET record
    private static void insertTicket(Connection conn, int gigId, String name, String email, char priceType, int cost) throws SQLException {
        String sql = "INSERT INTO TICKET (gigid, customername, customeremail, pricetype, cost) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, gigId);
            stmt.setString(2, name);
            stmt.setString(3, email);
            stmt.setString(4, String.valueOf(priceType));
            stmt.setInt(5, cost);
            stmt.executeUpdate();
            // If trigger raises exception (capacity exceeded, cost mismatch), it will propagate up
        }
    }
    
    // Helper method to get act ID by name
    private static int getActIdByName(Connection conn, String actName) throws SQLException {
        String sql = "SELECT actid FROM ACT WHERE actname = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, actName);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("actid");
                }
            }
        }
        return -1; // Act not found
    }
    
    // Helper class to store performance information
    private static class PerformanceInfo {
        int actId;
        LocalDateTime onTime;
        int duration;
        LocalDateTime endTime;
        
        PerformanceInfo(int actId, LocalDateTime onTime, int duration) {
            this.actId = actId;
            this.onTime = onTime;
            this.duration = duration;
            this.endTime = onTime.plusMinutes(duration);
        }
    }
    
    // Helper method to get all performances for a gig, ordered by ontime
    private static List<PerformanceInfo> getAllPerformances(Connection conn, int gigId) throws SQLException {
        List<PerformanceInfo> performances = new ArrayList<>();
        String sql = "SELECT actid, ontime, duration FROM ACT_GIG WHERE gigid = ? ORDER BY ontime";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, gigId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int actId = rs.getInt("actid");
                    Timestamp ontimeTs = rs.getTimestamp("ontime");
                    int duration = rs.getInt("duration");
                    LocalDateTime onTime = ontimeTs.toLocalDateTime();
                    performances.add(new PerformanceInfo(actId, onTime, duration));
                }
            }
        }
        return performances;
    }
    
    // Helper method to check if act is headline act (final or only act)
    private static boolean isHeadlineAct(Connection conn, int gigId, int actId) throws SQLException {
        // Get all performances for this gig
        List<PerformanceInfo> performances = getAllPerformances(conn, gigId);
        
        if (performances.isEmpty()) {
            return false; // No performances
        }
        
        // Check if this is the only act in the gig
        boolean isOnlyAct = true;
        for (PerformanceInfo perf : performances) {
            if (perf.actId != actId) {
                isOnlyAct = false;
                break;
            }
        }
        if (isOnlyAct) {
            return true; // Only act is always headline
        }
        
        // Find the latest end time (headline act is the one that finishes last)
        LocalDateTime latestEndTime = performances.get(0).endTime;
        int headlineActId = performances.get(0).actId;
        
        for (PerformanceInfo perf : performances) {
            if (perf.endTime.isAfter(latestEndTime)) {
                latestEndTime = perf.endTime;
                headlineActId = perf.actId;
            } else if (perf.endTime.equals(latestEndTime) && perf.actId == actId) {
                // If multiple acts end at the same time and one is our act, it's headline
                headlineActId = perf.actId;
            }
        }
        
        // Check if this act is the headline act
        return headlineActId == actId;
    }
    
    // Helper method to calculate total duration of cancelled performances
    private static int getTotalCancelledDuration(Connection conn, int gigId, int actId) throws SQLException {
        String sql = "SELECT SUM(duration) as total FROM ACT_GIG WHERE gigid = ? AND actid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, gigId);
            stmt.setInt(2, actId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    int total = rs.getInt("total");
                    return rs.wasNull() ? 0 : total;
                }
            }
        }
        return 0;
    }
    
    // Helper method to get the latest end time of cancelled performances
    private static LocalDateTime getLatestCancelledEndTime(Connection conn, int gigId, int actId) throws SQLException {
        String sql = "SELECT MAX(ontime + (duration || ' minutes')::INTERVAL) as latest_end FROM ACT_GIG WHERE gigid = ? AND actid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, gigId);
            stmt.setInt(2, actId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("latest_end");
                    if (ts != null) {
                        return ts.toLocalDateTime();
                    }
                }
            }
        }
        return null;
    }
    
    // Helper method to check if cancellation would violate interval rules
    private static boolean wouldViolateIntervalRules(Connection conn, int gigId, int actId, int totalCancelledDuration) throws SQLException {
        // Get all performances
        List<PerformanceInfo> allPerfs = getAllPerformances(conn, gigId);
        
        if (allPerfs.size() <= 1) {
            return false; // Only one or no performances, no intervals to check
        }
        
        // Get the latest end time of cancelled performances
        LocalDateTime latestCancelledEnd = getLatestCancelledEndTime(conn, gigId, actId);
        if (latestCancelledEnd == null) {
            return false;
        }
        
        // Find the performance that ends just before the cancelled ones (or at the same time)
        LocalDateTime prevEnd = null;
        for (PerformanceInfo perf : allPerfs) {
            if (perf.actId != actId && (perf.endTime.isBefore(latestCancelledEnd) || perf.endTime.equals(latestCancelledEnd))) {
                if (prevEnd == null || perf.endTime.isAfter(prevEnd)) {
                    prevEnd = perf.endTime;
                }
            }
        }
        
        // Find the first performance after cancelled ones
        PerformanceInfo firstAfter = null;
        for (PerformanceInfo perf : allPerfs) {
            if (perf.actId != actId && perf.onTime.isAfter(latestCancelledEnd)) {
                if (firstAfter == null || perf.onTime.isBefore(firstAfter.onTime)) {
                    firstAfter = perf;
                }
            }
        }
        
        // Check the gap that would be created after cancellation
        if (firstAfter != null) {
            LocalDateTime adjustedNextStart = firstAfter.onTime.minusMinutes(totalCancelledDuration);
            long gapMinutes;
            
            // Get gig start time
            String gigStartSql = "SELECT gigdatetime FROM GIG WHERE gigid = ?";
            LocalDateTime gigStart = null;
            try (PreparedStatement stmt = conn.prepareStatement(gigStartSql)) {
                stmt.setInt(1, gigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        gigStart = rs.getTimestamp("gigdatetime").toLocalDateTime();
                    } else {
                        return false; // Gig not found
                    }
                }
            }
            
            if (prevEnd != null) {
                // There's a performance before the cancelled ones
                gapMinutes = java.time.Duration.between(prevEnd, adjustedNextStart).toMinutes();
            } else {
                // No performance before - cancelled act was the first act
                // After cancellation, next act should start at gig start (Business Rule 11)
                // But if adjusted start is before gig start, we need to check the actual gap
                if (adjustedNextStart.isBefore(gigStart)) {
                    // This shouldn't happen, but if it does, it's a violation
                    return true;
                } else if (adjustedNextStart.equals(gigStart)) {
                    // Next act starts exactly at gig start - no interval, which violates Business Rule 10
                    // (intervals must be 10-30 minutes, but 0 minutes is not allowed)
                    return true; // Would violate interval rule
                } else {
                    // There's a gap from gig start to adjusted next start
                    gapMinutes = java.time.Duration.between(gigStart, adjustedNextStart).toMinutes();
                }
            }
            
            // Check if gap violates Business Rule 10 (10-30 minutes)
            // Note: gapMinutes can be 0, negative, or positive
            if (gapMinutes < 10 || gapMinutes > 30) {
                return true; // Would violate interval rule
            }
        } else {
            // No performance after cancelled ones - this means cancelled act was the last act
            // This should be caught by isHeadlineAct check, but if not, we should still check
            // If there's a performance before, we need to check if removing the last act
            // would leave a gap that violates rules
            if (prevEnd != null) {
                // There's a performance before, but nothing after
                // After cancellation, the previous performance becomes the last one
                // We need to check if the gig would still meet minimum duration (Business Rule 13)
                String gigStartSql = "SELECT gigdatetime FROM GIG WHERE gigid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(gigStartSql)) {
                    stmt.setInt(1, gigId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            LocalDateTime gigStart = rs.getTimestamp("gigdatetime").toLocalDateTime();
                            // Check if prevEnd is at least 60 minutes after gig start
                            long gigDuration = java.time.Duration.between(gigStart, prevEnd).toMinutes();
                            if (gigDuration < 60) {
                                return true; // Would violate Business Rule 13 (minimum 60 minutes)
                            }
                        }
                    }
                }
            }
        }
        
        // Also check intervals between remaining performances (they stay the same, but we should verify)
        // Actually, intervals between performances that are both after cancelled ones remain the same
        // So we only need to check the gap created by cancellation
        
        return false;
    }
    
    // Helper method to cancel entire gig
    private static String[][] cancelEntireGig(Connection conn, int gigId) throws SQLException {
        // Update gig status to cancelled
        String updateGigSql = "UPDATE GIG SET gigstatus = 'C' WHERE gigid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateGigSql)) {
            stmt.setInt(1, gigId);
            stmt.executeUpdate();
        }
        
        // Update all ticket costs to 0
        String updateTicketSql = "UPDATE TICKET SET cost = 0 WHERE gigid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(updateTicketSql)) {
            stmt.setInt(1, gigId);
            stmt.executeUpdate();
        }
        
        // Get affected customers (distinct, ordered by name)
        String customerSql = "SELECT DISTINCT customername, customeremail FROM TICKET WHERE gigid = ? ORDER BY customername ASC";
        List<String[]> customers = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(customerSql)) {
            stmt.setInt(1, gigId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String[] customer = new String[2];
                    customer[0] = rs.getString("customername");
                    customer[1] = rs.getString("customeremail");
                    customers.add(customer);
                }
            }
        }
        
        // Convert to 2D array
        if (customers.isEmpty()) {
            return new String[0][2]; // No customers affected
        }
        
        String[][] result = new String[customers.size()][2];
        for (int i = 0; i < customers.size(); i++) {
            result[i] = customers.get(i);
        }
        return result;
    }
    
    // Helper method to cancel act and adjust schedule
    private static String[][] cancelActAndAdjustSchedule(Connection conn, int gigId, int actId, int totalCancelledDuration) throws SQLException {
        // Get latest end time of cancelled performances
        LocalDateTime latestCancelledEnd = getLatestCancelledEndTime(conn, gigId, actId);
        
        // Delete cancelled performances
        String deleteSql = "DELETE FROM ACT_GIG WHERE gigid = ? AND actid = ?";
        try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
            stmt.setInt(1, gigId);
            stmt.setInt(2, actId);
            stmt.executeUpdate();
        }
        
        // Adjust subsequent performances (move earlier by total cancelled duration)
        if (latestCancelledEnd != null && totalCancelledDuration > 0) {
            String adjustSql = "UPDATE ACT_GIG SET ontime = ontime - (? || ' minutes')::INTERVAL WHERE gigid = ? AND ontime > ?";
            try (PreparedStatement stmt = conn.prepareStatement(adjustSql)) {
                stmt.setInt(1, totalCancelledDuration);
                stmt.setInt(2, gigId);
                stmt.setTimestamp(3, Timestamp.valueOf(latestCancelledEnd));
                stmt.executeUpdate();
            }
        }
        
        // Return updated lineup using task1 logic
        return task1(conn, gigId);
    }


    public static String[][] task1(Connection conn, int gigID){
        // SQL query to get act schedule for a specific gig
        // Joins ACT_GIG with ACT to get act names
        // Formats ontime and calculates offtime (ontime + duration)
        // Orders results by ontime (earliest first)


        // Sample return: 
        /*
            {
                {"ViewBee 40", "18:00", "18:50"},
                {"The Where", "19:00", "20:10"},
                {"The Selecter", "20:25", "21:25"}
            }
        */

        String sql = "SELECT " +
                     "a.actname, " +
                     "TO_CHAR(ag.ontime, 'HH24:MI') as ontime, " + //format timestamp to HH:mm 
                     "TO_CHAR(ag.ontime + (ag.duration || ' minutes')::INTERVAL, 'HH24:MI') as offtime " + //adds duration and formats to HH:mm
                     "FROM ACT_GIG ag " +
                     "JOIN ACT a ON ag.actid = a.actid " +
                     "WHERE ag.gigid = ? " +
                     "ORDER BY ag.ontime ASC";
        

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, gigID);
            // Prepraed statement auto-closes the connection when the block ends
            // Execute the query and get results
    
            try (ResultSet rs = stmt.executeQuery()) {
                // Parameters binding: ensures the query is executed with the correct parameters
                // Use helper method to convert ResultSet to String[][]
                return convertResultToStrings(rs);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            // Return null on error (or could return empty array)
            return null;
        }
    }

    public static void task2(Connection conn, String venue, String gigTitle, LocalDateTime gigStart, int adultTicketPrice, ActPerformanceDetails[] actDetails){
        // Validate input
        if (venue == null || venue.trim().isEmpty()) {
            return; // Invalid venue name
        }
        if (gigTitle == null || gigTitle.trim().isEmpty()) {
            return; // Invalid gig title
        }
        if (actDetails == null || actDetails.length == 0) {
            return; // No acts provided
        }
        if (adultTicketPrice < 0) {
            return; // Invalid ticket price
        }
        
        boolean originalAutoCommit = true;
        try {
            // Set up transaction - disable auto-commit
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            // Validate venue exists and get venueid
            int venueId = getVenueId(conn, venue);
            if (venueId == -1) {
                conn.rollback();
                return; // Venue not found
            }
            
            // Validate gig start time (Business Rule 15: 9am to 11:59pm)
            int hour = gigStart.getHour();
            int minute = gigStart.getMinute();
            if (hour < 9 || hour > 23 || (hour == 23 && minute > 59)) {
                conn.rollback();
                return; // Invalid gig start time
            }
            
            // Sort acts chronologically by onTime
            Arrays.sort(actDetails, Comparator.comparing(ActPerformanceDetails::getOnTime));
            
            // Validate all acts exist and collect genres
            Set<String> genres = new HashSet<>();
            for (ActPerformanceDetails act : actDetails) {
                if (!actExists(conn, act.getActID())) {
                    conn.rollback();
                    return; // Act does not exist
                }
                // Get genre for finish time validation
                String genre = getActGenre(conn, act.getActID());
                if (genre != null) {
                    genres.add(genre);
                }
            }
            
            // Validate first act starts at gigStart (Business Rule 11)
            if (!actDetails[0].getOnTime().equals(gigStart)) {
                conn.rollback();
                return; // First act must start at gig start time
            }
            
            // Validate final act finishes at least 60 mins after start (Business Rule 13)
            ActPerformanceDetails lastAct = actDetails[actDetails.length - 1];
            LocalDateTime lastActEnd = lastAct.getOnTime().plusMinutes(lastAct.getDuration());
            LocalDateTime gigStartPlus60 = gigStart.plusMinutes(60);
            if (lastActEnd.isBefore(gigStartPlus60)) {
                conn.rollback();
                return; // Final act must finish at least 60 minutes after gig start
            }
            
            // Validate gig finish time by genre (Business Rule 14)
            LocalDateTime maxFinishTime;
            boolean hasRockOrPop = genres.contains("rock") || genres.contains("pop");
            if (hasRockOrPop) {
                // Rock/pop gigs must finish by 11pm
                maxFinishTime = gigStart.toLocalDate().atTime(23, 0);
            } else {
                // Other gigs must finish by 1am next day
                maxFinishTime = gigStart.toLocalDate().plusDays(1).atTime(1, 0);
            }
            if (lastActEnd.isAfter(maxFinishTime)) {
                conn.rollback();
                return; // Gig finish time violates genre-based rule
            }
            
            // Validate act fees are consistent per act per gig (Business Rule 4)
            Map<Integer, Integer> actFees = new HashMap<>();
            for (ActPerformanceDetails act : actDetails) {
                int actId = act.getActID();
                int fee = act.getFee();
                if (actFees.containsKey(actId)) {
                    // Same act appears multiple times - fees must match
                    if (actFees.get(actId) != fee) {
                        conn.rollback();
                        return; // Same act has different fees for same gig
                    }
                } else {
                    actFees.put(actId, fee);
                }
            }
            
            // Insert GIG record
            int gigId = insertGig(conn, venueId, gigTitle, gigStart);
            if (gigId == -1) {
                conn.rollback();
                return; // Failed to insert gig
            }
            
            // Insert ACT_GIG records (triggers will validate most business rules)
            for (ActPerformanceDetails act : actDetails) {
                insertActGig(conn, act.getActID(), gigId, act.getFee(), act.getOnTime(), act.getDuration());
                // If insert fails, SQLException will be thrown and caught by outer try-catch
            }
            
            // Insert GIG_TICKET record for adult tickets
            if (!insertGigTicket(conn, gigId, 'A', adultTicketPrice)) {
                conn.rollback();
                return; // Failed to insert ticket pricing
            }
            
            // All validations passed and inserts successful - commit transaction
            conn.commit();
            
        } catch (SQLException e) {
            // Any SQL error - rollback transaction
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            e.printStackTrace();
        } finally {
            // Restore original auto-commit setting
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static void task3(Connection conn, int gigid, String name, String email, String ticketType){
        // Validate input
        if (name == null || name.trim().isEmpty()) {
            return; // Invalid customer name
        }
        if (email == null || email.trim().isEmpty()) {
            return; // Invalid email
        }
        if (ticketType == null || ticketType.length() != 1) {
            return; // Invalid ticket type (must be single character)
        }
        
        boolean originalAutoCommit = true;
        try {
            // Set up transaction - disable auto-commit
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            // Validate gig exists and is not cancelled
            if (!gigExistsAndActive(conn, gigid)) {
                conn.rollback();
                return; // Gig does not exist or is cancelled
            }
            
            // Validate ticketType exists in GIG_TICKET for this gig
            Integer ticketPrice = getTicketPrice(conn, gigid, ticketType.charAt(0));
            if (ticketPrice == null) {
                conn.rollback();
                return; // Ticket type not available for this gig
            }
            
            // Insert TICKET record
            // Triggers will validate:
            // - Ticket cost matches GIG_TICKET price (Business Rule via trigger)
            // - Venue capacity is not exceeded (Business Rule 12 via trigger)
            insertTicket(conn, gigid, name, email, ticketType.charAt(0), ticketPrice);
            
            // All validations passed and insert successful - commit transaction
            conn.commit();
            
        } catch (SQLException e) {
            // Any SQL error - rollback transaction
            // This includes trigger violations (capacity exceeded, cost mismatch, etc.)
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            e.printStackTrace();
        } finally {
            // Restore original auto-commit setting
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static String[][] task4(Connection conn, int gigID, String actName){
        // Validate input
        if (actName == null || actName.trim().isEmpty()) {
            return null;
        }
        
        boolean originalAutoCommit = true;
        try {
            // Set up transaction - disable auto-commit
            originalAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            
            // Validate gig exists
            if (!gigExistsAndActive(conn, gigID)) {
                // Check if gig exists but is cancelled
                String checkGigSql = "SELECT 1 FROM GIG WHERE gigid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(checkGigSql)) {
                    stmt.setInt(1, gigID);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return null; // Gig does not exist
                        }
                    }
                }
                // Gig exists but is cancelled - cannot cancel act from cancelled gig
                conn.rollback();
                return null;
            }
            
            // Get act ID by name
            int actId = getActIdByName(conn, actName);
            if (actId == -1) {
                conn.rollback();
                return null; // Act not found
            }
            
            // Check if act has performances in this gig
            String checkPerfSql = "SELECT COUNT(*) as count FROM ACT_GIG WHERE gigid = ? AND actid = ?";
            int performanceCount = 0;
            try (PreparedStatement stmt = conn.prepareStatement(checkPerfSql)) {
                stmt.setInt(1, gigID);
                stmt.setInt(2, actId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        performanceCount = rs.getInt("count");
                    }
                }
            }
            
            if (performanceCount == 0) {
                conn.rollback();
                return null; // Act has no performances in this gig
            }
            
            // Calculate total cancelled duration
            int totalCancelledDuration = getTotalCancelledDuration(conn, gigID, actId);
            
            // Check if act is headline act (final or only act)
            boolean isHeadline = isHeadlineAct(conn, gigID, actId);
            
            // Check if cancellation would violate interval rules
            boolean wouldViolate = false;
            if (!isHeadline) {
                // Only check interval violations if not headline (headline always cancels gig)
                wouldViolate = wouldViolateIntervalRules(conn, gigID, actId, totalCancelledDuration);
            }
            
            // Decide action based on conditions
            String[][] result;
            
            if (isHeadline || wouldViolate) {
                // Situation B: Cancel entire gig
                result = cancelEntireGig(conn, gigID);
            } else {
                // Situation A: Cancel act and adjust schedule
                result = cancelActAndAdjustSchedule(conn, gigID, actId, totalCancelledDuration);
            }
            
            // All operations successful - commit transaction
            conn.commit();
            return result;
            
        } catch (SQLException e) {
            // Any SQL error - rollback transaction
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            e.printStackTrace();
            return null;
        } finally {
            // Restore original auto-commit setting
            try {
                conn.setAutoCommit(originalAutoCommit);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public static String[][] task5(Connection conn){
        try {
            // SQL query to calculate tickets needed to sell for each gig
            // Uses CTEs to:
            // 1. Calculate act fees per gig (each act counted once per gig, as per Business Rule 4)
            // 2. Calculate total cost per gig (act fees + venue hire cost)
            // 3. Calculate total revenue per gig (sum of all ticket costs)
            // 4. Find cheapest ticket price per gig
            // 5. Calculate tickets needed to sell
            String sql = 
                "WITH act_fees_per_gig AS (" +
                "    SELECT " +
                "        gigid, " +
                "        actid, " +
                "        MAX(actgigfee) as act_fee " +  // All performances by same act have same fee (Business Rule 4)
                "    FROM ACT_GIG " +
                "    GROUP BY gigid, actid" +
                "), " +
                "gig_costs AS (" +
                "    SELECT " +
                "        g.gigid, " +
                "        COALESCE(SUM(af.act_fee), 0) as total_act_fees, " +
                "        v.hirecost, " +
                "        COALESCE(SUM(af.act_fee), 0) + v.hirecost as total_cost " +
                "    FROM GIG g " +
                "    LEFT JOIN VENUE v ON g.venueid = v.venueid " +
                "    LEFT JOIN act_fees_per_gig af ON g.gigid = af.gigid " +
                "    GROUP BY g.gigid, v.hirecost" +
                "), " +
                "gig_revenue AS (" +
                "    SELECT " +
                "        gigid, " +
                "        COALESCE(SUM(cost), 0) as total_revenue " +
                "    FROM TICKET " +
                "    GROUP BY gigid" +
                "), " +
                "gig_cheapest_price AS (" +
                "    SELECT " +
                "        gigid, " +
                "        MIN(price) as cheapest_price " +
                "    FROM GIG_TICKET " +
                "    GROUP BY gigid" +
                ") " +
                "SELECT " +
                "    gc.gigid, " +
                "    CASE " +
                "        WHEN COALESCE(gr.total_revenue, 0) >= gc.total_cost THEN 0 " +
                "        WHEN gcp.cheapest_price IS NULL OR gcp.cheapest_price = 0 THEN 0 " +  // No tickets defined, return 0
                "        ELSE CEIL((gc.total_cost - COALESCE(gr.total_revenue, 0))::NUMERIC / gcp.cheapest_price)::INTEGER " +
                "    END as tickets_to_sell " +
                "FROM gig_costs gc " +
                "LEFT JOIN gig_revenue gr ON gc.gigid = gr.gigid " +
                "LEFT JOIN gig_cheapest_price gcp ON gc.gigid = gcp.gigid " +
                "ORDER BY gc.gigid ASC";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                // Collect results in a list first to determine size
                List<String[]> results = new ArrayList<>();
                while (rs.next()) {
                    String[] row = new String[2];
                    row[0] = String.valueOf(rs.getInt("gigid"));
                    row[1] = String.valueOf(rs.getInt("tickets_to_sell"));
                    results.add(row);
                }

                // Convert to 2D array
                if (results.isEmpty()) {
                    return new String[0][2];
                }

                String[][] result = new String[results.size()][2];
                for (int i = 0; i < results.size(); i++) {
                    result[i] = results.get(i);
                }
                return result;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String[][] task6(Connection conn){
        try {
            // SQL query to find tickets sold per act per year for headline acts only
            // Uses CTEs to:
            // 1. Identify headline acts (acts with latest end time per gig)
            // 2. Count tickets per act per year
            // 3. Calculate totals per act
            String sql = 
                "WITH headline_acts AS (" +
                "    SELECT DISTINCT ag.gigid, ag.actid, a.actname " +
                "    FROM ACT_GIG ag " +
                "    JOIN ACT a ON ag.actid = a.actid " +
                "    JOIN GIG g ON ag.gigid = g.gigid " +
                "    WHERE g.gigstatus = 'G' " +
                "      AND (ag.ontime + (ag.duration || ' minutes')::INTERVAL) = (" +
                "          SELECT MAX(ag2.ontime + (ag2.duration || ' minutes')::INTERVAL) " +
                "          FROM ACT_GIG ag2 " +
                "          WHERE ag2.gigid = ag.gigid" +
                "      )" +
                "), " +
                "tickets_per_year AS (" +
                "    SELECT ha.actname, EXTRACT(YEAR FROM g.gigdatetime)::INTEGER as year, COUNT(*) as tickets_sold " +
                "    FROM headline_acts ha " +
                "    JOIN GIG g ON ha.gigid = g.gigid " +
                "    JOIN TICKET t ON g.gigid = t.gigid " +
                "    GROUP BY ha.actname, EXTRACT(YEAR FROM g.gigdatetime)" +
                "), " +
                "act_totals AS (" +
                "    SELECT actname, SUM(tickets_sold) as total_tickets " +
                "    FROM tickets_per_year " +
                "    GROUP BY actname" +
                ") " +
                "SELECT combined.actname, combined.year, combined.tickets_sold " +
                "FROM (" +
                "    SELECT tpy.actname, tpy.year::TEXT as year, tpy.tickets_sold::TEXT as tickets_sold, at.total_tickets " +
                "    FROM tickets_per_year tpy " +
                "    JOIN act_totals at ON tpy.actname = at.actname " +
                "    UNION ALL " +
                "    SELECT at.actname, 'Total' as year, at.total_tickets::TEXT as tickets_sold, at.total_tickets " +
                "    FROM act_totals at" +
                ") combined " +
                "ORDER BY combined.total_tickets ASC, combined.actname ASC, " +
                "    CASE WHEN combined.year = 'Total' THEN 1 ELSE 0 END, " +
                "    CASE WHEN combined.year = 'Total' THEN NULL ELSE combined.year::INTEGER END ASC NULLS LAST";
            
            // Debug: Print full SQL query
            System.out.println("DEBUG Task6 SQL (full):");
            System.out.println(sql);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                // Collect results in a list
                List<String[]> results = new ArrayList<>();
                while (rs.next()) {
                    String[] row = new String[3];
                    row[0] = rs.getString("actname");
                    row[1] = rs.getString("year");
                    row[2] = rs.getString("tickets_sold");
                    results.add(row);
                }
                
                System.out.println("DEBUG Task6: SQL returned " + results.size() + " rows from database");
                
                // Convert to 2D array
                if (results.isEmpty()) {
                    return new String[0][3];
                }
                
                String[][] result = new String[results.size()][3];
                for (int i = 0; i < results.size(); i++) {
                    result[i] = results.get(i);
                }
                
                // Debug output
                System.out.println("DEBUG Task6: Returned " + result.length + " rows");
                for (int i = 0; i < Math.min(result.length, 25); i++) {
                    System.out.println("DEBUG Task6[" + i + "]: " + 
                        (result[i][0] != null ? result[i][0] : "null") + " | " + 
                        (result[i][1] != null ? result[i][1] : "null") + " | " + 
                        (result[i][2] != null ? result[i][2] : "null"));
                }
                if (result.length > 25) {
                    System.out.println("DEBUG Task6: ... (showing first 25 of " + result.length + " rows)");
                }
                
                return result;
            }
        } catch (SQLException e) {
            System.err.println("DEBUG Task6 SQLException: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLState());
            e.printStackTrace(); 
            return null; 
        }
    }

    public static String[][] task7(Connection conn){
        try {
            // SQL query to find regular customers for headline acts
            // Shows each act who has performed as headline act along with customers who attended
            // Acts ordered alphabetically, customers ordered by ticket count (most first)
            String sql = 
                "WITH headline_acts AS (" +
                "    SELECT DISTINCT ag.gigid, ag.actid, a.actname " +
                "    FROM ACT_GIG ag " +
                "    JOIN ACT a ON ag.actid = a.actid " +
                "    JOIN GIG g ON ag.gigid = g.gigid " +
                "    WHERE g.gigstatus = 'G' " +
                "      AND (ag.ontime + (ag.duration || ' minutes')::INTERVAL) = (" +
                "          SELECT MAX(ag2.ontime + (ag2.duration || ' minutes')::INTERVAL) " +
                "          FROM ACT_GIG ag2 " +
                "          WHERE ag2.gigid = ag.gigid" +
                "      )" +
                "), " +
                "customer_tickets AS (" +
                "    SELECT ha.actname, t.customername, COUNT(*) as ticket_count " +
                "    FROM headline_acts ha " +
                "    JOIN TICKET t ON ha.gigid = t.gigid " +
                "    GROUP BY ha.actname, t.customername" +
                "    HAVING COUNT(*) >= 2" +
                "), " +
                "all_headline_acts AS (" +
                "    SELECT DISTINCT actname " +
                "    FROM headline_acts" +
                ") " +
                "SELECT aha.actname, COALESCE(ct.customername, '[None]') as customername, ct.ticket_count " +
                "FROM all_headline_acts aha " +
                "LEFT JOIN customer_tickets ct ON aha.actname = ct.actname " +
                "ORDER BY aha.actname ASC, ct.ticket_count DESC NULLS LAST";
            
            // Debug: Print full SQL query
            System.out.println("DEBUG Task7 SQL (full):");
            System.out.println(sql);
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                // Collect results in a list
                List<String[]> results = new ArrayList<>();
                while (rs.next()) {
                    String[] row = new String[2];
                    row[0] = rs.getString("actname");
                    row[1] = rs.getString("customername");
                    Integer ticketCount = rs.getObject("ticket_count") != null ? rs.getInt("ticket_count") : null;
                    System.out.println("DEBUG Task7 DB row: " + row[0] + " | " + row[1] + " | tickets: " + ticketCount);
                    results.add(row);
                }
                
                System.out.println("DEBUG Task7: SQL returned " + results.size() + " rows from database");
                
                // Convert to 2D array
                if (results.isEmpty()) {
                    return new String[0][2];
                }
                
                String[][] result = new String[results.size()][2];
                for (int i = 0; i < results.size(); i++) {
                    result[i] = results.get(i);
                }
                
                // Debug output
                System.out.println("DEBUG Task7: Returned " + result.length + " rows");
                for (int i = 0; i < result.length; i++) {
                    System.out.println("DEBUG Task7[" + i + "]: " + 
                        (result[i][0] != null ? result[i][0] : "null") + " | " + 
                        (result[i][1] != null ? result[i][1] : "null"));
                }
                
                return result;
            }
        } catch (SQLException e) {
            System.err.println("DEBUG Task7 SQLException: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLState());
            e.printStackTrace();
        return null;
        }
    }

    public static String[][] task8(Connection conn){
        try {
            // SQL query to find economically feasible venue-act combinations
            // Uses CTEs to:
            // 1. Calculate average ticket price from all non-cancelled gigs
            // 2. Generate all venue-act combinations
            // 3. Calculate total costs and tickets required
            // 4. Filter economically feasible combinations
            String sql = 
                "WITH average_ticket_price AS (" +
                "    SELECT ROUND(AVG(t.cost))::INTEGER as avg_price " +
                "    FROM TICKET t " +
                "    JOIN GIG g ON t.gigid = g.gigid " +
                "    WHERE g.gigstatus = 'G'" +
                "), " +
                "venue_act_combinations AS (" +
                "    SELECT v.venuename, a.actname, a.standardfee, v.hirecost " +
                "    FROM VENUE v " +
                "    CROSS JOIN ACT a" +
                "), " +
                "total_costs AS (" +
                "    SELECT " +
                "        vac.venuename, " +
                "        vac.actname, " +
                "        vac.standardfee + vac.hirecost as total_cost, " +
                "        atp.avg_price " +
                "    FROM venue_act_combinations vac " +
                "    CROSS JOIN average_ticket_price atp" +
                "), " +
                "tickets_required AS (" +
                "    SELECT " +
                "        venuename, " +
                "        actname, " +
                "        CASE " +
                "            WHEN avg_price > 0 THEN CEIL(total_cost::NUMERIC / avg_price)::INTEGER " +
                "            ELSE NULL " +
                "        END as tickets_needed, " +
                "        total_cost, " +
                "        avg_price " +
                "    FROM total_costs" +
                ") " +
                "SELECT " +
                "    venuename, " +
                "    actname, " +
                "    tickets_needed::TEXT as tickets_required " +
                "FROM tickets_required " +
                "WHERE tickets_needed IS NOT NULL " +
                "  AND avg_price * tickets_needed >= total_cost " +
                "ORDER BY venuename ASC, tickets_needed DESC";
            
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                
                // Collect results in a list
                List<String[]> results = new ArrayList<>();
                while (rs.next()) {
                    String[] row = new String[3];
                    row[0] = rs.getString("venuename");
                    row[1] = rs.getString("actname");
                    row[2] = rs.getString("tickets_required");
                    results.add(row);
                }
                
                // Convert to 2D array
                if (results.isEmpty()) {
                    return new String[0][3];
                }
                
                String[][] result = new String[results.size()][3];
                for (int i = 0; i < results.size(); i++) {
                    result[i] = results.get(i);
                }
                return result;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        return null;
        }
    }

    // Utility Methods

    /**
     * Prompts the user for input
     * @param prompt Prompt for user input
     * @return the text the user typed
     */
    private static String readEntry(String prompt) {
        
        try {
            StringBuffer buffer = new StringBuffer();
            System.out.print(prompt);
            System.out.flush();
            int c = System.in.read();
            while(c != '\n' && c != -1) {
                buffer.append((char)c);
                c = System.in.read();
            }
            return buffer.toString().trim();
        } catch (IOException e) {
            return "";
        }

    }
     
    /**
    * Gets the connection to the database using the Postgres driver, connecting via unix sockets
    * @return A JDBC Connection object
    */
    public static Connection getSocketConnection(){
        Properties props = new Properties();
        props.setProperty("socketFactory", "org.newsclub.net.unix.AFUNIXSocketFactory$FactoryArg");
        props.setProperty("socketFactoryArg",System.getenv("HOME") + "/cs258-postgres/postgres/tmp/.s.PGSQL.5432");
        Connection conn;
        try{
          conn = DriverManager.getConnection("jdbc:postgresql://localhost/cwk", props);
          // Test the connection is valid
          if (conn != null && !conn.isClosed()) {
          return conn;
          }
        }catch(Exception e){
            // Socket connection failed, return null to trigger fallback
            // Don't print stack trace here - it's expected in local environment
        }
        return null;
    }

    /**
     * Gets the connection to the database using the Postgres driver, connecting via TCP/IP port
     * @return A JDBC Connection object
     */
    public static Connection getPortConnection() {
        
        // Try with postgres user first (for standard setup)
        String user = "postgres";
        String passwrd = "password";
        Connection conn;

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException x) {
            System.out.println("Driver could not be loaded");
        }

        // Try with postgres user and password
        try {
            conn = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/cwk?user="+ user +"&password=" + passwrd);
            if (conn != null && !conn.isClosed()) {
            return conn;
            }
        } catch(SQLException e) {
            // Try with current system user (for local PostgreSQL with peer authentication)
            String currentUser = System.getProperty("user.name");
            try {
                // Try without password (peer authentication)
                conn = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/cwk?user=" + currentUser);
                if (conn != null && !conn.isClosed()) {
                    return conn;
                }
            } catch(SQLException e2) {
                // Try with empty password
                try {
                    conn = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/cwk?user=" + currentUser + "&password=");
                    if (conn != null && !conn.isClosed()) {
                        return conn;
                    }
                } catch(SQLException e3) {
                    System.err.format("SQL State: %s\n%s\n", e3.getSQLState(), e3.getMessage());
                    e3.printStackTrace();
                    System.out.println("Error retrieving connection with all methods");
                }
            }
        }
            return null;
        }

    /**
     * Gets the connection to the database, trying socket connection first, then port connection as fallback
     * @return A JDBC Connection object
     */
    public static Connection getConnection() {
        // Try socket connection first (for university server)
        Connection conn = getSocketConnection();
        if (conn != null) {
            return conn;
        }
        
        // Fall back to port connection (for local environment)
        System.out.println("Socket connection failed, trying port connection...");
        return getPortConnection();
    }

    /**
     * Iterates through a ResultSet and converts to a 2D Array of Strings
     * @param rs JDBC ResultSet
     * @return 2D Array of Strings
     */
     public static String[][] convertResultToStrings(ResultSet rs) {
        List<String[]> output = new ArrayList<>();
        String[][] out = null;
        try {
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                String[] thisRow = new String[columns];
                for (int i = 0; i < columns; i++) {
                    thisRow[i] = rs.getString(i + 1);
                }
                output.add(thisRow);
            }
            out = new String[output.size()][columns];
            for (int i = 0; i < output.size(); i++) {
                out[i] = output.get(i);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    public static void printTable(String[][] out){
        int numCols = out[0].length;
        int w = 20;
        int widths[] = new int[numCols];
        for(int i = 0; i < numCols; i++){
            widths[i] = w;
        }
        printTable(out,widths);
    }

    public static void printTable(String[][] out, int[] widths){
        for(int i = 0; i < out.length; i++){
            for(int j = 0; j < out[i].length; j++){
                System.out.format("%"+widths[j]+"s",out[i][j]);
                if(j < out[i].length - 1){
                    System.out.print(",");
                }
            }
            System.out.println();
        }
    }

}
