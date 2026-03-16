import express from "express";
import { v4 as uuidv4 } from "uuid";
import dotenv from "dotenv";
import mysql from "mysql2/promise";

dotenv.config();

const app = express();
const port = Number(process.env.PORT ?? 3000);

app.get("/healthz", (_req, res) => {
  res.status(200).json({ status: "ok", service: "ticket-service" });
  console.log("Health check endpoint called");
});

app.get("/ticket", async (_req, res) => {
  const ticketId = uuidv4();
  console.log("\n\n [x] Create Ticket %s", ticketId);

  const userId = 67;
  const eventId = 444;
  const eventTag = "pre-final-presentation";

  let connection;
  try {
    connection = await mysql.createConnection({
      host: "ticket-generation-db.ctwgyeke24cg.eu-north-1.rds.amazonaws.com",
      user: "admin",
      password: "devopsUVA!",
      database: "ticket_generation",
      port: 3306,
    });
    console.log("✅ Connected to RDS");

    await connection.execute(
      "INSERT INTO ticket_generation (user_id, event_id, event_tag, created_at) VALUES (?, ?, ?, NOW())",
      [userId, eventId, eventTag]
    );
    console.log("✅ Ticket inserted");

    console.log("Create ticket", ticketId);
    res.json({ ticketId });
  } catch (error) {
    console.error("DB insert failed:", error);
    res.status(500).json({ error: "failed_to_create_ticket" });
  } finally {
    if (connection) await connection.end();
  }
});

app.listen(port, () => {
  console.log(`Ticket service listening on port ${port}`);
});
