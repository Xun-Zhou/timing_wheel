import com.utils.Timer;
import com.utils.TimerTask;

import java.util.concurrent.CountDownLatch;

public class Main {

    static int inCount = 0;

    static int runCount = 0;

    public static void main(String[] args) {
        CountDownLatch countDownLatch = new CountDownLatch(100);
        Timer timer = new Timer();
        for (int i = 1; i <= 100; i++) {
            TimerTask timerTask = new TimerTask(i, () -> {
                countDownLatch.countDown();
                addRun();
            });
            timer.addTask(timerTask);
            inCount++;
        }
        try {
            countDownLatch.await();
            System.out.println("inCount" + inCount);
            System.out.println("runCount" + runCount);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public synchronized static void addRun() {
        runCount++;
    }
}
