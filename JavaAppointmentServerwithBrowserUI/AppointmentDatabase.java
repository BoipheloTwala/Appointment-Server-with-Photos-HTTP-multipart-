import java.io.*;
import java.util.ArrayList;

public class AppointmentDatabase {
    private Appointment[] appointments;
    private int count;
    private int nextId;
    private final String filename;

    public AppointmentDatabase(String filename) {
        this.filename = filename;
        appointments = new Appointment[16];
        count = 0;
        nextId = 1;
        load();
    }

    public synchronized Appointment add(String date, String time, String with, String notes, String photoFilename) {
        ensureCapacity();
        Appointment a = new Appointment(nextId++, date, time, with, notes, photoFilename);
        appointments[count++] = a;
        save();
        return a;
    }

    public synchronized void setPhoto(int id, String photoFilename) {
        Appointment a = findById(id);
        if (a != null) {
            a.photoFilename = photoFilename;
            save();
        }
    }

    public synchronized boolean deleteById(int id) {
        for (int i = 0; i < count; i++) {
            if (appointments[i].id == id) {
                for (int j = i; j < count - 1; j++) appointments[j] = appointments[j + 1];
                appointments[--count] = null;
                save();
                return true;
            }
        }
        return false;
    }

    public synchronized Appointment findById(int id) {
        for (int i = 0; i < count; i++) if (appointments[i].id == id) return appointments[i];
        return null;
    }

    public synchronized Appointment[] search(String keyword) {
        ArrayList<Appointment> results = new ArrayList<>();
        String k = keyword.toLowerCase();
        for (int i = 0; i < count; i++) {
            Appointment a = appointments[i];
            if (a.date.toLowerCase().contains(k) || a.time.toLowerCase().contains(k)
                    || a.withWhom.toLowerCase().contains(k) || a.notes.toLowerCase().contains(k)) {
                results.add(a);
            }
        }
        return results.toArray(new Appointment[0]);
    }

    public synchronized Appointment[] all() {
        Appointment[] copy = new Appointment[count];
        System.arraycopy(appointments, 0, copy, 0, count);
        return copy;
    }

    private void ensureCapacity() {
        if (count == appointments.length) {
            Appointment[] bigger = new Appointment[appointments.length * 2];
            System.arraycopy(appointments, 0, bigger, 0, appointments.length);
            appointments = bigger;
        }
    }

    private void load() {
        File f = new File(filename);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                Appointment a = Appointment.fromFileLine(line);
                if (a != null) {
                    ensureCapacity();
                    appointments[count++] = a;
                    if (a.id >= nextId) nextId = a.id + 1;
                }
            }
        } catch (IOException e) { /* silent */ }
    }

    private void save() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filename, false))) {
            for (int i = 0; i < count; i++) pw.println(appointments[i].toFileLine());
        } catch (IOException e) { /* silent */ }
    }
}