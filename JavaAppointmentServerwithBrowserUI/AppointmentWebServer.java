import java.io.*;
import java.net.*;
import java.util.*;

public class AppointmentWebServer {

    private static final int PORT = 55556;
    private static final boolean DEBUG = true; // set false before demo
    private static final File PHOTO_DIR = new File("photos");
    private static AppointmentDatabase db;

    public static void main(String[] args) throws IOException {
        PHOTO_DIR.mkdirs();
        db = new AppointmentDatabase("appointments.txt");

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            if (DEBUG) System.out.println("Server listening on http://127.0.0.1:" + PORT);
            while (true) {
                Socket client = serverSocket.accept();
                Thread t = new Thread(() -> handleClient(client));
                t.setDaemon(true);
                t.start();
            }
        }
    }

    // ---- Per-connection handling ----

    private static void handleClient(Socket client) {
        try (Socket s = client) {
            InputStream in = s.getInputStream();
            OutputStream out = s.getOutputStream();

            String requestLine = readLine(in);
            if (requestLine == null || requestLine.isEmpty()) return;
            if (DEBUG) System.out.println("Request: " + requestLine);

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String fullPath = parts[1];

            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(),
                                line.substring(colon + 1).trim());
                }
            }

            byte[] body = new byte[0];
            String cl = headers.get("content-length");
            if (cl != null) {
                int len = Integer.parseInt(cl.trim());
                body = readExact(in, len);
            }

            String path = fullPath.contains("?") ? fullPath.substring(0, fullPath.indexOf('?')) : fullPath;
            String query = fullPath.contains("?") ? fullPath.substring(fullPath.indexOf('?') + 1) : "";
            Map<String, String> params = parseQuery(query);

            route(method, path, params, headers, body, out);

        } catch (IOException e) {
            if (DEBUG) e.printStackTrace();
        }
    }

    // ---- Routing ----

    private static void route(String method, String path, Map<String, String> params,
                               Map<String, String> headers, byte[] body, OutputStream out) throws IOException {

        if (path.equals("/") && method.equals("GET")) {
            sendHtml(out, 200, buildHomePage(db.all(), null));

        } else if (path.equals("/add") && method.equals("GET")) {
            sendHtml(out, 200, buildAddForm());

        } else if (path.equals("/add") && method.equals("POST")) {
            handleAddPost(headers, body, out);

        } else if (path.equals("/delete") && method.equals("GET")) {
            int id = Integer.parseInt(params.getOrDefault("id", "-1"));
            Appointment a = db.findById(id);
            if (a != null && a.photoFilename != null) {
                new File(PHOTO_DIR, a.photoFilename).delete();
            }
            db.deleteById(id);
            sendRedirect(out, "/");

        } else if (path.equals("/search") && method.equals("GET")) {
            String q = params.getOrDefault("q", "");
            sendHtml(out, 200, buildHomePage(db.search(q), q));

        } else if (path.equals("/photo") && method.equals("GET")) {
            int id = Integer.parseInt(params.getOrDefault("id", "-1"));
            servePhoto(db.findById(id), out);

        } else {
            sendHtml(out, 404, "<html><body><h2>404 Not Found</h2></body></html>");
        }
    }

    private static void handleAddPost(Map<String, String> headers, byte[] body, OutputStream out) throws IOException {
        String contentType = headers.get("content-type");
        if (contentType == null || !contentType.contains("boundary=")) {
            sendHtml(out, 400, "<html><body>Bad request</body></html>");
            return;
        }
        String boundary = contentType.substring(contentType.indexOf("boundary=") + 9);

        MultipartParser mp = MultipartParser.parse(body, boundary);
        String date = mp.fields.getOrDefault("date", "");
        String time = mp.fields.getOrDefault("time", "");
        String with = mp.fields.getOrDefault("with", "");
        String notes = mp.fields.getOrDefault("notes", "");

        Appointment a = db.add(date, time, with, notes, null);

        MultipartParser.UploadedFile photo = mp.files.get("photo");
        if (photo != null && photo.data.length > 0) {
            String ext = guessExtension(photo.filename, photo.contentType);
            String storedName = "appt_" + a.id + ext;
            try (FileOutputStream fos = new FileOutputStream(new File(PHOTO_DIR, storedName))) {
                fos.write(photo.data);
            }
            db.setPhoto(a.id, storedName);
        }

        sendRedirect(out, "/");
    }

    // ---- Serving the photo as a binary HTTP response (the second "challenge" piece) ----

    private static void servePhoto(Appointment a, OutputStream out) throws IOException {
        if (a == null || a.photoFilename == null) {
            sendHtml(out, 404, "<html><body>No photo</body></html>");
            return;
        }
        File file = new File(PHOTO_DIR, a.photoFilename);
        if (!file.exists()) {
            sendHtml(out, 404, "<html><body>Photo missing</body></html>");
            return;
        }

        byte[] data = readAllBytes(file);
        String contentType = mapExtensionToContentType(a.photoFilename);

        PrintWriter headerWriter = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), false);
        headerWriter.print("HTTP/1.1 200 OK\r\n");
        headerWriter.print("Content-Type: " + contentType + "\r\n");
        headerWriter.print("Content-Length: " + data.length + "\r\n");
        headerWriter.print("Connection: close\r\n");
        headerWriter.print("\r\n");
        headerWriter.flush();

        out.write(data); // raw image bytes, not text
        out.flush();
    }

    // ---- HTML page builders ----

    private static String buildHomePage(Appointment[] list, String searchQuery) {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>Appointments</title>").append(STYLE).append("</head><body>");
        html.append("<div class=\"wrap\">");
        html.append("<h1>📅 Appointments</h1>");
        html.append("<p><a class=\"btn\" href=\"/add\">+ New appointment</a></p>");

        html.append("<form class=\"search\" method=\"GET\" action=\"/search\">");
        html.append("<input type=\"text\" name=\"q\" placeholder=\"Search...\" value=\"")
            .append(searchQuery == null ? "" : escape(searchQuery)).append("\">");
        html.append("<button type=\"submit\">Search</button>");
        if (searchQuery != null) html.append(" <a href=\"/\">Clear</a>");
        html.append("</form>");

        if (list.length == 0) {
            html.append("<p class=\"empty\">No appointments found.</p>");
        }

        html.append("<div class=\"list\">");
        for (Appointment a : list) {
            html.append("<div class=\"card\">");
            if (a.photoFilename != null) {
                html.append("<img class=\"thumb\" src=\"/photo?id=").append(a.id).append("\">");
            } else {
                html.append("<div class=\"thumb placeholder\">No photo</div>");
            }
            html.append("<div class=\"info\">");
            html.append("<div class=\"who\">").append(escape(a.withWhom)).append("</div>");
            html.append("<div class=\"when\">").append(escape(a.date)).append(" at ").append(escape(a.time)).append("</div>");
            html.append("<div class=\"notes\">").append(escape(a.notes)).append("</div>");
            html.append("<a class=\"del\" href=\"/delete?id=").append(a.id).append("\" onclick=\"return confirm('Delete this appointment?')\">Delete</a>");
            html.append("</div></div>");
        }
        html.append("</div></div></body></html>");
        return html.toString();
    }

    private static String buildAddForm() {
        StringBuilder html = new StringBuilder();
        html.append("<html><head><title>New Appointment</title>").append(STYLE).append("</head><body>");
        html.append("<div class=\"wrap\">");
        html.append("<h1>New Appointment</h1>");
        html.append("<form method=\"POST\" action=\"/add\" enctype=\"multipart/form-data\" class=\"form\">");
        html.append("<label>Date (YYYY-MM-DD)</label><input type=\"text\" name=\"date\" required>");
        html.append("<label>Time (HH:MM)</label><input type=\"text\" name=\"time\" required>");
        html.append("<label>With whom</label><input type=\"text\" name=\"with\" required>");
        html.append("<label>Notes</label><textarea name=\"notes\"></textarea>");
        html.append("<label>Photo (optional)</label><input type=\"file\" name=\"photo\" accept=\"image/*\">");
        html.append("<button type=\"submit\">Save</button>");
        html.append("</form>");
        html.append("<p><a href=\"/\">&larr; Back to list</a></p>");
        html.append("</div></body></html>");
        return html.toString();
    }

    private static final String STYLE =
        "<style>" +
        "body{font-family:Arial,sans-serif;background:#1e1e2f;color:#eee;margin:0;padding:30px;}" +
        ".wrap{max-width:700px;margin:0 auto;}" +
        "h1{margin-bottom:20px;}" +
        ".btn{background:#4fd1c5;color:#12121c;padding:8px 16px;border-radius:6px;text-decoration:none;font-weight:bold;}" +
        ".search{margin:15px 0;} .search input{padding:6px;border-radius:4px;border:1px solid #444;background:#12121c;color:#eee;}" +
        ".search button{padding:6px 12px;}" +
        ".card{display:flex;gap:15px;background:#12121c;border:1px solid #333;border-radius:10px;padding:15px;margin-bottom:12px;align-items:center;}" +
        ".thumb{width:70px;height:70px;object-fit:cover;border-radius:8px;background:#333;}" +
        ".placeholder{display:flex;align-items:center;justify-content:center;font-size:0.7em;color:#888;}" +
        ".info{flex:1;} .who{font-weight:bold;font-size:1.1em;} .when{color:#4fd1c5;} .notes{color:#aaa;margin-top:4px;}" +
        ".del{color:#f6ad55;text-decoration:none;font-size:0.85em;}" +
        ".form label{display:block;margin-top:12px;color:#bbb;} .form input,.form textarea{width:100%;padding:8px;margin-top:4px;border-radius:6px;border:1px solid #444;background:#12121c;color:#eee;box-sizing:border-box;}" +
        ".form button{margin-top:16px;background:#4fd1c5;border:none;padding:10px 20px;border-radius:6px;font-weight:bold;cursor:pointer;}" +
        "a{color:#4fd1c5;} .empty{color:#888;}" +
        "</style>";

    // ---- Low-level HTTP helpers ----

    private static String readLine(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int c;
        boolean any = false;
        while ((c = in.read()) != -1) {
            any = true;
            if (c == '\n') break;
            if (c == '\r') continue;
            sb.append((char) c);
        }
        return any ? sb.toString() : null;
    }

    private static byte[] readExact(InputStream in, int len) throws IOException {
        byte[] buf = new byte[len];
        int total = 0;
        while (total < len) {
            int read = in.read(buf, total, len - total);
            if (read == -1) break;
            total += read;
        }
        return buf;
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            map.put(urlDecode(pair.substring(0, eq)), urlDecode(pair.substring(eq + 1)));
        }
        return map;
    }

    private static String urlDecode(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '+') sb.append(' ');
            else if (c == '%' && i + 2 < s.length()) {
                sb.append((char) Integer.parseInt(s.substring(i + 1, i + 3), 16));
                i += 2;
            } else sb.append(c);
        }
        return sb.toString();
    }

    private static void sendHtml(OutputStream out, int code, String html) throws IOException {
        String status = code == 200 ? "200 OK" : code == 404 ? "404 Not Found" : "400 Bad Request";
        byte[] bytes = html.getBytes("UTF-8");
        PrintWriter w = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), false);
        w.print("HTTP/1.1 " + status + "\r\n");
        w.print("Content-Type: text/html; charset=UTF-8\r\n");
        w.print("Content-Length: " + bytes.length + "\r\n");
        w.print("Connection: close\r\n\r\n");
        w.flush();
        out.write(bytes);
        out.flush();
    }

    private static void sendRedirect(OutputStream out, String location) throws IOException {
        PrintWriter w = new PrintWriter(new OutputStreamWriter(out, "UTF-8"), false);
        w.print("HTTP/1.1 303 See Other\r\n");
        w.print("Location: " + location + "\r\n");
        w.print("Connection: close\r\n\r\n");
        w.flush();
    }

    private static byte[] readAllBytes(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            return bos.toByteArray();
        }
    }

    private static String guessExtension(String filename, String contentType) {
        if (filename != null && filename.contains(".")) {
            return filename.substring(filename.lastIndexOf('.')).toLowerCase();
        }
        if (contentType != null) {
            if (contentType.contains("png")) return ".png";
            if (contentType.contains("gif")) return ".gif";
        }
        return ".jpg";
    }

    private static String mapExtensionToContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}