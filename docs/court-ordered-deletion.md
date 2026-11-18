# Court-ordered deletion (manual runbook)

politigif never deletes data: delete, hide, reset all flip an integer `status` on the record
(`src/main/java/com/kismetsoftware/politigif/status/Status.java`), and `NoDeleteArchitectureTest`
fails the build if a delete comes back into the app. **The one exception is a court order.** There
is deliberately no code path for it: a person runs these commands by hand, one record at a time.

Production Mongo is on the droplet (`db.kismet-software.com`); the person who owns production runs
everything below. Never paste credentials into a ticket, a chat or this file.

## 0. Before anything: record the order

Write down, somewhere outside the database (the order is the authority, not this runbook):

- the court order reference (case number, court, date, what it orders deleted);
- who ran the deletion, when, and the exact ids deleted (the commands below print them).

Keep that record even though the data itself is gone. Do not copy the deleted content into it.

## 1. Connect

```powershell
mongosh "$env:MONGO_DB_URI"
```

```javascript
use politigif
```

(`MONGO_DB_URI` is the same value the app runs with. The database name is `politigif`.)

## 2. Find the ids

Every status is included on purpose: a reset or hidden record is still a record.

```javascript
// The person, by email: every account they have had (ACTIVE = 1, RESET = 2).
const email = "person@example.com";            // lowercase, as stored
const users = db.users.find({ email }, { _id: 1, status: 1, createdAt: 1, retiredAt: 1 }).toArray();
printjson(users);
const userIds = users.map(u => u._id.toString());

// Their GIFs: current owner OR original owner (an account reset detaches GIFs but keeps originalOwnerId).
const gifs = db.gifs.find({ $or: [ { ownerId: { $in: userIds } }, { originalOwnerId: { $in: userIds } } ] },
                          { _id: 1, title: 1, status: 1 }).toArray();
printjson(gifs);
const gifIds = gifs.map(g => g._id);

// Reports filed BY them, and reports filed ABOUT their GIFs.
printjson(db.reports.find({ $or: [ { reporterId: { $in: userIds } }, { originalReporterId: { $in: userIds } },
                                   { gifId: { $in: gifIds } } ] }, { _id: 1, gifId: 1, status: 1 }).toArray());

// Sign-in link records for the address (hashes only, but they carry the email).
printjson(db.signin_links.find({ email }, { _id: 1, status: 1, createdAt: 1 }).toArray());
```

If the order names one GIF instead of a person, the id is the 10 characters in its URL:
`https://www.politigif.com/g/<id>`.

## 3. Delete exactly what the order covers

Run the `find` again with the same filter and check the count before each delete.

```javascript
// One GIF record by id:
db.gifs.deleteOne({ _id: "abcdEFGH23" });

// Reports, by id (or by the filter from step 2 once you have checked it):
db.reports.deleteOne({ _id: ObjectId("...") });

// Sign-in link records for the address:
db.signin_links.deleteMany({ email: "person@example.com" });

// The account record(s), by id:
db.users.deleteOne({ _id: ObjectId("...") });
```

## 4. Delete the stored MP4 in Spaces

The MP4 for GIF `<id>` is the object `gifs/<id>.mp4` in the bucket. Nothing else is stored for a GIF:
the `.gif`, `.webp` and poster are converted on each request and never saved.

With the AWS CLI (Spaces speaks the S3 API), using the same `SPACES_*` values as the app:

```powershell
aws s3 rm "s3://$env:SPACES_BUCKET/gifs/abcdEFGH23.mp4" --endpoint-url "$env:SPACES_ENDPOINT"
```

Check it is gone:

```powershell
aws s3 ls "s3://$env:SPACES_BUCKET/gifs/abcdEFGH23.mp4" --endpoint-url "$env:SPACES_ENDPOINT"
```

(Use a Spaces key that can delete objects in that bucket. The app never deletes, so nothing about
this depends on its key.)

## 5. After

- Re-run the step 2 `find`s: they should return nothing for what the order covered.
- Add the deleted ids and the time to the record from step 0.
- Backups: nightly dumps of the droplet keep the data until they rotate out. If the order covers
  backups too, that is a separate step on the backup copies.
