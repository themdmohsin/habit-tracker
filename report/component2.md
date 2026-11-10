# Component 2: Ansible, Docker and Kubernetes Deployment

Repository: https://github.com/themdmohsin/habit-tracker.git

## Abstract

This component takes the `habit-tracker.jar` built in Component 1 and deploys the habit-tracker application in three ways. Ansible (role `habit_tracker`) configures a local WSL Ubuntu machine and runs the app as a systemd service on port 8090 under a service user called `habit`. Docker packages the app with a multi-stage Dockerfile (images `habit-tracker:1.0`, `2.0` and `3.0`). Kubernetes (the Docker Desktop built-in cluster) runs 3 replicas of `habit-tracker:3.0` behind a NodePort Service on port 30080 with `sessionAffinity: ClientIP`. The chosen enhancements are a ConfigMap, liveness and readiness probes, the multi-stage Dockerfile, and a rollback demo. Scaling from 3 to 5 and back to 3 was done manually with `kubectl scale`; there is no autoscaling. Habit data is held in memory in each pod, which is the main limitation.

## Introduction

Component 1 ends with a tested JAR. This component covers running it: first on a server with configuration management (Ansible), then in a container (Docker), then in a cluster (Kubernetes). Each step is more repeatable than the one before.

The application has a single-page UI at `/` and these endpoints: `GET/POST /habits`, `POST /habits/{id}/check-in`, `DELETE /habits/{id}`, `GET /info` and `GET /actuator/health`. The data is stored in memory inside each running process or pod.

## Implementation

### Development Environment

| Item | Value |
|---|---|
| Host OS | Windows, with PowerShell for `docker` and `kubectl` |
| Ansible target | Local WSL Ubuntu machine (`ansible_connection=local`) with passwordless sudo |
| Java on the Ansible target | `openjdk-17-jre-headless` (installed by the role) |
| Application | Spring Boot 3.3.5, Java 17 (see Component 1) |
| Docker base images | `eclipse-temurin:17-jdk` (build stage), `eclipse-temurin:17-jre` (runtime stage) |
| Docker images | `habit-tracker:1.0`, `2.0`, `3.0` |
| Kubernetes | Docker Desktop built-in single-node cluster |
| Ports in use on this machine | 8080 (another app), 8081 (Jenkins), 8082 (a test container), 8090 (Ansible service), 30080 (Kubernetes NodePort) |

The versions of Ansible, Docker Desktop, kubectl and Ubuntu are not recorded in the repository, so they are not stated here.

### Code

#### Part A: Ansible

The `ansible/` folder contains `inventory.ini`, `playbook.yml` and the role `habit_tracker`, which has the full `ansible-galaxy` structure (`defaults`, `tasks`, `handlers`, `templates`, `files`, `vars`, `meta`, `tests`, `README.md`).

**Inventory and playbook (abbreviated).**

```ini
[habit_tracker]
localhost ansible_connection=local
```

```yaml
- name: Configure Habit Tracker server
  hosts: habit_tracker
  become: true
  pre_tasks: ...      # checks that the JAR exists
  roles:
    - habit_tracker
  post_tasks: ...     # waits for /actuator/health to return 200
```

The target is the local WSL Ubuntu machine, reached with `ansible_connection=local`. Tasks run with sudo (`become: true`). Passwordless sudo is used because `sudo-rs` on the WSL machine failed with `--ask-become-pass`. The `pre_tasks` stop the run with a message ("Run mvnw.cmd clean package first") if `target/habit-tracker.jar` is missing, and the `post_tasks` poll `/actuator/health` (up to 10 retries, 5 seconds apart).

**Role variables (`defaults/main.yml`).**

```yaml
app_name: habit-tracker
app_port: 8090
app_user: habit
app_dir: /opt/habit-tracker
jar_src: "{{ playbook_dir }}/../target/habit-tracker.jar"
java_package: openjdk-17-jre-headless
```

**Role tasks (`tasks/main.yml`, abbreviated).**

```yaml
- name: Create application system user
  ansible.builtin.user:
    name: "{{ app_user }}"
    system: true
    shell: /usr/sbin/nologin
    create_home: false
    state: present

- name: Copy application JAR
  ansible.builtin.copy:
    src: "{{ jar_src }}"
    dest: "{{ app_dir }}/habit-tracker.jar"
    owner: "{{ app_user }}"
    group: "{{ app_user }}"
    mode: '0644'
  notify: Restart habit-tracker

- name: Install systemd unit
  ansible.builtin.template:
    src: habit-tracker.service.j2
    dest: "/etc/systemd/system/{{ app_name }}.service"
    mode: '0644'
  notify: Restart habit-tracker
```

The role also installs the Java package, creates the application directory, and enables and starts the service.

**Handler (`handlers/main.yml`).**

```yaml
- name: Restart habit-tracker
  ansible.builtin.systemd:
    name: "{{ app_name }}"
    state: restarted
    daemon_reload: true
```

**Service template (`habit-tracker.service.j2`, key lines).**

```ini
User={{ app_user }}
Environment=PORT={{ app_port }}
ExecStart=/usr/bin/java -jar {{ app_dir }}/habit-tracker.jar
Restart=on-failure
RestartSec=5
```

Explanation: the role installs Java, creates the `habit` system user (no login shell, no home directory) and `/opt/habit-tracker`, copies the JAR, installs a systemd unit from a template, then enables and starts the service. The tasks use modules with a declared state (`present`, `directory`, `started`), so running the role a second time should change nothing. The JAR copy and the unit template both `notify` the handler `Restart habit-tracker`, so the service restarts only when one of them changes. The unit runs the app as the `habit` user, passes the port as `PORT` (which the app reads through `server.port=${PORT:8080}`), and restarts the app 5 seconds after a failure.

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

Explanation: the first stage uses a full JDK and builds the JAR inside Docker, so no JDK or Maven is needed on the host. The wrapper and `pom.xml` are copied first and the dependencies are downloaded in their own layer, so Docker reuses that layer when only the source changes. The `sed` line removes Windows line endings from `mvnw` so it runs on Linux. The second stage starts from a JRE image, creates a non-root user `app`, copies only the finished JAR and runs it as that user. `.dockerignore` excludes `target/`, `.git/`, `.idea/`, `*.md`, `k8s/` and `ansible/` from the build context.

Image tags: `habit-tracker:1.0` is the earlier single-stage version (it copied a JAR built on the host), `2.0` is the first multi-stage version, and `3.0` is built from the same Dockerfile and contains the UI and the extra endpoints.

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
  ...
      containers:
        - name: habit-tracker
          image: habit-tracker:3.0
          imagePullPolicy: IfNotPresent
          ports:
            - containerPort: 8080
          env:
            - name: PORT
              valueFrom:
                configMapKeyRef:
                  name: habit-tracker-config
                  key: PORT
          readinessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 20
            periodSeconds: 5
          livenessProbe:
            httpGet:
              path: /actuator/health
              port: 8080
            initialDelaySeconds: 40
            periodSeconds: 10
            failureThreshold: 3
```

The container also sets resource requests (200m CPU, 256Mi memory) and limits (500m CPU, 512Mi memory).

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
  sessionAffinity: ClientIP
```

Explanation: the Deployment keeps three replicas running. The image is the local `habit-tracker:3.0`; `imagePullPolicy: IfNotPresent` lets the Docker Desktop cluster use a locally built image without a registry. `PORT` comes from the ConfigMap instead of being hard-coded in the pod spec. The readiness probe keeps a pod out of the Service until `/actuator/health` answers, and the liveness probe restarts the container after 3 consecutive failures. The Service selects pods by the label `app: habit-tracker` and exposes them on node port 30080 (ports 8080 to 8082 are already used on this machine). Port 80 is the Service port and 8080 is the container port.

Because data is stored in memory in each pod, `sessionAffinity: ClientIP` is used as a workaround: it keeps one client talking to the same pod, so the UI looks consistent. It does not share data between pods. A shared database would be the real fix (see Limitations and Future Work).

### Description

**Ansible flow:** build the JAR with `mvnw.cmd clean package`, then run `ansible-playbook -i inventory.ini playbook.yml` from `ansible/` in WSL. The role installs Java, creates the user, deploys the JAR to `/opt/habit-tracker`, and installs and starts the systemd service. The playbook then checks `/actuator/health` on port 8090.

**Docker flow:** `docker build -t habit-tracker:3.0 .` compiles the app inside the build stage and produces the runtime image.

**Kubernetes flow:** apply the ConfigMap, Deployment and Service with `kubectl apply -f`. Three pods start, and the Service sends traffic only to ready pods. The app is reached at `http://localhost:30080`, and the footer of the UI shows which pod served the page (from `/info`). The replica count was changed manually with `kubectl scale` from 3 to 5 and back to 3. The exact commands are in `k8s/README.md`.

## Enhancement chosen and why

**Multi-stage Dockerfile.** Chosen so the image can be built from source with only Docker installed (no local JDK or Maven), with a cached dependency layer so rebuilds are faster when only the source changes, and with a non-root runtime user. **It does not make the image smaller:** `habit-tracker:1.0` and `habit-tracker:2.0` are the same size, because both use the same JRE base image and contain the same JAR.

**ConfigMap.** Chosen to keep configuration (the port) outside the image and the pod spec. The pods read `PORT` from the ConfigMap `habit-tracker-config` through `configMapKeyRef`.

**Liveness and readiness probes.** Chosen so Kubernetes can tell whether the app is working. The readiness probe keeps a pod out of the Service until the app responds. The liveness probe restarts a container that stops responding. Readiness probe warnings in the pod events during startup are normal, because the app takes time to start.

**Rollback demo.** Chosen to show a benefit of a Deployment. After `kubectl set image deployment/habit-tracker habit-tracker=habit-tracker:broken`, the new pods fail with `ErrImagePull` / `ImagePullBackOff` while the old pods keep running. `kubectl rollout undo deployment/habit-tracker` then returns to the previous working revision.

## Results

### Ansible

![Fig 11: First Ansible playbook run](figures/fig11.png)

Fig 11: First Ansible playbook run showing tasks with `changed` status. It proves the role sets up the target from its initial state (Java, user, JAR, service).

![Fig 12: Second Ansible playbook run](figures/fig12.png)

Fig 12: Second playbook run with `changed=0`. It proves the role is idempotent, and that the handler did not restart the service on the second run.

![Fig 13: systemctl status and health check](figures/fig13.png)

Fig 13: `systemctl status habit-tracker` and a `curl` of the health endpoint on port 8090. It proves the app runs as a systemd service and answers on the Ansible-configured port.

![Fig 14: Ansible folder tree](figures/fig14.png)

Fig 14: The `ansible/` folder tree (inventory, playbook, role directories). It proves the configuration is organised as a standard Ansible role.

![Fig 15: Role defaults and handler](figures/fig15.png)

Fig 15: Role `defaults/main.yml` (port 8090, user `habit`) and `handlers/main.yml` (`Restart habit-tracker`). It proves the role uses variables and a handler.

![Fig 16: Systemd unit template](figures/fig16.png)

Fig 16: The systemd unit template or the installed unit file (`User=habit`, `Environment=PORT=8090`). It proves the service runs as the dedicated user on the configured port.

### Docker

![Fig 17: Dockerfile](figures/fig17.png)

Fig 17: Output of `Get-Content Dockerfile`. It shows the two stages (`build` and `runtime`) and the non-root `USER app`.

![Fig 18: docker build output](figures/fig18.png)

Fig 18: End of the output of `docker build -t habit-tracker:3.0 .`. It proves the image is built from source inside Docker.

![Fig 19: docker images habit-tracker](figures/fig19.png)

Fig 19: `docker images habit-tracker`. It proves the images `1.0`, `2.0` and `3.0` exist, and that `1.0` and `2.0` have the same size, so the multi-stage build did not reduce the image size.

### Kubernetes

![Fig 20: kubectl apply and pods starting](figures/fig20.png)

Fig 20: `kubectl apply` of the ConfigMap, Deployment and Service, then `kubectl get pods` with pods in `ContainerCreating`. It proves the three manifests were applied and the pods started.

![Fig 21: Pods and service](figures/fig21.png)

Fig 21: `kubectl get pods` and `kubectl get svc`. It shows 3 pods `1/1 Running` and the Service on `80:30080/TCP`.

![Fig 22: The UI in the browser](figures/fig22.png)

Fig 22: The browser at `http://localhost:30080` with a habit added and "Done today" ticked; the footer shows the pod name. It proves the UI is reachable through the NodePort and shows which pod served it.

![Fig 23: API calls through the NodePort](figures/fig23.png)

Fig 23: `Invoke-RestMethod` POST to `/habits`, then `curl.exe` on `/habits` and `/info`. It proves the REST API and `/info` respond through the NodePort.

![Fig 24: Service description](figures/fig24.png)

Fig 24: `kubectl describe svc habit-tracker-service`. It shows Session Affinity `ClientIP` and 3 endpoints.

![Fig 25: Scaling up to 5](figures/fig25.png)

Fig 25: `kubectl scale deployment/habit-tracker --replicas=5`, then `kubectl get pods` with the new pods starting. It shows a manual scale-up; this is not autoscaling.

![Fig 26: Five pods running](figures/fig26.png)

Fig 26: `kubectl get pods` with 5 pods `1/1 Running`. It shows the manual scale-up completed.

![Fig 27: Scaling back to 3](figures/fig27.png)

Fig 27: `kubectl scale deployment/habit-tracker --replicas=3`, then `kubectl get pods` with pods in `Terminating`. It shows a manual scale-down back to 3.

![Fig 28: Pod description, probes and environment](figures/fig28.png)

Fig 28: `kubectl describe pod` showing the Liveness and Readiness probes and the `PORT` environment variable from the ConfigMap. It proves the probes and the ConfigMap are applied to the pods.

![Fig 29: Pod events](figures/fig29.png)

Fig 29: The Events section of `kubectl describe pod`. Readiness probe warnings during pod startup are normal, because the app takes time to start.

![Fig 30: ConfigMap](figures/fig30.png)

Fig 30: `kubectl get configmap habit-tracker-config -o yaml`. It proves the ConfigMap exists in the cluster with `PORT: "8080"`.

![Fig 31: deployment.yaml](figures/fig31.png)

Fig 31: `k8s/deployment.yaml` showing the probes and the `configMapKeyRef`. It shows how the probes and the ConfigMap are declared.

![Fig 32: Rollback demo, broken image](figures/fig32.png)

Fig 32: `kubectl set image` to `habit-tracker:broken`, then `kubectl get pods` showing `ImagePullBackOff` / `ErrImagePull`. The old pods are still running.

![Fig 33: Rollback demo, undo](figures/fig33.png)

Fig 33: `kubectl rollout undo`, `rollout status`, `get pods` and `rollout history`. It proves the Deployment returned to the previous working revision.

### Git

![Fig 34: Git history graph](figures/fig34.png)

Fig 34: `git log --graph --oneline --all --decorate`. It shows the branches and merges in the final repository, including the `feature/ui` pull request merge and `feature/ansible-fix`.

![Fig 35: GitHub network graph](figures/fig35.png)

Fig 35 (optional): GitHub Insights > Network. It shows the branch and merge history on GitHub.

## Conclusion

The same application was deployed three ways. Ansible gives a repeatable server setup using a role with variables, a template and a handler. Docker gives a reproducible multi-stage build with a non-root runtime user. Kubernetes adds replicas, health probes, ConfigMap configuration and rollback. The enhancements (multi-stage Dockerfile, ConfigMap, probes, rollback demo) were each demonstrated. The multi-stage build did not reduce the image size, and scaling was done by hand.

## Limitations and Future Work

- **In-memory data.** Habit data is stored in memory in each pod, so pods do not share data and the data is lost when a pod restarts. `sessionAffinity: ClientIP` is only a workaround that keeps one client on the same pod.
- **Shared database.** Adding a shared database is future work and the proper fix for the point above. There is none in this project.
- **Autoscaling.** Scaling was manual (`kubectl scale`, 3 to 5 to 3). No autoscaler is configured; adding one is future work.
- **Failing-build demo.** Only successful pipeline runs were shown. A deliberately failing test, to show the pipeline stopping at the Test stage, is future work.
- Jenkins uses polling every minute, not a webhook. The pipeline does not trigger the Ansible, Docker or Kubernetes steps; they are run manually.
- The cluster is a single-node Docker Desktop cluster using a local image, with no registry.

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
10. Spring Boot Actuator. https://docs.spring.io/spring-boot/reference/actuator/index.html