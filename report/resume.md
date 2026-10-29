**Habit Tracker, CI/CD Pipeline & Kubernetes Deployment** | https://github.com/themdmohsin/habit-tracker.git
*Java 17, Spring Boot, Maven, Git, Jenkins, GitHub Actions, Ansible, Docker, Kubernetes*

- Built a Jenkins CI pipeline (SCM polling every minute) for a Spring Boot REST API that runs JUnit tests before the build and archives the JAR, with a matching GitHub Actions workflow; a failing test blocks the artifact.
- Automated deployment with an idempotent Ansible role (systemd service), a multi-stage Docker image with a non-root user, and a 3-replica Kubernetes Deployment using a ConfigMap, liveness/readiness probes and a NodePort Service; manually scaled 3 to 5 to 3 replicas and demonstrated rollback from a failed image rollout.
