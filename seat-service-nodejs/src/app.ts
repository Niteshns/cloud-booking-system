import express from "express";
import { ZBClient } from "zeebe-node";
import dotenv from "dotenv";

dotenv.config();

const app = express();
const port = Number(process.env.PORT ?? 3001);

app.get("/healthz", (_req, res) => {
  res.status(200).json({ status: "ok", service: "seat-service" });
});

app.listen(port, () => {
  console.log(`Seat service health endpoint listening on port ${port}`);
});

const zeebeClient = new ZBClient();
zeebeClient.createWorker("reserve-seats", (job) => {
  console.log("Reserve seats for", job.variables?.bookingReferenceId);

  if (job.variables?.simulateBookingFailure === "seats") {
    return job.error("ErrorSeatsNotAvailable");
  }

  return job.complete({ reservationId: "1234" });
});
