# DevOpsUvA
## CI/CD Pipeline

When a developer pushes changes to the Booking Service or Fake Services repository, GitHub Actions automatically builds and tests the application. After a successful build, the Docker images are pushed to Amazon Elastic Container Registry (ECR).

The Kubernetes Manifests repository contains all deployment YAML files required to run the application inside the cluster. The CI pipeline updates the image tag in this repository, after which ArgoCD automatically synchronises the changes with the Amazon EKS cluster following a GitOps approach.

## AWS Infrastructure

The application runs inside an Amazon EKS cluster. Once ArgoCD applies the Kubernetes manifests, the cluster pulls the container images from Amazon ECR and deploys them as pods.

External user traffic enters the system through the NGINX Ingress Controller, which handles HTTPS termination and routes requests to the appropriate Kubernetes Service. The Service then load-balances traffic across the scaled Spring Boot pods.

The Booking Service communicates with Camunda 8 (Zeebe) via gRPC to start and manage workflow instances. Camunda orchestrates the ticket booking process, including seat reservation, payment processing, and ticket generation. This is done in three steps.
1. Camunda publishes the reserve seat job. The booking service picks it up and executes the seat reservation. The booking service reports the completion back to Camunda via gRPC.
2. Camunda publishes the retrieve payment job. The booking service picks it up and sends a payment request to RabbitMQ via AMQP. The fake service listens on RabbitMQ, processes the payment, and publishes a response. The booking service receives the payment confirmation and sends it back to Camunda using the Zeebe message.
3. Camunda publishes the generate ticket job. The booking service picks it up and makes a synchronous rest call to the fake service, which generates and returns the ticket. The worker reports the completion back to Camunda.

Once these three tasks are finished, Camunda finishes the process, and the booking service returns the result to the user.

## Autoscaling
The system uses two autoscaling mechanisms:
1. HPA is used in the booking service and the fake services deployments. It monitors the CPU and memory utilzation scaling the number of pods accordingly.
2. KEDA is used to watch the RabbitMQ queue and scale the fake services pods based on how many payment messages are waiting.


