# DevOpsUvA Ticket Booking on Camunda 8

## Overview

This repository contains a ticket-booking demo running on AWS with Camunda 8 (Zeebe) orchestration.

Flow summary:

1. A client calls the Booking Service REST API.
2. The Booking Service starts the ticket-booking BPMN process in Zeebe.
3. Zeebe coordinates three steps: seat reservation, payment retrieval, and ticket generation.
4. The Booking Service exposes booking status until the process completes.

## Components

- booking-service-java: Java Spring Boot service that starts process instances and serves booking status.
- seat-service-nodejs: Zeebe worker for reserve-seats jobs.
- payment-service-nodejs: RabbitMQ consumer for payment requests and publisher for payment responses.
- ticket-service-nodejs: REST service used by the booking flow to generate a ticket.
- k8s: Kubernetes manifests for namespace, deployments, services, and HPAs.

## Runtime Architecture

- Platform: Amazon EKS (Kubernetes).
- Images: Amazon ECR.
- Messaging and orchestration:
	- Zeebe jobs for reserve-seats and retrieve-payment command handling.
	- RabbitMQ queues paymentRequest and paymentResponse for payment handoff.
	- REST call to the ticket-service /ticket endpoint for ticket generation.
- Databases: each Node.js microservice writes to its own MySQL-compatible Amazon RDS instance.

Note: workloads run as Kubernetes pods. They are not pinned to one EC2 node per microservice.

## CI and CD

GitHub Actions workflow in .github/workflows/ci-build.yaml does the following on push to main:

1. Builds booking-service-java with Maven package step.
2. Builds and pushes Docker images for all four services to ECR.
3. Tags images with both commit SHA and latest.
4. Applies Kubernetes manifests (HPAs, services, configmaps) from the k8s directory.
5. Updates image tags in EKS deployments using kubectl set image.
6. Waits for rollout completion for each deployment.

Important: current workflow skips tests for booking-service-java and does not run test stages for Node.js services.

## Autoscaling

Each microservice has a HorizontalPodAutoscaler defined in the k8s directory. The HPAs scale based on average CPU utilization with a target of 65%. Minimum replicas is set to 1 and maximum to 5 for all services. This keeps pods available under load while staying within the capacity of the EKS cluster, which runs 2 nodes by default and can scale up to 5.

The HPA manifests are applied as part of the CI/CD pipeline alongside other Kubernetes resources, so any changes to scaling limits are picked up automatically on the next push to main.

## Monitoring

Observability is handled through Amazon CloudWatch Container Insights, which collects metrics from the EKS cluster and the RDS instances.

A custom CloudWatch dashboard (ticketMonitoring) tracks the following:

- CPU and memory usage per service (booking, seat, payment, ticket).
- Request count and latency across the services.
- Number of running pods per service, which reflects HPA scaling activity.
- RDS read/write latency and IOPS for the payment and ticket-generation databases.
- Application error rate.

The dashboard also surfaces application logs from the /aws/containerinsights/ticketing-cluster/application log group, which aggregates stdout from all pods in the ticketing namespace. This is useful for spotting startup failures, Zeebe connection issues, or RabbitMQ errors without having to kubectl into the cluster.

CloudWatch alarms are configured for CPU and memory usage on each service (e.g. Booking-service-cpu-alert, Seat-service-memory-alert) as well as a latency alarm. These fire when resource usage or response times exceed defined thresholds.

## Kubernetes Manifests

Core manifests are in k8s:

- namespace.yaml
- rabbitmq-deployment.yaml
- booking-deployment.yaml and booking-service.yaml
- seat-deployment.yaml and seat-service.yaml
- payment-deployment.yaml and payment-service.yaml
- ticket-deployment.yaml and ticket-service.yaml
- booking-hpa.yaml, seat-hpa.yaml, payment-hpa.yaml, ticket-hpa.yaml
- kustomization.yaml to apply the full stack
