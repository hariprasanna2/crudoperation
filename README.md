# Student CRUD Operations

Spring Boot 4.1.2 REST API with MySQL persistence. The Maven project lives in
`crudoperation/`. Requires JDK 21. Boot manages compatible dependency versions;
unused Lombok and redundant compiler configuration have been removed.

## IntelliJ setup

1. Open the inner `crudoperation/pom.xml` as a Maven project and reload Maven.
2. Select JDK 21. Start MySQL and create `student_db` using a database administrator:

   ```sql
   CREATE DATABASE IF NOT EXISTS student_db;
   ```

   Your application user needs SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER and
   INDEX privileges on `student_db.*` for this learning setup.
3. Under **Run → Edit Configurations → CrudoperationApplication**, add your actual
   MySQL credentials under **Environment variables** (use **Modify options** if
   the field is hidden):

   ```text
   DB_USERNAME=your_mysql_username;DB_PASSWORD=your_mysql_password
   ```

   Optionally set `DB_URL` to override `jdbc:mysql://localhost:3306/student_db`.
   If port 8080 is already used by your other project, also set `PORT=8081`.
4. Configure the API accounts and JWT key as described below. These accounts are
   separate from your MySQL login.
5. Run `com.example.crudoperation.CrudoperationApplication` and wait for the
   successful startup message. Keep it running while sending requests.

The default port is now 8080 instead of a random port. Set `PORT` as needed.
There is no HTML frontend. Use Postman with a Bearer token at
`http://localhost:8080/students`. A browser request without a token returns 401. This project retains its original `/students` path.

## JWT security setup

All student endpoints require a Bearer token. USER accounts may read students;
ADMIN accounts may read, create, update and delete students. Other paths/methods
are denied. POST `/auth/login` is the public sign-in endpoint.

In IntelliJ's run configuration, set all five additional environment variables:

| Name | Value |
| --- | --- |
| `API_ADMIN_USERNAME` | Your chosen admin username |
| `API_ADMIN_PASSWORD` | A unique password of at least 12 characters |
| `API_USER_USERNAME` | A different username for read-only access |
| `API_USER_PASSWORD` | A different strong password of at least 12 characters |
| `JWT_SECRET` | A random Base64-encoded signing key, generated below |

Account passwords may contain at most 72 UTF-8 bytes (the BCrypt limit). Generate
JWT_SECRET once in a terminal and paste the result into IntelliJ's environment
variables; do not paste this key into Postman or commit it:

```sh
openssl rand -base64 48
```

These two accounts are stored in memory as BCrypt hashes, with no default
credentials, registration endpoint or users table. Configure them before startup.
Student records continue to be stored in MySQL. The signing key must decode to at
least 32 bytes; invalid or missing settings prevent startup.

## Sign in using Postman

1. Create a **POST** request to `http://localhost:8080/auth/login` (adjust PORT).
2. Select **Authorization → No Auth** on this login request.
3. Select **Body → raw → JSON** and enter your configured API credentials:

   ```json
   {
     "username": "your_chosen_admin_username",
     "password": "your_actual_admin_password"
   }
   ```

4. Click **Send**. A correct login returns 200 with `accessToken`,
   `tokenType: "Bearer"` and `expiresIn: 900`. Wrong credentials return 401.
5. Copy only the `accessToken` value. On each student request select
   **Authorization → Bearer Token** and paste it into **Token**.

Tokens expire after 15 minutes; sign in again for a new token. The server verifies
signature, issuer, audience and expiry. No authentication cookies or HTTP Basic
credentials are accepted. The API is stateless, so CSRF tokens are not required.
Use HTTPS outside localhost because tokens and login passwords must be protected
in transit. For a deployed service add login rate limiting and a persistent account
or identity provider. There is no refresh/revocation endpoint: existing tokens last
until expiry (with up to 60 seconds clock skew); changing the signing key invalidates
all existing tokens. Logging out in Postman means deleting your local token.

## Test using Postman

Sign in as ADMIN first and set the Bearer token on each request. Replace port 8080
below if you set `PORT`. For POST and PUT select **Body → raw →
JSON**, then paste the request into the upper body editor:

```json
{
  "name": "Sai",
  "email": "sai@example.com",
  "course": "Java"
}
```

| Method | URL | Expected response |
| --- | --- | --- |
| POST | `http://localhost:8080/students` | 201 and saved student; note the ID |
| GET | `http://localhost:8080/students` | 200 and student list |
| GET | `http://localhost:8080/students/1` | 200 and student, or 404 if absent |
| PUT | `http://localhost:8080/students/1` | 200 and updated student, or 404 |
| DELETE | `http://localhost:8080/students/1` | 204, or 404 if absent |

Use the actual generated ID instead of `1`. For PUT send all three fields with
updated values. GET again after updating to confirm persistence. After deletion,
GET for that ID should return 404. Blank fields, invalid email, fields longer than
255 characters, nonpositive IDs and malformed requests return 400. Names and
courses are trimmed. The database generates IDs; POST cannot overwrite an existing
student by supplying its ID.

Also check the access rules:

- Remove the Bearer token: any student request returns 401.
- Use an expired, altered or incorrectly signed token: returns 401.
- Log in as the read-only USER: GET returns 200, POST/PUT/DELETE return 403.
- Log in as ADMIN: the CRUD tests in the table above succeed.

To inspect stored data in MySQL:

```sql
USE student_db;
SELECT * FROM students;
```

## Automated build and tests

From the inner `crudoperation/` directory:

```sh
./mvnw clean verify
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd`. The repaired Maven wrapper pins Maven 3.9.16 and its
SHA-256 checksum. Tests use isolated H2 in MySQL compatibility mode, with no MySQL
server or credentials required. They verify HTTP CRUD behavior, validation,
missing students, persistence and protection against accidental overwrites, plus real
JWT login, signature/expiry checks and USER/ADMIN access rules. Test credentials and
the test signing key are isolated under src/test and are not production defaults.
A local smoke test is still needed for your actual MySQL server and schema.

`pom.xml` defines the build; `src/main/resources/application.properties` defines
runtime settings. The `DDL_AUTO=update` default creates/updates the learning schema;
use migrations and `DDL_AUTO=validate` for a deployed database. Generated `target/`
files and local IDE settings are excluded from Git.

The previous revision committed a database password, including in generated files.
If it was real, rotate it in MySQL: removing current files does not erase Git history.
