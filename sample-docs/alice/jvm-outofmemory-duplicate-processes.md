# Runbook: JVM OutOfMemoryError caused by duplicate processes

Applies to the fictional "Report Builder" batch service running on shared virtual machines.

## Symptoms
- Services throw `java.lang.OutOfMemoryError: Java heap space` shortly after a deploy.
- Sometimes the error is `OutOfMemoryError: unable to create native thread` instead.
- The host shows two Java processes for the same service, each started by a different deploy.
- Free memory on the host drops to near zero; the kernel OOM killer may terminate processes.

## Likely causes
1. The deploy script started a new process before the old one fully stopped, so two copies run at once and each takes its full `-Xmx`.
2. A stale PID file made the stop script think the service was already down.
3. A cron job or watchdog restarted the service while a deploy was also starting it.
4. The heap size was raised without accounting for the total memory on the host.

## Diagnosis steps
1. List Java processes with `ps -ef | grep java` and count instances per service name.
2. Compare process start times with the deploy log timestamps.
3. Check the PID file against the running process id.
4. Read the GC log of the surviving process: frequent full GCs right before the error confirm heap pressure.
5. Check whether a watchdog or cron entry also starts the service.

## Fix
- Stop the duplicate process gracefully, then confirm only one instance remains.
- Make the stop step wait until the process has exited (poll the PID with a timeout) before the start step runs.
- Remove stale PID files as part of the stop step.
- If memory is still tight with a single process, lower `-Xmx` so total heap across all services fits in host memory with headroom.

## Prevention
- Use a process supervisor (for example systemd) so only one instance can run per service.
- Add a deploy pre-check that fails if the service is already running twice.
- Alert when the number of processes for a service is greater than one.
- Set `-XX:+HeapDumpOnOutOfMemoryError` to capture evidence next time.
