## Appointment Server with Photos

A full CRUD appointment manager, driven entirely through a standard browser (Chrome/Firefox) - including optional photo uploads per contact, transmitted and served over raw HTTP with no libraries.

## How it works
ServerSocket handles both GET and POST requests, reading headers line-by-line and the body as an exact byte count (Content-Length) rather than as text, necessary since a photo's bytes aren't safe to read line by line.
MultipartParser hand-decodes multipart/form-data POST bodies: splitting on the browser-chosen boundary string, then separating each part's headers (Content-Disposition, Content-Type) from its raw content bytes.
Uploaded photos are saved to disk and served back via /photo?id=N with a correct binary Content-Type (image/jpeg, image/png, etc.) and Content-Length - the key "challenge" part of the assignment.
Appointments (array-backed) persist to appointments.txt; photos are stored as separate files referenced by filename.
Styled with a dark-themed CSS UI: appointment cards with thumbnails, an add-appointment form, and a search box.

## Running
bash

javac *.java

java AppointmentWebServer


Open http://127.0.0.1:55556 in a browser. All interaction — adding, searching, deleting, viewing photos - happens through the browser UI; no command-line interaction is needed after startup.
