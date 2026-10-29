# Component 2: Ansible, Docker and Kubernetes Deployment

Repository: https://github.com/themdmohsin/habit-tracker.git

## Abstract

This component takes the `habit-tracker.jar` produced in Component 1 and deploys it in three ways. Ansible configures a local Ubuntu machine (WSL) with Java and runs the application as a systemd service, idempotently. Docker packages the application as an image, upgraded from a single-stage to a multi-stage Dockerfile. Kubernetes (the Docker Desktop single-node cluster) runs three replicas of the image behind a NodePort Service, with configuration in a ConfigMap and liveness and readiness probes. A rollback demonstration shows Kubernetes recovering from a bad image tag. Scaling from 3 to 5 and back to 3 replicas was done manually with `kubectl scale`; there is no autoscaler.

## Introduction

Component 1 ends with a tested JAR. This component covers getting that JAR running reliably: first on a server via configuration management (Ansible), then in a container (Docker), then in an orchestrated cluster (Kubernetes). Each step makes the deployment more repeatable than the one before.

## Implementation

### Development Environment

| Item | Value |
|---|---|
| Host OS | Windows, with PowerShell for `docker` and `kubectl` |
| Ansible target | Local WSL Ubuntu machine (`ansible_connection=local`) |
| Java on target | `openjdk-17-jre-headless` (installed by the role) |
| Application | Spring Boot 3.3.5, Java 17 (see Component 1) |
| Docker base images | `eclipse-temurin:17-jdk` (build), `eclipse-temurin:17-jre` (runtime) |
| Kubernetes | Docker Desktop built-in single-node cluster |
| Local ports in use | 8080 (another app), 8081 (Jenkins), 8082 (a test container) |

The Ansible, Docker Desktop, kubectl and Ubuntu versions were not recorded in the project files, so they are not stated here. Add them by hand if required.

### Code

#### Part A: Ansible

The `ansible/` folder contains `inventory.ini`, `playbook.yml` and a role `habit_tracker` (defaults, tasks, handlers, template, meta and tests).

**Inventory and playbook.**

```ini
[habit_tracker]
localhost ansible_connection=local
```

```yaml
- name: Configure Habit Tracker server
  hosts: habit_tracker
  become: true
  roles:
    - habit_tracker
```

The target is the local WSL Ubuntu machine, reached with `ansible_connection=local` (no SSH). `become: true` runs tasks with sudo.

**Role variables (`defaults/main.yml`).**

```yaml
app_name: habit-tracker
app_port: 8080
app_directory: /opt/habit-tracker
app_jar: habit-tracker.jar
```

The role default port is 8080. Because 8080 is taken on this machine by another application, the service was run on port 8090 (the value is overridable, since it is a role default).

**Role tasks (`tasks/main.yml`, abbreviated).**

```yaml
- name: Install Java runtime
  ansible.builtin.apt:
    name: openjdk-17-jre-headless
    state: present
    update_cache: true

- name: Create application directory
  ansible.builtin.file:
    path: "{{ app_directory }}"
    state: directory
    mode: '0755'

- name: Copy application JAR
  ansible.builtin.copy:
    src: "{{ playbook_dir }}/../target/habit-tracker.jar"
    dest: "{{ app_directory }}/{{ app_jar }}"
    mode: '0644'
  notify: Restart habit tracker

- name: Install systemd service
  ansible.builtin.template:
    src: habit-tracker.service.j2
    dest: /etc/systemd/system/habit-tracker.service
    mode: '0644'
  notify: Restart habit tracker

- name: Enable and start habit tracker
  ansible.builtin.systemd:
    name: habit-tracker
    enabled: true
    state: started
    daemon_reload: true
```

**Handler (`handlers/main.yml`).**

```yaml
- name: Restart habit tracker
  ansible.builtin.systemd:
    name: habit-tracker
    state: restarted
    daemon_reload: true
```

**Service template (`habit-tracker.service.j2`, key lines).**

```ini
Environment="SERVER_PORT={{ app_port }}"
ExecStart=/usr/bin/java -jar {{ app_directory }}/{{ app_jar }}
Restart=on-failure
RestartSec=5
```

Explanation: the role installs Java, creates `/opt/habit-tracker`, copies the JAR built by Maven from `target/`, renders a systemd unit from a template, then enables and starts the service. Every task uses an Ansible module with a declared desired state (`present`, `directory`, `started`), which makes the role idempotent: running it a second time changes nothing. The JAR copy and the unit file both `notify` the handler, so the service is restarted only when one of them actually changes, not on every run. The unit passes the port as `SERVER_PORT` and restarts the app on failure after 5 seconds. The unit runs the service as `root` (`User=root`), which is simple but not best practice (see Conclusion).

Passwordless sudo was configured for the Ansible user on the WSL machine. The system's `sudo-rs` did not work with `--ask-become-pass`, so a sudoers entry was used instead.

#### Part B: Docker

**Dockerfile (multi-stage).**

```dockerfile
# ---- Stage 1: build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre AS runtime
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /build/target/habit-tracker.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

Explanation: the first stage uses a full JDK and builds the JAR inside Docker. The wrapper script and `pom.xml` are copied first and dependencies are downloaded in their own layer, so Docker reuses that layer when only the source code changes. The `sed` line removes Windows line endings from `mvnw` as a safeguard so it runs on Linux. The second stage starts from a JRE image, creates a non-root user `app`, copies only the finished JAR, and runs it as that user. A `.dockerignore` excludes `target/`, `.git/`, `.idea/`, `*.md`, `k8s/` and `ansible/` from the build context.

The earlier single-stage version (tag `habit-tracker:1.0`) only copied a JAR built on the host into a JRE image. The multi-stage version is tagged `habit-tracker:2.0`.

#### Part C: Kubernetes

**ConfigMap (`k8s/configmap.yaml`).**

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: habit-tracker-config
data:
  PORT: "8080"
```

**Deployment (`k8s/deployment.yaml`, key parts).**

```yaml
spec:
  replicas: 3
  selector:
    matchLabels:
      app: habit-tracker
  template:
    metadata:
      labels:
        app: habit-tracker
    spec:
      containers:
        - name: habit-tracker
          image: habit-tracker:2.0
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
          env:
            - name: PORT
              valueFrom:
                configMapKeyRef:
                  name: habit-tracker-config
                  key: PORT
          resources:
            requests: { cpu: 200m, memory: 256Mi }
            limits:   { cpu: 500m, memory: 512Mi }
          readinessProbe:
            httpGet: { path: /actuator/health, port: 8080 }
            initialDelaySeconds: 20
            periodSeconds: 5
          livenessProbe:
            httpGet: { path: /actuator/health, port: 8080 }
            initialDelaySeconds: 40
            periodSeconds: 10
            failureThreshold: 3
```

**Service (`k8s/service.yaml`).**

```yaml
apiVersion: v1
kind: Service
metadata:
  name: habit-tracker-service
spec:
  type: NodePort
  selector:
    app: habit-tracker
  ports:
    - protocol: TCP
      port: 80
      targetPort: 8080
      nodePort: 30080
```

Explanation: the Deployment keeps three replicas running. The image is the local `habit-tracker:2.0`; `imagePullPolicy: IfNotPresent` lets Docker Desktop's cluster use the locally built image without a registry. The `PORT` environment variable is read from the ConfigMap rather than being hard-coded in the pod spec, and the application reads it through `server.port=${PORT:8080}`. Requests and limits give the scheduler resource figures and cap usage, with extra memory because the JVM needs it. The readiness probe keeps a pod out of the Service until `/actuator/health` answers; the liveness probe restarts the container after three consecutive failures. The Service selects pods by the `app: habit-tracker` label (identical to the Deployment's labels) and exposes them on node port 30080, because ports 8080 to 8082 are already used on this machine. Port 80 is the cluster-internal service port and 8080 is the container port.

### Description

**Ansible flow:** run the playbook against the local WSL host; the role installs Java, deploys the JAR to `/opt/habit-tracker`, installs and starts the systemd service; the health endpoint confirms the app is up.

**Docker flow:** `docker build -t habit-tracker:2.0 .` compiles inside the build stage and produces the runtime image. A smoke test ran the image on host port 8095 (`-p 8095:8080`); `/actuator/health` returned `{"status":"UP"}`, and the process inside ran as uid 999 (user `app`). The test container was then removed.

**Kubernetes flow:** apply the ConfigMap, Deployment and Service; three pods start; the Service sends traffic to ready pods; the app is reached at `http://localhost:30080`. The replica count was changed manually with `kubectl scale` from 3 to 5 and back to 3. The exact commands are in `k8s/README.md`.

## Enhancement chosen and why

**Multi-stage Dockerfile.** Chosen so the image can be built from source with only Docker installed, without a local JDK or Maven, and so the build is reproducible. It also caches the dependency download in its own layer, which speeds up rebuilds when only source code changes, and it runs the application as a non-root user. **It does not make the image smaller:** `habit-tracker:1.0` and `habit-tracker:2.0` are the same size (470MB disk usage, 130MB content size in `docker images`), because both use the same JRE base image and contain the same JAR.

**ConfigMap.** Chosen to keep configuration (the port) outside the image and the pod template, so it can be changed without rebuilding the image. It is verified by `kubectl describe pod` showing `PORT` coming from the ConfigMap, and by `kubectl get configmap habit-tracker-config -o yaml`.

**Liveness and readiness probes.** Chosen so Kubernetes can tell whether the application is actually working. The readiness probe prevents traffic from reaching a pod that is still starting (a Spring Boot JVM takes several seconds), and the liveness probe restarts a container that stops responding.

**Rollback demonstration.** Chosen to show a benefit of running under a Deployment: after setting the image to a nonexistent tag (`habit-tracker:broken`), the new pods fail with `ErrImagePull` / `ImagePullBackOff`, and `kubectl rollout undo deployment/habit-tracker` restores the previous working revision. During a rolling update, old pods are not removed until new pods are ready, so the application stays available while the bad rollout is stuck.

## Results

[Figure 1: First Ansible playbook run, showing tasks with `changed` status, proves the role installs Java, copies the JAR, installs the service and starts it on a fresh target.]

The first run changes the machine from its initial state to the desired state, so most tasks report `changed`.

[Figure 2: Second Ansible playbook run with `changed=0`, proves the role is idempotent.]

Running the same playbook again makes no changes, because the system already matches the declared state. The handler does not fire, so the service is not restarted needlessly.

[Figure 3: `systemctl status habit-tracker` and a `curl` of the health endpoint on the Ansible-configured port, proves the application runs as a systemd service and responds.]

The service is `active (running)` and the health endpoint returns `{"status":"UP"}`.

[Figure 4: The `ansible/` folder tree (inventory, playbook, role directories), proves the configuration is organised as a standard Ansible role.]

The layout follows the usual role structure: `defaults`, `tasks`, `handlers`, `templates`, `meta`, `tests`, `vars`.

[Figure 5: The multi-stage `Dockerfile` and the `docker build -t habit-tracker:2.0 .` output, proves the image is built from source in two stages.]

The build output shows the build-stage steps (dependency download, package) followed by the runtime-stage copy.

[Figure 6: `docker images habit-tracker` listing `1.0` and `2.0`, proves both images exist and have the same size (470MB disk usage, 130MB content size).]

The sizes are identical. The value of the multi-stage build lies in reproducibility, layer caching and the non-root user, not in size.

[Figure 7: `kubectl get pods` and `kubectl get svc`, proves three replicas are running and the NodePort Service exists on 30080.]

All three pods reach `1/1 Running` once the readiness probe succeeds, and the Service shows `80:30080/TCP`.

[Figure 8: Requests to `http://localhost:30080` (`POST /habits`, then `GET /habits`), proves the app is reachable from outside the cluster through the NodePort.]

The POST returns 201 with the new habit and the GET returns a JSON list.

Because habit data is stored in memory inside each pod, and the Service load-balances across three pods, a GET can return a different list depending on which pod answers. For example, a habit created through one pod may not appear when another pod handles the next request. This is expected behaviour for an in-memory design. A shared database would solve it and is listed as future work.

[Figure 9: `kubectl get pods` after `kubectl scale deployment/habit-tracker --replicas=5`, showing 5/5 Running, proves the Deployment can be scaled up.]

Scaling was done manually with `kubectl scale`. There is no autoscaler (no HorizontalPodAutoscaler) in this project.

[Figure 10: `kubectl get pods` after scaling back to 3 replicas, proves the Deployment can be scaled down again.]

The two extra pods are terminated and three remain, again by a manual `kubectl scale`.

[Figure 11: `kubectl describe pod` output showing the liveness and readiness probes and the `PORT` environment variable from the ConfigMap, proves the probes and ConfigMap are applied to the running pods.]

The describe output lists the probe settings and shows `PORT` sourced from the `habit-tracker-config` ConfigMap. Readiness probe warnings may appear in the Events section (for example "Readiness probe failed" shortly after a pod starts). These happen during JVM startup, before the application is listening, and are normal; the pod becomes Ready once the application is up.

[Figure 12: `kubectl get configmap habit-tracker-config -o yaml`, proves the ConfigMap exists in the cluster with `PORT: "8080"`.]

This is the configuration source for the pods' `PORT` variable.

[Figure 13: Rollback demo: pods in `ErrImagePull` / `ImagePullBackOff` after setting image `habit-tracker:broken`, then `kubectl rollout undo` and pods healthy again, proves Kubernetes can recover from a bad release.]

The new pods fail to pull the nonexistent image while the older pods keep running. After `kubectl rollout undo deployment/habit-tracker`, the Deployment returns to the previous working revision and all pods are `Running`.

## Conclusion

The component shows the same application deployed three ways, each more automated than the last. Ansible gives an idempotent, repeatable server setup using a role, a template and a handler. Docker gives a reproducible multi-stage build with a non-root runtime user. Kubernetes adds replication, health checking, external configuration and rollback. The enhancements (multi-stage build, ConfigMap, probes, rollback demonstration) were each demonstrated.

Limitations and future work:
- Habit data lives in memory per pod, so replicas do not share state. A shared database would fix this.
- Scaling is manual. A HorizontalPodAutoscaler could automate it (not implemented).
- The multi-stage image is not smaller than the single-stage image.
- The Ansible-managed service runs as `root`; a dedicated service user would be safer.
- The Kubernetes cluster is a single-node Docker Desktop cluster using a local image, with no registry or Ingress.
- The pipeline from Component 1 does not yet trigger the Ansible, Docker or Kubernetes steps; they are run manually.

## References

1. Ansible Documentation. https://docs.ansible.com/
2. Ansible: Roles. https://docs.ansible.com/ansible/latest/playbook_guide/playbooks_reuse_roles.html
3. Ansible: Handlers. https://docs.ansible.com/ansible/latest/playbook_guide/playbooks_handlers.html
4. Docker: Multi-stage builds. https://docs.docker.com/build/building/multi-stage/
5. Dockerfile reference. https://docs.docker.com/reference/dockerfile/
6. Kubernetes: Deployments. https://kubernetes.io/docs/concepts/workloads/controllers/deployment/
7. Kubernetes: Services. https://kubernetes.io/docs/concepts/services-networking/service/
8. Kubernetes: ConfigMaps. https://kubernetes.io/docs/concepts/configuration/configmap/
9. Kubernetes: Liveness, Readiness and Startup Probes. https://kubernetes.io/docs/tasks/configure-pod-container/configure-liveness-readiness-startup-probes/
10. Kubernetes: Resource Management for Pods and Containers. https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
11. Spring Boot Actuator. https://docs.spring.io/spring-boot/reference/actuator/index.html
