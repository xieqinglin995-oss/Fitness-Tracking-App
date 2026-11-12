import java.nio.file.*;
import java.time.*;
import java.util.*;
import com.xieqinglin.fitness.backup.*;
import com.xieqinglin.fitness.domain.*;

public class BoundaryProbe {
    public static void main(String[] args) throws Exception {
        LocalDate today=LocalDate.of(2026,11,12);
        FitnessData data=BackupCodec.INSTANCE.decode(Files.readString(Path.of(args[0])),today).getData();
        Rules.INSTANCE.validate(data,today);
        try {
            Rules.INSTANCE.validate(data,today.minusDays(1));
            System.out.println("clock_rollback: accepted");
        } catch (IllegalArgumentException e) {
            System.out.println("clock_rollback: rejected existing history ("+e.getClass().getSimpleName()+")");
        }
        Execution e=data.getExecutions().get(data.getExecutions().size()-1);
        Execution draft=new Execution(e.getId(),e.getDate(),e.getExerciseId(),e.getNameSnapshot(),e.getKindSnapshot(),e.getTargetSnapshot(),e.getActual(),ExecutionStatus.DRAFT,"",e.getUpdatedAt(),Collections.emptyList());
        FitnessData draftOnly=new FitnessData(data.getWeights(),data.getPlans(),Collections.singletonList(draft),data.getTemplates(),data.getSettings());
        long dateCount=draftOnly.getExecutions().stream().filter(x->x.getStatus()!=ExecutionStatus.SKIPPED).map(Execution::getDate).distinct().count();
        System.out.println("draft_only_training_date_count: "+dateCount);
        System.out.println("draft_only_week_trained_days: "+ComfortKt.weekOverview(draftOnly,today).getTrainedDays());
        Path recoveryDir=Files.createTempDirectory("fitness-audit-recovery-");
        RecoveryStore recovery=new RecoveryStore(recoveryDir.toFile());
        recovery.save(data,false);
        String name=recovery.points().get(0).getName();
        boolean changed=recoveryDir.resolve(name).toFile().setLastModified(System.currentTimeMillis()+86400000L);
        recovery.save(data,false);
        System.out.println("future_recovery_mtime_set: "+changed);
        System.out.println("recovery_count_after_next_save: "+recovery.points().size());
        Path controlDir=Files.createTempDirectory("fitness-audit-recovery-control-");
        RecoveryStore control=new RecoveryStore(controlDir.toFile());
        control.save(data,false);
        String controlName=control.points().get(0).getName();
        boolean controlChanged=controlDir.resolve(controlName).toFile().setLastModified(System.currentTimeMillis()-120000L);
        control.save(data,false);
        System.out.println("control_recovery_mtime_set: "+controlChanged);
        System.out.println("control_recovery_count_after_next_save: "+control.points().size());
    }
}
