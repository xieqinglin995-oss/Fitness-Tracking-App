import java.nio.file.*;
import java.time.*;
import java.util.*;
import com.xieqinglin.fitness.backup.*;
import com.xieqinglin.fitness.domain.*;

public class FutureBench {
    static volatile Object sink;
    interface Task { Object run() throws Exception; }
    static String sample(Task task, int warmup, int rounds) throws Exception {
        for (int i=0;i<warmup;i++) sink=task.run();
        double[] ms=new double[rounds];
        for (int i=0;i<rounds;i++) { long start=System.nanoTime(); sink=task.run(); ms[i]=(System.nanoTime()-start)/1e6; }
        Arrays.sort(ms);
        return String.format(Locale.ROOT,"{\"median_ms\":%.3f,\"p95_ms\":%.3f,\"rounds\":%d}",ms[rounds/2],ms[(int)Math.ceil(rounds*.95)-1],rounds);
    }
    public static void main(String[] args) throws Exception {
        LocalDate today=LocalDate.of(2026,11,12);
        System.out.println("{\"java\":\""+System.getProperty("java.version")+"\",\"cases\":[");
        for (int n=0;n<args.length;n++) {
            String input=Files.readString(Path.of(args[n]));
            FitnessData data=BackupCodec.INSTANCE.decode(input,today).getData();
            String date=data.getPlans().get(data.getPlans().size()-1).getDate();
            if(n>0)System.out.println(",");
            System.out.print("{\"file\":\""+Path.of(args[n]).getFileName()+"\",\"weights\":"+data.getWeights().size()+",\"executions\":"+data.getExecutions().size());
            System.out.print(",\"validate_all\":"+sample(()->{ Rules.INSTANCE.validate(data,today);return data; },8,31));
            System.out.print(",\"decode_backup\":"+sample(()->BackupCodec.INSTANCE.decode(input,today),3,11));
            System.out.print(",\"encode_backup\":"+sample(()->BackupCodec.INSTANCE.encode(data),3,11));
            System.out.print(",\"trend_all\":"+sample(()->Statistics.INSTANCE.points(data.getWeights(),null,today),8,31));
            System.out.print(",\"calendar_month_status\":"+sample(()->{ for(int d=1;d<=30;d++) sink=ModelsKt.dayStatus(data,"2026-11-"+String.format(Locale.ROOT,"%02d",d));return sink; },8,31));
            System.out.print("}");
        }
        System.out.println("]}");
    }
}
