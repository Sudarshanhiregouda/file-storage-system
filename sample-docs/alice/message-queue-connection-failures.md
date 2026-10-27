# Runbook: Message queue connection failures

Applies to the fictional "Notifications Worker", which consumes events from a message broker.

## Symptoms
- Logs show `ConnectionRefused` or `Broker not available` repeated every few seconds.
- Consumer lag grows steadily while the worker reports itself as healthy.
- Messages pile up in the queue; downstream notifications are delayed.
- After a broker restart, some consumers never reconnect.

## Likely causes
1. The broker is down, restarting, or failing over to a replica.
2. Credentials or TLS certificates for the broker connection expired.
3. A firewall or security group change blocks the broker port.
4. The client library's reconnect logic gave up after a fixed number of attempts.
5. The connection limit on the broker was reached.

## Diagnosis steps
1. Check broker health and recent restarts or failovers.
2. Test connectivity from a worker pod with a simple TCP check to the broker host and port.
3. Inspect certificate expiry dates for the client and broker certificates.
4. Compare the broker's active connection count with its configured limit.
5. Review the client's reconnect settings: max attempts, backoff, and whether it reconnects forever.

## Fix
- If the broker failed over, make sure clients use the cluster address, not a single node.
- Renew expired credentials or certificates and roll the workers.
- Restore the firewall rule for the broker port.
- Configure the client to reconnect indefinitely with exponential backoff.
- Restart stuck consumers that never re-established their connection.

## Prevention
- Alert on consumer lag, not only on process health.
- Track certificate expiry and alert 30 days ahead.
- Make the health check verify an active broker connection.
- Load-test the broker connection limit before scaling out workers.
