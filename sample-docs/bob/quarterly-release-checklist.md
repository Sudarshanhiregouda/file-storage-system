# Quarterly release checklist (fictional)

Sample data for the made-up company "Example Widgets Ltd."

## Four weeks before
- [ ] Freeze the feature list and publish it to stakeholders.
- [ ] Confirm database migrations are backward compatible.
- [ ] Schedule the load test.

## Two weeks before
- [ ] Code freeze on the release branch; only bug fixes merge after this point.
- [ ] Run the full regression suite and the load test.
- [ ] Draft release notes and customer-facing documentation.

## Release week
- [ ] Get sign-off from QA, product and security.
- [ ] Announce the maintenance window 48 hours ahead.
- [ ] Deploy to staging and run smoke tests, then deploy to production with a canary at 10% of traffic.
- [ ] Watch error rate and latency for one hour before going to 100%.

## After release
- [ ] Publish release notes.
- [ ] Hold a retrospective within one week.
- [ ] Merge the release branch back into main.
