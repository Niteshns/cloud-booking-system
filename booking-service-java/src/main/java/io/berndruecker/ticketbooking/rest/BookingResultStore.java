package io.berndruecker.ticketbooking.rest;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for booking results.
 * When PUT /ticket fires the workflow asynchronously, the result is stored here
 * once the workflow completes. GET /ticket/{id} reads from this store.
 */
@Component
public class BookingResultStore {

    public enum BookingStatus {
        PROCESSING, COMPLETED, FAILED
    }

    public static class BookingResult {
        public BookingStatus status;
        public String bookingReferenceId;
        public String reservationId;
        public String paymentConfirmationId;
        public String ticketId;

        public BookingResult(String bookingReferenceId) {
            this.bookingReferenceId = bookingReferenceId;
            this.status = BookingStatus.PROCESSING;
        }
    }

    private final ConcurrentHashMap<String, BookingResult> store = new ConcurrentHashMap<>();

    public void markProcessing(String bookingReferenceId) {
        store.put(bookingReferenceId, new BookingResult(bookingReferenceId));
    }

    public void markCompleted(String bookingReferenceId, String reservationId,
                              String paymentConfirmationId, String ticketId) {
        BookingResult result = store.computeIfAbsent(bookingReferenceId, BookingResult::new);
        result.status = BookingStatus.COMPLETED;
        result.reservationId = reservationId;
        result.paymentConfirmationId = paymentConfirmationId;
        result.ticketId = ticketId;
    }

    public void markFailed(String bookingReferenceId) {
        BookingResult result = store.computeIfAbsent(bookingReferenceId, BookingResult::new);
        result.status = BookingStatus.FAILED;
    }

    public BookingResult get(String bookingReferenceId) {
        return store.get(bookingReferenceId);
    }
}
