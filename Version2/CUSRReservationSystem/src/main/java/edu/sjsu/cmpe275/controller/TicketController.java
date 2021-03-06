package edu.sjsu.cmpe275.controller;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
import edu.sjsu.cmpe275.model.Response;
import edu.sjsu.cmpe275.model.SearchResponse;
import edu.sjsu.cmpe275.model.Ticket;
import edu.sjsu.cmpe275.model.TicketResponse;
import edu.sjsu.cmpe275.model.TrainCapacity;
import edu.sjsu.cmpe275.model.User;
import edu.sjsu.cmpe275.repository.TicketRepository;
import edu.sjsu.cmpe275.repository.TrainCapacityRepository;
import edu.sjsu.cmpe275.repository.UserRepository;

@RestController 
@RequestMapping("/CUSR")
public class TicketController {
	@Autowired
	private TicketRepository ticketRepository; 
	//START: P-A
	@Autowired
	private UserRepository userRepository; 
	
	@Autowired
	private TrainCapacityRepository trainCapacityRepository; 
	//END: P-A
//************* START: REQUEST STRUCTURE (AASHI/PRANJALI) *************
	// Field passenferCount has been added to the structure that is forming the request here and is coming similar to how the search result was returned to Frontend
	//REQUEST STRUCTURE  <SINGLE TICKET>:
//	{
//        "trainNumber": "SB0630",
//        "departingStation": "A",
//        "arrivalStation": "D",
//        "departingTime": "06:30",
//        "arrivalTime": "06:51",
//        "fare": "$2",
//        "passengerCount": 4,
//        "departingDate1": "2017-12-18",
//        "departingDate2": null,
//        "userId": "12",
//        "connections":"One",
//        "ticketType": "Any",
//        "connected": []
//  }
	
	//REQUEST STRUCTURE<ONE CONNECTION>
//	{
//        "trainNumber": null,
//        "departingStation": "\u0000",
//        "arrivalStation": "\u0000",
//        "departingTime": null,
//        "arrivalTime": null,
//        "fare": "$3",
//        "passengerCount": 4,
//        "departingDate1": "2017-12-18",
//        "departingDate2": null,
//        "connections": null,
//        "ticketType": null,
//        "connected": [
//            {
//                "trainNumber": "SB0645",
//                "departingStation": "A",
//                "arrivalStation": "C",
//                "departingTime": "06:45",
//                "arrivalTime": "06:58",
//                "fare": "$2",
//                "passengerCount": 4,
//                "departingDate1": "2017-12-18",
//                "departingDate2": null,
//                "connections": "One",
//                "ticketType": "Any",
//                "connected": []
//            },
//            {
//                "trainNumber": "SB0745",
//                "departingStation": "C",
//                "arrivalStation": "D",
//                "departingTime": "08:01",
//                "arrivalTime": "08:06",
//                "fare": "$2",
//                "passengerCount": 4,
//                "departingDate1": "2017-12-18",
//                "departingDate2": null,
//                "connections": "One",
//                "ticketType": "Any",
//                "connected": []
//            }
//        ]
//    }
//************* END: REQUEST STRUCTURE (AASHI/PRANJALI) *************
	@RequestMapping(value = "/ticket", method = RequestMethod.POST)
    public ResponseEntity<?> createTicket(@RequestBody String payload) {
		try{
			System.out.println("************in createTicket************");
			JSONObject jsonObj = new JSONObject(payload.toString());
			//START: P-A
			JSONArray connected = (JSONArray) jsonObj.get("connected");
			TicketResponse response = new TicketResponse();
			Ticket ticket = new Ticket();
			Ticket ticketObj = new Ticket();
			if(connected.isNull(0)){
				System.out.println("IT IS A DIRECT TRAIN");
				ticket = getTicketObject(jsonObj);
				updateCapacity(ticket);
				ticketObj = ticketRepository.save(ticket);
				response.getTicketResponse().add(ticketObj);
			}else{
				System.out.println("IT IS CONNECTED TRAIN");
				JSONObject first = (JSONObject) connected.get(0);
				JSONObject second = (JSONObject) connected.get(1);
				ticket = getTicketObject(first);
				ticket.setFare(Integer.parseInt(jsonObj.getString("fare").substring(1)));
				ticket.setEmail(jsonObj.getString("userId"));
				//START: Saving 1st ticket and updating the train_capacity
				updateCapacity(ticket);
				ticketObj = ticketRepository.save(ticket);
				response.getTicketResponse().add(ticketObj);
				//END: Saving 1st ticket and updating the train_capacity
				//Since same transaction id has to saved for the second ticket as well
				String transactionId = ticket.getTransactionId();
				ticket = getTicketObject(second);
				ticket.setFare(Integer.parseInt(jsonObj.getString("fare").substring(1)));
				ticket.setTransactionId(transactionId);
				ticket.setEmail(jsonObj.getString("userId"));
				//START: Saving 1st ticket and updating the train_capacity
				updateCapacity(ticket);
				ticketObj = ticketRepository.save(ticket);
				response.getTicketResponse().add(ticketObj);
				//END: Saving 1st ticket and updating the train_capacity
			}
			//END: P-A
			//JsonObj.has("searchResponse")==true ? jsonObj.getBoolean("isRoundTrip"):false;
	        return new ResponseEntity<>(response, HttpStatus.OK);
    	}
    	catch(Exception e){
    		System.out.println("exception="+e);
    		return new ResponseEntity<>("BadRequest",HttpStatus.BAD_REQUEST);
    	}
    }
//START: P
	private void updateCapacity(Ticket ticket) throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		// TODO Auto-generated method stub
		List<TrainCapacity> list = trainCapacityRepository.searchTrainsToUpdateCapacity(ticket.getTrainNumber(), ticket.getTravelDate());
		System.out.println("GETTING LIST***********************************************");
		TrainCapacity tc = list.get(0);
		int from = ticket.getDepartingStation() - 'A';
		int to = ticket.getArrivalStation() - 'A';
		System.out.println(from + " " + to);
		for(int i = from; i<to; i++){
			char station = (char)('A' + i);
			String str = station + "_capacity";
			Field field = tc.getClass().getDeclaredField(str);
			int currentCapacity = field.getInt(tc);
			int newCapacity = currentCapacity - ticket.getPassengerCount();
			field.setInt(tc, newCapacity);
			//field.setInt(tc, 5);
		}
		TrainCapacity tcObj = trainCapacityRepository.save(tc);
		System.out.println("After saving to database ");
	}

	private Ticket getTicketObject(JSONObject jsonObj) throws ParseException {
		// TODO Auto-generated method stub
		Ticket ticket = new Ticket();
		ticket.setTransactionId(String.valueOf(UUID.randomUUID()));
		try {
			ticket.setTrainNumber(jsonObj.getString("trainNumber"));
			if(jsonObj.has("userId")){
				ticket.setEmail(jsonObj.getString("userId"));
			}
			ticket.setPassengerCount(jsonObj.getInt("passengerCount"));
			ticket.setConnections(jsonObj.getString("connections"));
			ticket.setTicketType(jsonObj.getString("ticketType"));
			ticket.setDepartingStation(jsonObj.getString("departingStation").charAt(0));
			ticket.setArrivalStation(jsonObj.getString("arrivalStation").charAt(0));
		   // Date date1=new SimpleDateFormat("yyyy-MM-dd").parse(jsonObj.getString("departingDate1"));
		    java.sql.Date sqlDate = java.sql.Date.valueOf( jsonObj.getString("departingDate1") );
		    ticket.setTravelDate(sqlDate);
		    ticket.setDepartureTime(jsonObj.getString("departingTime"));
		    ticket.setArrivalTime(jsonObj.getString("arrivalTime"));
		    ticket.setFare(Integer.parseInt(jsonObj.getString("fare").substring(1)));
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return ticket;
	}
	
	//END: P

//	private SearchResponse getRequestInformation(JSONObject jsonObj) {
//		// TODO Auto-generated method stub
//		SearchResponse response = new SearchResponse();
//		try {
//			response.setTrainNumber(jsonObj.getString("trainNumber"));
//			response.setDepartingStation(jsonObj.getString("departingStation").charAt(0));
//			response.setArrivalStation(jsonObj.getString("arrivalStation").charAt(0));
//			response.setPassengerCount(jsonObj.getInt("passengerCount"));
//			response.setDepartingDate1(jsonObj.getString("departingDate1"));
//		} catch (JSONException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		return response;
//	}
}
