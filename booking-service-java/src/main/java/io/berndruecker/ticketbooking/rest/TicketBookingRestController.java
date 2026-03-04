package io.berndruecker.ticketbooking.rest;

import java.util.HashMap;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import io.berndruecker.ticketbooking.ProcessConstants;
import io.berndruecker.ticketbooking.adapter.RetrievePaymentAdapter;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.spring.client.EnableZeebeClient;

@SpringBootConfiguration
@RestController
@EnableZeebeClient
public class TicketBookingRestController {

  private Logger logger = LoggerFactory.getLogger(RetrievePaymentAdapter.class);

  @Autowired
  private ZeebeClient client;

  @Autowired
  private BookingResultStore resultStore;

  @PutMapping("/ticket")
  public ResponseEntity<BookTicketResponse> bookTicket(ServerWebExchange exchange) {
    String simulateBookingFailure = exchange.getRequest().getQueryParams().getFirst("simulateBookingFailure");

    BookTicketResponse response = new BookTicketResponse();
    response.bookingReferenceId = UUID.randomUUID().toString();
    response.status = "processing";

    HashMap<String, Object> variables = new HashMap<String, Object>();
    variables.put(ProcessConstants.VAR_BOOKING_REFERENCE_ID, response.bookingReferenceId);
    if (simulateBookingFailure != null) {
      variables.put(ProcessConstants.VAR_SIMULATE_BOOKING_FAILURE, simulateBookingFailure);
    }

    // Track this booking
    resultStore.markProcessing(response.bookingReferenceId);

    // Start workflow asynchronously (fire-and-forget, no .withResult())
    client.newCreateInstanceCommand()
        .bpmnProcessId("ticket-booking")
        .latestVersion()
        .variables(variables)
        .send();

    logger.info("Started workflow for booking " + response.bookingReferenceId);

    // Return immediately with 202 Accepted
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @GetMapping("/ticket/{bookingReferenceId}")
  public ResponseEntity<BookTicketResponse> getBookingStatus(
      @PathVariable String bookingReferenceId) {

    BookingResultStore.BookingResult result = resultStore.get(bookingReferenceId);
    if (result == null) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    BookTicketResponse response = new BookTicketResponse();
    response.bookingReferenceId = result.bookingReferenceId;
    response.status = result.status.name().toLowerCase();
    response.reservationId = result.reservationId;
    response.paymentConfirmationId = result.paymentConfirmationId;
    response.ticketId = result.ticketId;

    if (result.status == BookingResultStore.BookingStatus.COMPLETED) {
      return ResponseEntity.status(HttpStatus.OK).body(response);
    } else {
      return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }
  }

  public static class BookTicketResponse {
    public String status;
    public String reservationId;
    public String paymentConfirmationId;
    public String ticketId;
    public String bookingReferenceId;

    public boolean isSuccess() {
      return (ticketId != null);
    }
  }
}
