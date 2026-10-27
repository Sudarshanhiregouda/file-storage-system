# Runbook: Charset errors in message payloads

Applies to the fictional "Customer Profile Sync" service, which exchanges JSON messages with partner systems.

## Symptoms
- Customer names with accents appear garbled (for example `JosÃ©` instead of `José`) or as question marks.
- Logs show `MalformedInputException: Input length = 1` or `Invalid UTF-8 start byte`.
- JSON parsing fails only for some messages, usually those with non-English characters.
- The same message looks correct in the partner's system but broken after it reaches ours.

## Likely causes
1. A producer encodes payloads as ISO-8859-1 (Latin-1) while the consumer decodes them as UTF-8.
2. Code converts bytes to a String using the platform default charset instead of an explicit one.
3. The `Content-Type` header is missing the `charset` parameter.
4. A byte-oriented transformation (for example truncating to a byte length) cut a multi-byte character in half.

## Diagnosis steps
1. Capture the raw bytes of a failing message and inspect them in hex. `C3 A9` is UTF-8 for an accented e; a single `E9` byte means Latin-1.
2. Check the JVM's `file.encoding` on both producer and consumer hosts.
3. Search the code for `new String(bytes)` or `getBytes()` calls without a charset argument.
4. Check message headers for `Content-Type` and its charset.
5. Look for truncation logic that works on byte counts.

## Fix
- Always pass an explicit charset, for example `new String(bytes, StandardCharsets.UTF_8)`.
- Agree with the producer that payloads are UTF-8 and set `Content-Type: application/json; charset=utf-8`.
- Truncate by characters (code points), not bytes.
- Reprocess affected messages from the dead-letter queue after the fix is deployed.

## Prevention
- Set `-Dfile.encoding=UTF-8` on every JVM so defaults are consistent.
- Add contract tests with accented and non-Latin names.
- Add a static analysis rule that flags charset-less String and byte conversions.
