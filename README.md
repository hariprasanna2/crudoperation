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
4. Run `com.example.crudoperation.CrudoperationApplication` and wait for the
   successful startup message. Keep it running while sending requests.

The default port is now 8080 instead of a random port. Set `PORT` as needed.
There is no HTML frontend. View JSON at `http://localhost:8080/students` or use
Postman. This project retains its original `/students` path.

## Test using Postman

Replace port 8080 below if you set `PORT`. For POST and PUT select **Body → raw →
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
missing students, persistence and protection against accidental overwrites.
A local smoke test is still needed for your actual MySQL server and schema.

`pom.xml` defines the build; `src/main/resources/application.properties` defines
runtime settings. The `DDL_AUTO=update` default creates/updates the learning schema;
use migrations and `DDL_AUTO=validate` for a deployed database. Generated `target/`
files and local IDE settings are excluded from Git.

The previous revision committed a database password, including in generated files.
If it was real, rotate it in MySQL: removing current files does not erase Git history.
