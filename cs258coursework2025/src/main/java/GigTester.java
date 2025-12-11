import java.util.Date;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.Random;
import java.util.GregorianCalendar;

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
                            System.out.println("Test 3 status: " + testTask3());
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
        
        LocalDateTime[] onDates = new LocalDateTime[3];
        onDates[0] = LocalDateTime.of(2021,java.time.Month.NOVEMBER,02,20,00);
        onDates[1] = LocalDateTime.of(2021,java.time.Month.NOVEMBER,02,20,35);
        onDates[2] = LocalDateTime.of(2021,java.time.Month.NOVEMBER,02,21,20);
        ActPerformanceDetails[] apd = new ActPerformanceDetails[3];
        apd[0] = new ActPerformanceDetails(3, 20000, onDates[0], 30);
        apd[1] = new ActPerformanceDetails(4, 30000, onDates[1], 40);
        apd[2] = new ActPerformanceDetails(6, 10000, onDates[2], 20);

        // Call task2 to create the gig
        GigSystem.task2(conn, venues[3], "The November Party", onDates[0], 40, apd);
        
        try {
            // Get the new gigid (should be maxGigIdBefore + 1 if successful)
            int maxGigIdAfter = getMaxGigId(conn);
            
            // Verify a new gig was created
            if (maxGigIdAfter <= maxGigIdBefore) {
                System.err.println("Test failed: No new gig was created");
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
            // Act 2: 20:35, duration 40 -> ends 21:15
            // Act 3: 21:20, duration 20 -> ends 21:40
            String[] expectedOnTimes = {"20:00", "20:35", "21:20"};
            String[] expectedOffTimes = {"20:30", "21:15", "21:40"};
            
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
        int gigid = 24;
        String name = "B Simpson";
        String email = "bsimpson@testemail";

        GigSystem.task3(GigSystem.getSocketConnection(), gigid, name, email,"A");

        System.out.println("Should test task3 - you need to implement the test");
        return false;
        //You should put some test code here to read the state of the database after calling task 3 and check it is what you expected
        //You could also call testTask1 here to check the scshedule matches what you think it should
    }

    public static boolean testTask4(){
        int cancelGigID = 40;
        String actName = "Scalar Swift";
        GigSystem.task4(GigSystem.getSocketConnection(),cancelGigID,actName);
        System.out.println("Should test task4 - you need to implement the test");

        //You should put some code here to read the state of the database after calling task 4 to confirm that the input gig has been cancelled

        //For instance, if gigid 40 was to be cancelled in the main test data, the result should be:
        // String[][] expectedEmails = {{"c2 c2","c2cc@example.com"},{"d3 d3","d3dd@example.com"}};

        //In the case the gig is not cancelled, you can do something similar to testTask1
        
        return false;
    }

    public static boolean testTask5(){
        String[][] out = GigSystem.task5(GigSystem.getSocketConnection());
        //This data should work for the main set of test data.
        int numTickets[] = {1600, 2000, 1525, 1225, 1650, 1525, 1300, 1850, 2023, 398, 1873, 1849, 1125, 1949, 1498, 1073, 1900, 399, 749, 1425, 697, 1098, 2875, 825, 1224, 1849, 1149, 1525, 1625, 1548, 850, 300, 524, 775, 1297, 2522, 1274, 1150, 2250, 1223, 1974, 950, 775, 525, 749, 1800, 1900, 973, 298, 2275};
        try{
            for(int i = 1; i < numTickets.length; i++){
                checkValues(out[i-1][0],String.valueOf(i));
                checkValues(out[i-1][1],String.valueOf(numTickets[i-1]));
            }
        }catch(Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
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