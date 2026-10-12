# US Insurance Forms Platform — runnable learning project

A Java 21 / Spring Boot 3 / Spring Data JPA / H2 / LibreOffice training implementation of the supplied project
blueprint.

**This is a newly designed educational project, not an extraction of a real insurer's private code or approved insurance
forms.** The mock policy service returns fictional policies. The TX notices are illustrative and **not** legally
approved insurance documents. Do not use the generated forms to sell or administer actual insurance.

## What's implemented

- Policy revision lookup via `PolicyClient` interface and `MockPolicyClient` (a stand-in for upstream Policy
  Administration).
- `POST /v1/form-jobs` creates an asynchronous job (202 Accepted) with idempotency key.
- Background processing fetches the exact policy revision, checks request context, evaluates
  product/state/transaction/coverage rules, resolves effective-dated template versions, maps/validates dynamic fields,
  fills LibreOffice Writer `.odt` templates and uses installed LibreOffice to convert to PDF.
- H2 file-backed persistence for job history and generated-document metadata; generated files in `generated-documents/`.
- Job status, policy documents, download, applicability preview, version lookup and controlled retry APIs.
- Basic Auth (LOCAL DEMO ONLY); tests for form rules and date boundary.
- Two product examples: **HOME** and **AUTO**; five transaction types: NEW_BUSINESS, RENEWAL, ENDORSEMENT, CANCELLATION,
  REINSTATEMENT.

## Prerequisites

1. **JDK 21** (Temurin/OpenJDK) and **Maven 3.9+**. Internet required for Maven to download Spring dependencies on first
   run.
2. **LibreOffice** installed for automatic PDF generation. If missing, the app still saves filled `.odt` files (not
   PDFs). Install it separately; check `libreoffice --version` in a terminal.
3. **IntelliJ IDEA Community Edition** recommended; VS Code with Extension Pack for Java and Spring Boot Extension Pack
   also works.

## IntelliJ run steps

1. Unzip the project. Open IntelliJ -> **Open** -> select the unzipped `insurance-forms-platform` folder (or`pom.xml` ->
   Open as Project).
2. Allow Maven to import dependencies; choose **Project SDK: JDK 21** in File -> Project Structure. If dependencies
   fail, check network/Maven settings and click Maven Reload.
3. Ensure LibreOffice is installed. On Windows set `app.libreoffice.command` in
   `src/main/resources/application.properties` to your `soffice.exe` path; on macOS often
   `/Applications/LibreOffice.app/Contents/MacOS/soffice`; on Linux typically `libreoffice`.
4. Open `src/main/java/com/example/forms/FormsApplication.java`; click the green triangle beside `main` -> Run.
5. Wait for `Tomcat started on port 8080`. Your app is now running.
6. Open IntelliJ Terminal and execute the example curl commands below. If using PowerShell, use `curl.exe` instead of
   `curl`.
7. Files are saved under `<project>/generated-documents/` and H2 database under `<project>/data/`.

## Alternative: terminal

```bash
mvn clean test
mvn spring-boot:run
```

## Try HOME New Business

```bash
curl -i -u operator:local-demo-password \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: HOME-1-NEW' \
  -d '{"requestId":"REQ-HOME-001","policyNumber":"H100245","policyRevision":1,"productCode":"HOME","transactionType":"NEW_BUSINESS","jurisdiction":"TX","transactionEffectiveDate":"2026-01-01","requestedBy":"POLICY_ADMIN"}' \
  http://localhost:8080/v1/form-jobs
```

Response (actual generated ID varies):

```json
{
  "jobId": "JOB-xxxxxxxxxxxx",
  "requestId": "REQ-HOME-001",
  "status": "ACCEPTED",
  "statusPath": "/v1/form-jobs/JOB-xxxxxxxxxxxx"
}
```

Check job (use returned jobId):

```bash
curl -u operator:local-demo-password http://localhost:8080/v1/form-jobs/JOB-xxxxxxxxxxxx
```

Expected after processing: `COMPLETED`, with `HOME_DECLARATIONS`, `HOME_BASE`, `TX_HOME_NOTICE` (version `2026-01`) and
`HOME_LIABILITY` documents. A quick status check may show `PROCESSING`; check again shortly.

## Try HOME Endorsement (scheduled jewelry)

```bash
curl -i -u operator:local-demo-password -H 'Content-Type: application/json' \
 -H 'Idempotency-Key: HOME-2-END' \
 -d '{"requestId":"REQ-HOME-002","policyNumber":"H100245","policyRevision":2,"productCode":"HOME","transactionType":"ENDORSEMENT","jurisdiction":"TX","transactionEffectiveDate":"2026-06-01","requestedBy":"POLICY_ADMIN"}' \
 http://localhost:8080/v1/form-jobs
```

This selects the `SCHEDULED_PROPERTY_ENDORSEMENT` and `HOME_CHANGE_NOTICE` in addition to matching state/coverage
documents. Revision 2 adds `SCHEDULED_JEWELRY` coverage and a scheduled item.

## Try AUTO New Business

```bash
curl -i -u operator:local-demo-password -H 'Content-Type: application/json' \
 -H 'Idempotency-Key: AUTO-1-NEW' \
 -d '{"requestId":"REQ-AUTO-001","policyNumber":"A200678","policyRevision":1,"productCode":"AUTO","transactionType":"NEW_BUSINESS","jurisdiction":"TX","transactionEffectiveDate":"2026-01-01","requestedBy":"POLICY_ADMIN"}' \
 http://localhost:8080/v1/form-jobs
```

## Try AUTO Endorsement (vehicle added)

```bash
curl -i -u operator:local-demo-password -H 'Content-Type: application/json' \
 -H 'Idempotency-Key: AUTO-2-END' \
 -d '{"requestId":"REQ-AUTO-002","policyNumber":"A200678","policyRevision":2,"productCode":"AUTO","transactionType":"ENDORSEMENT","jurisdiction":"TX","transactionEffectiveDate":"2026-06-01","requestedBy":"POLICY_ADMIN"}' \
 http://localhost:8080/v1/form-jobs
```

## More endpoints

| Method | Path                                                                                                         | Purpose                                          |
|--------|--------------------------------------------------------------------------------------------------------------|--------------------------------------------------|
| POST   | `/v1/form-jobs`                                                                                              | Create job; requires `Idempotency-Key` header    |
| GET    | `/v1/form-jobs/{jobId}`                                                                                      | Status + document metadata + error               |
| GET    | `/v1/policies/{policyNumber}/documents`                                                                      | List generated policy documents                  |
| GET    | `/v1/documents/{documentId}/download`                                                                        | Download generated PDF/ODT                       |
| GET    | `/v1/forms/applicable?policyNumber=H100245&revision=1&transactionType=NEW_BUSINESS&effectiveDate=2026-01-01` | Preview rule matches                             |
| GET    | `/v1/forms/TX_HOME_NOTICE/versions`                                                                          | View configured form versions                    |
| POST   | `/v1/form-jobs/{jobId}/retry`                                                                                | Retry failed job when no partial documents exist |

All endpoints use Basic Auth `operator:local-demo-password` in this **local demo only**. This is not production-grade
authentication/authorization or tenant isolation.

## Architecture

```text
Policy Administration (mock policy client; owns issued policies)
    |
    | POST /v1/form-jobs with policy number + immutable revision
    v
Spring Security -> FormsController -> FormJobRepository (H2)
                                |
                                v
                       JobProcessor (@Async)
                                |
                       PolicyClient -> PolicySnapshot
                                |
                       FormCatalog -> Form + effective version
                                |
                       FormDataMapper -> dynamic values
                                |
                       OdtDocumentService -> ODT merge -> LibreOffice PDF
                                |
                       GeneratedDocumentRepository (H2)
                                |
                     GET status / documents / download
                                |
                        Customer portal (not built)
```

## Simplified ER diagram

```text
FORM_JOB (id PK, request_id, policy_number, policy_revision, transaction_type,
          product_code, jurisdiction, transaction_effective_date, idempotency_key UNIQUE,
          status, error_code, created_at, updated_at)
    1
    |
    | generated from
    M
GENERATED_DOCUMENT (id PK, job_id [logical FK], policy_number, policy_revision,
                    form_code, form_version, filename, status, created_at)
```

`PolicySnapshot`, coverages and risk objects are mock upstream data (not persisted in our forms database). Form rules
and version metadata are code-configured in `FormCatalog` in this MVP; production would typically use versioned
configuration tables and approved form catalogs. `job_id` is a logical link rather than a physical database FK in this
demo.

## LibreOffice template details

Templates are actual `.odt` files in `src/main/resources/templates`. Open `home_declarations.odt` with LibreOffice
Writer. Placeholder text such as `{{POLICY_NUMBER}}` and `{{DWELLING_LIMIT}}` is replaced by the backend inside ODT XML;
the filled ODT is converted by LibreOffice headless to PDF. Do not split placeholders across formatting runs in Writer,
because this demo uses text replacement in `content.xml` rather than a full ODF templating engine.

## Important learning limitations

- **Not a real policy administration system:** upstream data is mocked and limited to two sample policies and two
  revisions each.
- **Not a real customer website/portal:** the download API simulates a portal-facing document endpoint;
  customer-specific ownership authorization is not implemented.
- **Not production-safe:** local Basic Auth, file storage, simple `@Async`, no durable queue, no access-control list per
  policy, no full audit/legal retention controls, no cross-process job locking, no template approval workflow, no secure
  signed URLs.
- **Not all 15 products implemented:** the original PDF lists 15 product categories for learning; this runnable MVP
  implements HOME and AUTO rules. Extend `PolicyClient`, `FormCatalog` and templates to add more.
- **Not verified against the real insurer:** names, rules, form codes and payloads are invented for training. Texas
  notices are illustrations only.
- **Asynchronous processing is in-process:** an application crash after accepting a job can leave it incomplete. A
  production design needs recovery, durable work scheduling, safe retries and monitoring.
- **No external integration:** actual insurer policy APIs, document repositories, email delivery, customer portals and
  regulatory form libraries are not included.

## Interview study path

1. Run HOME new business, inspect `FormCatalog.select()` and see which forms were selected.
2. Change effective date to `2025-12-31` in the preview endpoint and observe TX notice version `2025-01`.
3. Run HOME revision 2 as ENDORSEMENT and explain scheduled jewelry coverage.
4. Open an ODT template and find each placeholder in `FormDataMapper`.
5. Break one placeholder by changing it to `{{UNKNOWN_FIELD}}`, run a new idempotency key, and inspect failed job/error.
6. Run AUTO revision 2 ENDORSEMENT and explain vehicle-change notice.
7. Read `FormCatalogTest` and add a rule-boundary test of your own.
