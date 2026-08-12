import java.util.*;

public class MultipartParser {

    public static class UploadedFile {
        public String filename;
        public String contentType;
        public byte[] data;
    }

    public Map<String, String> fields = new HashMap<>();
    public Map<String, UploadedFile> files = new HashMap<>();

    public static MultipartParser parse(byte[] body, String boundary) {
        MultipartParser result = new MultipartParser();
        byte[] boundaryBytes = ("--" + boundary).getBytes();

        int pos = indexOf(body, boundaryBytes, 0);
        while (pos != -1) {
            int partStart = pos + boundaryBytes.length;
            if (partStart + 1 < body.length && body[partStart] == '-' && body[partStart + 1] == '-') {
                break; // reached the final "--boundary--"
            }
            int nextBoundary = indexOf(body, boundaryBytes, partStart);
            if (nextBoundary == -1) break;

            int partEnd = Math.max(partStart, nextBoundary - 2); // strip CRLF before next boundary
            parsePart(body, partStart, partEnd, result);
            pos = nextBoundary;
        }
        return result;
    }

    private static void parsePart(byte[] body, int start, int end, MultipartParser result) {
        int p = start;
        if (p + 1 < end && body[p] == '\r' && body[p + 1] == '\n') p += 2;

        byte[] headerEnd = { '\r', '\n', '\r', '\n' };
        int headerEndPos = indexOf(body, headerEnd, p);
        if (headerEndPos == -1 || headerEndPos > end) return;

        String headerText = new String(body, p, headerEndPos - p);
        int contentStart = headerEndPos + 4;
        int contentEnd = Math.max(contentStart, end);

        String name = extractAttr(headerText, "name");
        String filename = extractAttr(headerText, "filename");
        String contentType = extractContentType(headerText);

        byte[] content = Arrays.copyOfRange(body, contentStart, contentEnd);

        if (filename != null && !filename.isEmpty()) {
            UploadedFile uf = new UploadedFile();
            uf.filename = filename;
            uf.contentType = contentType;
            uf.data = content;
            result.files.put(name, uf);
        } else if (name != null) {
            result.fields.put(name, new String(content).trim());
        }
    }

    private static String extractAttr(String headerText, String attr) {
        String marker = attr + "=\"";
        int i = headerText.indexOf(marker);
        if (i == -1) return null;
        int start = i + marker.length();
        int end = headerText.indexOf("\"", start);
        return end == -1 ? null : headerText.substring(start, end);
    }

    private static String extractContentType(String headerText) {
        for (String line : headerText.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-type:")) {
                return line.substring(line.indexOf(":") + 1).trim();
            }
        }
        return "application/octet-stream";
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}