import express from "express";
import { v4 as uuidv4 } from "uuid";
import dotenv from "dotenv";

dotenv.config();

const app = express();
const port = Number(process.env.PORT ?? 3000);

app.get("/healthz", (_req, res) => {
  res.status(200).json({ status: "ok", service: "ticket-service" });
});

app.get("/ticket", (_req, res) => {
  const ticketId = uuidv4();
  console.log("Create ticket", ticketId);
  res.json({ ticketId });
});

app.listen(port, () => {
  console.log(`Ticket service listening on port ${port}`);
});
