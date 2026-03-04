package io.berndruecker.ticketbooking.adapter;

import io.berndruecker.ticketbooking.ProcessConstants;
import io.berndruecker.ticketbooking.rest.BookingResultStore;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

@Component
public class GenerateTicketAdapter {

  Logger logger = LoggerFactory.getLogger(GenerateTicketAdapter.class);

  @Value("${ticket.service.endpoint:http://localhost:3000/ticket}")
  private String endpoint;

  @Autowired
  private RestTemplate restTemplate;

  @Autowired
  private BookingResultStore resultStore;

  @JobWorker(type = "generate-ticket")
  public Map<String, Object> callGenerateTicketRestService(final ActivatedJob job) throws IOException {
    logger.info("Generate ticket via REST [" + job + "]");

    Map<String, Object> vars = job.getVariablesAsMap();

    if ("ticket".equalsIgnoreCase((String) vars.get(ProcessConstants.VAR_SIMULATE_BOOKING_FAILURE))) {

      // Simulate a network problem to the HTTP server
      String bookingRef = (String) vars.get(ProcessConstants.VAR_BOOKING_REFERENCE_ID);
      if (bookingRef != null) {
        resultStore.markFailed(bookingRef);
      }
      throw new IOException("[Simulated] Could not connect to HTTP server");
      
    } else {
      
      // Call REST API, simply returns a ticketId
      CreateTicketResponse ticket = restTemplate.getForObject(endpoint, CreateTicketResponse.class);
      logger.info("Succeeded with " + ticket);

      // Store completed result so GET /ticket/{id} can return it
      String bookingRef = (String) vars.get(ProcessConstants.VAR_BOOKING_REFERENCE_ID);
      String reservationId = (String) vars.get(ProcessConstants.VAR_RESERVATION_ID);
      String paymentConfirmationId = (String) vars.get(ProcessConstants.VAR_PAYMENT_CONFIRMATION_ID);
      if (bookingRef != null) {
        resultStore.markCompleted(bookingRef, reservationId, paymentConfirmationId, ticket.ticketId);
      }

      return Collections.singletonMap(ProcessConstants.VAR_TICKET_ID, ticket.ticketId);
    }
  }

  public static class CreateTicketResponse {
    public String ticketId;
  }
}
