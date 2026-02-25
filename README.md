# DevOpsUvA

## Overview

This is a ticket booking system running on AWS. A user sends a REST request to the Booking Service, which triggers a workflow in Zeebe (Camunda). Zeebe then drives three microservices in order: Seat Reservation, Payment, and Ticket Generation. Each microservice is independent and has its own dedicated database.

## CI/CD Pipeline

All code is stored on GitHub. When a developer pushes, GitHub Actions automatically builds the Docker images, runs the tests, and pushes the images to Amazon ECR (Elastic Container Registry). The microservices running inside the EKS cluster pull their images directly from ECR.

## AWS Infrastructure

All services run inside an Amazon EKS cluster. EKS is a managed Kubernetes service, meaning AWS handles the control plane and the team only manages the workloads. Each microservice runs on its own EC2 node within the cluster.

## Booking Service

The Booking Service is built with Java Spring Boot. It is the entry point for all user requests. It receives the booking request over REST and uses gRPC to start a workflow instance in Zeebe.

## Zeebe (Camunda 8)

Zeebe is the workflow engine that orchestrates the entire booking process. It manages the state of every booking, drives each step in order, and handles retries when something fails. The booking process has three steps:

1. Zeebe publishes the seat reservation job over gRPC. The Seat Reservation service picks it up, reserves the seat, and reports completion back to Zeebe.

2. Zeebe publishes the payment job over AMQP. The Payment service picks it up, processes the payment, and reports completion back to Zeebe.

3. Zeebe publishes the ticket generation job over REST. The Ticket Generation service picks it up, generates the ticket, and reports completion back to Zeebe.

Once all three steps are done, Zeebe marks the process as finished and the Booking Service returns the result to the user.

## Microservices

**Seat Reservation** runs on EC2. It uses Amazon ElastiCache (Redis) as a cache and Amazon RDS as the persistent database for seat data.

**Payment** runs on EC2. It uses Amazon RDS (Postgres) to store payment records. Postgres is used because payment data is relational and transactional.

**Ticket Generation** runs on EC2. It uses Amazon RDS (Postgres) to store generated tickets.

## Monitoring

Prometheus scrapes metrics from all services and is where alerting rules are defined. When an alert fires, Alertmanager routes the notification to Slack or Email. Grafana connects to Prometheus and provides dashboards for visualising the health of the system.

