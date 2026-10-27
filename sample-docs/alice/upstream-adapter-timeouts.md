# Runbook: Upstream adapter timeouts

Applies to the fictional "Orders Gateway" service, which calls several upstream partner APIs through adapter modules.

## Symptoms
- Requests to `/orders/submit` return HTTP 504 after roughly 30 seconds.
- Logs show `AdapterTimeoutException: upstream did not respond within 30000 ms`.
- The adapter thread-pool active count stays at its maximum while CPU stays low.
- Latency dashboards show p99 latency climbing while p50 looks normal.

## Likely causes
1. The upstream partner API is slow or degraded.
2. The adapter connection pool is exhausted, so requests wait for a free connection before the timeout even starts.
3. A recent configuration change lowered the read timeout below the upstream's normal response time.
4. DNS resolution for the upstream host is slow or failing intermittently.

## Diagnosis steps
1. Check the upstream partner's status page and recent incident notes.
2. Compare the adapter's `pool.active` and `pool.pending` metrics. Pending above zero for minutes means pool exhaustion.
3. Run a manual request against the upstream health endpoint from inside the cluster and time it.
4. Review configuration changes from the last 24 hours for `adapter.read-timeout` and `adapter.max-connections`.
5. Look for retry storms: count retries per minute in the logs. Aggressive retries multiply load on a struggling upstream.

## Fix
- If the upstream is degraded, enable the circuit breaker for that adapter so callers fail fast instead of piling up.
- If the pool is exhausted, raise `adapter.max-connections` gradually (for example from 50 to 100) and confirm the upstream can handle it.
- Revert any timeout change that caused the regression.
- Restart only the affected adapter pods if connections are stuck in a half-open state.

## Prevention
- Set timeouts per upstream based on measured p99 latency plus headroom.
- Use exponential backoff with jitter and a retry budget.
- Alert on `pool.pending > 0` for more than 5 minutes.
- Add a synthetic check that calls each upstream every minute.
