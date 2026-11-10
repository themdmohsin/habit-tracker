habit_tracker
=============

Installs Java and deploys the Habit Tracker Spring Boot jar as a systemd service under a dedicated system user.

Requirements
------------

- Ubuntu/Debian target with systemd and sudo.
- The jar built beforehand (`mvnw.cmd clean package`) at `target/habit-tracker.jar`.

Role Variables
--------------

Defined in `defaults/main.yml`:

| Variable | Default | Meaning |
|----------|---------|---------|
| `app_name` | `habit-tracker` | systemd service name |
| `app_port` | `8090` | Passed to the app as `PORT` |
| `app_user` | `habit` | System user running the service |
| `app_dir` | `/opt/habit-tracker` | Install directory |
| `jar_src` | `{{ playbook_dir }}/../target/habit-tracker.jar` | Jar on the control machine |
| `java_package` | `openjdk-17-jre-headless` | apt package |

Dependencies
------------

None.

Example Playbook
----------------

    - hosts: habit_tracker
      become: true
      roles:
        - habit_tracker

License
-------

MIT

Author Information
------------------

themdmohsin
