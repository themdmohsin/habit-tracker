**habit-tracker: CI/CD Pipeline & Kubernetes Deployment** | https://github.com/themdmohsin/habit-tracker.git
*Java/Spring Boot, Git, Jenkins, GitHub Actions, Docker, Ansible, Kubernetes*

- Built a Spring Boot 3 (Java 17) REST API for habit tracking (create, check-in with daily streaks, delete, `/info` pod endpoint) with a single-page web UI and 10 JUnit tests.
- Set up CI with automated tests in a Jenkins pipeline (Checkout, Test, Build, Archive; SCM polling every minute) and a GitHub Actions workflow that builds and tests on every push, using Git feature branches, merges and a pull request.
- Containerized the app with a multi-stage Docker build (no local JDK/Maven needed, cached dependency layer, non-root user) and deployed it on Kubernetes with 3 replicas behind a NodePort Service with ClientIP session affinity.
- Wrote an Ansible role with variables and a restart handler that deploys the app as a systemd service under a dedicated user.
- Added deployment enhancements: ConfigMap-based configuration, liveness/readiness probes, and a rollback demo (bad image tag, then `kubectl rollout undo`); manually scaled to 5 replicas and back to 3.