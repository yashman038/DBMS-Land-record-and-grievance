# PattaDesk - Digital Land Record Management and Grievance Redressal System

Java Servlets + Oracle JDBC (Thin driver) + plain HTML/CSS/JavaScript.
No Spring, no Hibernate, no Node.js. Runs on Apache Tomcat 10.1.

```
Browser (HTML/CSS/JS)  --fetch-->  Servlets (/api/...)  -->  DAO classes  --JDBC-->  Oracle XE
```

## 1. What you need (Windows)

| Tool | Version | Check with |
|---|---|---|
| JDK | 17 or newer | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| Apache Tomcat | **10.1.x** (not 9, not 11) | download the zip from tomcat.apache.org |
| Oracle Database XE | 18c / 21c | `sqlplus` opens |

Tomcat 10.1 matters: the code uses `jakarta.servlet`. Tomcat 9 uses `javax.servlet` and will not work.

## 2. Database

1. In SQL*Plus connect as the schema user that will own the tables, for example `sqlplus land_user/password@localhost:1521/XEPDB1`.
2. Run your setup script once: `@land_records_setup.sql` (or `@land_setup.sql`). Both create the same tables, procedures and sample data.
   WARNING: the script drops and recreates these tables. Use a practice schema.
3. Check: `SELECT COUNT(*) FROM users;` should give 6.

Confirm the service name: `SHOW CON_NAME` should print `XEPDB1`.

## 3. Configure the connection

Edit `src/main/resources/db.properties`:

```
db.url=jdbc:oracle:thin:@localhost:1521/XEPDB1
db.user=land_user
db.password=your_password
```

(Environment variables `DB_URL`, `DB_USER`, `DB_PASSWORD` override the file.)

## 4. Create working demo passwords (one time)

The SQL script only stores dummy text in `password_hash`, so nobody could log in. Run:

```
mvn compile exec:java
```

It replaces every non-BCrypt `password_hash` with a real BCrypt hash of `Demo@1234` and prints the accounts:

| Role | Email | Password |
|---|---|---|
| admin | suresh.babu@landoffice.gov.in | Demo@1234 |
| admin | meena.iyer@landoffice.gov.in | Demo@1234 |
| citizen | arun.prakash@mail.com | Demo@1234 |
| citizen | fathima.b@mail.com | Demo@1234 |
| citizen | lakshmi.d@mail.com | Demo@1234 |
| citizen | karthik.r@mail.com | Demo@1234 |

Your existing rows are not deleted; only the `password_hash` column changes (and only if it is not already a BCrypt hash).

## 5. Build and run

```
mvn clean package
copy target\pattadesk.war C:\path\to\apache-tomcat-10.1.x\webapps\
C:\path\to\apache-tomcat-10.1.x\bin\startup.bat
```

Open http://localhost:8080/pattadesk/

Errors appear in `apache-tomcat-10.1.x\logs\catalina.<date>.log` and `localhost.<date>.log`.
After editing `db.properties` you must rebuild (`mvn clean package`) and redeploy the WAR.

## 6. How the pieces connect

| Layer | Files |
|---|---|
| Pages | `index.html` (login), `user/portal.html`, `admin/portal.html` |
| Page scripts | `js/common.js` (shared), `js/login.js`, `js/user.js`, `js/admin.js` |
| Security filter | `filter/AuthFilter.java` (login, role, CSRF check on every `/api`, `/user`, `/admin` request) |
| Servlets | `LoginServlet`, `LogoutServlet`, `MeServlet`, `CitizenApiServlet` (`/api/citizen/*`), `AdminApiServlet` (`/api/admin/*`) |
| DAOs (all SQL lives here) | `UserDAO`, `LandParcelDAO`, `GrievanceDAO` |
| JDBC connection | `config/DBConnection.java` |
| Models | `model/*.java` (Java records) |
| Helpers | `util/Db`, `util/JsonUtil`, `util/Http`, `util/ApiException` |
| Demo passwords | `tools/DemoSetup.java` |

### Which Oracle objects are used

| Action | Oracle object |
|---|---|
| Submit grievance | `INSERT INTO grievance ... RETURNING ticket_id` (trigger `TRG_GRIEVANCE_BIU` creates the id and ticket number) |
| Assign grievance | procedure `SP_ASSIGN_GRIEVANCE(admin_id, grievance_id, assignee_id)` |
| Resolve / reject | procedure `SP_RESOLVE_GRIEVANCE(admin_id, grievance_id, remarks, outcome)` |
| Move to In Progress | one guarded `UPDATE` (status must be `assigned`); the schema has no procedure for this step |
| Change land status | procedure `SP_UPDATE_PARCEL_STATUS(admin_id, parcel_id, status, encumbrance)` |
| Transfer ownership | procedure `SP_TRANSFER_OWNERSHIP(admin_id, parcel_id, new_owner_id, type, deed_no, amount, date)` |
| Valuation sync | trigger `TRG_PRICE_SYNC_MARKET_VALUE` keeps `land_parcel.market_value` current |

Ownership history is only read or appended by the procedure. The app has no edit or delete for it.

## 7. Security built in

- Server-side login with BCrypt; new session id at login; 30-minute timeout; HttpOnly + SameSite=Strict cookie.
- Role check on the server for every page and API call (a citizen cannot open admin pages or call admin APIs).
- The user id always comes from the session, never from the browser.
- Citizens can open only parcels where `current_owner_id` is their own id, and only their own grievances.
- CSRF token required on every POST.
- All SQL uses `PreparedStatement` / `CallableStatement`.
- Every value shown on a page is HTML-escaped; JSON output is also escaped.
- Database errors show a safe message in the browser; details go to the Tomcat log.
- Aadhaar is masked; password hashes are never sent to the browser.
- Add `<secure>true</secure>` to the cookie-config in `web.xml` when you serve the site over HTTPS.

## 8. Test checklist

1. Login as `arun.prakash@mail.com`: you land on the citizen dashboard.
2. Wrong password: "Invalid email or password."
3. Login as `suresh.babu@landoffice.gov.in`: you land on the admin dashboard.
4. While logged in as a citizen, open `http://localhost:8080/pattadesk/admin/portal.html`: you are sent back to the citizen portal.
5. Citizen: My Land Records shows only parcels 1 and 5. Open one and check the ownership history.
6. Citizen: try `#/parcel/2` (Fathima's parcel): "You are not authorised to view this land record."
7. Citizen: submit a grievance; a ticket such as `GRV-2026-00005` is shown.
8. Citizen: Grievances shows it as Submitted.
9. Admin: Grievances list shows all tickets. Open one, Assign it. Status becomes Assigned.
10. Admin: Move to In Progress, then add remarks and Resolve. Try Reject on another one.
11. Citizen: refresh Grievances; the new status and admin remarks are visible.
12. Admin: Land Records, open a parcel, change status to Disputed with details; then back to Clear.
13. Admin: transfer a parcel to another citizen; the old owner's period is closed and a new "Current" row appears.
14. Admin dashboard: "Check Oracle connection" shows the schema user and service.
15. Logout, then press the browser Back button: you cannot see private pages.

## 9. Troubleshooting

| Symptom | Fix |
|---|---|
| Login page says "Cannot reach the database" | Edit `db.properties`, rebuild, redeploy; check Oracle XE is running and the service is `XEPDB1` |
| `ORA-12514` in the log | Wrong service name in `db.url` |
| `ORA-00942 table or view does not exist` | The tables belong to a different Oracle user than `db.user` |
| "Invalid email or password" for demo accounts | You did not run `mvn compile exec:java` yet |
| HTTP 404 for every page | WAR not deployed, or you opened the wrong path (use `/pattadesk/`) |
| `ClassNotFoundException: javax.servlet...` or the app does not start | Use Tomcat 10.1, not 9 |
| `mvn` cannot find a dependency | Check your internet connection; Maven downloads ojdbc11 and jbcrypt |
