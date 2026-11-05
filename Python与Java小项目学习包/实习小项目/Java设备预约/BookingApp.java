import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.io.IOException;

/** 单机命令行教学项目：时间冲突检测、文件持久化、异常处理。 */
public class BookingApp {
    static class Booking {
        String id, device, user;
        LocalDateTime start, end;
        Booking(String id, String device, String user, LocalDateTime start, LocalDateTime end) {
            this.id=id; this.device=device; this.user=user; this.start=start; this.end=end;
        }
        public String toString() { return id+"\t"+device+"\t"+user+"\t"+start+"\t"+end; }
    }
    static boolean overlaps(LocalDateTime a, LocalDateTime b, LocalDateTime c, LocalDateTime d) {
        return a.isBefore(d) && c.isBefore(b); // 半开区间，首尾相接允许预约
    }
    static List<Booking> load(Path file) throws IOException {
        List<Booking> rows = new ArrayList<>();
        if (!Files.exists(file)) return rows;
        int line=0;
        for (String s: Files.readAllLines(file, StandardCharsets.UTF_8)) {
            line++;
            String[] v=s.split("\t", -1);
            try {
                if(v.length!=5) throw new IllegalArgumentException();
                Booking r=new Booking(v[0],v[1],v[2],LocalDateTime.parse(v[3]),LocalDateTime.parse(v[4]));
                if(!r.start.isBefore(r.end)) throw new IllegalArgumentException();
                rows.add(r);
            } catch(RuntimeException ex) { throw new IOException("数据文件第"+line+"行损坏；请先检查备份", ex); }
        }
        return rows;
    }
    static void save(Path file, List<Booking> rows) throws IOException {
        Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        List<String> lines=new ArrayList<>();
        for(Booking r: rows) lines.add(r.toString());
        Files.write(tmp, lines, StandardCharsets.UTF_8);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
    static String read(Scanner s, String label) {
        System.out.print(label+": ");
        String v=s.nextLine().trim();
        if(v.isEmpty() || v.contains("\t")) throw new IllegalArgumentException("输入不能为空或包含制表符");
        return v;
    }
    public static void main(String[] args) throws Exception {
        Path file=Paths.get("bookings.tsv");
        if(args.length>0 && args[0].equals("--self-test")) {
            LocalDateTime t=LocalDateTime.of(2026,11,6,9,0);
            if(!overlaps(t,t.plusHours(2),t.plusHours(1),t.plusHours(3))) throw new AssertionError("交叉区间");
            if(overlaps(t,t.plusHours(1),t.plusHours(1),t.plusHours(2))) throw new AssertionError("相邻区间");
            if(!overlaps(t,t.plusHours(3),t.plusHours(1),t.plusHours(2))) throw new AssertionError("包含区间");
            Path temp=Files.createTempFile("booking-test", ".tsv");
            try {
                List<Booking> rows=new ArrayList<>();
                rows.add(new Booking("test", "camera", "student", t, t.plusHours(1)));
                save(temp,rows);
                if(load(temp).size()!=1 || !load(temp).get(0).device.equals("camera")) throw new AssertionError("持久化");
            } finally { Files.deleteIfExists(temp); }
            System.out.println("PASS: overlap, adjacency, containment, persistence"); return;
        }
        List<Booking> rows=load(file);
        Scanner s=new Scanner(System.in);
        while(true) {
            System.out.println("\n1 新增预约 | 2 查看预约 | 3 取消预约 | 0 退出");
            if(!s.hasNextLine()) break;
            String cmd=s.nextLine().trim();
            try {
                if(cmd.equals("0")) break;
                if(cmd.equals("1")) {
                    String device=read(s,"设备编号"), user=read(s,"姓名");
                    LocalDateTime start=LocalDateTime.parse(read(s,"开始时间，例如2026-11-06T09:00"));
                    LocalDateTime end=LocalDateTime.parse(read(s,"结束时间"));
                    if(!start.isBefore(end)) throw new IllegalArgumentException("结束时间必须晚于开始时间");
                    boolean conflict=false;
                    for(Booking r:rows) if(r.device.equals(device)&&overlaps(start,end,r.start,r.end)) conflict=true;
                    if(conflict) { System.out.println("预约冲突，请选择其他时段"); continue; }
                    List<Booking> next=new ArrayList<>(rows);
                    Booking r=new Booking(UUID.randomUUID().toString(),device,user,start,end);
                    next.add(r); save(file,next); rows=next;
                    System.out.println("预约成功，编号："+r.id);
                } else if(cmd.equals("2")) {
                    rows.sort(Comparator.comparing(r -> r.start));
                    System.out.println("编号\t设备\t姓名\t开始\t结束");
                    for(Booking r:rows) System.out.println(r);
                } else if(cmd.equals("3")) {
                    String id=read(s,"要取消的完整预约编号");
                    List<Booking> next=new ArrayList<>(rows);
                    if(next.removeIf(r -> r.id.equals(id))) { save(file,next); rows=next; System.out.println("已取消"); }
                    else System.out.println("未找到预约");
                } else System.out.println("请输入菜单编号");
            } catch(RuntimeException | IOException ex) { System.out.println("操作失败："+ex.getMessage()); }
        }
    }
}
