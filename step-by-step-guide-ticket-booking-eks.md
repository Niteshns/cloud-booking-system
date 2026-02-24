# Step-by-Step Guide — Ticket Booking System (Fast-Track to EKS + HPA)

This guide is a **deadline-focused implementation plan** to get your project running in AWS with a **microservices split**, **EKS deployment**, and **autoscaling demo (HPA)**.

## Scope (what to finish first)

### Finish now (minimum strong demo)
- Split the system into separate deployable services
- Create separate Docker images (one per service)
- Push images to Amazon ECR
- Deploy services to a minimal Amazon EKS cluster
- Expose only the Booking Service publicly
- Add HPA to the Booking Service
- Run a load test and show scaling behavior
- Keep **Camunda SaaS** for now (because it already works)

### Postpone until after the deadline
- Camunda 8 Self-Managed on EKS (Helm)
- ArgoCD
- Prometheus/Grafana/Alertmanager
- AWS Secrets Manager integration (use K8s Secrets temporarily if needed)
- RabbitMQ (unless your current working flow depends on it)

---

## 1) Split the monolith into microservices (pragmatic version)

You do **not** need a perfect rewrite first. Do a practical split that is deployable.

### Target services
- **booking-service** (REST API entrypoint + Camunda SaaS client/orchestration)
- **seat-service**
- **payment-service**
- **ticket-service**

### Fast extraction approach
If the monolith currently calls internal methods like:
- `reserveSeat()`
- `chargePayment()`
- `generateTicket()`

Replace them with HTTP calls to separate services:
- `POST /reserve-seat` on `seat-service`
- `POST /pay` on `payment-service`
- `POST /generate-ticket` on `ticket-service`

This gives you **real deployable microservices** quickly.

---

## 2) Create a separate Dockerfile for each microservice

**Yes — create separate Dockerfiles** for each service you will deploy independently.

### Why this is needed
Each microservice should be:
- built independently
- versioned independently
- deployed independently
- scalable independently

### Minimum set of Dockerfiles
- `booking-service/Dockerfile`
- `seat-service/Dockerfile`
- `payment-service/Dockerfile`
- `ticket-service/Dockerfile`

### Also add health endpoints now
These help Kubernetes and HPA behavior:
- Java Spring Boot: `/actuator/health`
- Node.js services: `/healthz`

---

## 3) Prepare local folder structure

Use a structure like this (monorepo is fine):

```text
project/
  booking-service/
    Dockerfile
  seat-service/
    Dockerfile
  payment-service/
    Dockerfile
  ticket-service/
    Dockerfile
  k8s/
    namespace.yaml
    booking-deployment.yaml
    booking-service.yaml
    booking-hpa.yaml
    seat-deployment.yaml
    seat-service.yaml
    payment-deployment.yaml
    payment-service.yaml
    ticket-deployment.yaml
    ticket-service.yaml
```

If you already have `deployment.yaml`, `service.yaml`, and `kustomization.yaml`, duplicate/adapt them per service.

---

## 4) Test all services locally in Docker (before AWS)

Before touching AWS, verify each service runs in a container.

### What to test
- Booking service starts and can call Camunda SaaS
- Seat service responds to `/reserve-seat`
- Payment service responds to `/pay`
- Ticket service responds to `/generate-ticket`
- Health endpoints return OK

### Goal
You should be able to run the complete booking flow locally.

---

## 5) Set up AWS basics (minimal infrastructure only)

## 5.1 Create billing protection first
Create:
- AWS Budget (monthly cap alert)
- Email alarms at 50%, 80%, 100%

> EKS is **not free**. EKS charges a control plane fee and separate charges for compute, storage, load balancers, and more.

## 5.2 Choose one AWS region
Use one region only to reduce cost/complexity.

## 5.3 Create Amazon ECR repositories
Create one repo per service:
- `booking-service`
- `seat-service`
- `payment-service`
- `ticket-service`

## 5.4 Build and push images to ECR
For each service:
1. Build Docker image
2. Tag image with ECR repo URI
3. Push to ECR

---

## 6) Create a minimal EKS cluster

Create **one small EKS cluster** only.

### Keep it minimal
- Single cluster
- Minimal nodes
- No extra tooling yet (ArgoCD, Prometheus, etc.)

### Required setup after cluster creation
- Configure `kubectl`
- Confirm nodes are ready: `kubectl get nodes`

---

## 7) Install Metrics Server (required for HPA)

HPA needs a metrics source.

### Install Metrics Server
Install Kubernetes Metrics Server in the EKS cluster.

### Verify it works
- `kubectl top nodes`
- `kubectl top pods`

If these commands work, HPA can read CPU/memory metrics.

---

## 8) Deploy your microservices to EKS

## 8.1 Create a namespace
Use a dedicated namespace, for example:
- `ticketing`

## 8.2 Create Kubernetes manifests for each service
For each microservice, create:
- `Deployment`
- `Service` (ClusterIP)

### Important: add resource requests/limits
This is especially important for HPA.

Example (conceptually):
- CPU request set (e.g. `100m`)
- CPU limit set (e.g. `500m`)
- Memory request/limit set

## 8.3 Set environment variables (no hardcoded secrets)
Configure service URLs and Camunda values via environment variables.

Examples:
- `SEAT_SERVICE_URL`
- `PAYMENT_SERVICE_URL`
- `TICKET_SERVICE_URL`
- `CAMUNDA_CLIENT_ID`
- `CAMUNDA_CLIENT_SECRET`
- `CAMUNDA_CLUSTER_ID` / endpoint

> Do not commit secrets into YAML. Use K8s Secrets for now if needed.

## 8.4 Deploy all manifests
Apply namespace, deployments, and services.

## 8.5 Test internally first
Use port-forward on `booking-service` and verify the booking flow works **inside the cluster** before exposing it publicly.

---

## 9) Expose the Booking Service publicly (fastest path)

For the deadline, use the quickest working option:

### Option A (fastest): Service type LoadBalancer
Expose only the `booking-service` with `type: LoadBalancer`.

### Option B (later): ALB Ingress
Use AWS Load Balancer Controller + Ingress after the basic demo works.

### Important
Keep internal services private (`ClusterIP`):
- seat-service
- payment-service
- ticket-service

---

## 10) Add Horizontal Pod Autoscaler (HPA) to booking-service

This is your key autoscaling deliverable.

### Create an HPA resource for booking-service
Use CPU-based autoscaling first.

Recommended starting settings:
- `minReplicas: 1`
- `maxReplicas: 5`
- target CPU utilization (e.g. 60–70%)

### HPA prerequisites checklist
- Metrics Server installed ✅
- CPU requests set on the deployment ✅
- Load generator available ✅

---

## 11) Run a load test and demonstrate autoscaling

Generate traffic to the Booking API using a simple load tool.

### Possible tools
- `hey`
- `wrk`
- `ab`
- shell loop with `curl` (less ideal, but acceptable)

### What to monitor during the test
Run these in separate terminals:
- `kubectl get hpa -n ticketing -w`
- `kubectl get pods -n ticketing -w`
- `kubectl top pods -n ticketing`

### What to capture for your report/demo
- HPA output showing scaling target and current metrics
- Pod count increasing under load
- Pod count decreasing after load stops (if time allows)
- Short note on response behavior (latency/errors)

---

## 12) What to submit/demo (minimum solid deliverable)

### Functional proof
- Booking request works through Booking Service in AWS
- Camunda SaaS orchestration still works
- Services are separated and deployed independently

### Cloud-native proof
- Docker images in ECR
- Microservices running on EKS
- HPA scaling under load

### Evidence
- Architecture diagram (simple is fine)
- `kubectl get pods/services/hpa` screenshots
- Load test screenshots/results
- Short README with setup + known limitations

---

## 13) Common pitfalls (avoid these)

### Pitfall 1: Trying to finish self-managed Camunda + ArgoCD + monitoring now
**Fix:** keep Camunda SaaS for now and focus on EKS + HPA.

### Pitfall 2: HPA does not scale
**Fixes:**
- Install Metrics Server
- Add CPU requests to Deployment
- Increase load duration/intensity

### Pitfall 3: Public endpoint works but service-to-service calls fail
**Fixes:**
- Use Kubernetes service DNS names (`http://seat-service:PORT`)
- Check namespace and ports
- Check container logs

### Pitfall 4: Secrets leaked in manifests
**Fix:** use Kubernetes Secrets (temporary) and rotate exposed credentials if already committed.

---

## 14) After the deadline (upgrade path)

Once the basic EKS + HPA demo works, continue with your original stronger architecture:

1. Add RabbitMQ for async messaging/compensation
2. Add per-service databases (database-per-service pattern)
3. Move from Camunda SaaS to **Camunda 8 Self-Managed on EKS via Helm**
4. Add ALB Ingress
5. Add GitHub Actions CI/CD
6. Add ArgoCD (GitOps)
7. Add Prometheus/Grafana/Alertmanager
8. Integrate AWS Secrets Manager

---

## Quick answer to your main questions

### Do I need separate Dockerfiles for all microservices?
**Yes.** One Dockerfile per deployable microservice is the standard and the right move for Kubernetes.

### How do I separate the monolith quickly?
Keep the Booking Service as orchestrator/API, extract seat/payment/ticket actions into separate HTTP services, and replace internal method calls with network calls.

### I don’t have cloud infrastructure yet — can I still finish?
**Yes**, if you keep scope tight: **EKS + ECR + microservices + HPA**, and postpone self-managed Camunda/ArgoCD/monitoring.

---

## References (official docs)
- Amazon EKS pricing (costs and billing model)
- Amazon EKS HPA guide (Metrics Server + HPA usage on EKS)
- Kubernetes Horizontal Pod Autoscaler concepts
- Camunda 8 Self-Managed deployment docs (Helm recommended for Kubernetes)
