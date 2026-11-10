# Ansible deployment

Deploys the Habit Tracker jar as a systemd service (`habit-tracker`, user `habit`, port 8090) on the local WSL Ubuntu machine (`ansible_connection=local`).

## Prerequisites
- Build the jar first on Windows: `mvnw.cmd clean package` (produces `target/habit-tracker.jar`).
- The WSL host needs systemd enabled (`systemd=true` under `[boot]` in `/etc/wsl.conf`) and passwordless sudo, so no `--ask-become-pass` is used.

## Run
```
cd /mnt/c/Users/moham_etr05so/Projects/DevOps/ansible
ansible-playbook -i inventory.ini playbook.yml
```

## Idempotency demo
Run the same command a second time; it should report `changed=0`.
```
ansible-playbook -i inventory.ini playbook.yml
```

## Verify
```
systemctl status habit-tracker
curl localhost:8090/actuator/health
```

## Layout
`roles/habit_tracker/` follows the `ansible-galaxy init` layout (tasks, handlers, defaults, vars, meta, files, templates, tests). Variables are in `roles/habit_tracker/defaults/main.yml`.
