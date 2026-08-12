public class Appointment {
    int id;
    String date, time, withWhom, notes;
    String photoFilename; // null if no photo

    public Appointment(int id, String date, String time, String withWhom, String notes, String photoFilename) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.withWhom = withWhom;
        this.notes = notes;
        this.photoFilename = photoFilename;
    }

    public String toFileLine() {
        return id + "|" + date + "|" + time + "|" + withWhom + "|" + notes + "|" +
               (photoFilename == null ? "" : photoFilename);
    }

    public static Appointment fromFileLine(String line) {
        String[] p = line.split("\\|", -1);
        if (p.length < 6) return null;
        return new Appointment(Integer.parseInt(p[0]), p[1], p[2], p[3], p[4],
                p[5].isEmpty() ? null : p[5]);
    }
}