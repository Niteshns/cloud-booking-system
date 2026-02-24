import amqp from "amqplib";
import express from "express";
import { v4 as uuidv4 } from "uuid";
import dotenv from "dotenv";

dotenv.config();

const app = express();
const port = Number(process.env.PORT ?? 3002);
const rabbitMqUrl = process.env.RABBITMQ_URL ?? "amqp://localhost:5672";

app.get("/healthz", (_req, res) => {
  res.status(200).json({ status: "ok", service: "payment-service" });
});

app.listen(port, () => {
  console.log(`Payment service health endpoint listening on port ${port}`);
});

async function startPaymentWorker() {
  const queuePaymentRequest = "paymentRequest";
  const queuePaymentResponse = "paymentResponse";

  const connection = await amqp.connect(rabbitMqUrl);
  const channel = await connection.createChannel();

  await channel.assertQueue(queuePaymentRequest, { durable: true });
  await channel.assertQueue(queuePaymentResponse, { durable: true });

  console.log("Payment worker connected to RabbitMQ", rabbitMqUrl);

  await channel.consume(
    queuePaymentRequest,
    (inputMessage) => {
      if (!inputMessage) {
        return;
      }

      const paymentRequestId = inputMessage.content.toString();
      const paymentConfirmationId = uuidv4();

      const outputMessage = JSON.stringify({ paymentRequestId, paymentConfirmationId });
      channel.sendToQueue(queuePaymentResponse, Buffer.from(outputMessage));
      channel.ack(inputMessage);

      console.log("Processed payment request", paymentRequestId);
    },
    { noAck: false }
  );
}

startPaymentWorker().catch((error) => {
  console.error("Payment worker failed", error);
  process.exit(1);
});
