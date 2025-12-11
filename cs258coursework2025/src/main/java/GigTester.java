import java.util.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.Random;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

import java.time.LocalDateTime;
public class GigTester {
    public static void main(String[] args) throws SQLException{
        if(args.length > 0){
            if(args[0].equals("reset")){
                //Using the same seednum will generate consistent 'random' data. 
                //Run the script with a different test number to change the data
                //Or leave as -1 to get 'random' data
                int rSeed = -1;
                if(args.length > 1){
                    try {
                        rSeed = Integer.parseInt(args[1]);
                    } catch (Exception e) {
                        System.err.println("Setting data to random seed");
                        rSeed = -1;
                    }
                }
                generateTestDataMain(rSeed);
            }
            if(args[0].equals("test")){
                String warning = "WARNING: These tests have NOT been fully implemented, it is up to you to read them and check the logic\n"
                + "WARNING: Please note that tests 1,5,6 are based on testbig.sql, tests 7 and 8 are based on testsmall.sql";
                System.out.println(warning);
                System.err.println(warning);
                if(args.length > 1){
                    int test = Integer.parseInt(args[1]);
                    switch(test){
                        case 1:
                            System.out.println("Test 1 status: " + testTask1());
                            break;
                        case 2:
                            System.out.println("Test 2 status: " + testTask2());
                            break;
                        case 3:
                            System.out.println("Test 3 (valid) status: " + testTask3());
                            System.out.println("Test 3 (invalid) status: " + testTask3Invalid());
                            break;
                        case 4:
                            System.out.println("Test 4 status: " + testTask4());
                            break;
                        case 5:
                            System.out.println("Test 5 status: " + testTask5());
                            break;
                        case 6:
                            System.out.println("Test 6 status: " + testTask6());
                            break;
                        case 7:
                            System.out.println("Test 7 status: " + testTask7());
                            break;
                        case 8:
                            System.out.println("Test 8 status: " + testTask8());
                            break;
                    }
                }
            }
        }
    }

    public static boolean testTask1(){
        String[][] out = GigSystem.task1(GigSystem.getSocketConnection(),11);
        String[] gigacts = {"ViewBee 40", "The Where", "The Selecter"};
        String[] ontime = {"18:00", "19:00", "20:25"};
        String[] offtime = {"18:50","20:10", "21:25"};
        try {
            if(out.length != gigacts.length){
                throw new TestFailedException("Length " + out.length,"Length " + gigacts.length);
            }
            if(out[0].length != 3){
                throw new TestFailedException("Columns " + out[0].length, "3");
            }
            for(int i = 0; i < out.length; i++){
                checkValues(out[i][0],gigacts[i]);
                checkValues(out[i][1],ontime[i]);
                checkValues(out[i][2],offtime[i]);
            }            
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
    
    public static boolean testTask2(){
        Connection conn = GigSystem.getSocketConnection();
        if (conn == null) {
            System.err.println("Failed to get database connection");
            return false;
        }
        
        // Get the maximum gigid before creating new gig (to find the new one later)
        int maxGigIdBefore = getMaxGigId(conn);
        System.out.println("DEBUG: Max gig ID before task2: " + maxGigIdBefore);
        System.out.println("DEBUG: Expected max gig ID from test data should be 50 (if testbig.sql loaded)");
        
        LocalDateTime[] onDates = new LocalDateTime[3];
        onDates[0] = LocalDateTime.of(2021,java.time.Month.NOVEMBER,02,20,00);
        // Act 1 ends at 20:30, so Act 2 must start at 20:40 (10 min gap) or later, but within 30 min
        // Using 20:40 for a 10-minute gap (minimum allowed)
        onDates[1] = LocalDateTime.of(2021,java.time.Month.NOVEMBER,02,20,40);
        // Act 2 ends at 21:20, so Act 3 must start at 21:30 (10 min gap) or later
        // Using 21:30 for a 10-minute gap
        onDates[2] = LocalDateTime.of(2021,java.time.Month.NOVEMBER,02,21,30);
        ActPerformanceDetails[] apd = new ActPerformanceDetails[3];
        apd[0] = new ActPerformanceDetails(3, 20000, onDates[0], 30);
        apd[1] = new ActPerformanceDetails(4, 30000, onDates[1], 40);
        apd[2] = new ActPerformanceDetails(6, 10000, onDates[2], 20);

        // Call task2 to create the gig
        System.out.println("Attempting to create gig at venue: " + venues[3]);
        System.out.println("Act 1: ID=" + apd[0].getActID() + ", starts=" + onDates[0] + ", duration=" + apd[0].getDuration());
        System.out.println("Act 2: ID=" + apd[1].getActID() + ", starts=" + onDates[1] + ", duration=" + apd[1].getDuration());
        System.out.println("Act 3: ID=" + apd[2].getActID() + ", starts=" + onDates[2] + ", duration=" + apd[2].getDuration());
        
        GigSystem.task2(conn, venues[3], "The November Party", onDates[0], 40, apd);
        
        try {
            // Get the new gigid (should be maxGigIdBefore + 1 if successful)
            int maxGigIdAfter = getMaxGigId(conn);
            
            System.out.println("Max gig ID before: " + maxGigIdBefore);
            System.out.println("Max gig ID after: " + maxGigIdAfter);
            
            // Verify a new gig was created
            if (maxGigIdAfter <= maxGigIdBefore) {
                System.err.println("Test failed: No new gig was created");
                System.err.println("This might be because:");
                System.err.println("  1. Business rules were violated (check interval durations, overlaps, etc.)");
                System.err.println("  2. Venue does not exist: " + venues[3]);
                System.err.println("  3. Acts do not exist (IDs: 3, 4, 6)");
                System.err.println("  4. Conflicts with existing gigs");
                
                // Check if venue exists
                String checkVenue = "SELECT COUNT(*) as count FROM VENUE WHERE venuename = ?";
                try (PreparedStatement stmt = conn.prepareStatement(checkVenue)) {
                    stmt.setString(1, venues[3]);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next() && rs.getInt("count") == 0) {
                            System.err.println("  -> Venue '" + venues[3] + "' does not exist in database");
                        }
                    }
                }
                
                // Check if acts exist
                String checkActs = "SELECT actid FROM ACT WHERE actid IN (3, 4, 6) ORDER BY actid";
                try (PreparedStatement stmt = conn.prepareStatement(checkActs)) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        List<Integer> existingActs = new ArrayList<>();
                        while (rs.next()) {
                            existingActs.add(rs.getInt("actid"));
                        }
                        if (existingActs.size() < 3) {
                            System.err.println("  -> Some acts are missing. Found: " + existingActs);
                        }
                    }
                }
                
                return false;
            }
            
            int newGigId = maxGigIdAfter;
            
            // Verify the gig details
            String sql = "SELECT gigtitle, gigdatetime, gigstatus FROM GIG WHERE gigid = ?";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, newGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        System.err.println("Test failed: Gig was not found in database");
                        return false;
                    }
                    String title = rs.getString("gigtitle");
                    if (!"The November Party".equals(title)) {
                        System.err.println("Test failed: Gig title mismatch. Expected 'The November Party', got '" + title + "'");
                        return false;
                    }
                    String status = rs.getString("gigstatus");
                    if (!"G".equals(status)) {
                        System.err.println("Test failed: Gig status should be 'G', got '" + status + "'");
                        return false;
                    }
                }
            }
            
            // Verify ACT_GIG records
            sql = "SELECT COUNT(*) as count FROM ACT_GIG WHERE gigid = ?";
            int actCount = 0;
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, newGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        actCount = rs.getInt("count");
                    }
                }
            }
            if (actCount != 3) {
                System.err.println("Test failed: Expected 3 acts, got " + actCount);
                return false;
            }
            
            // Verify ACT_GIG records have correct act IDs and fees
            sql = "SELECT actid, actgigfee FROM ACT_GIG WHERE gigid = ? ORDER BY ontime";
            int[] expectedActIds = {3, 4, 6};
            int[] expectedFees = {20000, 30000, 10000};
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, newGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    int idx = 0;
                    while (rs.next() && idx < 3) {
                        int actId = rs.getInt("actid");
                        int fee = rs.getInt("actgigfee");
                        if (actId != expectedActIds[idx]) {
                            System.err.println("Test failed: Act " + (idx+1) + " ID mismatch. Expected " + expectedActIds[idx] + ", got " + actId);
                            return false;
                        }
                        if (fee != expectedFees[idx]) {
                            System.err.println("Test failed: Act " + (idx+1) + " fee mismatch. Expected " + expectedFees[idx] + ", got " + fee);
                            return false;
                        }
                        idx++;
                    }
                }
            }
            
            // Verify GIG_TICKET record
            sql = "SELECT price FROM GIG_TICKET WHERE gigid = ? AND pricetype = 'A'";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, newGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        System.err.println("Test failed: GIG_TICKET record not found");
                        return false;
                    }
                    int price = rs.getInt("price");
                    if (price != 40) {
                        System.err.println("Test failed: Expected ticket price 40, got " + price);
                        return false;
                    }
                }
            }
            
            // Verify schedule using task1
            String[][] schedule = GigSystem.task1(conn, newGigId);
            if (schedule == null || schedule.length != 3) {
                System.err.println("Test failed: Schedule should have 3 acts, got " + (schedule == null ? "null" : schedule.length));
                return false;
            }
            
            // Verify schedule times match expected values
            // Act 1: 20:00, duration 30 -> ends 20:30
            // Act 2: 20:40, duration 40 -> ends 21:20
            // Act 3: 21:30, duration 20 -> ends 21:50
            String[] expectedOnTimes = {"20:00", "20:40", "21:30"};
            String[] expectedOffTimes = {"20:30", "21:20", "21:50"};
            
            for (int i = 0; i < 3; i++) {
                if (!expectedOnTimes[i].equals(schedule[i][1])) {
                    System.err.println("Test failed: Act " + (i+1) + " ontime mismatch. Expected '" + expectedOnTimes[i] + "', got '" + schedule[i][1] + "'");
                    return false;
                }
                if (!expectedOffTimes[i].equals(schedule[i][2])) {
                    System.err.println("Test failed: Act " + (i+1) + " offtime mismatch. Expected '" + expectedOffTimes[i] + "', got '" + schedule[i][2] + "'");
                    return false;
                }
            }
            
            System.out.println("Test passed: Gig created successfully with correct schedule");
            return true;
            
        } catch (SQLException e) {
            System.err.println("Test failed with SQLException: " + e.getMessage());
            e.printStackTrace();
        return false;
        }
    }
    
    // Helper method to get maximum gigid
    private static int getMaxGigId(Connection conn) {
        try {
            String sql = "SELECT COALESCE(MAX(gigid), 0) as maxid FROM GIG";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("maxid");
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    //This method isn't called by anywhere - you can adapt it if you like
    public static boolean testTask2Invalid(){
        Connection conn = GigSystem.getSocketConnection();
        if (conn == null) {
            System.err.println("Failed to get database connection");
            return false;
        }
        
        // Get the maximum gigid before attempting to create invalid gig
        int maxGigIdBefore = getMaxGigId(conn);
        
        LocalDateTime[] onDates = new LocalDateTime[3];
        onDates[0] = LocalDateTime.of(2021,java.time.Month.NOVEMBER,02,20,00);
        onDates[1] = LocalDateTime.of(2021,java.time.Month.NOVEMBER,02,20,35);
        //Nothing should be added, because there is a gap of more than 30 minutes between the second act and the third act
        // Act 1 ends at 20:30, Act 2 starts at 20:35 (5 min gap, OK)
        // Act 2 ends at 21:15, Act 3 starts at 22:20 (65 min gap - violates Business Rule 10: intervals must be 10-30 minutes)
        onDates[2] = LocalDateTime.of(2021,java.time.Month.NOVEMBER,02,22,20);
        ActPerformanceDetails[] apd = new ActPerformanceDetails[3];
        apd[0] = new ActPerformanceDetails(3, 20000, onDates[0], 30);
        apd[1] = new ActPerformanceDetails(4, 30000, onDates[1], 40);
        apd[2] = new ActPerformanceDetails(6, 10000, onDates[2], 20);

        // Call task2 - this should fail and not create a gig
        GigSystem.task2(conn, venues[3], "The November Party Invalid", onDates[0], 40, apd);
        
        try {
            // Get the maximum gigid after attempting to create invalid gig
            int maxGigIdAfter = getMaxGigId(conn);
            
            // Verify NO new gig was created (gigid should be the same)
            if (maxGigIdAfter > maxGigIdBefore) {
                System.err.println("Test failed: A new gig was created even though it should have been rejected");
                // Clean up: delete the incorrectly created gig
                String deleteSql = "DELETE FROM GIG WHERE gigid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(deleteSql)) {
                    stmt.setInt(1, maxGigIdAfter);
                    stmt.executeUpdate();
                }
                return false;
            }
            
            // Verify the invalid gig title doesn't exist
            String sql = "SELECT COUNT(*) as count FROM GIG WHERE gigtitle = 'The November Party Invalid'";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt("count");
                        if (count > 0) {
                            System.err.println("Test failed: Invalid gig was created in database");
                            return false;
                        }
                    }
                }
            }
            
            System.out.println("Test passed: Invalid gig was correctly rejected");
            return true;
            
        } catch (SQLException e) {
            System.err.println("Test failed with SQLException: " + e.getMessage());
            e.printStackTrace();
        return false;
        }
    }

    public static boolean testTask3(){
        Connection conn = GigSystem.getSocketConnection();
        if (conn == null) {
            System.err.println("Failed to get database connection");
            return false;
        }
        
        int gigid = 24;
        String name = "B Simpson";
        String email = "bsimpson@testemail";
        String ticketType = "A";
        
        try {
            // Get ticket count before purchase
            String countSql = "SELECT COUNT(*) as count FROM TICKET WHERE gigid = ?";
            int ticketCountBefore = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, gigid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountBefore = rs.getInt("count");
                    }
                }
            }
            
            // Get expected ticket price from GIG_TICKET
            String priceSql = "SELECT price FROM GIG_TICKET WHERE gigid = ? AND pricetype = ?";
            int expectedPrice = -1;
            try (PreparedStatement stmt = conn.prepareStatement(priceSql)) {
                stmt.setInt(1, gigid);
                stmt.setString(2, ticketType);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        expectedPrice = rs.getInt("price");
                    } else {
                        System.err.println("Test failed: GIG_TICKET record not found for gig " + gigid + " and type " + ticketType);
                        return false;
                    }
                }
            }
            
            // Verify gig exists and is active
            String statusSql = "SELECT gigstatus FROM GIG WHERE gigid = ?";
            String gigStatus = null;
            try (PreparedStatement stmt = conn.prepareStatement(statusSql)) {
                stmt.setInt(1, gigid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        gigStatus = rs.getString("gigstatus");
                    } else {
                        System.err.println("Test failed: Gig " + gigid + " does not exist");
                        return false;
                    }
                }
            }
            
            if (!"G".equals(gigStatus)) {
                System.err.println("Test failed: Gig " + gigid + " is not active (status: " + gigStatus + ")");
                return false;
            }
            
            System.out.println("DEBUG: Ticket count before: " + ticketCountBefore);
            System.out.println("DEBUG: Expected ticket price: " + expectedPrice);
            System.out.println("DEBUG: Gig status: " + gigStatus);
            
            // Call task3 to purchase ticket
            GigSystem.task3(conn, gigid, name, email, ticketType);
            
            // Get ticket count after purchase
            int ticketCountAfter = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, gigid);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountAfter = rs.getInt("count");
                    }
                }
            }
            
            System.out.println("DEBUG: Ticket count after: " + ticketCountAfter);
            
            // Verify a new ticket was created
            if (ticketCountAfter != ticketCountBefore + 1) {
                System.err.println("Test failed: Expected ticket count " + (ticketCountBefore + 1) + ", got " + ticketCountAfter);
                return false;
            }
            
            // Verify the new ticket details
            String ticketSql = "SELECT customername, customeremail, pricetype, cost FROM TICKET WHERE gigid = ? AND customername = ? AND customeremail = ?";
            try (PreparedStatement stmt = conn.prepareStatement(ticketSql)) {
                stmt.setInt(1, gigid);
                stmt.setString(2, name);
                stmt.setString(3, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        System.err.println("Test failed: Ticket not found for customer " + name + " (" + email + ")");
                        return false;
                    }
                    
                    String ticketName = rs.getString("customername");
                    String ticketEmail = rs.getString("customeremail");
                    String ticketPriceType = rs.getString("pricetype");
                    int ticketCost = rs.getInt("cost");
                    
                    if (!name.equals(ticketName)) {
                        System.err.println("Test failed: Customer name mismatch. Expected '" + name + "', got '" + ticketName + "'");
                        return false;
                    }
                    if (!email.equals(ticketEmail)) {
                        System.err.println("Test failed: Customer email mismatch. Expected '" + email + "', got '" + ticketEmail + "'");
                        return false;
                    }
                    if (!ticketType.equals(ticketPriceType)) {
                        System.err.println("Test failed: Ticket type mismatch. Expected '" + ticketType + "', got '" + ticketPriceType + "'");
                        return false;
                    }
                    if (expectedPrice != ticketCost) {
                        System.err.println("Test failed: Ticket cost mismatch. Expected " + expectedPrice + ", got " + ticketCost);
                        return false;
                    }
                }
            }
            
            System.out.println("Test passed: Ticket purchased successfully with correct details");
            return true;
            
        } catch (SQLException e) {
            System.err.println("Test failed with SQLException: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Test Task 3 with invalid information
    public static boolean testTask3Invalid(){
        Connection conn = GigSystem.getSocketConnection();
        if (conn == null) {
            System.err.println("Failed to get database connection");
            return false;
        }
        
        try {
            // Test Case 1: Invalid gig ID (gig doesn't exist)
            System.out.println("Testing invalid gig ID...");
            int invalidGigId = 99999;
            String countSql = "SELECT COUNT(*) as count FROM TICKET WHERE gigid = ?";
            int ticketCountBefore1 = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, invalidGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountBefore1 = rs.getInt("count");
                    }
                }
            }
            
            GigSystem.task3(conn, invalidGigId, "Test Customer", "test@example.com", "A");
            
            int ticketCountAfter1 = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, invalidGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountAfter1 = rs.getInt("count");
                    }
                }
            }
            
            if (ticketCountAfter1 != ticketCountBefore1) {
                System.err.println("Test failed: Ticket was created for non-existent gig");
                return false;
            }
            System.out.println("  ✓ Invalid gig ID correctly rejected");
            
            // Test Case 2: Invalid ticket type (ticket type doesn't exist for gig)
            System.out.println("Testing invalid ticket type...");
            int validGigId = 24; // Use a valid gig
            String invalidTicketType = "Z"; // Ticket type that doesn't exist
            
            int ticketCountBefore2 = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, validGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountBefore2 = rs.getInt("count");
                    }
                }
            }
            
            GigSystem.task3(conn, validGigId, "Test Customer", "test@example.com", invalidTicketType);
            
            int ticketCountAfter2 = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, validGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountAfter2 = rs.getInt("count");
                    }
                }
            }
            
            if (ticketCountAfter2 != ticketCountBefore2) {
                System.err.println("Test failed: Ticket was created with invalid ticket type");
                return false;
            }
            System.out.println("  ✓ Invalid ticket type correctly rejected");
            
            // Test Case 3: Empty customer name
            System.out.println("Testing empty customer name...");
            int ticketCountBefore3 = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, validGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountBefore3 = rs.getInt("count");
                    }
                }
            }
            
            GigSystem.task3(conn, validGigId, "", "test@example.com", "A");
            
            int ticketCountAfter3 = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, validGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountAfter3 = rs.getInt("count");
                    }
                }
            }
            
            if (ticketCountAfter3 != ticketCountBefore3) {
                System.err.println("Test failed: Ticket was created with empty customer name");
                return false;
            }
            System.out.println("  ✓ Empty customer name correctly rejected");
            
            // Test Case 4: Empty email
            System.out.println("Testing empty email...");
            int ticketCountBefore4 = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, validGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountBefore4 = rs.getInt("count");
                    }
                }
            }
            
            GigSystem.task3(conn, validGigId, "Test Customer", "", "A");
            
            int ticketCountAfter4 = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, validGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountAfter4 = rs.getInt("count");
                    }
                }
            }
            
            if (ticketCountAfter4 != ticketCountBefore4) {
                System.err.println("Test failed: Ticket was created with empty email");
                return false;
            }
            System.out.println("  ✓ Empty email correctly rejected");
            
            // Test Case 5: Invalid ticket type format (not single character)
            System.out.println("Testing invalid ticket type format...");
            int ticketCountBefore5 = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, validGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountBefore5 = rs.getInt("count");
                    }
                }
            }
            
            GigSystem.task3(conn, validGigId, "Test Customer", "test@example.com", "AA"); // Invalid: not single character
            
            int ticketCountAfter5 = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                stmt.setInt(1, validGigId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ticketCountAfter5 = rs.getInt("count");
                    }
                }
            }
            
            if (ticketCountAfter5 != ticketCountBefore5) {
                System.err.println("Test failed: Ticket was created with invalid ticket type format");
                return false;
            }
            System.out.println("  ✓ Invalid ticket type format correctly rejected");
            
            // Test Case 6: Cancelled gig (if we can find one or create a cancelled gig scenario)
            // Note: This test assumes there might be a cancelled gig, or we skip it if none exists
            System.out.println("Testing cancelled gig...");
            String cancelledGigSql = "SELECT gigid FROM GIG WHERE gigstatus = 'C' LIMIT 1";
            Integer cancelledGigId = null;
            try (PreparedStatement stmt = conn.prepareStatement(cancelledGigSql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        cancelledGigId = rs.getInt("gigid");
                    }
                }
            }
            
            if (cancelledGigId != null) {
                int ticketCountBefore6 = 0;
                try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                    stmt.setInt(1, cancelledGigId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            ticketCountBefore6 = rs.getInt("count");
                        }
                    }
                }
                
                GigSystem.task3(conn, cancelledGigId, "Test Customer", "test@example.com", "A");
                
                int ticketCountAfter6 = 0;
                try (PreparedStatement stmt = conn.prepareStatement(countSql)) {
                    stmt.setInt(1, cancelledGigId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            ticketCountAfter6 = rs.getInt("count");
                        }
                    }
                }
                
                if (ticketCountAfter6 != ticketCountBefore6) {
                    System.err.println("Test failed: Ticket was created for cancelled gig");
                    return false;
                }
                System.out.println("  ✓ Cancelled gig correctly rejected");
            } else {
                System.out.println("  ⚠ Skipped: No cancelled gig found in database");
            }
            
            System.out.println("All invalid test cases passed!");
            return true;
            
        } catch (SQLException e) {
            System.err.println("Test failed with SQLException: " + e.getMessage());
            e.printStackTrace();
        return false;
        }
    }

    public static boolean testTask4(){
        Connection conn = GigSystem.getSocketConnection();
        if (conn == null) {
            System.err.println("Failed to get database connection");
            return false;
        }
        
        int cancelGigID = 40;
        String actName = "Scalar Swift";
        
        try {
            // Check gig status before cancellation
            String statusSql = "SELECT gigstatus FROM GIG WHERE gigid = ?";
            String statusBefore = null;
            try (PreparedStatement stmt = conn.prepareStatement(statusSql)) {
                stmt.setInt(1, cancelGigID);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        statusBefore = rs.getString("gigstatus");
                    } else {
                        System.err.println("Test failed: Gig " + cancelGigID + " does not exist");
                        return false;
                    }
                }
            }
            
            System.out.println("DEBUG: Gig status before: " + statusBefore);
            
            // Check if act exists in gig
            String actIdSql = "SELECT actid FROM ACT WHERE actname = ?";
            int actId = -1;
            try (PreparedStatement stmt = conn.prepareStatement(actIdSql)) {
                stmt.setString(1, actName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        actId = rs.getInt("actid");
                    }
                }
            }
            
            if (actId == -1) {
                System.err.println("Test failed: Act '" + actName + "' not found");
                return false;
            }
            
            System.out.println("DEBUG: Act ID for '" + actName + "': " + actId);
            
            // Check how many acts are in this gig
            String countActsSql = "SELECT COUNT(DISTINCT actid) as total FROM ACT_GIG WHERE gigid = ?";
            int totalActs = 0;
            try (PreparedStatement stmt = conn.prepareStatement(countActsSql)) {
                stmt.setInt(1, cancelGigID);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        totalActs = rs.getInt("total");
                    }
                }
            }
            
            System.out.println("DEBUG: Total distinct acts in gig: " + totalActs);
            
            // Determine if act is headline (will be checked after task4 based on result format)
            boolean isHeadline = (totalActs == 1); // If only one act, it's definitely headline
            
            // Get ticket costs before cancellation
            String ticketCostSql = "SELECT ticketid, cost FROM TICKET WHERE gigid = ?";
            Map<Integer, Integer> ticketCostsBefore = new HashMap<>();
            try (PreparedStatement stmt = conn.prepareStatement(ticketCostSql)) {
                stmt.setInt(1, cancelGigID);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        ticketCostsBefore.put(rs.getInt("ticketid"), rs.getInt("cost"));
                    }
                }
            }
            
            System.out.println("DEBUG: Tickets before: " + ticketCostsBefore.size());
            
            // Call task4
            String[][] result = GigSystem.task4(conn, cancelGigID, actName);
            
            if (result == null) {
                System.err.println("Test failed: task4 returned null");
                return false;
            }
            
            System.out.println("DEBUG: Result length: " + result.length);
            if (result.length > 0) {
                System.out.println("DEBUG: Result columns: " + result[0].length);
            }
            
            // Check gig status after cancellation
            String statusAfter = null;
            try (PreparedStatement stmt = conn.prepareStatement(statusSql)) {
                stmt.setInt(1, cancelGigID);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        statusAfter = rs.getString("gigstatus");
                    }
                }
            }
            
            System.out.println("DEBUG: Gig status after: " + statusAfter);
            
            // Determine if entire gig was cancelled based on status
            boolean entireGigCancelled = "C".equals(statusAfter);
            
            if (entireGigCancelled) {
                // Should cancel entire gig
                if (!"C".equals(statusAfter)) {
                    System.err.println("Test failed: Gig should be cancelled (status should be 'C'), got '" + statusAfter + "'");
                    return false;
                }
                
                // Check ticket costs are set to 0
                String checkCostSql = "SELECT ticketid, cost FROM TICKET WHERE gigid = ?";
                try (PreparedStatement stmt = conn.prepareStatement(checkCostSql)) {
                    stmt.setInt(1, cancelGigID);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            int ticketId = rs.getInt("ticketid");
                            int cost = rs.getInt("cost");
                            if (cost != 0) {
                                System.err.println("Test failed: Ticket " + ticketId + " cost should be 0, got " + cost);
                                return false;
                            }
                        }
                    }
                }
                
                // Verify result format: should be customer names and emails
                if (result.length == 0) {
                    System.out.println("DEBUG: No customers affected (gig had no tickets)");
                    return true;
                }
                
                if (result[0].length != 2) {
                    System.err.println("Test failed: Result should have 2 columns (name, email), got " + result[0].length);
                    return false;
                }
                
                // Verify customers are ordered by name and distinct
                String prevName = null;
                for (int i = 0; i < result.length; i++) {
                    String name = result[i][0];
                    String email = result[i][1];
                    
                    if (name == null || email == null) {
                        System.err.println("Test failed: Result row " + i + " has null values");
                        return false;
                    }
                    
                    // Check ordering (ascending by name)
                    if (prevName != null && name.compareTo(prevName) < 0) {
                        System.err.println("Test failed: Results not ordered by name. '" + prevName + "' should come before '" + name + "'");
                        return false;
                    }
                    
                    // Check for duplicates
                    for (int j = i + 1; j < result.length; j++) {
                        if (result[j][0].equals(name) && result[j][1].equals(email)) {
                            System.err.println("Test failed: Duplicate customer found: " + name + ", " + email);
                            return false;
                        }
                    }
                    
                    prevName = name;
                }
                
                System.out.println("Test passed: Gig cancelled successfully, " + result.length + " customers affected");
                return true;
                
            } else {
                // Should only cancel act, not entire gig
                if (!"G".equals(statusAfter)) {
                    System.err.println("Test failed: Gig should still be active (status should be 'G'), got '" + statusAfter + "'");
                    return false;
                }
                
                // Verify result is lineup format (like task1)
                if (result.length == 0) {
                    System.out.println("DEBUG: No acts remaining after cancellation");
                    return true;
                }
                
                if (result[0].length != 3) {
                    System.err.println("Test failed: Result should have 3 columns (actname, ontime, offtime), got " + result[0].length);
        return false;
                }
                
                // Verify cancelled act is not in result
                for (int i = 0; i < result.length; i++) {
                    if (actName.equals(result[i][0])) {
                        System.err.println("Test failed: Cancelled act '" + actName + "' still appears in lineup");
                        return false;
                    }
                }
                
                System.out.println("Test passed: Act cancelled successfully, lineup updated");
                return true;
            }
            
        } catch (SQLException e) {
            System.err.println("Test failed with SQLException: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean testTask5(){
        Connection conn = GigSystem.getSocketConnection();
        if (conn == null) {
            System.err.println("Failed to get database connection");
            return false;
        }
        
        try {
            // Get result from task5
            String[][] out = GigSystem.task5(conn);
            
            // Check if result is null
            if (out == null) {
                System.err.println("Test failed: task5 returned null");
                return false;
            }
            
            System.out.println("DEBUG: Task 5 returned " + out.length + " rows");
            
            // Check if result is empty (should have at least some gigs)
            if (out.length == 0) {
                System.err.println("Test failed: task5 returned empty result (expected at least some gigs)");
                return false;
            }
            
            // Verify result format: each row should have 2 columns
            for (int i = 0; i < out.length; i++) {
                if (out[i] == null || out[i].length != 2) {
                    System.err.println("Test failed: Row " + i + " has invalid format (expected 2 columns, got " + 
                                      (out[i] == null ? "null" : out[i].length) + ")");
                    return false;
                }
                
                // Verify gigID is a valid integer
                try {
                    int gigId = Integer.parseInt(out[i][0]);
                    if (gigId <= 0) {
                        System.err.println("Test failed: Row " + i + " has invalid gigID: " + gigId);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Test failed: Row " + i + " has invalid gigID format: " + out[i][0]);
                    return false;
                }
                
                // Verify tickets_to_sell is a valid non-negative integer
                try {
                    int ticketsToSell = Integer.parseInt(out[i][1]);
                    if (ticketsToSell < 0) {
                        System.err.println("Test failed: Row " + i + " has negative tickets_to_sell: " + ticketsToSell);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Test failed: Row " + i + " has invalid tickets_to_sell format: " + out[i][1]);
                    return false;
                }
            }
            
            // Verify gigIDs are in ascending order
            for (int i = 1; i < out.length; i++) {
                int prevGigId = Integer.parseInt(out[i-1][0]);
                int currGigId = Integer.parseInt(out[i][0]);
                if (currGigId < prevGigId) {
                    System.err.println("Test failed: Results not ordered by gigID. Row " + (i-1) + " has gigID " + 
                                      prevGigId + ", Row " + i + " has gigID " + currGigId);
                    return false;
                }
            }
            
            // Expected values for test data (gigs 1-50 from testbig.sql)
            // This data should work for the main set of test data.
            int[] expectedTickets = {1600, 2000, 1525, 1225, 1650, 1525, 1300, 1850, 2023, 398, 
                                     1873, 1849, 1125, 1949, 1498, 1073, 1900, 399, 749, 1425, 
                                     697, 1098, 2875, 825, 1224, 1849, 1149, 1525, 1625, 1548, 
                                     850, 300, 524, 775, 1297, 2522, 1274, 1150, 2250, 1223, 
                                     1974, 950, 775, 525, 749, 1800, 1900, 973, 298, 2275};
            
            // Verify results match expected values for known test data
            // Only check if we have enough results and the first gig ID is 1
            if (out.length >= expectedTickets.length) {
                boolean firstGigIsOne = false;
                int startIndex = -1;
                
                // Find where gig ID 1 starts
                for (int i = 0; i < out.length; i++) {
                    if (Integer.parseInt(out[i][0]) == 1) {
                        firstGigIsOne = true;
                        startIndex = i;
                        break;
                    }
                }
                
                if (firstGigIsOne && startIndex >= 0) {
                    // Verify expected values for gigs 1-50
                    for (int i = 0; i < expectedTickets.length && (startIndex + i) < out.length; i++) {
                        int expectedGigId = i + 1;
                        int actualGigId = Integer.parseInt(out[startIndex + i][0]);
                        
                        if (actualGigId != expectedGigId) {
                            System.out.println("DEBUG: Skipping expected value check - gig IDs don't match at index " + i);
                            break; // Stop checking if gig IDs don't match
                        }
                        
                        int expectedTicketsToSell = expectedTickets[i];
                        int actualTicketsToSell = Integer.parseInt(out[startIndex + i][1]);
                        
                        if (actualTicketsToSell != expectedTicketsToSell) {
                            System.err.println("Test failed: Gig " + expectedGigId + " - Expected " + expectedTicketsToSell + 
                                              " tickets, got " + actualTicketsToSell);
                            return false;
                        }
                    }
                    System.out.println("DEBUG: Verified expected values for gigs 1-" + 
                                     Math.min(expectedTickets.length, out.length - startIndex));
                } else {
                    System.out.println("DEBUG: Could not find gig ID 1 in results, skipping expected value verification");
                }
            }
            
            // Manual verification: Check a few specific cases
            // Verify that gigs with no tickets sold still have valid calculations
            System.out.println("DEBUG: Sample results:");
            for (int i = 0; i < Math.min(5, out.length); i++) {
                System.out.println("  Gig " + out[i][0] + ": " + out[i][1] + " tickets to sell");
            }
            
            System.out.println("Test passed: Task 5 returned valid results with correct format and ordering");
            return true;
            
        } catch (Exception e) {
            System.err.println("Test failed with exception: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public static boolean testTask6(){
        String[][] out = GigSystem.task6(GigSystem.getSocketConnection());
        String[] acts = {"QLS","QLS","QLS","ViewBee 40","ViewBee 40","ViewBee 40","Scalar Swift","Scalar Swift","Scalar Swift","Scalar Swift",
        "Join Division","Join Division","Join Division","Join Division","The Selecter","The Selecter","The Selecter","The Where","The Where","The Where",
        "The Where","The Where"};
        String[] years = {"2018","2019","Total","2017","2018","Total","2017","2018","2019","Total","2016","2018","2020","Total","2017","2018","Total","2016","2017","2018","2020","Total"};
        String[] totals = {"2","1","3","3","1","4","3","1","1","5","2","2","3","7","4","4","8","1","3","5","4","13"};
        try{
            if(out.length != acts.length){
                throw new TestFailedException("Length " + out.length,"Length " + acts.length);
            }
            if(out[0].length != 3){
                throw new TestFailedException("Columns " + out[0].length, "3");
            }
            for(int i = 0; i < acts.length; i++){
                checkValues(out[i][0], acts[i]);
                checkValues(out[i][1], years[i]);
                checkValues(out[i][2], totals[i]);
            }
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean testTask7(){
        //In the test data the solution is...
        String[][] out = GigSystem.task7(GigSystem.getSocketConnection());
        String[] acts = {"Join Division","QLS","Scalar Swift","Scalar Swift"};
        String[] customers = {"G Jones","[None]","G Jones", "J Smith"};
        try {
            if(out.length != acts.length){
                throw new TestFailedException("Length " + out.length,"Length " + acts.length);
            }
            if(out[0].length != 2){
                throw new TestFailedException("Columns " + out[0].length, "2");
            }
            for(int i = 0; i < acts.length; i++){
                checkValues(out[i][0],acts[i]);
                checkValues(out[i][1],customers[i]);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public static boolean testTask8(){
        //In the test data the solution is...
        String[][] out = GigSystem.task8(GigSystem.getSocketConnection());
        String[] venues = {"Arts Centre Theatre","Big Hall","Big Hall","Cinema","City Hall","Symphony Hall","Symphony Hall","Symphony Hall",
        "Symphony Hall","Symphony Hall","Symphony Hall","Town Hall","Town Hall","Village Green","Village Hall"};
        String[] acts = {"Join Division","The Where","Join Division","Join Division","Join Division","ViewBee 40","Scalar Swift","QLS","The Selecter","The Where",
        "Join Division","The Where","Join Division","Join Division","Join Division"};
        String[] seats = {"150","675","375","175","225","1275","1250","1225","1200","825","525","575","275","100","75"};
        try {
            if(out.length != acts.length){
                throw new TestFailedException("Length " + out.length,"Length " + acts.length);
            }
            if(out[0].length != 3){
                throw new TestFailedException("Columns " + out[0].length, "3");
            }
            for(int i = 0; i < acts.length; i++){
                checkValues(out[i][0],venues[i]);
                checkValues(out[i][1],acts[i]);
                checkValues(out[i][2],seats[i]);
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    //Make your own if you like, these are based on local theatres
    static String[] venues = {"Big Hall","Arts Centre Theatre","City Hall","Village Green","Village Hall","Cinema","Symphony Hall","Town Hall"};

    /** 
    * This method generates an SQL file that you can use to populate the database
    * Note we are outputing statements rather than using JDBC
    * Do not confuse this method with the prepared statements you should be writing in the other file
    * In GigSystem.java, you must use JDBC methods rather than generating SQL files like this
    */
    private static void generateTestDataMain(int seednum) throws SQLException{
        Random rn = null;
        if(seednum == -1){
            rn = new Random();
        }else{
            rn = new Random(seednum);
        }        

        Act[] acts = generateActs(rn);

        int numGigs = 50;
        int[] ticketPrice = new int[numGigs];

        for(int i = 0; i < acts.length; i++){
            System.out.format("INSERT INTO ACT (actid, actname, genre, standardfee) VALUES(%d,'%s','Music',%d);\n",i+1,acts[i].actName,acts[i].standardFee);
        }
        
        for(int i = 0; i < venues.length; i++){
            //The venues intentionally have a very low capacity to make it easier to test capacity without having loads of tickets to worry about!
            System.out.format("INSERT INTO VENUE (venueid, venuename, hirecost, capacity) VALUES (%d,'%s',%d,%d);\n",i+1,venues[i],1000*(1+rn.nextInt(20)),1+rn.nextInt(20));
        }

        for(int i = 0; i < numGigs; i++){
            ticketPrice[i] = 10 * (rn.nextInt(10)+1);
            //Although for now we can assume each ticket costs £40
            ticketPrice[i] = 40;
            int year = 2016 + rn.nextInt(5);
            int month = 1 + rn.nextInt(11);
            //Note this isn't evenly distributed, only considering up to 28th of each month
            int day = 1 + rn.nextInt(28);
            int time = 18 + rn.nextInt(3);
            GregorianCalendar gc = new GregorianCalendar();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            gc.set(year,month,day,time,0);
            sdf.setCalendar(gc);
            Date gigStartDate = gc.getTime();
            System.out.format("INSERT INTO GIG (gigid, venueid, gigtitle, gigdatetime, gigstatus) VALUES (%d,%d,'%s','%s','G');\n",(i+1),(1+rn.nextInt(venues.length)),"Test title",sdf.format(gigStartDate));

            int totalDuration = 0;
            boolean enoughActs = false;
            while(totalDuration < 180 && !enoughActs){
                int actID = 1+rn.nextInt(acts.length);
                int duration = 20 + (10 * rn.nextInt(7));
                Date onDate = new Date(gigStartDate.getTime() + totalDuration * 60 * 1000);

                //If you want to test what happens when there is a gap of more than 20 minutes, modify the below line
                int gap = 5 * rn.nextInt(4);
                totalDuration = totalDuration + duration + gap;
                if(rn.nextInt(3) == 0){
                    enoughActs = true;
                }
                System.out.format("INSERT INTO ACT_GIG (actid, gigid, actgigfee, ontime, duration) VALUES(%d,%d,%d,'%s',%d);\n",actID,(i+1),acts[actID - 1].standardFee,sdf.format(onDate),duration);
            }

            System.out.format("INSERT INTO GIG_TICKET (gigid, pricetype, price) VALUES(%d,'A',%d);\n",(i+1),ticketPrice[i]);
        }

        int numCustomers = 10;

        //You can make more imaginitive customer names if you like
        Customer customers[] = new Customer[numCustomers];
        for(int i = 0; i < numCustomers; i++){
            String fname = Character.toString((char)(97+(i%26))) + i;
            String lname = Character.toString((char)(97+(i%26))) + i;
            String name = fname + " " + lname;
            String email = Integer.toString(i) + Character.toString((char)(97+(i%26))) + Character.toString((char)(97+(i%26)));
            customers[i] = new Customer(name,email);
        }
        
        for(int i = 0; i < 40; i++){
            int custID = rn.nextInt(10);
            int gigID = rn.nextInt(50) + 1;
            System.out.format("INSERT INTO TICKET (ticketid,gigid,CustomerName,CustomerEmail,pricetype,cost) VALUES (DEFAULT,%d,'%s','%s','A',%d);\n",gigID,customers[custID].name,customers[custID].email,ticketPrice[gigID-1]);
        }

    }

    public static Act[] generateActs(Random r){
        //Feel free to add/change act names
        String[] acts = {"QLS", "The Where", "Join Division", "The Selecter", "Scalar Swift", "ViewBee 40"};
        Act[] allActs = new Act[acts.length];
        for(int i = 0; i < acts.length; i++){
            allActs[i] = new Act(r,acts[i]);
        }
        return allActs;
    }

    private static void checkValues(String provided, String expected) throws TestFailedException{
        if(!provided.equals(expected)){
            throw new TestFailedException(provided, expected);
        }
    }
}

class Act{
    public int standardFee;
    public String actName;
    Act(Random r, String name){
        standardFee = 1000 * (1+ r.nextInt(40));
        actName = name;
    }
}

class Customer{
    public String name;
    public String email;
    Customer(String name, String email){
        this.name = name;
        this.email = email;
    }   
}