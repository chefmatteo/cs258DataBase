# Tasks: 
## Task 1:
- The Java program needs to be able to find the line-up for any given gigID. There should be the actname, the time they will start and the time they will finish. Times should be given in 24 hour clock, without seconds. 
- The task1 method must return this information in the two-dimensional array of strings, as in the example shown below. 
```text
[[“ViewBee 40”, ”18:00”, ”18:50”], 
 
[“The Where”, ”19:00”, ”20:25”], 
 
[“The Selecter”, ”20:25”, ”21:25”]] 
``` 
where the columns are 'Act Name', 'On Time', 'Off Time' respectively. 
Note: You must return the array of Strings in the format described above. 

## Task 2: 
- Set up a new gig at a given venue (referred to as a string containing the venue name). 
- There will be an array of ActPerformanceDetails objects which gives details of the actID, the fee, the datetime the act will start, and the duration of the act. There will be a standard adult ticket price provided (adultTicketPrice). 
- The ActPerformanceDetails will not necessarily be provided in chronological order. 
- If any details of the gig (or acts) violate any of the business rules do not accept this gig, and ensure the database state is as it was before the method was called. 


## Task 3: 
- A customer wants to buy a ticket. You will be provided with a gigID, a customer name, email and a ticketType (which may match a pricetype from gig_ticket). If any details are inconsistent (e.g. if the gig does not exist, or there is no matching pricetype, or there is some other error), do not allow the ticket purchase and ensure the database state is as it was before the method was called


## Task 4: 
- An act needs to cancel a gig (gigID and actName supplied). 
- Remove all performances of the specified act from the specified gig. Adjust the ontime of all subsequent performances in the gig to remove the gap left by the cancellation (e.g., if the cancelled performance lasts 40 minutes, move all subsequent acts 40 minutes earlier). Do not attempt to connect/squash intervals (e.g. if the performance was between a 20 minute interval and a 20 minute interval, there would now be a 40 minute interval, which would violate the business rules). If the cancellation of the act would violate the 
constraints in the specification (e.g. intervals that are too long), or if the act is the headline act for this gig (the final or only act), cancel the entire gig. 
 
**To cancel just an act (updated line-up)** 
- If the gig does not need to be cancelled, remove the act from the gig, adjust the timing as described above, and return the updated line-up (in the same format as task 1). 

**To cancel an entire gig**
- Change gigstatus to 'C' (cancelled). Leave all gig line-up (act_gig) details as they are (do not remove the cancelling act), but change the cost of all tickets sold for that gig to be 0 (but do not change the original price of the ticket). 
- Return a 2D array of strings containing names and email addresses of customers who have affected tickets, ordered by customer name (ascending alphabetical order), containing no duplicates. 

## Task 5: Tickets needed to sell@: 
- For each gig, find how many more tickets (of the cheapest ticket price for that gig) still need to be sold for the promoters to, at least, be able to pay ¬ll the agreed fees (as listed in the act_gig table, not from the act's standard fee) for the acts and to pay the venue fee. 
- The output should only include the gigID and the tickets required to sell, ordered by gigID (smallest first). Include gigs that haven't sold any tickets yet. If a gig has already sold enough tickets to pay the agreed fees, represent this as 0 tickets. 
- You must return an array of strings that represent the table. 

Sample output (headings must not be in your output, these illustrate expected column order):
```text
gigID  Tickets To Sell 
1      1600 
2      2000 
3      1525 
```

## Task 6: How many tickets sold: 
- eate a 2-dimensional array of strings to show the total number of tickets (of any pricetype) that each act has sold. Only consider gigs where the act is listed as a headline act (the final or only act), and only include gigs that are not listed as cancelled. 
- For each act, find the number of tickets sold per year and show the total number of tickets ever sold by each act when the act was a headline act. 
- Order your result with the acts who have sold the least total number of tickets first, then ordered by Year (with 'Total' at the end of each group of years). 
Sample output (headings must not be in your output, these illustrate expected column order): Act name Year Total Tickets Sold 
QLS 2018 2 
QLS 2019 1 
QLS Total 3 
ViewBee 40 2017 3 
ViewBee 40 2018 1 
ViewBee 40 Total 4 
Scalar Swift 2017 3 
Scalar Swift 2018 1 
Scalar Swift 2019 1 
Scalar Swift Total 5 
Join Division 2016 2 
Join Division 2018 2 
Join Division 2020 3 
Join Division Total 7 
The Selecter 2017 4 
The Selecter 2018 4 
The Selecter Total 8 
The Where 2016 1 
The Where 2017 3 
The Where 2018 5 
The Where 2020 4 
The Where Total 13 

## Task 7: Regular Customers 
- The festival organisers want to know who regularly attends gigs that feature particular acts. 
- Create a two-dimensional array of strings that shows each act who has ever performed a gig as a headline act (the final or only act) along with the names of customers who have attended at least one of these gigs per calendar year (if the act performed such a gig as a headline act in that year). The output should list the acts in alphabetical order and the customers in order of the number of tickets that the customer has ever bought for a gig where that act was the headline act (with customers who have bought the most tickets listed first). 
- Only consider gigs that have not been cancelled. If the act has no such customers, and the act has played gigs as a headline act, make sure the act is still listed, but with '[None]' in the Customer Name column. 
- Sample output (headings must not be in your output, these illustrate expected column order): 
 
Act Name Customer Name 
Join Division G Jones 
QLS [None] 
Scalar Swift G Jones 
Scalar Swift J Smith

## Task 8: Economically Feasible Gigs 
- The festival organisers want to organise a gig with a single act. They're trying to choose an act for a specific venue, but don't want to charge more than the average ticket price, rounded to the nearest £ (you can assume this is a positive number, not free, and there will always be a positive number of tickets sold). 
- However, the organisers want to make sure they break even, so they need to be able to sell enough tickets to be able to pay for the act's standardfee and the cost of hiring the venue. 
Based on the standardfee for each act, and the average price amongst all tickets that have been sold 
(amongst all gigs that have not been cancelled), create a two-dimensional array of strings that includes each venue and each act that it would be economically feasible to book in that venue (assuming that act is theonly act at the gig). If there are no such acts, do not include any rows for the venue. 
- Also include the minimum number of tickets that would need to be sold (assuming each ticket is sold at the average price). Order your output in alphabetical order of venue name, then the number of tickets required (with highest number of tickets first). Hint: tickets can only be sold in integer numbers; it's impossible to sell half a ticket. 
- Sample output (headings must not be in your output, these illustrate expected column order): 
 
Venue Name Act Name Tickets Required 
Arts Centre Theatre Join Division 150 
Big Hall The Where 675 
Big Hall Join Division 375 
Cinema Join Division 175 
City Hall Join Division 225 
Symphony Hall ViewBee 40 1275 
Symphony Hall Scalar Swift 1250 
Symphony Hall QLS 1225 
Symphony Hall The Selecter 1200 
Symphony Hall The Where 825 
Symphony Hall Join Division 525