import express from "express";
import { ZBClient } from "zeebe-node";
import dotenv from "dotenv";
import mysql from "mysql2/promise";

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

zeebeClient.createWorker("reserve-seats", async (job) => {
  console.log("Reserve seats for", job.variables?.bookingReferenceId);

  if (job.variables?.simulateBookingFailure === "seats") {
    return job.error("ErrorSeatsNotAvailable");
  }

  let connection;

  try {
    connection = await mysql.createConnection({
      host: 'seat-reservation-db.c1co0e8k8s29.eu-west-3.rds.amazonaws.com',
      user: "admin",
      password: "devopsUVA!",
      database: "seat_service",
      port: 3306,
    });

    console.log("✅ Connected to RDS");

    await connection.execute(
      `
        INSERT INTO seats
        (event_id, venue_id, seat_id, user_id, status)
        VALUES (?, ?, ?, ?, ?)
      `,
      [1, 1, 105, 312, "reserved"]
    );

    console.log("✅ Seat inserted");

    return job.complete({ reservationId: "1234" });
  } catch (error) {
    console.error("DB insert failed:", error);
    return job.error("ErrorDatabaseInsertFailed");
  } finally {
    if (connection) {
      await connection.end();
    }
  }
});