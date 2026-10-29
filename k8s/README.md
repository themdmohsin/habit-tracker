# Kubernetes deployment (Docker Desktop)

Run from the repo root in PowerShell. Image `habit-tracker:2.0` must exist locally (`docker build -t habit-tracker:2.0 .`).

## 1. Deploy

```powershell
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml
kubectl get pods
kubectl get svc
```

Pods become `1/1 Running` after about 20-30 s (readiness probe delay).

## 2. Test through the NodePort

```powershell
curl.exe http://localhost:30080/actuator/health
curl.exe http://localhost:30080/habits
```

## 3. Scale

```powershell
kubectl scale deployment/habit-tracker --replicas=5
kubectl get pods
kubectl scale deployment/habit-tracker --replicas=3
kubectl get pods
```

## 4. Show probes and ConfigMap

```powershell
kubectl describe pod -l app=habit-tracker
kubectl get configmap habit-tracker-config -o yaml
```

In the describe output look for the `Liveness`, `Readiness` and `Environment` (PORT from ConfigMap) lines.

## 5. Optional: rollback demo

```powershell
kubectl set image deployment/habit-tracker habit-tracker=habit-tracker:broken
kubectl get pods
```

The new pods show `ErrImagePull` / `ImagePullBackOff`, while the old pods keep serving traffic (rolling update does not remove them until new ones are ready).

```powershell
kubectl rollout status deployment/habit-tracker --timeout=20s
kubectl rollout undo deployment/habit-tracker
kubectl rollout status deployment/habit-tracker
kubectl get pods
kubectl rollout history deployment/habit-tracker
```

## Cleanup (optional)

```powershell
kubectl delete -f k8s/
```
