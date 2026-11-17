package ai.magpie.android;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class CoreProcessTest {
    public static void main(String[] args) throws Exception {
        CoreProcess stopped = new CoreProcess(); stopped.stop();
        try { stopped.start(new ProcessBuilder("/usr/bin/sleep", "30")); throw new AssertionError("launched after stop"); }
        catch (IOException expected) { }
        for (int i=0;i<30;i++) {
            CoreProcess owner = new CoreProcess();
            Thread t = new Thread(() -> {try{owner.start(new ProcessBuilder("/usr/bin/sleep","30"));}catch(IOException expected){}});
            t.start(); owner.stop(); t.join(); owner.close();
            if(owner.isAlive())throw new AssertionError("child survived concurrent stop");
        }
        CoreProcess running=new CoreProcess();Process child=running.start(new ProcessBuilder("/usr/bin/sleep","30"));
        if(!child.isAlive())throw new AssertionError("child did not start");running.close();
        if(!child.waitFor(2,TimeUnit.SECONDS))throw new AssertionError("child did not terminate");
        System.out.println("CoreProcess: stop-before-start, 30 concurrent stop races, and process reaping passed");
    }
}
